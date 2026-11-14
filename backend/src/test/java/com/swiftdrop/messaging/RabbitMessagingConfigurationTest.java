package com.swiftdrop.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitMessagingConfigurationTest {

    @Test
    void declaresDurableExchangeAndIndependentConsumerQueuesWithSafeDeadLetters() {
        MessagingTestContext.runner().run(context -> {
            assertThat(context).hasNotFailed();
            TopicExchange events = context.getBean(TopicExchange.class);
            assertThat(events.getName()).isEqualTo("swiftdrop.events");
            assertThat(events.isDurable()).isTrue();
            assertThat(events.isAutoDelete()).isFalse();

            List<Declarable> declarations = context.getBeansOfType(Declarables.class).values()
                    .stream().map(Declarables::getDeclarables).flatMap(Collection::stream).toList();
            assertThat(declarations.stream().filter(Queue.class::isInstance)).hasSize(4);
            for (String consumer : List.of("merchant", "notification")) {
                String name = consumer + ".parcel-status-changed";
                Queue source = queue(declarations, name);
                assertThat(source.isDurable()).isTrue();
                assertThat(source.isAutoDelete()).isFalse();
                assertThat(source.isExclusive()).isFalse();
                assertThat(source.getArguments()).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "x-queue-type", "quorum", "x-delivery-limit", 5,
                        "x-max-length", 100000L, "x-overflow", "reject-publish",
                        "x-dead-letter-exchange", "swiftdrop.dead-letter",
                        "x-dead-letter-routing-key", name,
                        "x-dead-letter-strategy", "at-least-once"));

                Queue dlq = queue(declarations, name + ".dlq");
                assertThat(dlq.isDurable()).isTrue();
                assertThat(dlq.getArguments()).containsExactlyEntriesOf(Map.of("x-queue-type", "quorum"));
                List<Binding> bindings = declarations.stream().filter(Binding.class::isInstance)
                        .map(Binding.class::cast).filter(b -> b.getDestination().startsWith(name)).toList();
                assertThat(bindings).hasSize(2);
                assertThat(bindings).anySatisfy(b -> {
                    assertThat(b.getDestination()).isEqualTo(name);
                    assertThat(b.getExchange()).isEqualTo("swiftdrop.events");
                    assertThat(b.getRoutingKey()).isEqualTo("parcel.status-changed");
                }).anySatisfy(b -> {
                    assertThat(b.getDestination()).isEqualTo(name + ".dlq");
                    assertThat(b.getExchange()).isEqualTo("swiftdrop.dead-letter");
                    assertThat(b.getRoutingKey()).isEqualTo(name);
                });
            }
            List<DirectExchange> deadLetterExchanges = declarations.stream()
                    .filter(DirectExchange.class::isInstance).map(DirectExchange.class::cast).toList();
            assertThat(deadLetterExchanges).hasSize(2).allSatisfy(exchange -> {
                assertThat(exchange.getName()).isEqualTo("swiftdrop.dead-letter");
                assertThat(exchange.isDurable()).isTrue();
                assertThat(exchange.isAutoDelete()).isFalse();
            });
        });
    }

    @Test
    void enablesCorrelatedConfirmsReturnsAndMandatoryPublishingWithoutConnecting() {
        MessagingTestContext.runner().run(context -> {
            assertThat(context).hasNotFailed();
            CachingConnectionFactory factory = context.getBean(CachingConnectionFactory.class);
            assertThat(factory.isPublisherConfirms()).isTrue();
            assertThat(factory.isPublisherReturns()).isTrue();
            assertThat(context.getBean(RabbitTemplate.class).isMandatoryFor(new Message(new byte[0])))
                    .isTrue();
            assertThat(context.getEnvironment().getProperty(
                    "spring.rabbitmq.listener.simple.default-requeue-rejected", Boolean.class)).isFalse();
        });
    }

    @Test
    void acceptsExplicitQueueTuning() {
        MessagingTestContext.runner().withPropertyValues("swiftdrop.messaging.delivery-limit=10",
                "swiftdrop.messaging.queue-max-length=2000").run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBeansOfType(Declarables.class).values().stream()
                            .map(Declarables::getDeclarables).flatMap(Collection::stream)
                            .filter(Queue.class::isInstance).map(Queue.class::cast)
                            .filter(q -> !q.getName().endsWith(".dlq")).forEach(q -> {
                                assertThat(q.getArguments()).containsEntry("x-delivery-limit", 10)
                                        .containsEntry("x-max-length", 2000L);
                            });
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 21})
    void rejectsDeliveryLimitOutsideAdrBounds(int limit) {
        MessagingTestContext.runner().withPropertyValues("swiftdrop.messaging.delivery-limit=" + limit)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsUnboundedOrNegativeQueueLength(int length) {
        MessagingTestContext.runner().withPropertyValues("swiftdrop.messaging.queue-max-length=" + length)
                .run(context -> assertThat(context).hasFailed());
    }

    private Queue queue(List<Declarable> declarations, String name) {
        return declarations.stream().filter(Queue.class::isInstance).map(Queue.class::cast)
                .filter(q -> q.getName().equals(name)).findFirst().orElseThrow();
    }
}
