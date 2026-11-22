package com.swiftdrop.merchant.infrastructure.webhooks;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import com.swiftdrop.merchant.application.WebhookTransport;

/**
 * Apache HttpClient 5 transport for webhooks (ADR 0004 section 7.3). Connect within 5 seconds, respond
 * within 10 seconds, never follow a redirect, never retry on its own (the sender owns retries), keep no
 * cookies, and connect only to addresses the resolver accepted.
 */
@Component
public class HttpWebhookTransport implements WebhookTransport, AutoCloseable {

    static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(5);
    static final Timeout RESPONSE_TIMEOUT = Timeout.ofSeconds(10);

    private final CloseableHttpClient client;

    public HttpWebhookTransport(WebhookDnsResolver resolver) {
        PoolingHttpClientConnectionManager connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(CONNECT_TIMEOUT)
                        .setSocketTimeout(RESPONSE_TIMEOUT)
                        .build())
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(RESPONSE_TIMEOUT).build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    @Override
    public Result post(URI uri, Map<String, String> headers, String body) {
        HttpPost post = new HttpPost(uri);
        headers.forEach(post::setHeader);
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        try {
            return client.execute(post, response -> new Result(response.getCode()));
        } catch (IOException e) {
            // No response: refused, timed out, or the address was not allowed. The message is not
            // logged, because it can carry the host name.
            return new Result(null);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
