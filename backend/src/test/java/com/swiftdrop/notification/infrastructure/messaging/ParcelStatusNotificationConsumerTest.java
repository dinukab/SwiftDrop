package com.swiftdrop.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.swiftdrop.notification.application.NotificationService;

class ParcelStatusNotificationConsumerTest {

    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARCEL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private NotificationService notifications;
    private ParcelStatusNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        consumer = new ParcelStatusNotificationConsumer(notifications);
    }

    @Test
    void theEventDataGoesToTheServiceAndUnknownFieldsAreIgnored() {
        consumer.onParcelStatusChanged(event("DELIVERED", "CUSTOMER", "\"" + CUSTOMER + "\""));

        verify(notifications).record(EVENT, PARCEL, "SD7K3M9Q2XWB", "DELIVERED", "CUSTOMER", CUSTOMER);
    }

    @Test
    void aMerchantEventIsHandedOverToo_thePolicyDecidesNotTheConsumer() {
        consumer.onParcelStatusChanged(event("DELIVERED", "MERCHANT", "null"));

        verify(notifications).record(EVENT, PARCEL, "SD7K3M9Q2XWB", "DELIVERED", "MERCHANT", null);
    }

    @Test
    void aMalformedEventIsRejectedWithoutRequeueSoItReachesTheDeadLetterQueue() {
        assertThatThrownBy(() -> consumer.onParcelStatusChanged("not json"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> consumer.onParcelStatusChanged("{\"eventId\":\"" + EVENT + "\"}"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> consumer.onParcelStatusChanged("{\"data\":{\"parcelId\":\"" + PARCEL + "\"}}"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> consumer.onParcelStatusChanged("null"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static String event(String status, String source, String ownerCustomerId) {
        return "{\"eventId\":\"" + EVENT + "\",\"eventType\":\"parcel.status-changed\",\"schemaVersion\":1,"
                + "\"occurredAt\":\"2026-11-21T10:00:00Z\",\"producer\":\"delivery\",\"extra\":true,"
                + "\"data\":{\"parcelId\":\"" + PARCEL + "\",\"trackingNumber\":\"SD7K3M9Q2XWB\","
                + "\"previousStatus\":\"PICKED_UP\",\"newStatus\":\"" + status + "\",\"sequence\":3,"
                + "\"source\":\"" + source + "\",\"ownerCustomerId\":" + ownerCustomerId + ","
                + "\"ownerMerchantId\":null,\"merchantOrderReference\":null,\"failureReasonCode\":null}}";
    }
}
