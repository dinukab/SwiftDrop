package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.URI;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** A new direct connection per attempt: no proxy, redirect, automatic retry or stale pooled socket. */
public final class WebhookHttpSender {
    private final WebhookDestination destination;
    private final Clock clock;
    private final javax.net.ssl.SSLContext tls;

    public WebhookHttpSender(WebhookDestination destination, Clock clock) {
        this(destination, clock, org.apache.hc.core5.ssl.SSLContexts.createDefault());
    }

    WebhookHttpSender(WebhookDestination destination, Clock clock, javax.net.ssl.SSLContext tls) {
        this.tls = tls;
        this.destination = destination;
        this.clock = clock;
    }

    public Result send(String url, UUID eventId, String payload, byte[] secret) {
        try {
            URI uri = destination.validate(url);
            var manager = new PoolingHttpClientConnectionManagerBuilder() {
                @Override
                protected org.apache.hc.client5.http.io.HttpClientConnectionOperator createConnectionOperator(
                        org.apache.hc.client5.http.SchemePortResolver ports,
                        org.apache.hc.client5.http.DnsResolver dns,
                        org.apache.hc.client5.http.ssl.TlsSocketStrategy strategy) {
                    // Default Java sockets consult SOCKS/system proxy settings even on a direct route.
                    return new org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator(
                            ignored -> new java.net.Socket(java.net.Proxy.NO_PROXY), ports, dns,
                            org.apache.hc.core5.http.config.RegistryBuilder
                                    .<org.apache.hc.client5.http.ssl.TlsSocketStrategy>create()
                                    .register("https", strategy).build());
                }
            }
                    .setDnsResolver(destination)
                    .setTlsSocketStrategy(org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder.create()
                            .setSslContext(tls).buildClassic())
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(5))
                            .setSocketTimeout(Timeout.ofSeconds(10)).build()).build();
            try (var client = HttpClients.custom().setConnectionManager(manager)
                    .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                    .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                    .disableContentCompression()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                            .setResponseTimeout(Timeout.ofSeconds(10)).build()).build()) {
                HttpPost post = new HttpPost(uri);
                long timestamp = clock.instant().getEpochSecond();
                post.setHeader("X-SwiftDrop-Event-Id", eventId.toString());
                post.setHeader("X-SwiftDrop-Event-Type", "parcel.status-changed");
                post.setHeader("X-SwiftDrop-Timestamp", Long.toString(timestamp));
                post.setHeader("X-SwiftDrop-Signature", WebhookSecrets.sign(secret, timestamp, payload));
                post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
                // Immediate close avoids draining an unbounded or slow response body.
                try (var response = client.execute(post)) {
                    int status = response.getCode();
                    var retry = response.getFirstHeader("Retry-After");
                    long retryAfter = retry == null ? 0 : retryAfter(retry.getValue());
                    response.close(CloseMode.IMMEDIATE);
                    return new Result(status >= 200 && status < 300 ? "SUCCESS" : "HTTP_ERROR",
                            status, status >= 500 && status < 600 || status == 408 || status == 429, retryAfter);
                }
            }
        } catch (WebhookDestination.Refused ex) {
            return new Result("REFUSED_DESTINATION", null, false, 0);
        } catch (java.net.SocketTimeoutException ex) {
            return new Result("TIMEOUT", null, true, 0);
        } catch (java.io.IOException ex) {
            return new Result("NETWORK_ERROR", null, true, 0);
        }
    }

    private long retryAfter(String value) {
        try { return Math.max(0, Math.min(3600, Long.parseLong(value))); }
        catch (NumberFormatException ex) {
            try {
                return Math.max(0, Math.min(3600, ZonedDateTime.parse(value,
                        DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().getEpochSecond()
                        - clock.instant().getEpochSecond()));
            } catch (RuntimeException ignored) { return 0; }
        }
    }

    public record Result(String outcome, Integer status, boolean retryable, long retryAfterSeconds) {}
}
