package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class WebhookDestinationTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.1.2.3","10.2.3.4","100.64.0.1","127.0.0.1","169.254.169.254",
            "172.16.0.1","192.0.0.1","192.0.2.1","192.168.1.1","198.18.1.1","198.51.100.1",
            "203.0.113.1","224.0.0.1","255.255.255.255","::","::1","fc00::1","fe80::1",
            "fec0::1","ff00::1","2001:db8::1","::ffff:127.0.0.1","64:ff9b::a00:1",
            "2002:7f00:1::1","2001:0::1","::192.168.1.1","2130706433","127.1"})
    void prohibitedAddressesAreRefused(String ip) throws Exception {
        assertThat(WebhookDestination.prohibited(InetAddress.getByName(ip).getAddress())).isTrue();
    }

    @Test void mixedAnswersAreRejectedAndPublicAnswersAreReturnedUnchanged() throws Exception {
        var publicIp = InetAddress.getByName("8.8.8.8");
        var privateIp = InetAddress.getByName("10.0.0.1");
        var mixed = new WebhookDestination(false, Set.of(), host -> new InetAddress[] {publicIp, privateIp});
        assertThatThrownBy(() -> mixed.resolve("merchant.example")).isInstanceOf(WebhookDestination.Refused.class);
        var allowed = new WebhookDestination(false, Set.of(), host -> new InetAddress[] {publicIp});
        assertThat(allowed.resolve("merchant.example")).containsExactly(publicIp);
        assertThat(WebhookDestination.prohibited(InetAddress.getByName("2606:4700:4700::1111").getAddress())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "https://user@example.com", "https://example.com#frag",
            "https://example.com:8443", "//example.com", "file:///tmp/file", "https://2130706433",
            "https://127.1", "https://[::ffff:127.0.0.1]"})
    void invalidUrlsAndAlternateLoopbackFormsAreRefused(String url) {
        assertThatThrownBy(() -> new WebhookDestination(false, Set.of()).validate(url))
                .isInstanceOf(WebhookDestination.Refused.class);
    }

    @Test void localModeAllowsOnlyExplicitLoopbackAndDeniedHostsAlwaysWin() {
        var local = new WebhookDestination(true, Set.of());
        assertThat(local.validate("http://127.0.0.1:9876/hook").getPort()).isEqualTo(9876);
        assertThatThrownBy(() -> local.validate("http://192.168.1.1/hook")).isInstanceOf(WebhookDestination.Refused.class);
        assertThatThrownBy(() -> new WebhookDestination(true, Set.of("localhost")).validate("http://localhost"))
                .isInstanceOf(WebhookDestination.Refused.class);
    }
}
