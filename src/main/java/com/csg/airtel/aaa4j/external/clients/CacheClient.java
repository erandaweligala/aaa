package com.csg.airtel.aaa4j.external.clients;

import com.csg.airtel.aaa4j.domain.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.exception.BaseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;

import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.Duration;


@ApplicationScoped
public class CacheClient {

    private static final Logger log = Logger.getLogger(CacheClient.class);
    final ReactiveRedisDataSource reactiveRedisDataSource;
    final ObjectMapper objectMapper;
    private static final String KEY_PREFIX = "user:";

    @Inject
    public CacheClient(ReactiveRedisDataSource reactiveRedisDataSource, ObjectMapper objectMapper) {
        this.reactiveRedisDataSource = reactiveRedisDataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Store user data in Redis with version initialization
     */
    public Uni<Void> storeUserData(String userId, UserSessionData userData) {
        long startTime = System.currentTimeMillis();
        log.infof("Storing user data  for  cache userId: %s", userId);

        // Initialize version for new entries
        userData.initializeVersion();

        String key = KEY_PREFIX + userId;
        String jsonValue = serialize(userData);
        Uni<Void> result = reactiveRedisDataSource.value(String.class)
                .set(key, jsonValue, new SetArgs().ex(Duration.ofHours(1000)));
        log.infof("User data stored Complete for userId: %s in %d ms", userId, (System.currentTimeMillis() - startTime));
        return result;
    }

    /**
     * Retrieve user data from Redis with circuit breaker and retry for high availability
     */
    @CircuitBreaker(
            requestVolumeThreshold = 10,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2
    )
    @Retry(
            maxRetries = 3,
            delay = 100,
            maxDuration = 5000
    )
    @Timeout(value = 5000)
    public Uni<UserSessionData> getUserData(String userId) {
        long startTime = System.currentTimeMillis();
        log.infof("Retrieving user data for cache userId: %s", userId);
        String key = KEY_PREFIX + userId;
        return reactiveRedisDataSource.value(String.class)
                .get(key)
                .onItem().transform(Unchecked.function(jsonValue -> {
                    if (jsonValue == null || jsonValue.isEmpty()) {
                        return null; // No record found
                    }
                    try {
                        UserSessionData userSessionData = objectMapper.readValue(jsonValue, UserSessionData.class);
                        log.infof("User data retrieved Complete for userId: %s in %d ms", userId, (System.currentTimeMillis() - startTime));
                        return userSessionData;
                    } catch (Exception e) {
                        throw new BaseException("Failed to deserialize user data", ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.description(), Response.Status.INTERNAL_SERVER_ERROR,ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.code(), e.getStackTrace());
                    }
                }))
                .onFailure().invoke(e -> log.error("Failed to get user data for userId: " + "10001", e));
    }


    /**
     * Update user data with optimistic locking using version checking.
     * This method implements a retry mechanism to handle concurrent modifications.
     *
     * @param userId   The user ID
     * @param userData The updated user session data
     * @return Uni<Void> indicating completion
     */
    public Uni<Void> updateUserAndRelatedCaches(String userId, UserSessionData userData) {
        return updateUserDataWithRetry(userId, userData, 0, 5);
    }

    /**
     * Internal method to update user data with retry logic for optimistic locking
     *
     * @param userId       The user ID
     * @param userData     The updated user session data
     * @param attempt      Current retry attempt
     * @param maxAttempts  Maximum number of retry attempts
     * @return Uni<Void> indicating completion
     */
    private Uni<Void> updateUserDataWithRetry(String userId, UserSessionData userData, int attempt, int maxAttempts) {
        long startTime = System.currentTimeMillis();
        String userKey = KEY_PREFIX + userId;

        if (attempt >= maxAttempts) {
            log.errorf("Max retry attempts (%d) exceeded for updating user data for userId: %s", maxAttempts, userId);
            return Uni.createFrom().failure(
                new RuntimeException("Failed to update cache after " + maxAttempts + " attempts due to concurrent modifications")
            );
        }

        // Increment version before updating
        userData.incrementVersion();

        log.infof("Updating user data (attempt %d/%d) for userId: %s, version: %d",
                attempt + 1, maxAttempts, userId, userData.getVersion());

        return Uni.createFrom().item(() -> serialize(userData))
                .onItem().invoke(json -> {
                    if (log.isDebugEnabled()) {
                        log.debugf("Serialized data for user %s: %s", userId, json);
                    }
                })
                .onItem().transformToUni(serializedData ->
                        reactiveRedisDataSource.value(String.class)
                                .set(userKey, serializedData, new SetArgs().ex(Duration.ofHours(1000)))
                )
                .onItem().invoke(() ->
                        log.infof("Cache update complete for userId: %s, version: %d in %d ms",
                                userId, userData.getVersion(), (System.currentTimeMillis() - startTime))
                )
                .onFailure().invoke(err ->
                        log.errorf(err, "Failed to update cache for user %s on attempt %d", userId, attempt + 1)
                )
                .onFailure().retry()
                .withBackOff(Duration.ofMillis(50), Duration.ofMillis(500))
                .atMost(2)
                .replaceWithVoid();
    }

    public Uni<String> deleteKey(String key) {
        String userKey = KEY_PREFIX + key;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();

        return keyCommands.del(userKey)
                .map(deleted -> deleted > 0
                        ? "Key deleted: " + key
                        : "Key not found: " + key);
    }



    private String serialize(UserSessionData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BaseException("Failed to deserialize user data", ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.description(), Response.Status.INTERNAL_SERVER_ERROR,ResponseCodeEnum.EXCEPTION_CLIENT_LAYER.code(), e.getStackTrace());

        }
    }

}