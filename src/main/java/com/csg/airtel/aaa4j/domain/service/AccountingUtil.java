package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.UpdateResult;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.LocalDateTime;


import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


@ApplicationScoped
public class AccountingUtil {

    private static final Logger log = Logger.getLogger(AccountingUtil.class);
    private static final long GIGAWORD_MULTIPLIER = 4294967296L;
   private final AccountProducer accountProducer;
    private final CacheClient cacheClient;


    public AccountingUtil(AccountProducer accountProducer, CacheClient utilCache) {
        this.accountProducer = accountProducer;
        this.cacheClient = utilCache;
    }

    /**
     * @param balances user related buckets balances
     * @param bucketId specific bucket id to prioritize
     * @return return the balance with the highest priority
     */
    public Uni<Balance> findBalanceWithHighestPriority(List<Balance> balances,String bucketId) {
        log.infof("Finding balance with highest priority from %d balances", balances.size());
        return Uni.createFrom().item(() -> computeHighestPriority(balances,bucketId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Balance computeHighestPriority(List<Balance> balances,String bucketId) {

        if(bucketId!=null){
            for (Balance balance : balances) {
                if (balance.getBucketId().equals(bucketId)) {
                    return balance;
                }
            }
        }
        if (balances == null || balances.isEmpty()) {
            return null;
        }

        return getBalance(balances);

    }

    private static Balance getBalance(List<Balance> balances) {

        Balance highest = null;
        long highestPriority = Long.MIN_VALUE;
        LocalDateTime highestExpiry = null;

        for (Balance balance : balances) {

            String timeWindow = balance.getTimeWindow(); // assume time window 6PM-6AM

            if (balance.getQuota() <= 0 || !isWithinTimeWindow(timeWindow)) {
                continue;
            }

            if((balance.getServiceStartDate().isBefore(LocalDateTime.now()) || balance.getServiceStartDate().isEqual(LocalDateTime.now()) )&& balance.getServiceStatus().equals("ACTIVE")) {
                long priority = balance.getPriority();
                LocalDateTime expiry = balance.getServiceExpiry();

                if (highest == null || priority < highestPriority ||
                        (priority == highestPriority && expiry != null &&
                                (highestExpiry == null || expiry.isBefore(highestExpiry)))) {
                    highest = balance;
                    highestPriority = priority;
                    highestExpiry = expiry;
                }
            }
        }
        log.infof("Balance with highest priority selected: %s", highest != null ? highest.getBucketId() : "None");
        return highest;
    }

    /**
     * @param userData get user session data
     * @param sessionData get individual session Data
     * @param request packet request
     * @param bucketId bucket id
     * @return update results
     */
    public Uni<UpdateResult> updateSessionAndBalance(
            UserSessionData userData,
            Session sessionData,
            AccountingRequestDto request,
            String bucketId) {

        long totalUsage = calculateTotalUsage(request);

        return getCombinedBalances(userData.getGroupId(), userData.getBalance())
                .onItem().transformToUni(combinedBalances ->
                        findBalanceWithHighestPriority(combinedBalances, bucketId)
                                .onItem().transformToUni(foundBalance ->
                                        processBalanceUpdate(userData, sessionData, request, foundBalance, combinedBalances, totalUsage)
                                )
                );
    }

    private long calculateTotalUsage(AccountingRequestDto request) {
        long totalGigaWords = (long) request.outputGigaWords() + (long) request.inputGigaWords();
        long totalOctets = (long) request.inputOctets() + (long) request.outputOctets();
        return calculateTotalOctets(totalOctets, totalGigaWords);
    }

    private Uni<List<Balance>> getCombinedBalances(String groupId, List<Balance> userBalances) {
        return getGroupBucket(groupId)
                .onItem().transform(groupBalances -> {
                    List<Balance> combined = new ArrayList<>(userBalances);
                    if (groupBalances != null && !groupBalances.isEmpty()) {
                        combined.addAll(groupBalances);
                    }
                    return combined;
                });
    }

    private Uni<UpdateResult> processBalanceUpdate(
            UserSessionData userData,
            Session sessionData,
            AccountingRequestDto request,
            Balance foundBalance,
            List<Balance> combinedBalances,
            long totalUsage) {

        if (foundBalance == null) {
            log.warnf("No valid balance found for user: %s", request.username());
            return Uni.createFrom().item(UpdateResult.failure("error"));
        }

        String previousUsageBucketId = getPreviousUsageBucketId(sessionData, foundBalance);
        boolean bucketChanged = !previousUsageBucketId.equals(foundBalance.getBucketId());

        long newQuota = updateQuotaForBucketChange(
                userData, sessionData, foundBalance, combinedBalances,
                previousUsageBucketId, bucketChanged, totalUsage
        );

        updateSessionData(sessionData, foundBalance, totalUsage, request.sessionTime());

        UpdateResult result = UpdateResult.success(newQuota, foundBalance.getBucketId(), foundBalance, previousUsageBucketId);

        if (shouldDisconnectSession(result, foundBalance, previousUsageBucketId)) {
            return handleSessionDisconnect(userData, request, foundBalance, result);
        }

        return updateCacheForNormalOperation(userData, request, foundBalance, result);
    }

    private String getPreviousUsageBucketId(Session sessionData, Balance foundBalance) {
        String previousId = sessionData.getPreviousUsageBucketId();
        return previousId != null ? previousId : foundBalance.getBucketId();
    }

    private long updateQuotaForBucketChange(
            UserSessionData userData,
            Session sessionData,
            Balance foundBalance,
            List<Balance> combinedBalances,
            String previousUsageBucketId,
            boolean bucketChanged,
            long totalUsage) {

        long newQuota;

        if (bucketChanged) {
            log.infof("Bucket changed from %s to %s for session: %s",
                    previousUsageBucketId, foundBalance.getBucketId(), sessionData.getSessionId());

            Balance previousBalance = findBalanceByBucketId(combinedBalances, previousUsageBucketId);
            newQuota = updatePreviousBucketQuota(userData, sessionData, previousBalance, totalUsage);
        } else {
            newQuota = calculateAndUpdateCurrentBucketQuota(userData, sessionData, foundBalance, totalUsage);
        }

        return Math.max(newQuota, 0);
    }

    private long updatePreviousBucketQuota(
            UserSessionData userData,
            Session sessionData,
            Balance previousBalance,
            long totalUsage) {

        if (previousBalance == null) {
            return 0;
        }

        long newQuota = getNewQuota(sessionData, previousBalance, totalUsage);
        previousBalance.setQuota(Math.max(newQuota, 0));
        replaceInCollection(userData.getBalance(), previousBalance);

        log.infof("Updated previous bucket %s quota to %d",
                previousBalance.getBucketId(), previousBalance.getQuota());

        return newQuota;
    }

    private long calculateAndUpdateCurrentBucketQuota(
            UserSessionData userData,
            Session sessionData,
            Balance foundBalance,
            long totalUsage) {

        long newQuota = getNewQuota(sessionData, foundBalance, totalUsage);

        if (newQuota <= 0) {
            log.warnf("Quota depleted for session: %s", sessionData.getSessionId());
        }

        foundBalance.setQuota(Math.max(newQuota, 0));
        replaceInCollection(userData.getBalance(), foundBalance);
        replaceInCollection(userData.getSessions(), sessionData);

        return newQuota;
    }

    private void updateSessionData(Session sessionData, Balance foundBalance, long totalUsage, Integer sessionTime) {
        sessionData.setPreviousTotalUsageQuotaValue(totalUsage);
        sessionData.setSessionTime(sessionTime);
        sessionData.setPreviousUsageBucketId(foundBalance.getBucketId());
    }

    private boolean shouldDisconnectSession(UpdateResult result, Balance foundBalance, String previousUsageBucketId) {
        return result.newQuota() <= 0 || !foundBalance.getBucketId().equals(previousUsageBucketId);
    }

    private Uni<UpdateResult> handleSessionDisconnect(
            UserSessionData userData,
            AccountingRequestDto request,
            Balance foundBalance,
            UpdateResult result) {

        if (!foundBalance.getBucketUsername().equals(request.username())) {
            userData.getBalance().remove(foundBalance);
        }

        return updateCOASessionForDisconnect(userData, request.sessionId(), request.username())
                .invoke(() -> {
                    log.infof("Successfully updated COA Disconnect session: %s", request.sessionId());
                    userData.getSessions().removeIf(session ->
                            !session.getSessionId().equals(request.sessionId()));
                })
                .chain(() -> cacheClient.updateUserAndRelatedCaches(request.username(), userData))
                .onFailure().invoke(err ->
                        log.errorf(err, "Error updating cache COA Disconnect for user: %s", request.username()))
                .replaceWith(result);
    }

    private Uni<UpdateResult> updateCacheForNormalOperation(
            UserSessionData userData,
            AccountingRequestDto request,
            Balance foundBalance,
            UpdateResult result) {
        return getUpdateResultUni(userData, request, foundBalance, result);
    }


    /**
     * Find a balance by bucket ID from a list of balances
     * @param balances list of balances to search
     * @param bucketId the bucket ID to find
     * @return the balance with matching bucket ID, or null if not found
     */
    private Balance findBalanceByBucketId(List<Balance> balances, String bucketId) {
        if (balances == null || bucketId == null) {
            return null;
        }
        return balances.stream()
                .filter(balance -> bucketId.equals(balance.getBucketId()))
                .findFirst()
                .orElse(null);
    }

    private Uni<UpdateResult> getUpdateResultUni(UserSessionData userData, AccountingRequestDto request, Balance foundBalance, UpdateResult success) {
        if(!foundBalance.getBucketUsername().equals(request.username())) {
            userData.getBalance().remove(foundBalance);
            UserSessionData userSessionGroupData = new UserSessionData();
            userSessionGroupData.setBalance(List.of(foundBalance));
            return cacheClient.updateUserAndRelatedCaches(foundBalance.getBucketUsername(), userSessionGroupData)
                    .onFailure().invoke(err ->
                            log.errorf(err, "Error updating Group Balance cache for user: %s", foundBalance.getBucketUsername()))
                    .chain(() -> cacheClient.updateUserAndRelatedCaches(request.username(), userData)
                            .onFailure().invoke(err ->
                                    log.errorf(err, "Error updating cache for user: %s", request.username())))
                    .replaceWith(success);
        }else {
            return cacheClient.updateUserAndRelatedCaches(request.username(), userData)
                    .onFailure().invoke(err ->
                            log.errorf(err, "Error updating cache for user: %s", request.username()))
                    .replaceWith(success);
        }
    }

    private long getNewQuota(Session sessionData, Balance foundBalance, long totalUsage) {
        Long previousUsageObj = sessionData.getPreviousTotalUsageQuotaValue();
        long previousUsage = previousUsageObj == null ? 0L : previousUsageObj;
        long usageDelta = totalUsage - previousUsage;
        if (usageDelta < 0) {
            // if totalUsage is unexpectedly smaller than previous usage, clamp to 0
            usageDelta = 0;
        }

        return foundBalance.getQuota() - usageDelta;
    }

    private <T> void replaceInCollection(Collection<T> collection, T element) {
        collection.removeIf(item -> item.equals(element));
        collection.add(element);
    }


    private Uni<Void> updateCOASessionForDisconnect(UserSessionData userSessionData, String sessionId, String username) {
        return Multi.createFrom().iterable(userSessionData.getSessions())
                .filter(session -> !session.getSessionId().equals(sessionId))
                .onItem().transformToUniAndConcatenate(  // Change this
                        session -> accountProducer.produceAccountingResponseEvent(
                                        MappingUtil.createResponse(session.getSessionId(), "Disconnect", session.getNasIp(), session.getFramedId(), username)
                                )
                                .onFailure().retry()
                                .withBackOff(Duration.ofMillis(100), Duration.ofSeconds(2))
                                .atMost(2)
                                .onFailure().invoke(failure ->
                                        log.errorf(failure, "Failed to produce disconnect event for session: %s", session.getSessionId())
                                )
                                .onFailure().recoverWithNull()
                )
                .collect().asList()
                .ifNoItem().after(Duration.ofSeconds(45)).fail()
                .replaceWithVoid();
    }

    private long calculateTotalOctets(long octets, long gigawords) {
        return (gigawords * GIGAWORD_MULTIPLIER) + octets;
    }


    public static boolean isWithinTimeWindow(String timeWindow) {
        // Parse the time window string (e.g., "6PM-6AM" or "18:00-06:00")
        String[] times = timeWindow.split("-");

        if (times.length != 2) {
            log.errorf("Invalid time window: %s", timeWindow);
            throw new IllegalArgumentException("Invalid time window format");
        }

        LocalTime startTime = parseTime(times[0].trim());
        LocalTime endTime = parseTime(times[1].trim());
        LocalTime currentTime = LocalTime.now();

        if (startTime.isBefore(endTime)) {

            return !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        } else {
            return !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
        }
    }

    private static LocalTime parseTime(String time) {
        time = time.toUpperCase().trim();

        if (time.contains("AM") || time.contains("PM")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mma");
            if (!time.contains(":")) {
                time = time.substring(0, time.length() - 2) + ":00" + time.substring(time.length() - 2);
            }
            return LocalTime.parse(time, formatter);
        } else {
            // 24-hour format
            return LocalTime.parse(time);
        }
    }

    private Uni<List<Balance>> getGroupBucket(String groupId) {
        Uni<List<Balance>> balanceListUni;

        if (!Objects.equals(groupId, "1")) {
            balanceListUni = cacheClient.getUserData(groupId)
                    .onItem()
                    .transform(UserSessionData::getBalance);
        } else {
            balanceListUni = Uni.createFrom().item(new ArrayList<>());
        }
    return balanceListUni;
    }

}
