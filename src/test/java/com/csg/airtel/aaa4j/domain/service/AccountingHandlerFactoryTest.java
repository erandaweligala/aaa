package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingHandlerFactoryTest {

    @Mock
    private StartHandler startHandler;

    @Mock
    private InterimHandler interimHandler;

    @Mock
    private StopHandler stopHandler;

    @InjectMocks
    private AccountingHandlerFactory accountingHandlerFactory;

    private AccountingRequestDto createTestRequest(AccountingRequestDto.ActionType actionType) {
        return new AccountingRequestDto(
                "eventId1",
                "session1",
                "192.168.1.1",
                "testUser",
                actionType,
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
    }

    @Test
    void testGetHandler_StartAction_CallsStartHandler() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.START);
        String traceId = "trace123";

        when(startHandler.processAccountingStart(eq(request), eq(traceId)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingHandlerFactory
                .getHandler(request, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(startHandler).processAccountingStart(eq(request), eq(traceId));
        verify(interimHandler, never()).handleInterim(any(), any());
        verify(stopHandler, never()).stopProcessing(any(), any(), any());
    }

    @Test
    void testGetHandler_InterimUpdateAction_CallsInterimHandler() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.INTERIM_UPDATE);
        String traceId = "trace123";

        when(interimHandler.handleInterim(eq(request), eq(traceId)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingHandlerFactory
                .getHandler(request, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(interimHandler).handleInterim(eq(request), eq(traceId));
        verify(startHandler, never()).processAccountingStart(any(), any());
        verify(stopHandler, never()).stopProcessing(any(), any(), any());
    }

    @Test
    void testGetHandler_StopAction_CallsStopHandler() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.STOP);
        String traceId = "trace123";

        when(stopHandler.stopProcessing(eq(request), eq(null), eq(traceId)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingHandlerFactory
                .getHandler(request, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(stopHandler).stopProcessing(eq(request), eq(null), eq(traceId));
        verify(startHandler, never()).processAccountingStart(any(), any());
        verify(interimHandler, never()).handleInterim(any(), any());
    }

    @Test
    void testGetHandler_StartAction_WithNullTraceId() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.START);

        when(startHandler.processAccountingStart(eq(request), eq(null)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = accountingHandlerFactory
                .getHandler(request, null)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(startHandler).processAccountingStart(eq(request), eq(null));
    }

    @Test
    void testGetHandler_InterimUpdateAction_HandlerFailure() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.INTERIM_UPDATE);
        String traceId = "trace123";
        RuntimeException testException = new RuntimeException("Handler failed");

        when(interimHandler.handleInterim(eq(request), eq(traceId)))
                .thenReturn(Uni.createFrom().failure(testException));

        // When
        UniAssertSubscriber<Void> subscriber = accountingHandlerFactory
                .getHandler(request, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(interimHandler).handleInterim(eq(request), eq(traceId));
    }

    @Test
    void testGetHandler_StopAction_HandlerFailure() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.STOP);
        String traceId = "trace123";
        RuntimeException testException = new RuntimeException("Handler failed");

        when(stopHandler.stopProcessing(eq(request), eq(null), eq(traceId)))
                .thenReturn(Uni.createFrom().failure(testException));

        // When
        UniAssertSubscriber<Void> subscriber = accountingHandlerFactory
                .getHandler(request, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(stopHandler).stopProcessing(eq(request), eq(null), eq(traceId));
    }

    @Test
    void testGetHandler_StartAction_HandlerFailure() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.START);
        String traceId = "trace123";
        RuntimeException testException = new RuntimeException("Handler failed");

        when(startHandler.processAccountingStart(eq(request), eq(traceId)))
                .thenReturn(Uni.createFrom().failure(testException));

        // When
        UniAssertSubscriber<Void> subscriber = accountingHandlerFactory
                .getHandler(request, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(startHandler).processAccountingStart(eq(request), eq(traceId));
    }

    @Test
    void testGetHandler_MultipleStartRequests() {
        // Given
        AccountingRequestDto request1 = createTestRequest(AccountingRequestDto.ActionType.START);
        AccountingRequestDto request2 = createTestRequest(AccountingRequestDto.ActionType.START);
        String traceId1 = "trace1";
        String traceId2 = "trace2";

        when(startHandler.processAccountingStart(eq(request1), eq(traceId1)))
                .thenReturn(Uni.createFrom().voidItem());
        when(startHandler.processAccountingStart(eq(request2), eq(traceId2)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber1 = accountingHandlerFactory
                .getHandler(request1, traceId1)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        UniAssertSubscriber<Void> subscriber2 = accountingHandlerFactory
                .getHandler(request2, traceId2)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber1.awaitItem().assertCompleted();
        subscriber2.awaitItem().assertCompleted();
        verify(startHandler, times(2)).processAccountingStart(any(), any());
    }

    @Test
    void testGetHandler_MixedActionTypes() {
        // Given
        AccountingRequestDto startRequest = createTestRequest(AccountingRequestDto.ActionType.START);
        AccountingRequestDto interimRequest = createTestRequest(AccountingRequestDto.ActionType.INTERIM_UPDATE);
        AccountingRequestDto stopRequest = createTestRequest(AccountingRequestDto.ActionType.STOP);
        String traceId = "trace123";

        when(startHandler.processAccountingStart(eq(startRequest), eq(traceId)))
                .thenReturn(Uni.createFrom().voidItem());
        when(interimHandler.handleInterim(eq(interimRequest), eq(traceId)))
                .thenReturn(Uni.createFrom().voidItem());
        when(stopHandler.stopProcessing(eq(stopRequest), eq(null), eq(traceId)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> startSubscriber = accountingHandlerFactory
                .getHandler(startRequest, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        UniAssertSubscriber<Void> interimSubscriber = accountingHandlerFactory
                .getHandler(interimRequest, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        UniAssertSubscriber<Void> stopSubscriber = accountingHandlerFactory
                .getHandler(stopRequest, traceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        startSubscriber.awaitItem().assertCompleted();
        interimSubscriber.awaitItem().assertCompleted();
        stopSubscriber.awaitItem().assertCompleted();

        verify(startHandler).processAccountingStart(eq(startRequest), eq(traceId));
        verify(interimHandler).handleInterim(eq(interimRequest), eq(traceId));
        verify(stopHandler).stopProcessing(eq(stopRequest), eq(null), eq(traceId));
    }
}
