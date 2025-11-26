package com.csg.airtel.aaa4j.application.resources;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.domain.service.AccountingHandlerFactory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisDebugResourceTest {

    @Mock
    private UserBucketRepository userRepository;

    @Mock
    private CacheClient cacheClient;

    @Mock
    private AccountingHandlerFactory accountingHandlerFactory;

    private RedisDebugResource redisDebugResource;

    @BeforeEach
    void setUp() {
        redisDebugResource = new RedisDebugResource(userRepository, cacheClient, accountingHandlerFactory);
    }

    @Test
    void shouldTestConnectionSuccessfully() {
        List<Balance> buckets = new ArrayList<>();
        Balance balance = new Balance();
        balance.setBucketId("bucket-1");
        buckets.add(balance);

        when(userRepository.getServiceBucketsByUserName(anyString()))
                .thenReturn(Uni.createFrom().item(buckets));

        Uni<Map<String, Object>> result = redisDebugResource.testConnection();

        Map<String, Object> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response).containsKey("buckets");
        assertThat((List<?>) response.get("buckets")).hasSize(1);

        verify(userRepository).getServiceBucketsByUserName("100001");
    }

    @Test
    void shouldProcessInterimUpdateSuccessfully() {
        AccountingRequestDto request = createAccountingRequest();
        String expectedResult = "success";

        when(accountingHandlerFactory.getHandler(any(AccountingRequestDto.class), isNull()))
                .thenReturn(Uni.createFrom().item(expectedResult));

        Uni<Map<String, Object>> result = redisDebugResource.interimUpdate(request);

        Map<String, Object> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response).containsKey("accounting_result");
        assertThat(response.get("accounting_result")).isEqualTo(expectedResult);

        verify(accountingHandlerFactory).getHandler(request, null);
    }

    @Test
    void shouldDeleteKeyCacheSuccessfully() {
        String username = "user123";
        String deleteResult = "Key deleted: user123";

        when(cacheClient.deleteKey(anyString()))
                .thenReturn(Uni.createFrom().item(deleteResult));

        Uni<Map<String, Object>> result = redisDebugResource.deleteKeyCache(username);

        Map<String, Object> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response).containsKey("accounting_result");
        assertThat(response.get("accounting_result")).isEqualTo(deleteResult);

        verify(cacheClient).deleteKey(username);
    }

    @Test
    void shouldGetKeyCacheSuccessfully() {
        String username = "user123";
        UserSessionData userData = new UserSessionData();
        userData.setUserName(username);

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().item(userData));

        Uni<UserSessionData> result = redisDebugResource.getKeyCache(username);

        UserSessionData response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getUserName()).isEqualTo(username);

        verify(cacheClient).getUserData(username);
    }

    @Test
    void shouldHandleNullUserData() {
        String username = "user123";

        when(cacheClient.getUserData(anyString()))
                .thenReturn(Uni.createFrom().nullItem());

        Uni<UserSessionData> result = redisDebugResource.getKeyCache(username);

        UserSessionData response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNull();
    }

    private AccountingRequestDto createAccountingRequest() {
        return new AccountingRequestDto(
                "event-123", "session-123", "192.168.1.1", "user123",
                AccountingRequestDto.ActionType.INTERIM_UPDATE, 100, 200, 60,
                Instant.now(), "port-1", "10.0.0.1", 0, 0, 0, "nas-1"
        );
    }
}
