package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.response.ApiResponse;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    @Mock
    private CacheClient cacheClient;

    private BucketService bucketService;

    @BeforeEach
    void setUp() {
        bucketService = new BucketService(cacheClient);
    }

    @Test
    void shouldAddBucketBalanceSuccessfully() {
        String userName = "user123";
        Balance newBalance = createBalance("bucket-1", 1000L);
        UserSessionData userData = createUserSessionData(userName);

        when(cacheClient.getUserData(userName))
                .thenReturn(Uni.createFrom().item(userData));
        when(cacheClient.updateUserAndRelatedCaches(anyString(), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<ApiResponse<Balance>> result = bucketService.addBucketBalance(userName, newBalance);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Balance added successfully");
        assertThat(response.getData()).isEqualTo(newBalance);

        verify(cacheClient).getUserData(userName);
        verify(cacheClient).updateUserAndRelatedCaches(eq(userName), any(UserSessionData.class));
    }

    @Test
    void shouldReturnErrorWhenUsernameIsNull() {
        Balance balance = createBalance("bucket-1", 1000L);

        Uni<ApiResponse<Balance>> result = bucketService.addBucketBalance(null, balance);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Username is required");
        assertThat(response.getData()).isNull();

        verify(cacheClient, never()).getUserData(anyString());
    }

    @Test
    void shouldReturnErrorWhenUsernameIsBlank() {
        Balance balance = createBalance("bucket-1", 1000L);

        Uni<ApiResponse<Balance>> result = bucketService.addBucketBalance("", balance);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Username is required");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldReturnErrorWhenBalanceIsNull() {
        Uni<ApiResponse<Balance>> result = bucketService.addBucketBalance("user123", null);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Balance is required");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldUpdateBucketBalanceSuccessfully() {
        String userName = "user123";
        String serviceId = "service-1";
        Balance updatedBalance = createBalance("bucket-1", 500L);
        updatedBalance.setServiceId(serviceId);

        Balance existingBalance = createBalance("bucket-1", 1000L);
        existingBalance.setServiceId(serviceId);

        UserSessionData userData = createUserSessionData(userName);
        userData.setBalance(new ArrayList<>(List.of(existingBalance)));

        when(cacheClient.getUserData(userName))
                .thenReturn(Uni.createFrom().item(userData));
        when(cacheClient.updateUserAndRelatedCaches(anyString(), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<ApiResponse<Balance>> result = bucketService.updateBucketBalance(userName, updatedBalance, serviceId);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Balance added successfully");
        assertThat(response.getData()).isEqualTo(updatedBalance);

        verify(cacheClient).getUserData(userName);
        verify(cacheClient).updateUserAndRelatedCaches(eq(userName), any(UserSessionData.class));
    }

    @Test
    void shouldReturnErrorWhenServiceIdIsNull() {
        String userName = "user123";
        Balance balance = createBalance("bucket-1", 1000L);

        Uni<ApiResponse<Balance>> result = bucketService.updateBucketBalance(userName, balance, null);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Service Id is required");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldReturnErrorWhenServiceIdDoesNotMatch() {
        String userName = "user123";
        String serviceId = "service-1";
        Balance balance = createBalance("bucket-1", 1000L);
        balance.setServiceId("service-2");

        Uni<ApiResponse<Balance>> result = bucketService.updateBucketBalance(userName, balance, serviceId);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Balance serviceId must match the provided serviceId");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldHandleNullBalanceListWhenUpdating() {
        String userName = "user123";
        String serviceId = "service-1";
        Balance balance = createBalance("bucket-1", 1000L);
        balance.setServiceId(serviceId);

        UserSessionData userData = createUserSessionData(userName);
        userData.setBalance(null);

        when(cacheClient.getUserData(userName))
                .thenReturn(Uni.createFrom().item(userData));
        when(cacheClient.updateUserAndRelatedCaches(anyString(), any(UserSessionData.class)))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<ApiResponse<Balance>> result = bucketService.updateBucketBalance(userName, balance, serviceId);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("Balance added successfully");
        assertThat(response.getData()).isEqualTo(balance);
    }

    @Test
    void shouldHandleFailureWhenAddingBalance() {
        String userName = "user123";
        Balance balance = createBalance("bucket-1", 1000L);
        RuntimeException exception = new RuntimeException("Cache error");

        when(cacheClient.getUserData(userName))
                .thenReturn(Uni.createFrom().failure(exception));

        Uni<ApiResponse<Balance>> result = bucketService.addBucketBalance(userName, balance);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("Failed to add balance");
        assertThat(response.getData()).isNull();
    }

    private Balance createBalance(String bucketId, Long quota) {
        Balance balance = new Balance();
        balance.setBucketId(bucketId);
        balance.setQuota(quota);
        balance.setInitialBalance(quota);
        balance.setPriority(1L);
        balance.setServiceExpiry(LocalDateTime.now().plusDays(30));
        return balance;
    }

    private UserSessionData createUserSessionData(String userName) {
        UserSessionData userData = new UserSessionData();
        userData.setUserName(userName);
        userData.setBalance(new ArrayList<>());
        return userData;
    }
}
