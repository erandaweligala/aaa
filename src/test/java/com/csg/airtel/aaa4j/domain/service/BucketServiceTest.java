package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.response.ApiResponse;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class BucketServiceTest {

    @Mock
    private CacheClient cacheClient;

    @InjectMocks
    private BucketService bucketService;

    private Balance createTestBalance(String bucketId, String serviceId) {
        Balance balance = new Balance();
        balance.setBucketId(bucketId);
        balance.setServiceId(serviceId);
        balance.setQuota(1000L);
        balance.setPriority(5L);
        balance.setServiceStatus("Active");
        balance.setServiceStartDate(LocalDateTime.now().minusDays(1));
        balance.setServiceExpiry(LocalDateTime.now().plusDays(30));
        balance.setInitialBalance(2000L);
        return balance;
    }

    private UserSessionData createUserSessionData(String username) {
        Balance balance = createTestBalance("bucket1", "service1");
        return UserSessionData.builder()
                .userName(username)
                .balance(new ArrayList<>(Arrays.asList(balance)))
                .build();
    }

    @Test
    void testAddBucketBalance_Success() {
        // Given
        String username = "testUser";
        Balance newBalance = createTestBalance("bucket2", "service2");
        UserSessionData userData = createUserSessionData(username);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .addBucketBalance(username, newBalance)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isEqualTo(newBalance);
        assertThat(response.getMessage()).isEqualTo("Balance added successfully");
        verify(cacheClient).updateUserAndRelatedCaches(eq(username), any(UserSessionData.class));
    }

    @Test
    void testAddBucketBalance_NullUsername_ReturnsError() {
        // Given
        Balance newBalance = createTestBalance("bucket2", "service2");

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .addBucketBalance(null, newBalance)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Username is required");
        verify(cacheClient, never()).getUserData(anyString());
    }

    @Test
    void testAddBucketBalance_BlankUsername_ReturnsError() {
        // Given
        Balance newBalance = createTestBalance("bucket2", "service2");

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .addBucketBalance("", newBalance)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Username is required");
    }

    @Test
    void testAddBucketBalance_NullBalance_ReturnsError() {
        // Given
        String username = "testUser";

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .addBucketBalance(username, null)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Balance is required");
    }

    @Test
    void testUpdateBucketBalance_Success() {
        // Given
        String username = "testUser";
        String serviceId = "service1";
        Balance updatedBalance = createTestBalance("bucket1", serviceId);
        updatedBalance.setQuota(500L);

        UserSessionData userData = createUserSessionData(username);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .updateBucketBalance(username, updatedBalance, serviceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isEqualTo(updatedBalance);
        assertThat(response.getMessage()).isEqualTo("Balance added successfully");
        verify(cacheClient).updateUserAndRelatedCaches(eq(username), any(UserSessionData.class));
    }

    @Test
    void testUpdateBucketBalance_NullUsername_ReturnsError() {
        // Given
        Balance balance = createTestBalance("bucket1", "service1");

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .updateBucketBalance(null, balance, "service1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Username is required");
    }

    @Test
    void testUpdateBucketBalance_NullServiceId_ReturnsError() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .updateBucketBalance(username, balance, null)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Service Id is required");
    }

    @Test
    void testUpdateBucketBalance_MismatchedServiceId_ReturnsError() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .updateBucketBalance(username, balance, "service2")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("Balance serviceId must match the provided serviceId");
    }

    @Test
    void testUpdateBucketBalance_UserNotFound_ReturnsError() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().nullItem());

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .updateBucketBalance(username, balance, "service1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("User not found");
    }

    @Test
    void testUpdateBucketBalance_CacheFailure_ReturnsError() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");
        UserSessionData userData = createUserSessionData(username);

        when(cacheClient.getUserData(username)).thenReturn(Uni.createFrom().item(userData));
        when(cacheClient.updateUserAndRelatedCaches(eq(username), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Cache error")));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketService
                .updateBucketBalance(username, balance, "service1")
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> response = subscriber.awaitItem().getItem();
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).contains("Failed to update balance");
    }
}
