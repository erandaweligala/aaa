package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.ConsumptionRecord;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AccountingUtilTest {

    @Mock
    private AccountProducer accountProducer;

    @Mock
    private CacheClient cacheClient;

    private AccountingUtil accountingUtil;

    @BeforeEach
    void setUp() {
        accountingUtil = new AccountingUtil(accountProducer, cacheClient);
    }

    @Test
    void shouldFindBalanceWithHighestPriority() {
        List<Balance> balances = createBalanceList();

        Uni<Balance> result = accountingUtil.findBalanceWithHighestPriority(balances, null);

        Balance foundBalance = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(foundBalance).isNotNull();
        assertThat(foundBalance.getPriority()).isEqualTo(1L);
    }

    @Test
    void shouldFindSpecificBalanceByBucketId() {
        List<Balance> balances = createBalanceList();
        String targetBucketId = "bucket-2";

        Uni<Balance> result = accountingUtil.findBalanceWithHighestPriority(balances, targetBucketId);

        Balance foundBalance = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(foundBalance).isNotNull();
        assertThat(foundBalance.getBucketId()).isEqualTo(targetBucketId);
    }

    @Test
    void shouldReturnNullForEmptyBalanceList() {
        List<Balance> balances = new ArrayList<>();

        Uni<Balance> result = accountingUtil.findBalanceWithHighestPriority(balances, null);

        Balance foundBalance = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(foundBalance).isNull();
    }

    @Test
    void shouldValidateTimeWindowWithin24Hours() {
        String timeWindow = "0-24";

        boolean result = accountingUtil.isWithinTimeWindow(timeWindow);

        assertThat(result).isTrue();
    }

    @Test
    void shouldValidateTimeWindowWithinRange() {
        LocalTime currentTime = LocalTime.now();
        int currentHour = currentTime.getHour();

        int startHour = (currentHour > 0) ? currentHour - 1 : 0;
        int endHour = (currentHour < 23) ? currentHour + 1 : 23;
        String timeWindow = startHour + "-" + endHour;

        boolean result = accountingUtil.isWithinTimeWindow(timeWindow);

        assertThat(result).isTrue();
    }

    @Test
    void shouldValidateTimeWindowOutsideRange() {
        LocalTime currentTime = LocalTime.now();
        int currentHour = currentTime.getHour();

        int startHour = (currentHour + 2) % 24;
        int endHour = (currentHour + 4) % 24;
        String timeWindow = startHour + "-" + endHour;

        boolean result = accountingUtil.isWithinTimeWindow(timeWindow);

        assertThat(result).isFalse();
    }

    @Test
    void shouldThrowExceptionForInvalidTimeWindowFormat() {
        String invalidTimeWindow = "invalid";

        assertThatThrownBy(() -> accountingUtil.isWithinTimeWindow(invalidTimeWindow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid time window format");
    }

    @Test
    void shouldThrowExceptionForNullTimeWindow() {
        assertThatThrownBy(() -> accountingUtil.isWithinTimeWindow(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time window string cannot be null or empty");
    }

    @Test
    void shouldThrowExceptionForEmptyTimeWindow() {
        assertThatThrownBy(() -> accountingUtil.isWithinTimeWindow(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time window string cannot be null or empty");
    }

    @Test
    void shouldCalculateConsumptionInWindow() {
        Balance balance = createBalance("bucket-1", 1000L, 1L);
        balance.setConsumptionLimit(5000L);
        balance.setConsumptionLimitWindow(24L);

        ConsumptionRecord record1 = new ConsumptionRecord(LocalDateTime.now().minusHours(2), 1000L);
        ConsumptionRecord record2 = new ConsumptionRecord(LocalDateTime.now().minusHours(1), 2000L);
        balance.setConsumptionHistory(new ArrayList<>(List.of(record1, record2)));

        long consumption = accountingUtil.calculateConsumptionInWindow(balance, 24L);

        assertThat(consumption).isEqualTo(3000L);
    }

    @Test
    void shouldReturnZeroForEmptyConsumptionHistory() {
        Balance balance = createBalance("bucket-1", 1000L, 1L);
        balance.setConsumptionHistory(new ArrayList<>());

        long consumption = accountingUtil.calculateConsumptionInWindow(balance, 24L);

        assertThat(consumption).isEqualTo(0L);
    }

    @Test
    void shouldReturnZeroForNullConsumptionHistory() {
        Balance balance = createBalance("bucket-1", 1000L, 1L);
        balance.setConsumptionHistory(null);

        long consumption = accountingUtil.calculateConsumptionInWindow(balance, 24L);

        assertThat(consumption).isEqualTo(0L);
    }

    @Test
    void shouldCalculateConsumptionWithin12HourWindow() {
        Balance balance = createBalance("bucket-1", 1000L, 1L);
        balance.setConsumptionLimit(5000L);
        balance.setConsumptionLimitWindow(12L);

        ConsumptionRecord record1 = new ConsumptionRecord(LocalDateTime.now().minusHours(2), 1000L);
        ConsumptionRecord record2 = new ConsumptionRecord(LocalDateTime.now().minusHours(15), 2000L);
        balance.setConsumptionHistory(new ArrayList<>(List.of(record1, record2)));

        long consumption = accountingUtil.calculateConsumptionInWindow(balance, 12L);

        assertThat(consumption).isEqualTo(1000L);
    }

    @Test
    void shouldSkipBalanceWithZeroQuota() {
        List<Balance> balances = new ArrayList<>();
        Balance balance1 = createBalance("bucket-1", 0L, 1L);
        Balance balance2 = createBalance("bucket-2", 1000L, 2L);
        balances.add(balance1);
        balances.add(balance2);

        Uni<Balance> result = accountingUtil.findBalanceWithHighestPriority(balances, null);

        Balance foundBalance = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(foundBalance).isNotNull();
        assertThat(foundBalance.getBucketId()).isEqualTo("bucket-2");
    }

    @Test
    void shouldSkipExpiredBalance() {
        List<Balance> balances = new ArrayList<>();
        Balance balance1 = createBalance("bucket-1", 1000L, 1L);
        balance1.setServiceExpiry(LocalDateTime.now().minusDays(1));

        Balance balance2 = createBalance("bucket-2", 1000L, 2L);
        balance2.setServiceExpiry(LocalDateTime.now().plusDays(1));

        balances.add(balance1);
        balances.add(balance2);

        Uni<Balance> result = accountingUtil.findBalanceWithHighestPriority(balances, null);

        Balance foundBalance = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(foundBalance).isNotNull();
        assertThat(foundBalance.getBucketId()).isEqualTo("bucket-2");
    }

    private List<Balance> createBalanceList() {
        List<Balance> balances = new ArrayList<>();
        balances.add(createBalance("bucket-1", 1000L, 1L));
        balances.add(createBalance("bucket-2", 2000L, 2L));
        balances.add(createBalance("bucket-3", 500L, 3L));
        return balances;
    }

    private Balance createBalance(String bucketId, Long quota, Long priority) {
        Balance balance = new Balance();
        balance.setBucketId(bucketId);
        balance.setQuota(quota);
        balance.setInitialBalance(quota);
        balance.setPriority(priority);
        balance.setServiceExpiry(LocalDateTime.now().plusDays(30));
        balance.setBucketExpiryDate(LocalDateTime.now().plusDays(30));
        balance.setServiceStartDate(LocalDateTime.now().minusDays(1));
        balance.setServiceStatus("Active");
        balance.setTimeWindow("0-24");
        balance.setBucketUsername("user123");
        return balance;
    }
}
