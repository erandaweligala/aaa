package com.csg.airtel.aaa4j.external.repository;

import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.mutiny.sqlclient.*;
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
class UserBucketRepositoryTest {

    @Mock
    private Pool client;

    @Mock
    private PreparedQuery<RowSet<Row>> preparedQuery;

    @Mock
    private RowSet<Row> rowSet;

    @Mock
    private Row row;

    private UserBucketRepository userBucketRepository;

    @BeforeEach
    void setUp() {
        userBucketRepository = new UserBucketRepository(client);
    }

    @Test
    void shouldGetServiceBucketsSuccessfully() {
        String userName = "user123";
        List<Row> rows = createMockRows();

        when(client.preparedQuery(anyString()))
                .thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class)))
                .thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator())
                .thenReturn(rows.iterator());

        Uni<List<ServiceBucketInfo>> result = userBucketRepository.getServiceBucketsByUserName(userName);

        List<ServiceBucketInfo> buckets = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(buckets).isNotNull();
        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).getBucketId()).isEqualTo(1L);
        assertThat(buckets.get(0).getCurrentBalance()).isEqualTo(1000L);

        verify(preparedQuery).execute(any(Tuple.class));
    }

    @Test
    void shouldReturnEmptyListWhenNoBucketsFound() {
        String userName = "user123";
        List<Row> emptyRows = new ArrayList<>();

        when(client.preparedQuery(anyString()))
                .thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class)))
                .thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator())
                .thenReturn(emptyRows.iterator());

        Uni<List<ServiceBucketInfo>> result = userBucketRepository.getServiceBucketsByUserName(userName);

        List<ServiceBucketInfo> buckets = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(buckets).isNotNull();
        assertThat(buckets).isEmpty();
    }

    @Test
    void shouldHandleDatabaseError() {
        String userName = "user123";
        RuntimeException exception = new RuntimeException("Database connection error");

        when(client.preparedQuery(anyString()))
                .thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class)))
                .thenReturn(Uni.createFrom().failure(exception));

        Uni<List<ServiceBucketInfo>> result = userBucketRepository.getServiceBucketsByUserName(userName);

        result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .assertFailedWith(RuntimeException.class);
    }

    @Test
    void shouldMapAllFieldsCorrectly() {
        String userName = "user123";
        List<Row> rows = createMockRows();

        when(client.preparedQuery(anyString()))
                .thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class)))
                .thenReturn(Uni.createFrom().item(rowSet));
        when(rowSet.iterator())
                .thenReturn(rows.iterator());

        Uni<List<ServiceBucketInfo>> result = userBucketRepository.getServiceBucketsByUserName(userName);

        List<ServiceBucketInfo> buckets = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        ServiceBucketInfo bucket = buckets.get(0);
        assertThat(bucket.getBucketId()).isEqualTo(1L);
        assertThat(bucket.getCurrentBalance()).isEqualTo(1000L);
        assertThat(bucket.getServiceId()).isEqualTo(100L);
        assertThat(bucket.getRule()).isEqualTo("rule-1");
        assertThat(bucket.getPriority()).isEqualTo(1L);
        assertThat(bucket.getInitialBalance()).isEqualTo(1000L);
        assertThat(bucket.getStatus()).isEqualTo("Active");
        assertThat(bucket.getUsage()).isEqualTo(0L);
        assertThat(bucket.getPlanId()).isEqualTo("plan-1");
        assertThat(bucket.getBucketUser()).isEqualTo("user123");
        assertThat(bucket.getConsumptionLimit()).isEqualTo(5000L);
        assertThat(bucket.getConsumptionTimeWindow()).isEqualTo(24L);
        assertThat(bucket.getSessionTimeout()).isEqualTo("3600");
        assertThat(bucket.getTimeWindow()).isEqualTo("0-24");
    }

    private List<Row> createMockRows() {
        List<Row> rows = new ArrayList<>();
        Row mockRow = mock(Row.class);

        LocalDateTime now = LocalDateTime.now();

        when(mockRow.getLong("BUCKET_ID")).thenReturn(1L);
        when(mockRow.getLong("CURRENT_BALANCE")).thenReturn(1000L);
        when(mockRow.getLong("ID")).thenReturn(100L);
        when(mockRow.getString("RULE")).thenReturn("rule-1");
        when(mockRow.getLong("PRIORITY")).thenReturn(1L);
        when(mockRow.getLong("INITIAL_BALANCE")).thenReturn(1000L);
        when(mockRow.getString("STATUS")).thenReturn("Active");
        when(mockRow.getLong("USAGE")).thenReturn(0L);
        when(mockRow.getLocalDateTime("EXPIRY_DATE")).thenReturn(now.plusDays(30));
        when(mockRow.getLocalDateTime("SERVICE_START_DATE")).thenReturn(now.minusDays(1));
        when(mockRow.getString("PLAN_ID")).thenReturn("plan-1");
        when(mockRow.getString("BUCKET_USER")).thenReturn("user123");
        when(mockRow.getLong("CONSUMPTION_LIMIT")).thenReturn(5000L);
        when(mockRow.getLong("CONSUMPTION_LIMIT_WINDOW")).thenReturn(24L);
        when(mockRow.getString("SESSION_TIMEOUT")).thenReturn("3600");
        when(mockRow.getString("TIME_WINDOW")).thenReturn("0-24");
        when(mockRow.getLocalDateTime("EXPIRATION")).thenReturn(now.plusDays(30));

        rows.add(mockRow);
        return rows;
    }
}
