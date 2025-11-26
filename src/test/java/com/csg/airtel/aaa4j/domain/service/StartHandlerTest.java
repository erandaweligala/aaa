package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import com.csg.airtel.aaa4j.external.repository.UserBucketRepository;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartHandlerTest {

    @Mock
    private CacheClient cacheClient;

    @Mock
    private UserBucketRepository userRepository;

    @Mock
    private AccountProducer accountProducer;

    private StartHandler startHandler;

    @BeforeEach
    void setUp() {
        startHandler = new StartHandler(cacheClient, userRepository, accountProducer);
    }

    @Test
    void shouldProcessAccountingStartForNewUser() {
        AccountingRequestDto request = createAccountingRequest();
        List<ServiceBucketInfo> serviceBuckets = createServiceBuckets();

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().nullItem());
        when(userRepository.getServiceBucketsByUserName(anyString()))
                .thenReturn(Uni.createFrom().item(serviceBuckets));
        when(cacheClient.storeUserData(anyString(), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(cacheClient).getUserData(request.username());
        verify(userRepository).getServiceBucketsByUserName(request.username());
        verify(cacheClient).storeUserData(eq(request.username()), any(UserSessionData.class));
    }

    @Test
    void shouldProcessAccountingStartForExistingUserWithBalance() {
        AccountingRequestDto request = createAccountingRequest();
        UserSessionData existingUserData = createExistingUserData();

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().item(existingUserData));
        when(cacheClient.updateUserAndRelatedCaches(anyString(), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(cacheClient).getUserData(request.username());
        verify(cacheClient).updateUserAndRelatedCaches(eq(request.username()), any(UserSessionData.class));
    }

    @Test
    void shouldNotCreateDuplicateSession() {
        AccountingRequestDto request = createAccountingRequest();
        UserSessionData existingUserData = createExistingUserData();

        Session existingSession = new Session();
        existingSession.setSessionId(request.sessionId());
        existingUserData.setSessions(new ArrayList<>(List.of(existingSession)));

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().item(existingUserData));

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(cacheClient).getUserData(request.username());
        verify(cacheClient, never()).updateUserAndRelatedCaches(anyString(), any(UserSessionData.class));
    }

    @Test
    void shouldRejectStartWhenNoServiceBuckets() {
        AccountingRequestDto request = createAccountingRequest();

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().nullItem());
        when(userRepository.getServiceBucketsByUserName(anyString()))
                .thenReturn(Uni.createFrom().item(new ArrayList<>()));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(cacheClient, never()).storeUserData(anyString(), any(UserSessionData.class));
    }

    @Test
    void shouldRejectStartWhenTotalQuotaIsZero() {
        AccountingRequestDto request = createAccountingRequest();
        List<ServiceBucketInfo> serviceBuckets = createServiceBuckets();
        serviceBuckets.forEach(bucket -> bucket.setCurrentBalance(0L));

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().nullItem());
        when(userRepository.getServiceBucketsByUserName(anyString()))
                .thenReturn(Uni.createFrom().item(serviceBuckets));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(cacheClient, never()).storeUserData(anyString(), any(UserSessionData.class));
    }

    @Test
    void shouldRejectStartWhenExistingUserHasZeroBalance() {
        AccountingRequestDto request = createAccountingRequest();
        UserSessionData existingUserData = createExistingUserData();
        existingUserData.getBalance().forEach(balance -> balance.setQuota(0L));

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().item(existingUserData));
        when(accountProducer.produceAccountingResponseEvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(accountProducer).produceAccountingResponseEvent(any());
        verify(cacheClient, never()).updateUserAndRelatedCaches(anyString(), any(UserSessionData.class));
    }

    @Test
    void shouldHandleGroupUserBalances() {
        AccountingRequestDto request = createAccountingRequest();
        List<ServiceBucketInfo> serviceBuckets = createServiceBucketsWithGroup();

        UserSessionData groupUserData = new UserSessionData();
        groupUserData.setBalance(new ArrayList<>());

        when(cacheClient.getUserData("user123"))
                .thenReturn(Uni.createFrom().nullItem());
        when(cacheClient.getUserData("group-user"))
                .thenReturn(Uni.createFrom().nullItem());
        when(userRepository.getServiceBucketsByUserName(anyString()))
                .thenReturn(Uni.createFrom().item(serviceBuckets));
        when(cacheClient.storeUserData(anyString(), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(accountProducer.produceAccountingCDREvent(any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(cacheClient, atLeastOnce()).getUserData(anyString());
        verify(cacheClient, atLeast(1)).storeUserData(anyString(), any(UserSessionData.class));
    }

    @Test
    void shouldHandleFailureGracefully() {
        AccountingRequestDto request = createAccountingRequest();
        RuntimeException exception = new RuntimeException("Database error");

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().failure(exception));

        Uni<Void> result = startHandler.processAccountingStart(request, "trace-123");

        assertThat(result).isNotNull();
        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();
    }

    private AccountingRequestDto createAccountingRequest() {
        return new AccountingRequestDto(
                "event-123", "session-123", "192.168.1.1", "user123",
                AccountingRequestDto.ActionType.START, 0, 0, 0,
                Instant.now(), "port-1", "10.0.0.1", 0, 0, 0, "nas-1"
        );
    }

    private List<ServiceBucketInfo> createServiceBuckets() {
        List<ServiceBucketInfo> buckets = new ArrayList<>();

        ServiceBucketInfo bucket1 = new ServiceBucketInfo();
        bucket1.setBucketId(1L);
        bucket1.setServiceId(1L);
        bucket1.setCurrentBalance(1000L);
        bucket1.setInitialBalance(1000L);
        bucket1.setPriority(1L);
        bucket1.setStatus("Active");
        bucket1.setExpiryDate(LocalDateTime.now().plusDays(30));
        bucket1.setServiceStartDate(LocalDateTime.now().minusDays(1));
        bucket1.setBucketUser("user123");
        bucket1.setTimeWindow("0-24");
        bucket1.setBucketExpiryDate(LocalDateTime.now().plusDays(30));

        buckets.add(bucket1);
        return buckets;
    }

    private List<ServiceBucketInfo> createServiceBucketsWithGroup() {
        List<ServiceBucketInfo> buckets = new ArrayList<>();

        ServiceBucketInfo bucket1 = new ServiceBucketInfo();
        bucket1.setBucketId(1L);
        bucket1.setServiceId(1L);
        bucket1.setCurrentBalance(1000L);
        bucket1.setInitialBalance(1000L);
        bucket1.setPriority(1L);
        bucket1.setStatus("Active");
        bucket1.setExpiryDate(LocalDateTime.now().plusDays(30));
        bucket1.setServiceStartDate(LocalDateTime.now().minusDays(1));
        bucket1.setBucketUser("user123");
        bucket1.setTimeWindow("0-24");
        bucket1.setBucketExpiryDate(LocalDateTime.now().plusDays(30));

        ServiceBucketInfo bucket2 = new ServiceBucketInfo();
        bucket2.setBucketId(2L);
        bucket2.setServiceId(2L);
        bucket2.setCurrentBalance(2000L);
        bucket2.setInitialBalance(2000L);
        bucket2.setPriority(2L);
        bucket2.setStatus("Active");
        bucket2.setExpiryDate(LocalDateTime.now().plusDays(30));
        bucket2.setServiceStartDate(LocalDateTime.now().minusDays(1));
        bucket2.setBucketUser("group-user");
        bucket2.setTimeWindow("0-24");
        bucket2.setBucketExpiryDate(LocalDateTime.now().plusDays(30));

        buckets.add(bucket1);
        buckets.add(bucket2);
        return buckets;
    }

    private UserSessionData createExistingUserData() {
        UserSessionData userData = new UserSessionData();
        userData.setUserName("user123");
        userData.setGroupId("1");

        Balance balance = new Balance();
        balance.setBucketId("bucket-1");
        balance.setQuota(1000L);
        balance.setInitialBalance(1000L);
        balance.setPriority(1L);
        balance.setServiceExpiry(LocalDateTime.now().plusDays(30));
        balance.setBucketExpiryDate(LocalDateTime.now().plusDays(30));
        balance.setServiceStartDate(LocalDateTime.now().minusDays(1));
        balance.setServiceStatus("Active");
        balance.setTimeWindow("0-24");
        balance.setBucketUsername("user123");

        userData.setBalance(new ArrayList<>(List.of(balance)));
        userData.setSessions(new ArrayList<>());

        return userData;
    }
}
