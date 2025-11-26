package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.UpdateResult;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StopHandlerTest {

    @Mock
    private CacheClient cacheClient;

    @Mock
    private AccountProducer accountProducer;

    @Mock
    private AccountingUtil accountingUtil;

    @InjectMocks
    private StopHandler stopHandler;

    private AccountingRequestDto createTestRequest(String sessionId, String username, int sessionTime) {
        return new AccountingRequestDto(
                "eventId1",
                sessionId,
                "192.168.1.1",
                username,
                AccountingRequestDto.ActionType.STOP,
                1000,
                1000,
                sessionTime,
                Instant.now(),
                "port1",
                "10.0.0.1",
                0,
                0,
                0,
                "nas1"
        );
    }

    private UserSessionData createUserSessionData(String username) {
        Balance balance = new Balance();
        balance.setBucketId("bucket1");
        balance.setServiceId("service1");
        balance.setQuota(1000L);
        balance.setInitialBalance(2000L);
        balance.setPriority(5L);
        balance.setServiceStatus("Active");
        balance.setServiceStartDate(LocalDateTime.now().minusDays(1));
        balance.setServiceExpiry(LocalDateTime.now().plusDays(30));
        balance.setBucketUsername(username);

        Session session = new Session(
                "session1",
                LocalDateTime.now().minusHours(1),
                "bucket1",
                3600,
                500L,
                "10.0.0.1",
                "192.168.1.1"
        );

        return UserSessionData.builder()
                .userName(username)
                .groupId("1")
                .balance(new ArrayList<>(Arrays.asList(balance)))
                .sessions(new ArrayList<>(Arrays.asList(session)))
                .build();
    }

    @Test
    void testStopProcessing_Success() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        UserSessionData userData = createUserSessionData(username);
        Balance balance = userData.getBalance().get(0);

        UpdateResult updateResult = UpdateResult.success(900L, "bucket1", balance, "bucket1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceDBWriteEvent(any())).thenReturn(Uni.createFrom().voidItem());
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .stopProcessing(request, null, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil).updateSessionAndBalance(any(), any(), eq(request), eq(null));
        verify(accountProducer).produceDBWriteEvent(any());
        verify(cacheClient).updateUserAndRelatedCaches(eq(username), any(UserSessionData.class));
        verify(accountProducer).produceAccountingCDREvent(any());
        assertThat(userData.getSessions()).isEmpty();
    }

    @Test
    void testStopProcessing_WithBucketId_Success() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        String bucketId = "bucket1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        UserSessionData userData = createUserSessionData(username);
        Balance balance = userData.getBalance().get(0);

        UpdateResult updateResult = UpdateResult.success(900L, bucketId, balance, bucketId);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(bucketId)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceDBWriteEvent(any())).thenReturn(Uni.createFrom().voidItem());
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .stopProcessing(request, bucketId, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil).updateSessionAndBalance(any(), any(), eq(request), eq(bucketId));
    }

    @Test
    void testStopProcessing_NoUserData_CompletesWithoutError() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .stopProcessing(request, null, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil, never()).updateSessionAndBalance(any(), any(), any(), any());
    }

    @Test
    void testStopProcessing_NoActiveSessions_CompletesWithoutError() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        UserSessionData userData = createUserSessionData(username);
        userData.setSessions(new ArrayList<>());

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .processAccountingStop(userData, request, null)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil, never()).updateSessionAndBalance(any(), any(), any(), any());
    }

    @Test
    void testStopProcessing_SessionNotFound_CompletesWithoutError() {
        // Given
        String username = "testUser";
        String sessionId = "nonexistentSession";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        UserSessionData userData = createUserSessionData(username);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .processAccountingStop(userData, request, null)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil, never()).updateSessionAndBalance(any(), any(), any(), any());
    }

    @Test
    void testStopProcessing_UpdateBalanceFails_CompletesWithWarning() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        UserSessionData userData = createUserSessionData(username);
        Balance balance = userData.getBalance().get(0);

        UpdateResult updateResult = UpdateResult.failure("Update failed");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceDBWriteEvent(any())).thenReturn(Uni.createFrom().voidItem());
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .stopProcessing(request, null, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountProducer).produceDBWriteEvent(any());
        verify(cacheClient).updateUserAndRelatedCaches(eq(username), any(UserSessionData.class));
    }

    @Test
    void testStopProcessing_CacheUpdateFails_RecoverGracefully() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        UserSessionData userData = createUserSessionData(username);
        Balance balance = userData.getBalance().get(0);

        UpdateResult updateResult = UpdateResult.success(900L, "bucket1", balance, "bucket1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceDBWriteEvent(any())).thenReturn(Uni.createFrom().voidItem());
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Cache failure")));
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .stopProcessing(request, null, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(cacheClient).updateUserAndRelatedCaches(eq(username), any(UserSessionData.class));
    }

    @Test
    void testStopProcessing_DBWriteFails_ContinuesWithCDR() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 3600);

        UserSessionData userData = createUserSessionData(username);
        Balance balance = userData.getBalance().get(0);

        UpdateResult updateResult = UpdateResult.success(900L, "bucket1", balance, "bucket1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceDBWriteEvent(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB write failed")));
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = stopHandler
                .stopProcessing(request, null, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(accountProducer).produceDBWriteEvent(any());
    }
}
