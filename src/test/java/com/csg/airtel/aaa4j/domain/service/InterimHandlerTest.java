package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
import com.csg.airtel.aaa4j.domain.model.UpdateResult;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import com.csg.airtel.aaa4j.external.repository.UserBucketRepository;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterimHandlerTest {

    @Mock
    private CacheClient cacheClient;

    @Mock
    private UserBucketRepository userBucketRepository;

    @Mock
    private AccountingUtil accountingUtil;

    @Mock
    private AccountProducer accountProducer;

    @InjectMocks
    private InterimHandler interimHandler;

    private AccountingRequestDto createTestRequest(String sessionId, String username, int sessionTime) {
        return new AccountingRequestDto(
                "eventId1",
                sessionId,
                "192.168.1.1",
                username,
                AccountingRequestDto.ActionType.INTERIM_UPDATE,
                500,
                500,
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

    private ServiceBucketInfo createServiceBucketInfo(String bucketId, long quota) {
        ServiceBucketInfo info = new ServiceBucketInfo();
        info.setBucketId(bucketId);
        info.setServiceId("service1");
        info.setCurrentBalance(quota);
        info.setInitialBalance(quota);
        info.setPriority(5L);
        info.setStatus("Active");
        info.setServiceStartDate(LocalDateTime.now().minusDays(1));
        info.setExpiryDate(LocalDateTime.now().plusDays(30));
        info.setBucketUser("testUser");
        info.setTimeWindow("00:00-23:59");
        return info;
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
                60,
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
    void testHandleInterim_ExistingUser_Success() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 120);

        UserSessionData userData = createUserSessionData(username);
        Balance balance = userData.getBalance().get(0);

        UpdateResult updateResult = UpdateResult.success(950L, "bucket1", balance, "bucket1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil).updateSessionAndBalance(any(), any(), eq(request), eq(null));
        verify(accountProducer).produceAccountingCDREvent(any());
    }

    @Test
    void testHandleInterim_NewUser_NoServiceBuckets_SendsDisconnect() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 60);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(new ArrayList<>()));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(accountingUtil, never()).updateSessionAndBalance(any(), any(), any(), any());
    }

    @Test
    void testHandleInterim_NewUser_ZeroQuota_SendsDisconnect() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 60);

        ServiceBucketInfo bucketInfo = createServiceBucketInfo("bucket1", 0L);
        List<ServiceBucketInfo> buckets = Arrays.asList(bucketInfo);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(buckets));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(accountingUtil, never()).updateSessionAndBalance(any(), any(), any(), any());
    }

    @Test
    void testHandleInterim_NewUser_WithQuota_ProcessesUpdate() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 60);

        ServiceBucketInfo bucketInfo = createServiceBucketInfo("bucket1", 1000L);
        List<ServiceBucketInfo> buckets = Arrays.asList(bucketInfo);

        Balance balance = new Balance();
        balance.setBucketId("bucket1");
        balance.setQuota(1000L);

        UpdateResult updateResult = UpdateResult.success(950L, "bucket1", balance, "bucket1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(buckets));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil).updateSessionAndBalance(any(), any(), eq(request), eq(null));
        verify(accountProducer).produceAccountingCDREvent(any());
    }

    @Test
    void testHandleInterim_DuplicateSessionTime_SkipsUpdate() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 60);

        UserSessionData userData = createUserSessionData(username);
        userData.getSessions().get(0).setSessionTime(60);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil, never()).updateSessionAndBalance(any(), any(), any(), any());
        verify(accountProducer, never()).produceAccountingCDREvent(any());
    }

    @Test
    void testHandleInterim_SessionTimeDecreased_SkipsUpdate() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 50);

        UserSessionData userData = createUserSessionData(username);
        userData.getSessions().get(0).setSessionTime(60);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil, never()).updateSessionAndBalance(any(), any(), any(), any());
    }

    @Test
    void testHandleInterim_NoExistingSession_CreatesNewSession() {
        // Given
        String username = "testUser";
        String sessionId = "session2";
        AccountingRequestDto request = createTestRequest(sessionId, username, 60);

        UserSessionData userData = createUserSessionData(username);
        Balance balance = userData.getBalance().get(0);

        UpdateResult updateResult = UpdateResult.success(950L, "bucket1", balance, "bucket1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountingUtil).updateSessionAndBalance(any(), any(), eq(request), eq(null));
        verify(accountProducer).produceAccountingCDREvent(any());
    }

    @Test
    void testHandleInterim_UpdateBalanceFails_CompletesWithWarning() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username, 120);

        UserSessionData userData = createUserSessionData(username);

        UpdateResult updateResult = UpdateResult.failure("Update failed");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(accountingUtil.updateSessionAndBalance(any(), any(), eq(request), eq(null)))
                .thenReturn(Uni.createFrom().item(updateResult));
        when(accountProducer.produceAccountingCDREvent(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = interimHandler
                .handleInterim(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountProducer).produceAccountingCDREvent(any());
    }
}
