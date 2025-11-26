package com.csg.airtel.aaa4j.application.resources;

import com.csg.airtel.aaa4j.domain.model.response.ApiResponse;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.service.BucketService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BucketResourceTest {

    @Mock
    private BucketService bucketService;

    private BucketResource bucketResource;

    @BeforeEach
    void setUp() {
        bucketResource = new BucketResource(bucketService);
    }

    @Test
    void shouldAddBucketSuccessfully() {
        String userName = "user123";
        Balance balance = createBalance();
        ApiResponse<Balance> expectedResponse = createSuccessResponse(balance);

        when(bucketService.addBucketBalance(anyString(), any(Balance.class)))
                .thenReturn(Uni.createFrom().item(expectedResponse));

        Uni<ApiResponse<Balance>> result = bucketResource.addBucket(userName, balance);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getData()).isEqualTo(balance);
        assertThat(response.getMessage()).isEqualTo("Balance added successfully");

        verify(bucketService).addBucketBalance(userName, balance);
    }

    @Test
    void shouldUpdateBucketSuccessfully() {
        String userName = "user123";
        String serviceId = "service-1";
        Balance balance = createBalance();
        balance.setServiceId(serviceId);
        ApiResponse<Balance> expectedResponse = createSuccessResponse(balance);

        when(bucketService.updateBucketBalance(anyString(), any(Balance.class), anyString()))
                .thenReturn(Uni.createFrom().item(expectedResponse));

        Uni<ApiResponse<Balance>> result = bucketResource.updateBucket(userName, balance, serviceId);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getData()).isEqualTo(balance);

        verify(bucketService).updateBucketBalance(userName, balance, serviceId);
    }

    @Test
    void shouldHandleAddBucketFailure() {
        String userName = "user123";
        Balance balance = createBalance();
        ApiResponse<Balance> errorResponse = createErrorResponse("Failed to add balance");

        when(bucketService.addBucketBalance(anyString(), any(Balance.class)))
                .thenReturn(Uni.createFrom().item(errorResponse));

        Uni<ApiResponse<Balance>> result = bucketResource.addBucket(userName, balance);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).contains("Failed to add balance");
    }

    @Test
    void shouldHandleUpdateBucketFailure() {
        String userName = "user123";
        String serviceId = "service-1";
        Balance balance = createBalance();
        ApiResponse<Balance> errorResponse = createErrorResponse("Failed to update balance");

        when(bucketService.updateBucketBalance(anyString(), any(Balance.class), anyString()))
                .thenReturn(Uni.createFrom().item(errorResponse));

        Uni<ApiResponse<Balance>> result = bucketResource.updateBucket(userName, balance, serviceId);

        ApiResponse<Balance> response = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).contains("Failed to update balance");
    }

    private Balance createBalance() {
        Balance balance = new Balance();
        balance.setBucketId("bucket-1");
        balance.setServiceId("service-1");
        balance.setQuota(1000L);
        balance.setInitialBalance(1000L);
        balance.setPriority(1L);
        return balance;
    }

    private ApiResponse<Balance> createSuccessResponse(Balance balance) {
        ApiResponse<Balance> response = new ApiResponse<>();
        response.setTimestamp(Instant.now());
        response.setMessage("Balance added successfully");
        response.setData(balance);
        return response;
    }

    private ApiResponse<Balance> createErrorResponse(String message) {
        ApiResponse<Balance> response = new ApiResponse<>();
        response.setTimestamp(Instant.now());
        response.setMessage(message);
        response.setData(null);
        return response;
    }
}
