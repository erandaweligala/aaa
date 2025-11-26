package com.csg.airtel.aaa4j.external.clients;

import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheClientTest {

    @Mock
    private ReactiveRedisDataSource reactiveRedisDataSource;

    @Mock
    private ReactiveValueCommands<String, String> valueCommands;

    @Mock
    private ReactiveKeyCommands<String> keyCommands;

    private ObjectMapper objectMapper;

    private CacheClient cacheClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule
        cacheClient = new CacheClient(reactiveRedisDataSource, objectMapper);
    }

    private UserSessionData createUserSessionData(String username) {
        Balance balance = new Balance();
        balance.setBucketId("bucket1");
        balance.setServiceId("service1");
        balance.setQuota(1000L);
        balance.setPriority(5L);
        balance.setServiceStatus("Active");
        balance.setServiceStartDate(LocalDateTime.now().minusDays(1));
        balance.setServiceExpiry(LocalDateTime.now().plusDays(30));
        balance.setInitialBalance(2000L);

        return UserSessionData.builder()
                .userName(username)
                .groupId("1")
                .balance(new ArrayList<>(Arrays.asList(balance)))
                .sessions(new ArrayList<>())
                .build();
    }

    @Test
    void testStoreUserData_Success() throws Exception {
        // Given
        String userId = "testUser";
        UserSessionData userData = createUserSessionData(userId);
        String expectedKey = "user:" + userId;

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.set(eq(expectedKey), any(String.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = cacheClient
                .storeUserData(userId, userData)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(valueCommands).set(eq(expectedKey), any(String.class));
    }

    @Test
    void testStoreUserData_VerifyJsonSerialization() throws Exception {
        // Given
        String userId = "testUser";
        UserSessionData userData = createUserSessionData(userId);
        String expectedKey = "user:" + userId;

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.set(eq(expectedKey), jsonCaptor.capture()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = cacheClient
                .storeUserData(userId, userData)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        String capturedJson = jsonCaptor.getValue();
        assertThat(capturedJson).contains("testUser");
        assertThat(capturedJson).contains("bucket1");
    }

    @Test
    void testGetUserData_Success() throws Exception {
        // Given
        String userId = "testUser";
        UserSessionData userData = createUserSessionData(userId);
        String expectedKey = "user:" + userId;
        String jsonValue = objectMapper.writeValueAsString(userData);

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.get(expectedKey)).thenReturn(Uni.createFrom().item(jsonValue));

        // When
        UniAssertSubscriber<UserSessionData> subscriber = cacheClient
                .getUserData(userId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        UserSessionData result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getUserName()).isEqualTo("testUser");
        assertThat(result.getBalance()).hasSize(1);
        verify(valueCommands).get(expectedKey);
    }

    @Test
    void testGetUserData_NotFound_ReturnsNull() {
        // Given
        String userId = "nonexistent";
        String expectedKey = "user:" + userId;

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.get(expectedKey)).thenReturn(Uni.createFrom().item(""));

        // When
        UniAssertSubscriber<UserSessionData> subscriber = cacheClient
                .getUserData(userId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        UserSessionData result = subscriber.awaitItem().getItem();
        assertThat(result).isNull();
    }

    @Test
    void testGetUserData_NullValue_ReturnsNull() {
        // Given
        String userId = "nonexistent";
        String expectedKey = "user:" + userId;

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.get(expectedKey)).thenReturn(Uni.createFrom().nullItem());

        // When
        UniAssertSubscriber<UserSessionData> subscriber = cacheClient
                .getUserData(userId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        UserSessionData result = subscriber.awaitItem().getItem();
        assertThat(result).isNull();
    }

    @Test
    void testGetUserData_InvalidJson_ThrowsException() {
        // Given
        String userId = "testUser";
        String expectedKey = "user:" + userId;
        String invalidJson = "{invalid json}";

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.get(expectedKey)).thenReturn(Uni.createFrom().item(invalidJson));

        // When
        UniAssertSubscriber<UserSessionData> subscriber = cacheClient
                .getUserData(userId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure().assertFailedWith(BaseException.class);
    }

    @Test
    void testGetUserData_RedisFailure() {
        // Given
        String userId = "testUser";
        String expectedKey = "user:" + userId;

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.get(expectedKey))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Redis error")));

        // When
        UniAssertSubscriber<UserSessionData> subscriber = cacheClient
                .getUserData(userId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem();
        verify(valueCommands).get(expectedKey);
    }

    @Test
    void testUpdateUserAndRelatedCaches_Success() throws Exception {
        // Given
        String userId = "testUser";
        UserSessionData userData = createUserSessionData(userId);
        String expectedKey = "user:" + userId;

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.set(eq(expectedKey), any(String.class), any(SetArgs.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = cacheClient
                .updateUserAndRelatedCaches(userId, userData)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        verify(valueCommands).set(eq(expectedKey), any(String.class), any(SetArgs.class));
    }

    @Test
    void testUpdateUserAndRelatedCaches_VerifyExpiration() throws Exception {
        // Given
        String userId = "testUser";
        UserSessionData userData = createUserSessionData(userId);
        String expectedKey = "user:" + userId;

        ArgumentCaptor<SetArgs> setArgsCaptor = ArgumentCaptor.forClass(SetArgs.class);

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.set(eq(expectedKey), any(String.class), setArgsCaptor.capture()))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber = cacheClient
                .updateUserAndRelatedCaches(userId, userData)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem().assertCompleted();
        SetArgs capturedArgs = setArgsCaptor.getValue();
        assertThat(capturedArgs).isNotNull();
    }

    @Test
    void testUpdateUserAndRelatedCaches_Failure() {
        // Given
        String userId = "testUser";
        UserSessionData userData = createUserSessionData(userId);
        String expectedKey = "user:" + userId;

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.set(eq(expectedKey), any(String.class), any(SetArgs.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Redis error")));

        // When
        UniAssertSubscriber<Void> subscriber = cacheClient
                .updateUserAndRelatedCaches(userId, userData)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitItem();
        verify(valueCommands).set(eq(expectedKey), any(String.class), any(SetArgs.class));
    }

    @Test
    void testDeleteKey_Success() {
        // Given
        String key = "testUser";
        String expectedKey = "user:" + key;

        when(reactiveRedisDataSource.key()).thenReturn(keyCommands);
        when(keyCommands.del(expectedKey)).thenReturn(Uni.createFrom().item(1));

        // When
        UniAssertSubscriber<String> subscriber = cacheClient
                .deleteKey(key)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        String result = subscriber.awaitItem().getItem();
        assertThat(result).isEqualTo("Key deleted: " + key);
        verify(keyCommands).del(expectedKey);
    }

    @Test
    void testDeleteKey_NotFound() {
        // Given
        String key = "nonexistent";
        String expectedKey = "user:" + key;

        when(reactiveRedisDataSource.key()).thenReturn(keyCommands);
        when(keyCommands.del(expectedKey)).thenReturn(Uni.createFrom().item(0));

        // When
        UniAssertSubscriber<String> subscriber = cacheClient
                .deleteKey(key)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        String result = subscriber.awaitItem().getItem();
        assertThat(result).isEqualTo("Key not found: " + key);
    }

    @Test
    void testDeleteKey_Failure() {
        // Given
        String key = "testUser";
        String expectedKey = "user:" + key;

        when(reactiveRedisDataSource.key()).thenReturn(keyCommands);
        when(keyCommands.del(expectedKey))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Redis error")));

        // When
        UniAssertSubscriber<String> subscriber = cacheClient
                .deleteKey(key)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
    }

    @Test
    void testStoreUserData_MultipleUsers() throws Exception {
        // Given
        String userId1 = "user1";
        String userId2 = "user2";
        UserSessionData userData1 = createUserSessionData(userId1);
        UserSessionData userData2 = createUserSessionData(userId2);

        when(reactiveRedisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.set(any(String.class), any(String.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<Void> subscriber1 = cacheClient
                .storeUserData(userId1, userData1)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        UniAssertSubscriber<Void> subscriber2 = cacheClient
                .storeUserData(userId2, userData2)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber1.awaitItem().assertCompleted();
        subscriber2.awaitItem().assertCompleted();
        verify(valueCommands, times(2)).set(any(String.class), any(String.class));
    }
}
