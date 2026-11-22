package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationPolicyTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @ParameterizedTest
    @ValueSource(strings = {"PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"})
    void aCustomersParcelNotifiesForTheStatusesAPersonCaresAbout(String status) {
        var message = NotificationPolicy.messageFor(status, "CUSTOMER", CUSTOMER, "SD7K3M9Q2XWB");

        assertThat(message).isPresent();
        assertThat(message.get()).contains("SD7K3M9Q2XWB");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "IN_TRANSIT", "HUB_ARRIVED", "HUB_SORTED", "PAUSED", "CANCELLED", "UNKNOWN"})
    void quietStatusesAndHubMovesNotifyNobody(String status) {
        assertThat(NotificationPolicy.messageFor(status, "CUSTOMER", CUSTOMER, "SD7K3M9Q2XWB")).isEmpty();
    }

    @Test
    void aMerchantParcelNeverNotifiesBecauseTheMerchantGetsWebhooks() {
        assertThat(NotificationPolicy.messageFor("DELIVERED", "MERCHANT", null, "SD7K3M9Q2XWB")).isEmpty();
        // Even with an owner id present, only customer-created parcels notify
        assertThat(NotificationPolicy.messageFor("DELIVERED", "MERCHANT", CUSTOMER, "SD7K3M9Q2XWB")).isEmpty();
    }

    @Test
    void incompleteEventDataNotifiesNobody() {
        assertThat(NotificationPolicy.messageFor("DELIVERED", "CUSTOMER", null, "SD7K3M9Q2XWB")).isEmpty();
        assertThat(NotificationPolicy.messageFor(null, "CUSTOMER", CUSTOMER, "SD7K3M9Q2XWB")).isEmpty();
        assertThat(NotificationPolicy.messageFor("DELIVERED", "CUSTOMER", CUSTOMER, null)).isEmpty();
    }

    @Test
    void theMessageNamesTheTrackingNumberAndNothingPersonal() {
        String message = NotificationPolicy.messageFor("OUT_FOR_DELIVERY", "CUSTOMER", CUSTOMER, "SD7K3M9Q2XWB")
                .orElseThrow();

        assertThat(message).isEqualTo("Your parcel SD7K3M9Q2XWB is out for delivery.");
    }
}
