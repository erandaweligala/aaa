package com.csg.airtel.aaa4j.application.listener;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.domain.service.AccountingHandlerFactory;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingConsumerTest {

    @Mock
    private AccountProducer accountProducer;

    @Mock
    private AccountingHandlerFactory accountingHandlerFactory;

    @InjectMocks
    private AccountingConsumer accountingConsumer;

    private AccountingRequestDto createTestRequest(String sessionId, String username) {
        return new AccountingRequestDto(
                "eventId1",
                sessionId,
                "192.168.1.1",
                username,
                AccountingRequestDto.ActionType.START,
                0,
                0,
                0,
                Instant.now(),
                "port1",
                "10.0.0.1",
                0,
                0,
                0,
                "nas1"
        );
    }

    private Message<AccountingRequestDto> createMessage(AccountingRequestDto request) {
        return new Message<AccountingRequestDto>() {
            @Override
            public AccountingRequestDto getPayload() {
                return request;
            }

            @Override
            public Metadata getMetadata() {
                return Metadata.empty();
            }

            @Override
            public CompletionStage<Void> ack() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> nack(Throwable reason) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    @Test
    void testConsumeAccountingEvent_Success() {
        // Given
        AccountingRequestDto request = createTestRequest("session1", "testUser");
        Message<AccountingRequestDto> message = createMessage(request);

        when(accountingHandlerFactory.getHandler(eq(request), eq("eventId1")))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingConsumer
                .consumeAccountingEvent(message)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingHandlerFactory).getHandler(eq(request), eq("eventId1"));
    }

    @Test
    void testConsumeAccountingEvent_HandlerFailure_Nack() {
        // Given
        AccountingRequestDto request = createTestRequest("session1", "testUser");
        Message<AccountingRequestDto> message = createMessage(request);

        RuntimeException testException = new RuntimeException("Handler failed");
        when(accountingHandlerFactory.getHandler(eq(request), eq("eventId1")))
                .thenReturn(Uni.createFrom().failure(testException));

        // When
        UniAssertSubscriber<Void> subscriber = accountingConsumer
                .consumeAccountingEvent(message)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingHandlerFactory).getHandler(eq(request), eq("eventId1"));
    }

    @Test
    void testConsumeAccountingEvent_WithKafkaMetadata() {
        // Given
        AccountingRequestDto request = createTestRequest("session1", "testUser");
        @SuppressWarnings("unchecked")
        IncomingKafkaRecordMetadata<String, AccountingRequestDto> kafkaMetadata =
                mock(IncomingKafkaRecordMetadata.class);
        when(kafkaMetadata.getPartition()).thenReturn(1);
        when(kafkaMetadata.getOffset()).thenReturn(100L);

        Message<AccountingRequestDto> message = new Message<AccountingRequestDto>() {
            @Override
            public AccountingRequestDto getPayload() {
                return request;
            }

            @Override
            public Metadata getMetadata() {
                return Metadata.of(kafkaMetadata);
            }

            @Override
            public CompletionStage<Void> ack() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> nack(Throwable reason) {
                return CompletableFuture.completedFuture(null);
            }
        };

        when(accountingHandlerFactory.getHandler(eq(request), eq("eventId1")))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingConsumer
                .consumeAccountingEvent(message)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingHandlerFactory).getHandler(eq(request), eq("eventId1"));
    }

    @Test
    void testConsumeAccountingEvent_InterimUpdate() {
        // Given
        AccountingRequestDto request = new AccountingRequestDto(
                "eventId2",
                "session1",
                "192.168.1.1",
                "testUser",
                AccountingRequestDto.ActionType.INTERIM_UPDATE,
                1000,
                1000,
                3600,
                Instant.now(),
                "port1",
                "10.0.0.1",
                0,
                0,
                0,
                "nas1"
        );
        Message<AccountingRequestDto> message = createMessage(request);

        when(accountingHandlerFactory.getHandler(eq(request), eq("eventId2")))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingConsumer
                .consumeAccountingEvent(message)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingHandlerFactory).getHandler(eq(request), eq("eventId2"));
    }

    @Test
    void testConsumeAccountingEvent_Stop() {
        // Given
        AccountingRequestDto request = new AccountingRequestDto(
                "eventId3",
                "session1",
                "192.168.1.1",
                "testUser",
                AccountingRequestDto.ActionType.STOP,
                2000,
                2000,
                7200,
                Instant.now(),
                "port1",
                "10.0.0.1",
                0,
                0,
                0,
                "nas1"
        );
        Message<AccountingRequestDto> message = createMessage(request);

        when(accountingHandlerFactory.getHandler(eq(request), eq("eventId3")))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingConsumer
                .consumeAccountingEvent(message)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingHandlerFactory).getHandler(eq(request), eq("eventId3"));
    }
}
