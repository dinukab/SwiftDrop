package com.swiftdrop.merchant.infrastructure.webhooks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.hc.client5.http.DnsResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves a webhook host to public addresses only, at the moment each connection is made (ADR 0004
 * section 7.3). A host that resolves to a private address, or to nothing public, is refused, so a
 * merchant cannot point a webhook at the platform's own network by naming it in DNS. Only the
 * addresses that passed are used to connect.
 */
@Component
public class WebhookDnsResolver implements DnsResolver {

    private final boolean allowLocal;

    public WebhookDnsResolver(@Value("${swiftdrop.webhooks.allow-local:false}") boolean allowLocal) {
        this.allowLocal = allowLocal;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] all = InetAddress.getAllByName(host);
        if (allowLocal) {
            return all;
        }
        List<InetAddress> publicOnes = java.util.Arrays.stream(all).filter(AddressPolicy::isPublic).toList();
        if (publicOnes.isEmpty()) {
            throw new UnknownHostException("Webhook host does not resolve to a public address");
        }
        return publicOnes.toArray(InetAddress[]::new);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return host;
    }
}
