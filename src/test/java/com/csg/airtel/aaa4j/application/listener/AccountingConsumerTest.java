package com.csg.airtel.aaa4j.application.listener;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.domain.service.AccountingHandlerFactory;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingConsumerTest {

    @Mock
    private AccountProducer accountingProdEvent;

    @Mock
    private AccountingHandlerFactory accountingHandlerFactory;

    @Mock
    private Message<AccountingRequestDto> message;

    private AccountingConsumer accountingConsumer;

    @BeforeEach
    void setUp() {
        accountingConsumer = new AccountingConsumer(accountingProdEvent, accountingHandlerFactory);
    }

    @Test
    void shouldConsumeAccountingEventSuccessfully() {
        AccountingRequestDto request = createAccountingRequest();
        when(message.getPayload()).thenReturn(request);
        when(message.ack()).thenReturn(CompletableFuture.completedFuture(null));
        when(accountingHandlerFactory.getHandler(any(AccountingRequestDto.class), anyString()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = accountingConsumer.consumeAccountingEvent(message);

        assertThat(result).isNotNull();
        result.await().indefinitely();

        verify(accountingHandlerFactory).getHandler(request, request.eventId());
        verify(message).ack();
        verify(message, never()).nack(any());
    }

    @Test
    void shouldNackMessageOnFailure() {
        AccountingRequestDto request = createAccountingRequest();
        RuntimeException exception = new RuntimeException("Processing failed");

        when(message.getPayload()).thenReturn(request);
        when(message.nack(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(accountingHandlerFactory.getHandler(any(AccountingRequestDto.class), anyString()))
                .thenReturn(Uni.createFrom().failure(exception));

        Uni<Void> result = accountingConsumer.consumeAccountingEvent(message);

        assertThat(result).isNotNull();
        result.await().indefinitely();

        verify(accountingHandlerFactory).getHandler(request, request.eventId());
        verify(message).nack(exception);
        verify(message, never()).ack();
    }

    @Test
    void shouldHandleNullEventId() {
        AccountingRequestDto request = new AccountingRequestDto(
                null, "session-123", "192.168.1.1", "user123",
                AccountingRequestDto.ActionType.START, 100, 200, 60,
                Instant.now(), "port-1", "10.0.0.1", 0, 0, 0, "nas-1"
        );

        when(message.getPayload()).thenReturn(request);
        when(message.ack()).thenReturn(CompletableFuture.completedFuture(null));
        when(accountingHandlerFactory.getHandler(any(AccountingRequestDto.class), isNull()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = accountingConsumer.consumeAccountingEvent(message);

        assertThat(result).isNotNull();
        result.await().indefinitely();

        verify(accountingHandlerFactory).getHandler(request, null);
        verify(message).ack();
    }

    private AccountingRequestDto createAccountingRequest() {
        return new AccountingRequestDto(
                "event-123", "session-123", "192.168.1.1", "user123",
                AccountingRequestDto.ActionType.START, 100, 200, 60,
                Instant.now(), "port-1", "10.0.0.1", 0, 0, 0, "nas-1"
        );
    }
}
