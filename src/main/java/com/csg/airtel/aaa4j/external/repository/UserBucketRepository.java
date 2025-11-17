package com.csg.airtel.aaa4j.external.repository;


import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;


import java.util.ArrayList;
import java.util.List;

import static com.csg.airtel.aaa4j.domain.constant.SQLconstant.QUERY_BALANCE;

@ApplicationScoped
public class UserBucketRepository {

    private static final Logger log = Logger.getLogger(UserBucketRepository.class);

    final Pool client;

    @Inject
    public UserBucketRepository(Pool client) {
        this.client = client;
    }

    /**
     * Fetch service buckets with circuit breaker and retry for database resilience
     *
     * @param userName The username
     * @return Uni<List<ServiceBucketInfo>> containing the service buckets
     */
    @CircuitBreaker(
            requestVolumeThreshold = 20,
            failureRatio = 0.6,
            delay = 10000,
            successThreshold = 3
    )
    @Retry(
            maxRetries = 3,
            delay = 200,
            maxDuration = 10000
    )
    @Timeout(value = 10000)
    public Uni<List<ServiceBucketInfo>> getServiceBucketsByUserName(String userName) {
        long startTime = System.currentTimeMillis();
        log.infof("Fetching Start service buckets for user: %s", userName);

        return client
                .preparedQuery(QUERY_BALANCE)
                .execute(Tuple.of(userName))
                .onItem().transform(this::mapRowsToServiceBuckets)
                .onFailure().invoke(error ->
                    // Log with full stack trace
                    log.errorf(error, "Error fetching service buckets for user: %s", userName)
                )
                .onItem().invoke(results ->
                       log.infof("Fetched %d service buckets for user: %s in %s ms",
                               results.size(), userName, System.currentTimeMillis() - startTime));
    }

    private List<ServiceBucketInfo> mapRowsToServiceBuckets(RowSet<Row> rows) {
        List<ServiceBucketInfo> results = new ArrayList<>();
        for (Row row : rows) {
            ServiceBucketInfo info = new ServiceBucketInfo();
            info.setBucketId(row.getString("BUCKET_ID"));
            info.setCurrentBalance(row.getLong("CURRENT_BALANCE"));
            info.setServiceId(row.getString("SERVICE_ID"));
            info.setRule(row.getString("RULE"));
            info.setPriority(row.getLong("PRIORITY"));
            info.setInitialBalance(row.getLong("INITIAL_BALANCE"));
            info.setStatus(row.getString("STATUS"));
            info.setUsage(row.getLong("USAGE"));
            info.setExpiryDate(row.getLocalDateTime("EXPIRY_DATE"));
            info.setServiceStartDate(row.getLocalDateTime("SERVICE_START_DATE"));
            info.setPlanId(row.getString("PLAN_ID"));
            results.add(info);
        }
        return results;
    }

}
