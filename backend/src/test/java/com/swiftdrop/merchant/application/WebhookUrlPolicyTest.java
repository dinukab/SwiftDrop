package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookUrlPolicyTest {

    private final WebhookUrlPolicy production = new WebhookUrlPolicy(false);
    private final WebhookUrlPolicy development = new WebhookUrlPolicy(true);

    @Test
    void anHttpsUrlOnThePublicInternetIsAllowed() {
        assertThat(production.requireAllowed("https://api.acme.com/webhooks").toString())
                .isEqualTo("https://api.acme.com/webhooks");
        assertThat(production.requireAllowed("https://93.184.216.34/hook").getHost()).isEqualTo("93.184.216.34");
        assertThat(production.requireAllowed("https://api.acme.com:443/webhooks").getPort()).isEqualTo(443);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://api.acme.com/webhooks",
            "ftp://api.acme.com/webhooks",
            "api.acme.com/webhooks"})
    void onlyHttpsIsAllowedInProduction(String url) {
        assertThatThrownBy(() -> production.requireAllowed(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void credentialsInTheUrlAreRefused() {
        assertThatThrownBy(() -> production.requireAllowed("https://user:pass@api.acme.com/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook URL must not contain credentials");
    }

    @Test
    void aNonStandardPortIsRefusedInProduction() {
        assertThatThrownBy(() -> production.requireAllowed("https://api.acme.com:8443/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook URL must use port 443");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://localhost/hook",
            "https://127.0.0.1/hook",
            "https://10.0.0.5/hook",
            "https://192.168.1.10/hook",
            "https://169.254.169.254/latest/meta-data",
            "https://[::1]/hook",
            "https://[fc00::1]/hook"})
    void privateAndLocalAddressesAreRefusedInProduction(String url) {
        assertThatThrownBy(() -> production.requireAllowed(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook URL must not point to a private address");
    }

    @Test
    void developmentMayUseHttpAndLocalhostOnly() {
        assertThat(development.requireAllowed("http://localhost:3100/api/webhooks/swiftdrop").getPort())
                .isEqualTo(3100);
        assertThat(development.allowLocal()).isTrue();
        assertThatThrownBy(() -> development.requireAllowed("ftp://localhost/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook URL must use http or https");
    }

    @Test
    void theRejectionNeverEchoesTheUrl() {
        assertThatThrownBy(() -> production.requireAllowed("http://secret-host.internal/token=abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret-host", "token"));
    }
}
