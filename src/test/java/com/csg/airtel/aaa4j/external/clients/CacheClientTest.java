package com.csg.airtel.aaa4j.external.clients;

import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheClientTest {

    @Mock
    private ReactiveRedisDataSource reactiveRedisDataSource;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ReactiveValueCommands<String, String> valueCommands;

    @Mock
    private ReactiveKeyCommands<String> keyCommands;

    private CacheClient cacheClient;

    @BeforeEach
    void setUp() {
        cacheClient = new CacheClient(reactiveRedisDataSource, objectMapper);
    }

    @Test
    void shouldStoreUserDataSuccessfully() throws JsonProcessingException {
        String userId = "user123";
        UserSessionData userData = createUserSessionData(userId);
        String jsonValue = "{\"userName\":\"user123\"}";

        when(objectMapper.writeValueAsString(any(UserSessionData.class)))
                .thenReturn(jsonValue);
        when(reactiveRedisDataSource.value(String.class))
                .thenReturn(valueCommands);
        when(valueCommands.set(anyString(), anyString()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = cacheClient.storeUserData(userId, userData);

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(objectMapper).writeValueAsString(userData);
        verify(valueCommands).set(eq("user:user123"), eq(jsonValue));
    }

    @Test
    void shouldRetrieveUserDataSuccessfully() throws JsonProcessingException {
        String userId = "user123";
        String jsonValue = "{\"userName\":\"user123\"}";
        UserSessionData expectedData = createUserSessionData(userId);

        when(reactiveRedisDataSource.value(String.class))
                .thenReturn(valueCommands);
        when(valueCommands.get(anyString()))
                .thenReturn(Uni.createFrom().item(jsonValue));
        when(objectMapper.readValue(eq(jsonValue), eq(UserSessionData.class)))
                .thenReturn(expectedData);

        Uni<UserSessionData> result = cacheClient.getUserData(userId);

        UserSessionData retrievedData = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(retrievedData).isNotNull();
        assertThat(retrievedData.getUserName()).isEqualTo(userId);

        verify(valueCommands).get("user:user123");
        verify(objectMapper).readValue(jsonValue, UserSessionData.class);
    }

    @Test
    void shouldReturnNullWhenUserDataNotFound() {
        String userId = "user123";

        when(reactiveRedisDataSource.value(String.class))
                .thenReturn(valueCommands);
        when(valueCommands.get(anyString()))
                .thenReturn(Uni.createFrom().nullItem());

        Uni<UserSessionData> result = cacheClient.getUserData(userId);

        UserSessionData retrievedData = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(retrievedData).isNull();
    }

    @Test
    void shouldReturnNullWhenJsonValueIsEmpty() {
        String userId = "user123";

        when(reactiveRedisDataSource.value(String.class))
                .thenReturn(valueCommands);
        when(valueCommands.get(anyString()))
                .thenReturn(Uni.createFrom().item(""));

        Uni<UserSessionData> result = cacheClient.getUserData(userId);

        UserSessionData retrievedData = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(retrievedData).isNull();
    }

    @Test
    void shouldUpdateUserAndRelatedCachesSuccessfully() throws JsonProcessingException {
        String userId = "user123";
        UserSessionData userData = createUserSessionData(userId);
        String jsonValue = "{\"userName\":\"user123\"}";

        when(objectMapper.writeValueAsString(any(UserSessionData.class)))
                .thenReturn(jsonValue);
        when(reactiveRedisDataSource.value(String.class))
                .thenReturn(valueCommands);
        when(valueCommands.set(anyString(), anyString(), any(SetArgs.class)))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = cacheClient.updateUserAndRelatedCaches(userId, userData);

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .assertCompleted();

        verify(objectMapper).writeValueAsString(userData);
        verify(valueCommands).set(eq("user:user123"), eq(jsonValue), any(SetArgs.class));
    }

    @Test
    void shouldDeleteKeySuccessfully() {
        String key = "user123";

        when(reactiveRedisDataSource.key())
                .thenReturn(keyCommands);
        when(keyCommands.del(anyString()))
                .thenReturn(Uni.createFrom().item(1));

        Uni<String> result = cacheClient.deleteKey(key);

        String deleteResult = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(deleteResult).contains("Key deleted");
        verify(keyCommands).del("user:user123");
    }

    @Test
    void shouldReturnKeyNotFoundWhenDeletingNonExistentKey() {
        String key = "user123";

        when(reactiveRedisDataSource.key())
                .thenReturn(keyCommands);
        when(keyCommands.del(anyString()))
                .thenReturn(Uni.createFrom().item(0));

        Uni<String> result = cacheClient.deleteKey(key);

        String deleteResult = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(deleteResult).contains("Key not found");
    }

    private UserSessionData createUserSessionData(String userName) {
        UserSessionData userData = new UserSessionData();
        userData.setUserName(userName);
        userData.setGroupId("1");

        Balance balance = new Balance();
        balance.setBucketId("bucket-1");
        balance.setQuota(1000L);

        userData.setBalance(new ArrayList<>(List.of(balance)));
        userData.setSessions(new ArrayList<>());

        return userData;
    }
}
