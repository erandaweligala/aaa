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


import java.util.Collection;
import java.util.List;



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
            if (balance.getQuota() <= 0) {
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

    public Uni<UpdateResult> updateSessionAndBalance(
            UserSessionData userData,
            Session sessionData,
            AccountingRequestDto request,String bucketId) {

        long totalGigaWords =(long) request.outputGigaWords() + (long) request.inputGigaWords();

        long totalOctets = (long) request.inputOctets() + (long) request.outputOctets();

        long totalUsage = calculateTotalOctets(totalOctets, totalGigaWords);

        return findBalanceWithHighestPriority(userData.getBalance(),bucketId)
                .onItem().transformToUni(foundBalance -> {
                    if (foundBalance == null) {
                        log.warnf("No valid balance found for user: %s", request.username());
                        return Uni.createFrom().item(UpdateResult.failure("error"));
                    }

                    long newQuota = getNewQuota(sessionData, foundBalance, totalUsage);

                    if (newQuota <= 0) {
                        log.warnf("Quota depleted for session: %s", request.sessionId());
                        newQuota = 0;
                    }

                    foundBalance.setQuota(newQuota);

                    sessionData.setPreviousTotalUsageQuotaValue(totalUsage);
                    sessionData.setSessionTime(request.sessionTime());

                    replaceInCollection(userData.getBalance(), foundBalance);
                    replaceInCollection(userData.getSessions(), sessionData);

                    UpdateResult success = UpdateResult.success(newQuota, foundBalance.getBucketId(),foundBalance);
                    if (success.newQuota() <= 0) {
                        // Handle disconnect: produce events, remove session, update cache, return result
                        return updateCOASessionForDisconnect(userData, request.sessionId(), request.username())
                                .invoke(() -> {
                                    // Remove the current session after all disconnect events are produced
                                    log.infof("Successfully updated session: %s", request.sessionId());
                                    userData.getSessions().removeIf(session ->
                                            !session.getSessionId().equals(request.sessionId()));
                                })
                                .chain(() -> cacheClient.updateUserAndRelatedCaches(request.username(), userData))
                                .onFailure().invoke(err ->
                                        log.errorf(err, "Error updating cache for user: %s", request.username()))
                                .replaceWith(success);
                    }

                    return cacheClient.updateUserAndRelatedCaches(request.username(), userData)
                            .onFailure().invoke(err ->
                                    log.errorf(err, "Error updating cache for user: %s", request.username()))
                            .replaceWith(success);
                });
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


}
