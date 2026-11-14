package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WebhookHttpSenderTest {
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1234), ZoneOffset.UTC);
    private final byte[] secret = new byte[32];
    private final UUID event = UUID.randomUUID();
    private HttpServer server;
    @TempDir Path temp;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void directConnectionUsesResolverAndSendsExactSignedBodyWithoutProxy() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> eventHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            signature.set(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"));
            eventHeader.set(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Event-Id"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        var destination = new WebhookDestination(true, Set.of(), host -> {
            resolutions.incrementAndGet();
            // Only the checked 127.0.0.1 is listening; never rely on localhost's platform resolution.
            return new InetAddress[] {InetAddress.getByAddress(new byte[] {127,0,0,1})};
        });
        var original = java.net.ProxySelector.getDefault();
        java.net.ProxySelector.setDefault(new java.net.ProxySelector() {
            @Override public java.util.List<java.net.Proxy> select(java.net.URI uri) {
                throw new AssertionError("Webhook must never consult system proxy routing");
            }
            @Override public void connectFailed(java.net.URI uri, java.net.SocketAddress address, java.io.IOException ex) {}
        });
        try {
            assertThat(new WebhookHttpSender(destination, clock).send(url("http", "localhost"), event, "{\"sequence\":1}", secret).outcome())
                    .isEqualTo("SUCCESS");
        } finally { java.net.ProxySelector.setDefault(original); }
        assertThat(resolutions.get()).isEqualTo(2); // URL precheck + sole connection resolver.
        assertThat(body.get()).isEqualTo("{\"sequence\":1}");
        assertThat(eventHeader.get()).isEqualTo(event.toString());
        assertThat(signature.get()).isEqualTo(WebhookSecrets.sign(secret, 1234, body.get()));
    }

    @Test void rebindingAtConnectionIsRejectedAndNoRequestEscapes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var destination = new WebhookDestination(false, Set.of(), host -> new InetAddress[] {
                InetAddress.getByName(calls.incrementAndGet() == 1 ? "8.8.8.8" : "127.0.0.1")});
        var result = new WebhookHttpSender(destination, clock).send("https://rebind.invalid/hook", event, "{}", secret);
        assertThat(result.outcome()).isEqualTo("REFUSED_DESTINATION");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void redirectsAreTerminalAndConnectionsAreRevalidatedOnNextAttempt() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        AtomicInteger resolutions = new AtomicInteger();
        var destination = new WebhookDestination(true, Set.of(), host -> new InetAddress[] {
                InetAddress.getByName(resolutions.incrementAndGet() <= 2 ? "127.0.0.1" : "10.0.0.1")});
        var sender = new WebhookHttpSender(destination, clock);
        var first = sender.send(url("http", "localhost"), event, "{}", secret);
        assertThat(first.status()).isEqualTo(302);
        assertThat(first.retryable()).isFalse();
        assertThat(sender.send(url("http", "localhost"), event, "{}", secret).outcome()).isEqualTo("REFUSED_DESTINATION");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void tlsRequiresTrustedCertificateAndMatchingHostname() throws Exception {
        Path store = temp.resolve("ephemeral-test.p12");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", "test-only-password", "-noprompt")
                .redirectErrorStream(true).redirectOutput(temp.resolve("keytool.log").toFile()).start();
        assertThat(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var input = java.nio.file.Files.newInputStream(store)) { keys.load(input, "test-only-password".toCharArray()); }
        var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, "test-only-password".toCharArray());
        var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(keys);
        SSLContext trusted = SSLContext.getInstance("TLS");
        trusted.init(km.getKeyManagers(), tm.getTrustManagers(), null);
        var https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(trusted));
        https.createContext("/", exchange -> { exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server = https; server.start();
        var destination = new WebhookDestination(true, Set.of());
        assertThat(new WebhookHttpSender(destination, clock).send(url("https", "localhost"), event, "{}", secret).outcome())
                .isEqualTo("NETWORK_ERROR");
        var sender = new WebhookHttpSender(destination, clock, trusted);
        assertThat(sender.send(url("https", "localhost"), event, "{}", secret).outcome()).isEqualTo("SUCCESS");
        assertThat(sender.send(url("https", "127.0.0.1"), event, "{}", secret).outcome()).isEqualTo("NETWORK_ERROR");
    }

    @Test void retryAfterIsCappedAndOnlyTransientStatusesRetry() throws Exception {
        AtomicInteger status = new AtomicInteger(429);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "99999");
            exchange.sendResponseHeaders(status.get(), -1); exchange.close();
        });
        server.start();
        var sender = new WebhookHttpSender(new WebhookDestination(true, Set.of()), clock);
        var result = sender.send(url("http", "localhost"), event, "{}", secret);
        assertThat(result.retryAfterSeconds()).isEqualTo(3600);
        assertThat(result.retryable()).isTrue();
        for (int code : new int[] {408,500,503}) {
            status.set(code);
            assertThat(sender.send(url("http", "localhost"), event, "{}", secret).retryable()).isTrue();
        }
        for (int code : new int[] {301,400,401,404}) {
            status.set(code);
            assertThat(sender.send(url("http", "localhost"), event, "{}", secret).retryable()).isFalse();
        }
    }

    private String url(String scheme, String host) { return scheme + "://" + host + ":" + server.getAddress().getPort() + "/"; }
}
