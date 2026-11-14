package com.swiftdrop.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.notification.infrastructure.messaging.NotificationStatusConsumer;

class StatusConsumerValidationTest {
    @Test
    void malformedEventIsRejectedWithoutDatabaseEffects() {
        var jdbc = mock(JdbcTemplate.class);
        var transactions = mock(TransactionTemplate.class);
        var consumer = new NotificationStatusConsumer(jdbc, transactions, mock(ParcelEvents.class),
                new SimpleMeterRegistry());
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        assertThatThrownBy(() -> consumer.receive(new Message("{invalid".getBytes(StandardCharsets.UTF_8), properties)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageNotContaining("{invalid")
                .hasNoCause();
        verifyNoInteractions(jdbc, transactions);
    }

    @Test
    void nonJsonIsRejectedImmediately() {
        var transactions = mock(TransactionTemplate.class);
        var consumer = new NotificationStatusConsumer(mock(JdbcTemplate.class), transactions,
                mock(ParcelEvents.class), new SimpleMeterRegistry());
        assertThatThrownBy(() -> consumer.receive(new Message(new byte[0])))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(transactions);
    }
}
