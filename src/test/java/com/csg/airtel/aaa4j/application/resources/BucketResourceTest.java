package com.csg.airtel.aaa4j.application.resources;

import com.csg.airtel.aaa4j.domain.model.response.ApiResponse;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.service.BucketService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BucketResourceTest {

    @Mock
    private BucketService bucketService;

    @InjectMocks
    private BucketResource bucketResource;

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

    private ApiResponse<Balance> createSuccessResponse(Balance balance) {
        ApiResponse<Balance> response = new ApiResponse<>();
        response.setMessage("Success");
        response.setData(balance);
        response.setTimestamp(Instant.now());
        return response;
    }

    @Test
    void testAddBucket_Success() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");
        ApiResponse<Balance> expectedResponse = createSuccessResponse(balance);

        when(bucketService.addBucketBalance(eq(username), eq(balance)))
                .thenReturn(Uni.createFrom().item(expectedResponse));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .addBucket(username, balance)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isEqualTo("Success");
        assertThat(result.getData()).isEqualTo(balance);
        verify(bucketService).addBucketBalance(eq(username), eq(balance));
    }

    @Test
    void testAddBucket_Failure() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");
        ApiResponse<Balance> errorResponse = new ApiResponse<>();
        errorResponse.setMessage("Failed to add bucket");
        errorResponse.setData(null);
        errorResponse.setTimestamp(Instant.now());

        when(bucketService.addBucketBalance(eq(username), eq(balance)))
                .thenReturn(Uni.createFrom().item(errorResponse));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .addBucket(username, balance)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isEqualTo("Failed to add bucket");
        assertThat(result.getData()).isNull();
        verify(bucketService).addBucketBalance(eq(username), eq(balance));
    }

    @Test
    void testAddBucket_ServiceThrowsException() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");

        when(bucketService.addBucketBalance(eq(username), eq(balance)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Service error")));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .addBucket(username, balance)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(bucketService).addBucketBalance(eq(username), eq(balance));
    }

    @Test
    void testUpdateBucket_Success() {
        // Given
        String username = "testUser";
        String serviceId = "service1";
        Balance balance = createTestBalance("bucket1", serviceId);
        ApiResponse<Balance> expectedResponse = createSuccessResponse(balance);

        when(bucketService.updateBucketBalance(eq(username), eq(balance), eq(serviceId)))
                .thenReturn(Uni.createFrom().item(expectedResponse));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .updateBucket(username, balance, serviceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isEqualTo("Success");
        assertThat(result.getData()).isEqualTo(balance);
        verify(bucketService).updateBucketBalance(eq(username), eq(balance), eq(serviceId));
    }

    @Test
    void testUpdateBucket_Failure() {
        // Given
        String username = "testUser";
        String serviceId = "service1";
        Balance balance = createTestBalance("bucket1", serviceId);
        ApiResponse<Balance> errorResponse = new ApiResponse<>();
        errorResponse.setMessage("Failed to update bucket");
        errorResponse.setData(null);
        errorResponse.setTimestamp(Instant.now());

        when(bucketService.updateBucketBalance(eq(username), eq(balance), eq(serviceId)))
                .thenReturn(Uni.createFrom().item(errorResponse));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .updateBucket(username, balance, serviceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isEqualTo("Failed to update bucket");
        assertThat(result.getData()).isNull();
        verify(bucketService).updateBucketBalance(eq(username), eq(balance), eq(serviceId));
    }

    @Test
    void testUpdateBucket_ServiceThrowsException() {
        // Given
        String username = "testUser";
        String serviceId = "service1";
        Balance balance = createTestBalance("bucket1", serviceId);

        when(bucketService.updateBucketBalance(eq(username), eq(balance), eq(serviceId)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Service error")));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .updateBucket(username, balance, serviceId)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        verify(bucketService).updateBucketBalance(eq(username), eq(balance), eq(serviceId));
    }

    @Test
    void testAddBucket_WithNullUsername() {
        // Given
        Balance balance = createTestBalance("bucket1", "service1");
        ApiResponse<Balance> errorResponse = new ApiResponse<>();
        errorResponse.setMessage("Username is required");
        errorResponse.setData(null);
        errorResponse.setTimestamp(Instant.now());

        when(bucketService.addBucketBalance(eq(null), eq(balance)))
                .thenReturn(Uni.createFrom().item(errorResponse));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .addBucket(null, balance)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
    }

    @Test
    void testUpdateBucket_WithNullServiceId() {
        // Given
        String username = "testUser";
        Balance balance = createTestBalance("bucket1", "service1");
        ApiResponse<Balance> errorResponse = new ApiResponse<>();
        errorResponse.setMessage("Service Id is required");
        errorResponse.setData(null);
        errorResponse.setTimestamp(Instant.now());

        when(bucketService.updateBucketBalance(eq(username), eq(balance), eq(null)))
                .thenReturn(Uni.createFrom().item(errorResponse));

        // When
        UniAssertSubscriber<ApiResponse<Balance>> subscriber = bucketResource
                .updateBucket(username, balance, null)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        ApiResponse<Balance> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
    }
}
