package com.csg.airtel.aaa4j.application.resources;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.domain.service.AccountingHandlerFactory;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDebugResourceTest {

    @Mock
    private UserBucketRepository userBucketRepository;

    @Mock
    private CacheClient cacheClient;

    @Mock
    private AccountingHandlerFactory accountingHandlerFactory;

    @InjectMocks
    private RedisDebugResource redisDebugResource;

    private ServiceBucketInfo createServiceBucketInfo(String bucketId) {
        ServiceBucketInfo info = new ServiceBucketInfo();
        info.setBucketId(bucketId);
        info.setServiceId("service1");
        info.setCurrentBalance(1000L);
        info.setInitialBalance(2000L);
        info.setPriority(5L);
        info.setStatus("Active");
        info.setServiceStartDate(LocalDateTime.now().minusDays(1));
        info.setExpiryDate(LocalDateTime.now().plusDays(30));
        info.setBucketUser("testUser");
        return info;
    }

    private UserSessionData createUserSessionData(String username) {
        Balance balance = new Balance();
        balance.setBucketId("bucket1");
        balance.setServiceId("service1");
        balance.setQuota(1000L);
        balance.setPriority(5L);

        return UserSessionData.builder()
                .userName(username)
                .groupId("1")
                .balance(new ArrayList<>(Arrays.asList(balance)))
                .sessions(new ArrayList<>())
                .build();
    }

    private AccountingRequestDto createTestRequest() {
        return new AccountingRequestDto(
                "eventId1",
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
    }

    @Test
    void testTestConnection_Success() {
        // Given
        ServiceBucketInfo bucket1 = createServiceBucketInfo("bucket1");
        ServiceBucketInfo bucket2 = createServiceBucketInfo("bucket2");
        List<ServiceBucketInfo> buckets = Arrays.asList(bucket1, bucket2);

        when(userBucketRepository.getServiceBucketsByUserName("100001"))
                .thenReturn(Uni.createFrom().item(buckets));

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .testConnection()
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        Map<String, Object> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result).containsKey("buckets");
        assertThat((List<?>) result.get("buckets")).hasSize(2);
        verify(userBucketRepository).getServiceBucketsByUserName("100001");
    }

    @Test
    void testTestConnection_EmptyBuckets() {
        // Given
        when(userBucketRepository.getServiceBucketsByUserName("100001"))
                .thenReturn(Uni.createFrom().item(new ArrayList<>()));

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .testConnection()
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        Map<String, Object> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result).containsKey("buckets");
        assertThat((List<?>) result.get("buckets")).isEmpty();
    }

    @Test
    void testTestConnection_RepositoryFailure() {
        // Given
        when(userBucketRepository.getServiceBucketsByUserName("100001"))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .testConnection()
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
    }

    @Test
    void testInterimUpdate_Success() {
        // Given
        AccountingRequestDto request = createTestRequest();

        when(accountingHandlerFactory.getHandler(eq(request), eq(null)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .interimUpdate(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        Map<String, Object> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result).containsKey("accounting_result");
        verify(accountingHandlerFactory).getHandler(eq(request), eq(null));
    }

    @Test
    void testInterimUpdate_HandlerFailure() {
        // Given
        AccountingRequestDto request = createTestRequest();

        when(accountingHandlerFactory.getHandler(eq(request), eq(null)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Handler error")));

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .interimUpdate(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
    }

    @Test
    void testDeleteKeyCache_Success() {
        // Given
        String username = "testUser";
        when(cacheClient.deleteKey(username))
                .thenReturn(Uni.createFrom().item("Key deleted: " + username));

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .deleteKeyCache(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        Map<String, Object> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result).containsKey("accounting_result");
        assertThat(result.get("accounting_result")).isEqualTo("Key deleted: " + username);
        verify(cacheClient).deleteKey(username);
    }

    @Test
    void testDeleteKeyCache_KeyNotFound() {
        // Given
        String username = "nonexistent";
        when(cacheClient.deleteKey(username))
                .thenReturn(Uni.createFrom().item("Key not found: " + username));

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .deleteKeyCache(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        Map<String, Object> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.get("accounting_result")).isEqualTo("Key not found: " + username);
    }

    @Test
    void testDeleteKeyCache_CacheFailure() {
        // Given
        String username = "testUser";
        when(cacheClient.deleteKey(username))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Cache error")));

        // When
        UniAssertSubscriber<Map<String, Object>> subscriber = redisDebugResource
                .deleteKeyCache(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
    }

    @Test
    void testGetKeyCache_Success() {
        // Given
        String username = "testUser";
        UserSessionData userData = createUserSessionData(username);

        when(cacheClient.getUserData(username))
                .thenReturn(Uni.createFrom().item(userData));

        // When
        UniAssertSubscriber<UserSessionData> subscriber = redisDebugResource
                .getKeyCache(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        UserSessionData result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getUserName()).isEqualTo(username);
        assertThat(result.getBalance()).hasSize(1);
        verify(cacheClient).getUserData(username);
    }

    @Test
    void testGetKeyCache_UserNotFound() {
        // Given
        String username = "nonexistent";
        when(cacheClient.getUserData(username))
                .thenReturn(Uni.createFrom().nullItem());

        // When
        UniAssertSubscriber<UserSessionData> subscriber = redisDebugResource
                .getKeyCache(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        UserSessionData result = subscriber.awaitItem().getItem();
        assertThat(result).isNull();
    }

    @Test
    void testGetKeyCache_CacheFailure() {
        // Given
        String username = "testUser";
        when(cacheClient.getUserData(username))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Cache error")));

        // When
        UniAssertSubscriber<UserSessionData> subscriber = redisDebugResource
                .getKeyCache(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
    }
}
