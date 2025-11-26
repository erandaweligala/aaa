package com.csg.airtel.aaa4j.domain.produce;

import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import com.csg.airtel.aaa4j.domain.model.DBWriteRequest;
import com.csg.airtel.aaa4j.domain.model.EventType;
import com.csg.airtel.aaa4j.domain.model.cdr.*;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountProducerTest {

    @Mock
    private Emitter<DBWriteRequest> dbWriteRequestEmitter;

    @Mock
    private Emitter<AccountingResponseEvent> accountingResponseEmitter;

    @Mock
    private Emitter<AccountingCDREvent> accountingCDREventEmitter;

    private AccountProducer accountProducer;

    @BeforeEach
    void setUp() {
        accountProducer = new AccountProducer(
                dbWriteRequestEmitter,
                accountingResponseEmitter,
                accountingCDREventEmitter
        );
    }

    private DBWriteRequest createDBWriteRequest() {
        DBWriteRequest request = new DBWriteRequest();
        request.setSessionId("session1");
        request.setUserName("testUser");
        request.setEventType(EventType.UPDATE_EVENT);
        request.setTableName("BUCKET_INSTANCE");
        return request;
    }

    private AccountingResponseEvent createAccountingResponseEvent() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("username", "testUser");
        attributes.put("sessionId", "session1");

        return new AccountingResponseEvent(
                AccountingResponseEvent.EventType.COA,
                LocalDateTime.now(),
                "session1",
                AccountingResponseEvent.ResponseAction.DISCONNECT,
                "Test message",
                0L,
                attributes
        );
    }

    private AccountingCDREvent createAccountingCDREvent() {
        SessionCdr sessionCdr = SessionCdr.builder()
                .sessionId("session1")
                .sessionTime("3600")
                .startTime(Instant.now())
                .updateTime(Instant.now())
                .nasIdentifier("nas1")
                .nasIpAddress("192.168.1.1")
                .build();

        User user = User.builder()
                .userName("testUser")
                .build();

        Network network = Network.builder()
                .framedIpAddress("10.0.0.1")
                .calledStationId("192.168.1.1")
                .build();

        Accounting accounting = Accounting.builder()
                .acctStatusType("Interim-Update")
                .acctSessionTime(3600)
                .acctInputOctets(1000L)
                .acctOutputOctets(2000L)
                .acctInputGigawords(0)
                .acctOutputGigawords(0)
                .build();

        Payload payload = Payload.builder()
                .session(sessionCdr)
                .user(user)
                .network(network)
                .accounting(accounting)
                .build();

        return AccountingCDREvent.builder()
                .eventId("event1")
                .eventType("ACCOUNTING_INTERIM")
                .eventVersion("1.0")
                .source("AAA-Service")
                .eventTimestamp(Instant.now())
                .payload(payload)
                .build();
    }

    @Test
    void testProduceDBWriteEvent_Success() {
        // Given
        DBWriteRequest request = createDBWriteRequest();

        doAnswer(invocation -> {
            Message<DBWriteRequest> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(dbWriteRequestEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceDBWriteEvent(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(dbWriteRequestEmitter).send(any(Message.class));
    }

    @Test
    void testProduceDBWriteEvent_Failure() {
        // Given
        DBWriteRequest request = createDBWriteRequest();
        RuntimeException testException = new RuntimeException("Send failed");

        doAnswer(invocation -> {
            Message<DBWriteRequest> message = invocation.getArgument(0);
            message.nack(testException);
            return null;
        }).when(dbWriteRequestEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceDBWriteEvent(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(dbWriteRequestEmitter).send(any(Message.class));
    }

    @Test
    void testProduceDBWriteEvent_VerifyMessageKey() {
        // Given
        DBWriteRequest request = createDBWriteRequest();

        ArgumentCaptor<Message<DBWriteRequest>> messageCaptor = ArgumentCaptor.forClass(Message.class);

        doAnswer(invocation -> {
            Message<DBWriteRequest> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(dbWriteRequestEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceDBWriteEvent(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(dbWriteRequestEmitter).send(messageCaptor.capture());
        Message<DBWriteRequest> capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getPayload()).isEqualTo(request);
    }

    @Test
    void testProduceAccountingResponseEvent_Success() {
        // Given
        AccountingResponseEvent event = createAccountingResponseEvent();

        doAnswer(invocation -> {
            Message<AccountingResponseEvent> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(accountingResponseEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingResponseEvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingResponseEmitter).send(any(Message.class));
    }

    @Test
    void testProduceAccountingResponseEvent_Failure() {
        // Given
        AccountingResponseEvent event = createAccountingResponseEvent();
        RuntimeException testException = new RuntimeException("Send failed");

        doAnswer(invocation -> {
            Message<AccountingResponseEvent> message = invocation.getArgument(0);
            message.nack(testException);
            return null;
        }).when(accountingResponseEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingResponseEvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(accountingResponseEmitter).send(any(Message.class));
    }

    @Test
    void testProduceAccountingResponseEvent_VerifyMessageKey() {
        // Given
        AccountingResponseEvent event = createAccountingResponseEvent();

        ArgumentCaptor<Message<AccountingResponseEvent>> messageCaptor = ArgumentCaptor.forClass(Message.class);

        doAnswer(invocation -> {
            Message<AccountingResponseEvent> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(accountingResponseEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingResponseEvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingResponseEmitter).send(messageCaptor.capture());
        Message<AccountingResponseEvent> capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getPayload()).isEqualTo(event);
    }

    @Test
    void testProduceAccountingCDREvent_Success() {
        // Given
        AccountingCDREvent event = createAccountingCDREvent();

        doAnswer(invocation -> {
            Message<AccountingCDREvent> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(accountingCDREventEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingCDREvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingCDREventEmitter).send(any(Message.class));
    }

    @Test
    void testProduceAccountingCDREvent_Failure() {
        // Given
        AccountingCDREvent event = createAccountingCDREvent();
        RuntimeException testException = new RuntimeException("CDR Send failed");

        doAnswer(invocation -> {
            Message<AccountingCDREvent> message = invocation.getArgument(0);
            message.nack(testException);
            return null;
        }).when(accountingCDREventEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingCDREvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(accountingCDREventEmitter).send(any(Message.class));
    }

    @Test
    void testProduceAccountingCDREvent_VerifyMessageKey() {
        // Given
        AccountingCDREvent event = createAccountingCDREvent();

        ArgumentCaptor<Message<AccountingCDREvent>> messageCaptor = ArgumentCaptor.forClass(Message.class);

        doAnswer(invocation -> {
            Message<AccountingCDREvent> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(accountingCDREventEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingCDREvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingCDREventEmitter).send(messageCaptor.capture());
        Message<AccountingCDREvent> capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getPayload()).isEqualTo(event);
    }

    @Test
    void testProduceDBWriteEvent_MultipleRequests() {
        // Given
        DBWriteRequest request1 = createDBWriteRequest();
        DBWriteRequest request2 = createDBWriteRequest();
        request2.setSessionId("session2");

        doAnswer(invocation -> {
            Message<DBWriteRequest> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(dbWriteRequestEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber1 = accountProducer
                .produceDBWriteEvent(request1)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        UniAssertSubscriber<Void> subscriber2 = accountProducer
                .produceDBWriteEvent(request2)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber1.awaitItem().assertCompleted();
        subscriber2.awaitItem().assertCompleted();
        verify(dbWriteRequestEmitter, times(2)).send(any(Message.class));
    }

    @Test
    void testProduceAccountingCDREvent_StartEvent() {
        // Given
        AccountingCDREvent event = createAccountingCDREvent();
        event.setEventType("ACCOUNTING_START");
        event.getPayload().getAccounting().setAcctStatusType("Start");
        event.getPayload().getAccounting().setAcctSessionTime(0);

        doAnswer(invocation -> {
            Message<AccountingCDREvent> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(accountingCDREventEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingCDREvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingCDREventEmitter).send(any(Message.class));
    }

    @Test
    void testProduceAccountingCDREvent_StopEvent() {
        // Given
        AccountingCDREvent event = createAccountingCDREvent();
        event.setEventType("ACCOUNTING_STOP");
        event.getPayload().getAccounting().setAcctStatusType("Stop");

        doAnswer(invocation -> {
            Message<AccountingCDREvent> message = invocation.getArgument(0);
            message.ack();
            return null;
        }).when(accountingCDREventEmitter).send(any(Message.class));

        // When
        UniAssertSubscriber<Void> subscriber = accountProducer
                .produceAccountingCDREvent(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingCDREventEmitter).send(any(Message.class));
    }
}
