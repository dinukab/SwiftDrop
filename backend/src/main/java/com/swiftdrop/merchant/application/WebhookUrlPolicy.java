package com.swiftdrop.merchant.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.swiftdrop.merchant.infrastructure.webhooks.AddressPolicy;

/**
 * The rules a webhook URL must pass (ADR 0004 section 7.3). Checked when the endpoint is registered,
 * and again before every send. Public addresses are checked at connect time too, by the DNS resolver,
 * because a name can point somewhere else later.
 *
 * <p>Production: {@code https} on port 443, no credentials in the URL, no private or reserved address
 * written as an IP. Development: {@code SWIFTDROP_WEBHOOK_ALLOW_LOCAL=true} also allows {@code http} and
 * loopback, so a store on {@code localhost} can receive webhooks. Off by default.
 */
@Component
public class WebhookUrlPolicy {

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final boolean allowLocal;

    public WebhookUrlPolicy(@Value("${swiftdrop.webhooks.allow-local:false}") boolean allowLocal) {
        this.allowLocal = allowLocal;
    }

    public boolean allowLocal() {
        return allowLocal;
    }

    /**
     * @return the parsed URL
     * @throws IllegalArgumentException with a message that names the broken rule, never the URL
     */
    public URI requireAllowed(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid webhook URL");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (allowLocal) {
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("Webhook URL must use http or https");
            }
        } else if (!scheme.equals("https")) {
            throw new IllegalArgumentException("Webhook URL must use https");
        }

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Webhook URL must not contain credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must contain a host");
        }

        if (!allowLocal) {
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                throw new IllegalArgumentException("Webhook URL must use port 443");
            }
            String bare = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
            if (bare.equalsIgnoreCase("localhost")) {
                throw new IllegalArgumentException("Webhook URL must not point to a private address");
            }
            if (IPV4_LITERAL.matcher(bare).matches() || bare.contains(":")) {
                if (!AddressPolicy.isLiteralPublic(bare)) {
                    throw new IllegalArgumentException("Webhook URL must not point to a private address");
                }
            }
        }
        return uri;
    }
}
