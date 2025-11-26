package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartHandlerTest {

    @Mock
    private CacheClient cacheClient;

    @Mock
    private UserBucketRepository userBucketRepository;

    @Mock
    private AccountProducer accountProducer;

    @InjectMocks
    private StartHandler startHandler;

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

    private ServiceBucketInfo createServiceBucketInfo(String bucketId, String serviceId, long quota, String username) {
        ServiceBucketInfo info = new ServiceBucketInfo();
        info.setBucketId(bucketId);
        info.setServiceId(serviceId);
        info.setCurrentBalance(quota);
        info.setInitialBalance(quota);
        info.setPriority(5L);
        info.setStatus("Active");
        info.setServiceStartDate(LocalDateTime.now().minusDays(1));
        info.setExpiryDate(LocalDateTime.now().plusDays(30));
        info.setBucketUser(username);
        info.setTimeWindow("00:00-23:59");
        return info;
    }

    private UserSessionData createUserSessionData(String username, String groupId) {
        Balance balance = new Balance();
        balance.setBucketId("bucket1");
        balance.setServiceId("service1");
        balance.setQuota(1000L);
        balance.setPriority(5L);
        balance.setServiceStatus("Active");
        balance.setServiceStartDate(LocalDateTime.now().minusDays(1));
        balance.setServiceExpiry(LocalDateTime.now().plusDays(30));
        balance.setBucketUsername(username);

        return UserSessionData.builder()
                .userName(username)
                .groupId(groupId)
                .balance(new ArrayList<>(Arrays.asList(balance)))
                .sessions(new ArrayList<>())
                .build();
    }

    @Test
    void testProcessAccountingStart_NewUser_Success() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        ServiceBucketInfo bucketInfo = createServiceBucketInfo("bucket1", "service1", 1000L, username);
        List<ServiceBucketInfo> buckets = Arrays.asList(bucketInfo);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(buckets));
        when(cacheClient.storeUserData(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(cacheClient).storeUserData(eq(username), any(UserSessionData.class));
        verify(accountProducer).produceAccountingCDREvent(any());
    }

    @Test
    void testProcessAccountingStart_NewUser_NoServiceBuckets_SendsDisconnect() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(new ArrayList<>()));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(cacheClient, never()).storeUserData(anyString(), any());
    }

    @Test
    void testProcessAccountingStart_NewUser_ZeroQuota_SendsDisconnect() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        ServiceBucketInfo bucketInfo = createServiceBucketInfo("bucket1", "service1", 0L, username);
        List<ServiceBucketInfo> buckets = Arrays.asList(bucketInfo);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(buckets));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(cacheClient, never()).storeUserData(anyString(), any());
    }

    @Test
    void testProcessAccountingStart_ExistingUser_NewSession_Success() {
        // Given
        String username = "testUser";
        String sessionId = "session2";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        UserSessionData existingData = createUserSessionData(username, "1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(existingData));
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(cacheClient).updateUserAndRelatedCaches(eq(username), any(UserSessionData.class));
        verify(accountProducer).produceAccountingCDREvent(any());
    }

    @Test
    void testProcessAccountingStart_ExistingUser_SessionAlreadyExists_DoesNothing() {
        // Given
        String username = "testUser";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        UserSessionData existingData = createUserSessionData(username, "1");
        Session existingSession = new Session(
                sessionId,
                LocalDateTime.now(),
                null,
                0,
                0L,
                "10.0.0.1",
                "192.168.1.1"
        );
        existingData.getSessions().add(existingSession);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(existingData));

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(cacheClient, never()).updateUserAndRelatedCaches(anyString(), any());
    }

    @Test
    void testProcessAccountingStart_ExistingUser_ZeroBalance_SendsDisconnect() {
        // Given
        String username = "testUser";
        String sessionId = "session2";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        UserSessionData existingData = createUserSessionData(username, "1");
        existingData.getBalance().get(0).setQuota(0L);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(existingData));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(cacheClient, never()).updateUserAndRelatedCaches(anyString(), any());
    }

    @Test
    void testProcessAccountingStart_WithGroupBuckets_Success() {
        // Given
        String username = "testUser";
        String groupId = "group1";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        ServiceBucketInfo userBucket = createServiceBucketInfo("bucket1", "service1", 1000L, username);
        ServiceBucketInfo groupBucket = createServiceBucketInfo("bucket2", "service2", 2000L, groupId);
        List<ServiceBucketInfo> buckets = Arrays.asList(userBucket, groupBucket);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(cacheClient.getUserData(groupId)).thenReturn(Uni.createFrom().nullItem());
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(buckets));
        when(cacheClient.storeUserData(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(cacheClient.storeUserData(eq(groupId), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(cacheClient).storeUserData(eq(username), any(UserSessionData.class));
        verify(cacheClient).storeUserData(eq(groupId), any(UserSessionData.class));
    }

    @Test
    void testProcessAccountingStart_WithGroupBuckets_GroupAlreadyExists_SkipsGroupStorage() {
        // Given
        String username = "testUser";
        String groupId = "group1";
        String sessionId = "session1";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        ServiceBucketInfo userBucket = createServiceBucketInfo("bucket1", "service1", 1000L, username);
        ServiceBucketInfo groupBucket = createServiceBucketInfo("bucket2", "service2", 2000L, groupId);
        List<ServiceBucketInfo> buckets = Arrays.asList(userBucket, groupBucket);

        UserSessionData existingGroupData = createUserSessionData(groupId, "1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());
        when(cacheClient.getUserData(groupId)).thenReturn(Uni.createFrom().item(existingGroupData));
        when(userBucketRepository.getServiceBucketsByUserName(username))
                .thenReturn(Uni.createFrom().item(buckets));
        when(cacheClient.storeUserData(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(cacheClient).storeUserData(eq(username), any(UserSessionData.class));
        // Group storage should not be called since group already exists
        verify(cacheClient, times(1)).storeUserData(anyString(), any(UserSessionData.class));
    }

    @Test
    void testProcessAccountingStart_ExistingUser_WithGroupBalance() {
        // Given
        String username = "testUser";
        String groupId = "group1";
        String sessionId = "session2";
        AccountingRequestDto request = createTestRequest(sessionId, username);

        UserSessionData existingUserData = createUserSessionData(username, groupId);
        UserSessionData groupData = createUserSessionData(groupId, "1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(existingUserData));
        when(cacheClient.getUserData(groupId)).thenReturn(Uni.createFrom().item(groupData));
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = startHandler
                .processAccountingStart(request, "trace1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(cacheClient).getUserData(groupId);
        verify(cacheClient).updateUserAndRelatedCaches(eq(username), any(UserSessionData.class));
    }
}
