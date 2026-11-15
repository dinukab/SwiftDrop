import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHmac, randomBytes, randomUUID } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname, resolve, basename } from 'node:path';
import { once } from 'node:events';
import { createReceiver } from './receiver.mjs';

const secret = randomBytes(32);
const timestamp = 1800000000;
const event = (parcelId = randomUUID(), sequence = 1) => ({
  eventId: randomUUID(), eventType: 'parcel.status-changed', schemaVersion: 1,
  producer: 'delivery', data: { parcelId, sequence },
});
async function start(options = {}) {
  const receiver = createReceiver({ secret, now: () => timestamp * 1000, ...options });
  receiver.server.listen(0, '127.0.0.1');
  await once(receiver.server, 'listening');
  return { ...receiver, url: 'http://127.0.0.1:' + receiver.server.address().port + '/webhooks' };
}
async function close(receiver) { receiver.server.close(); await once(receiver.server, 'close'); }
async function send(receiver, value, overrides = {}) {
  const body = JSON.stringify(value);
  return fetch(receiver.url, { method: 'POST', body, headers: {
    'Content-Type': 'application/json',
    'X-SwiftDrop-Event-Id': value.eventId,
    'X-SwiftDrop-Event-Type': value.eventType,
    'X-SwiftDrop-Timestamp': String(timestamp),
    'X-SwiftDrop-Signature': 'v1=' + createHmac('sha256', secret).update(timestamp + '.' + body).digest('hex'),
    ...overrides,
  } });
}

test('two failures then success; duplicates and older sequences never repeat effects, including after restart', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'swiftdrop-demo-'));
  const stateFile = join(dir, 'state.json');
  let receiver = await start({ stateFile, failFirst: 2 });
  try {
    const value = event(randomUUID(), 2);
    assert.equal((await send(receiver, value)).status, 503);
    assert.equal((await send(receiver, value)).status, 503);
    assert.equal((await (await send(receiver, value)).json()).result, 'accepted');
    assert.equal((await (await send(receiver, value)).json()).result, 'duplicate');
    assert.equal((await (await send(receiver, event(value.data.parcelId, 1))).json()).result, 'older_sequence');
    assert.equal(receiver.snapshot().accepted, 1);
    await close(receiver);
    receiver = await start({ stateFile });
    assert.equal((await (await send(receiver, value)).json()).result, 'duplicate');
    assert.equal(receiver.snapshot().accepted, 1);
  } finally {
    await close(receiver);
    assert.equal(dirname(resolve(dir)), resolve(tmpdir()));
    assert.ok(basename(dir).startsWith('swiftdrop-demo-'));
    rmSync(dir, { recursive: true, force: true });
  }
});

test('bad signatures, stale timestamps and mismatched event IDs are rejected without an effect', async () => {
  const receiver = await start();
  try {
    const value = event();
    assert.equal((await send(receiver, value, { 'X-SwiftDrop-Signature': 'v1=' + '0'.repeat(64) })).status, 401);
    assert.equal((await send(receiver, value, { 'X-SwiftDrop-Timestamp': String(timestamp - 301) })).status, 401);
    assert.equal((await send(receiver, value, { 'X-SwiftDrop-Event-Id': randomUUID() })).status, 400);
    assert.equal(receiver.snapshot().accepted, 0);
  } finally { await close(receiver); }
});

test('invalid configuration fails closed', () => {
  assert.throws(() => createReceiver({ secret: Buffer.alloc(16) }));
  assert.throws(() => createReceiver({ secret, failFirst: 99 }));
});
