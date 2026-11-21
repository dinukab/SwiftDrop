package com.swiftdrop.merchant.infrastructure.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

class WebhookDnsResolverTest {

    @Test
    void aPublicAddressIsReturnedForConnecting() throws Exception {
        InetAddress[] resolved = new WebhookDnsResolver(false).resolve("8.8.8.8");

        assertThat(resolved).extracting(InetAddress::getHostAddress).containsExactly("8.8.8.8");
    }

    @Test
    void aPrivateAddressIsRefusedAtConnectTime() {
        assertThatThrownBy(() -> new WebhookDnsResolver(false).resolve("10.0.0.5"))
                .isInstanceOf(UnknownHostException.class)
                .hasMessage("Webhook host does not resolve to a public address");
    }

    @Test
    void localhostIsRefusedInProduction() {
        assertThatThrownBy(() -> new WebhookDnsResolver(false).resolve("localhost"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void developmentMayReachLocalhost() throws Exception {
        InetAddress[] resolved = new WebhookDnsResolver(true).resolve("localhost");

        assertThat(resolved).isNotEmpty();
        assertThat(resolved[0].isLoopbackAddress()).isTrue();
    }
}
