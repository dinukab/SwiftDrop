package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WebhookRetryPolicyTest {
    @Test void defaultsHaveBoundedExponentialJitterAndRetryAfter() {
        var policy = WebhookRetryPolicy.defaults();
        assertThat(policy.delay(1, 0, .8)).isEqualTo(24);
        assertThat(policy.delay(2, 0, 1.2)).isEqualTo(72);
        assertThat(policy.delay(1, 300, .8)).isEqualTo(300);
        assertThat(policy.delay(15, Long.MAX_VALUE, 1.2)).isEqualTo(3600);
        assertThat(new WebhookRetryPolicy(3, 2, 30, 1).delay(1, 0, 1)).isEqualTo(2);
    }

    @Test void invalidSettingsFailStartupRatherThanAllowingUnboundedRetries() {
        assertThatThrownBy(() -> new WebhookRetryPolicy(2, 30, 3600, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookRetryPolicy(16, 30, 3600, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookRetryPolicy(8, 0, 3600, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookRetryPolicy(8, 301, 3600, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookRetryPolicy(8, 30, 3601, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookRetryPolicy(8, 30, 10, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookRetryPolicy(8, 30, 3600, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
