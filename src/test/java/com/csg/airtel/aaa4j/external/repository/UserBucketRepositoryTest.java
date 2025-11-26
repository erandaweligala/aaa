package com.csg.airtel.aaa4j.external.repository;

import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBucketRepositoryTest {

    @Mock
    private Pool client;

    @Mock
    private PreparedQuery<RowSet<Row>> preparedQuery;

    @Mock
    private RowSet<Row> rowSet;

    @Mock
    private Row row1;

    @Mock
    private Row row2;

    private UserBucketRepository userBucketRepository;

    @BeforeEach
    void setUp() {
        userBucketRepository = new UserBucketRepository(client);
    }

    private void mockRow(Row row, String bucketId, String serviceId, long currentBalance) {
        when(row.getString("BUCKET_ID")).thenReturn(bucketId);
        when(row.getLong("CURRENT_BALANCE")).thenReturn(currentBalance);
        when(row.getString("SERVICE_ID")).thenReturn(serviceId);
        when(row.getString("RULE")).thenReturn("rule1");
        when(row.getLong("PRIORITY")).thenReturn(5L);
        when(row.getLong("INITIAL_BALANCE")).thenReturn(2000L);
        when(row.getString("STATUS")).thenReturn("Active");
        when(row.getLong("USAGE")).thenReturn(500L);
        when(row.getLocalDateTime("EXPIRY_DATE")).thenReturn(LocalDateTime.now().plusDays(30));
        when(row.getLocalDateTime("SERVICE_START_DATE")).thenReturn(LocalDateTime.now().minusDays(1));
        when(row.getString("PLAN_ID")).thenReturn("plan1");
        when(row.getString("BUCKET_USER")).thenReturn("testUser");
        when(row.getLong("CONSUMPTION_LIMIT")).thenReturn(1000L);
        when(row.getLong("CONSUMPTION_LIMIT_WINDOW")).thenReturn(24L);
        when(row.getString("SESSION_TIMEOUT")).thenReturn("3600");
        when(row.getString("TIME_WINDOW")).thenReturn("00:00-23:59");
        when(row.getLocalDateTime("CYCLE_END_DATE")).thenReturn(LocalDateTime.now().plusDays(25));
    }

    @Test
    void testGetServiceBucketsByUserName_Success() {
        // Given
        String username = "testUser";

        mockRow(row1, "bucket1", "service1", 1000L);
        mockRow(row2, "bucket2", "service2", 2000L);

        List<Row> rows = new ArrayList<>();
        rows.add(row1);
        rows.add(row2);

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator()).thenAnswer(invocation -> rows.iterator());

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        List<ServiceBucketInfo> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBucketId()).isEqualTo("bucket1");
        assertThat(result.get(0).getServiceId()).isEqualTo("service1");
        assertThat(result.get(0).getCurrentBalance()).isEqualTo(1000L);
        assertThat(result.get(1).getBucketId()).isEqualTo("bucket2");
        verify(preparedQuery).execute(any(Tuple.class));
    }

    @Test
    void testGetServiceBucketsByUserName_EmptyResult() {
        // Given
        String username = "nonexistent";
        List<Row> emptyRows = new ArrayList<>();

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator()).thenAnswer(invocation -> emptyRows.iterator());

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        List<ServiceBucketInfo> result = subscriber.awaitItem().getItem();
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void testGetServiceBucketsByUserName_DatabaseError() {
        // Given
        String username = "testUser";
        RuntimeException dbException = new RuntimeException("Database connection error");

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class)))
                .thenReturn(Uni.createFrom().failure(dbException));

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        subscriber.awaitFailure();
        Throwable failure = subscriber.getFailure();
        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure.getMessage()).isEqualTo("Database connection error");
        verify(preparedQuery).execute(any(Tuple.class));
    }

    @Test
    void testGetServiceBucketsByUserName_VerifyAllFields() {
        // Given
        String username = "testUser";
        mockRow(row1, "bucket1", "service1", 1000L);

        List<Row> rows = new ArrayList<>();
        rows.add(row1);

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator()).thenAnswer(invocation -> rows.iterator());

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        List<ServiceBucketInfo> result = subscriber.awaitItem().getItem();
        assertThat(result).hasSize(1);

        ServiceBucketInfo info = result.get(0);
        assertThat(info.getBucketId()).isEqualTo("bucket1");
        assertThat(info.getServiceId()).isEqualTo("service1");
        assertThat(info.getCurrentBalance()).isEqualTo(1000L);
        assertThat(info.getRule()).isEqualTo("rule1");
        assertThat(info.getPriority()).isEqualTo(5L);
        assertThat(info.getInitialBalance()).isEqualTo(2000L);
        assertThat(info.getStatus()).isEqualTo("Active");
        assertThat(info.getUsage()).isEqualTo(500L);
        assertThat(info.getPlanId()).isEqualTo("plan1");
        assertThat(info.getBucketUser()).isEqualTo("testUser");
        assertThat(info.getConsumptionLimit()).isEqualTo(1000L);
        assertThat(info.getConsumptionTimeWindow()).isEqualTo(24L);
        assertThat(info.getSessionTimeout()).isEqualTo("3600");
        assertThat(info.getTimeWindow()).isEqualTo("00:00-23:59");
        assertThat(info.getExpiryDate()).isNotNull();
        assertThat(info.getServiceStartDate()).isNotNull();
        assertThat(info.getBucketExpiryDate()).isNotNull();
    }

    @Test
    void testGetServiceBucketsByUserName_SingleBucket() {
        // Given
        String username = "testUser";
        mockRow(row1, "bucket1", "service1", 1500L);

        List<Row> rows = new ArrayList<>();
        rows.add(row1);

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator()).thenAnswer(invocation -> rows.iterator());

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        List<ServiceBucketInfo> result = subscriber.awaitItem().getItem();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentBalance()).isEqualTo(1500L);
    }

    @Test
    void testGetServiceBucketsByUserName_MultipleBuckets() {
        // Given
        String username = "testUser";

        mockRow(row1, "bucket1", "service1", 1000L);

        Row row3 = mock(Row.class);
        Row row4 = mock(Row.class);
        mockRow(row2, "bucket2", "service2", 2000L);
        mockRow(row3, "bucket3", "service3", 3000L);
        mockRow(row4, "bucket4", "service4", 4000L);

        List<Row> rows = new ArrayList<>();
        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator()).thenAnswer(invocation -> rows.iterator());

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        List<ServiceBucketInfo> result = subscriber.awaitItem().getItem();
        assertThat(result).hasSize(4);
        assertThat(result.get(0).getBucketId()).isEqualTo("bucket1");
        assertThat(result.get(1).getBucketId()).isEqualTo("bucket2");
        assertThat(result.get(2).getBucketId()).isEqualTo("bucket3");
        assertThat(result.get(3).getBucketId()).isEqualTo("bucket4");
    }

    @Test
    void testGetServiceBucketsByUserName_ZeroBalance() {
        // Given
        String username = "testUser";
        mockRow(row1, "bucket1", "service1", 0L);

        List<Row> rows = new ArrayList<>();
        rows.add(row1);

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator()).thenAnswer(invocation -> rows.iterator());

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        List<ServiceBucketInfo> result = subscriber.awaitItem().getItem();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentBalance()).isEqualTo(0L);
    }

    @Test
    void testGetServiceBucketsByUserName_InactiveStatus() {
        // Given
        String username = "testUser";
        mockRow(row1, "bucket1", "service1", 1000L);
        when(row1.getString("STATUS")).thenReturn("Inactive");

        List<Row> rows = new ArrayList<>();
        rows.add(row1);

        when(client.preparedQuery(any(String.class))).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator()).thenAnswer(invocation -> rows.iterator());

        // When
        UniAssertSubscriber<List<ServiceBucketInfo>> subscriber = userBucketRepository
                .getServiceBucketsByUserName(username)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        // Then
        List<ServiceBucketInfo> result = subscriber.awaitItem().getItem();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("Inactive");
    }
}
