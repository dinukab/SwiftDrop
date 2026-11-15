import http from 'node:http';
import { createHmac, timingSafeEqual } from 'node:crypto';
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Local demo only. Event IDs and sequences persist so restarts do not repeat accepted effects.
export function createReceiver({ secret, stateFile, failFirst = 0, now = () => Date.now(), log = () => {} }) {
  if (!Buffer.isBuffer(secret) || secret.length !== 32) throw new Error('A decoded 32-byte webhook secret is required');
  if (!Number.isInteger(failFirst) || failFirst < 0 || failFirst > 8) throw new Error('failFirst must be 0..8');
  let state = { events: {}, parcels: {}, accepted: 0 };
  if (stateFile) {
    try { state = JSON.parse(readFileSync(stateFile, 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT') throw new Error('Cannot read receiver state'); }
  }
  let remainingFailures = failFirst;
  function persist(next) {
    if (stateFile) {
      mkdirSync(dirname(stateFile), { recursive: true });
      writeFileSync(stateFile + '.tmp', JSON.stringify(next), { mode: 0o600 });
      renameSync(stateFile + '.tmp', stateFile);
    }
    state = next;
  }
  const server = http.createServer(async (request, response) => {
    const reply = (status, result) => {
      response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      response.end(JSON.stringify({ result }));
    };
    if (request.url === '/health' && request.method === 'GET') return reply(200, 'ready');
    if (request.url !== '/webhooks' || request.method !== 'POST') return reply(404, 'not_found');
    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of request) {
        size += chunk.length;
        if (size > 65536) { reply(413, 'too_large'); request.destroy(); return; }
        chunks.push(chunk);
      }
      const body = Buffer.concat(chunks);
      const timestamp = request.headers['x-swiftdrop-timestamp'];
      const signature = request.headers['x-swiftdrop-signature'];
      if (typeof timestamp !== 'string' || !/^\d{1,12}$/.test(timestamp)
          || Math.abs(Math.floor(now() / 1000) - Number(timestamp)) > 300
          || typeof signature !== 'string' || !/^v1=[0-9a-f]{64}$/.test(signature)) return reply(401, 'unauthorized');
      const expected = createHmac('sha256', secret).update(timestamp + '.').update(body).digest();
      if (!timingSafeEqual(expected, Buffer.from(signature.slice(3), 'hex'))) return reply(401, 'unauthorized');
      let event;
      try { event = JSON.parse(body.toString('utf8')); } catch { return reply(400, 'invalid_event'); }
      if (!uuid.test(event.eventId ?? '') || !uuid.test(event.data?.parcelId ?? '')
          || !Number.isSafeInteger(event.data?.sequence) || event.data.sequence < 1
          || event.schemaVersion !== 1 || event.eventType !== 'parcel.status-changed'
          || event.producer !== 'delivery'
          || request.headers['x-swiftdrop-event-id'] !== event.eventId
          || request.headers['x-swiftdrop-event-type'] !== event.eventType) return reply(400, 'invalid_event');
      const duplicate = Object.hasOwn(state.events, event.eventId);
      if (duplicate) return reply(200, 'duplicate');
      if (remainingFailures > 0) {
        remainingFailures--;
        log({ eventId: event.eventId, outcome: 'simulated_failure' });
        response.setHeader('Retry-After', '2');
        return reply(503, 'simulated_failure');
      }
      const next = structuredClone(state);
      const previous = next.parcels[event.data.parcelId] ?? 0;
      const outcome = event.data.sequence > previous ? 'accepted' : 'older_sequence';
      if (outcome === 'accepted') {
        next.parcels[event.data.parcelId] = event.data.sequence;
        next.accepted++;
      }
      next.events[event.eventId] = Math.floor(now() / 1000);
      // Retain IDs for 24h, longer than the signature tolerance. Highest sequence persists.
      for (const [id, seenAt] of Object.entries(next.events)) {
        if (seenAt < Math.floor(now() / 1000) - 86400) delete next.events[id];
      }
      persist(next);
      log({ eventId: event.eventId, sequence: event.data.sequence, outcome });
      return reply(200, outcome);
    } catch {
      if (!response.headersSent) reply(500, 'receiver_error');
      else response.destroy();
    }
  });
  server.requestTimeout = 10000;
  server.headersTimeout = 5000;
  return { server, snapshot: () => structuredClone(state) };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const encoded = process.env.SWIFTDROP_DEMO_WEBHOOK_SECRET ?? '';
    if (!/^[A-Za-z0-9+/]{43}=$/.test(encoded)) throw new Error('Set SWIFTDROP_DEMO_WEBHOOK_SECRET to the one-time Base64 secret');
    const port = Number(process.env.SWIFTDROP_DEMO_PORT ?? 9090);
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid demo port');
    const { server } = createReceiver({
      secret: Buffer.from(encoded, 'base64'),
      stateFile: resolve('.local/webhook-demo/state.json'),
      failFirst: Number(process.env.SWIFTDROP_DEMO_FAIL_FIRST ?? 2),
      log: entry => console.log(JSON.stringify(entry)),
    });
    server.listen(port, '127.0.0.1', () => console.log('Webhook demo listening on http://127.0.0.1:' + port + '/webhooks'));
    const stop = () => server.close(() => process.exit(0));
    process.on('SIGINT', stop);
    process.on('SIGTERM', stop);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
