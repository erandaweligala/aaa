package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import com.csg.airtel.aaa4j.domain.model.EventTypes;
import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
import com.csg.airtel.aaa4j.domain.model.cdr.*;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;

import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import com.csg.airtel.aaa4j.external.repository.UserBucketRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;


import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

@ApplicationScoped
public class InterimHandler {
    private static final Logger log = Logger.getLogger(InterimHandler.class);
    private static final String NO_SERVICE_BUCKETS_MSG = "No service buckets found";
    private static final String DATA_QUOTA_ZERO_MSG = "Data quota is zero";


    private final CacheClient cacheUtil;
    private final UserBucketRepository userRepository;
    private final StopHandler stopHandler;
    private final AccountingUtil accountingUtil;
    private final AccountProducer accountProducer;
    @Inject
    public InterimHandler(CacheClient cacheUtil, UserBucketRepository userRepository, StopHandler stopHandler, AccountingUtil accountingUtil, AccountProducer accountProducer) {
        this.cacheUtil = cacheUtil;
        this.userRepository = userRepository;
        this.stopHandler = stopHandler;
        this.accountingUtil = accountingUtil;
        this.accountProducer = accountProducer;
    }

    public Uni<Void> handleInterim(AccountingRequestDto request,String traceId) {
        long startTime = System.currentTimeMillis();
        log.infof("[traceId: %s] Processing interim accounting request Start for user: %s, sessionId: %s",traceId,
                request.username(), request.sessionId());
        return cacheUtil.getUserData(request.username())
                .onItem().invoke(() -> {
                    if (log.isDebugEnabled()) {
                        log.debugf("User data retrieved for user: %s", request.username());
                    }
                })
                .onItem().transformToUni(userSessionData ->
                        userSessionData == null
                                ? handleNewSessionUsage(request,traceId).invoke(() -> log.infof("[traceId: %s] Completed processing interim accounting for new session for  %s ms",traceId,System.currentTimeMillis()-startTime))
                                : processAccountingRequest(userSessionData, request,traceId).invoke(() -> log.infof("[traceId: %s] Completed processing interim accounting for existing session for  %s ms",traceId, System.currentTimeMillis()-startTime))

                )
                .onFailure().recoverWithUni(throwable -> {
                    log.errorf(throwable, "Error processing accounting for user: %s", request.username());
                    return Uni.createFrom().voidItem();
                });
    }

    private Uni<Void> handleNewSessionUsage(AccountingRequestDto request,String traceId) {

        if (log.isDebugEnabled()) {
            log.debugf("No cache entry found for user: %s", request.username());
        }
        return userRepository.getServiceBucketsByUserName(request.username())
                .onItem().transformToUni(serviceBuckets -> {
                    if (serviceBuckets == null || serviceBuckets.isEmpty()) {
                        log.warnf("No service buckets found for user: %s", request.username());
                       return accountProducer.produceAccountingResponseEvent(MappingUtil.createResponse(request, NO_SERVICE_BUCKETS_MSG, AccountingResponseEvent.EventType.COA,
                                AccountingResponseEvent.ResponseAction.DISCONNECT));
                    }
                    int bucketCount = serviceBuckets.size();
                    List<Balance> balanceList = new ArrayList<>(bucketCount);
                    double totalQuota = 0.0;

                    for (ServiceBucketInfo bucket : serviceBuckets) {
                        double currentBalance = bucket.getCurrentBalance();
                        totalQuota += currentBalance;
                        balanceList.add(createBalance(bucket));
                    }

                    if (totalQuota <= 0) {
                        log.warnf("User: %s has zero total data quota", request.username());
                        return accountProducer.produceAccountingResponseEvent(MappingUtil.createResponse(request, DATA_QUOTA_ZERO_MSG, AccountingResponseEvent.EventType.COA,
                                AccountingResponseEvent.ResponseAction.DISCONNECT));
                    }

                     UserSessionData newUserSessionData =  UserSessionData.builder()
                    .balance(balanceList).sessions(new ArrayList<>(List.of(createSession(request)))).build();

                     return processAccountingRequest(newUserSessionData, request,traceId);

                });
    }

    private Uni<Void> processAccountingRequest(
            UserSessionData userData, AccountingRequestDto request, String traceId) {
        long startTime = System.currentTimeMillis();
        log.infof("Processing interim accounting request for user: %s, sessionId: %s",
                request.username(), request.sessionId());

        Session session = findSession(userData, request.sessionId());
        if (session == null) {
            session = createSession(request);
        }

        // Thread-safe session time check to prevent duplicate processing
        final Session finalSession = session;
        synchronized (finalSession) {
            if (request.sessionTime() <= finalSession.getSessionTime()) {
                log.debugf("Duplicate Session time unchanged for sessionId: %s. Request time: %d, Session time: %d",
                        request.sessionId(), request.sessionTime(), finalSession.getSessionTime());
                return Uni.createFrom().voidItem();
            }
        }

        // Continue processing with updated session
        {
            return accountingUtil.updateSessionAndBalance(userData, session, request,null)
                    .onItem().transformToUni(updateResult -> {  // Changed from transform to transformToUni
                        if (!updateResult.success()) {
                            log.warnf("update failed for sessionId: %s", request.sessionId());
                        }
                        if(updateResult.newQuota()<=0){
                          return stopHandler.stopProcessing(request, AccountingResponseEvent.EventType.COA,
                                    AccountingResponseEvent.ResponseAction.DISCONNECT,updateResult.bucketId(),traceId);
                        }
                        log.infof("Interim accounting processing time ms : %d",
                                System.currentTimeMillis() - startTime);

                        // Generate and send CDR event asynchronously (fire and forget)
                        generateAndSendCDR(request, session);

                        return Uni.createFrom().voidItem();

                    });
        }
    }

    /**
     * Thread-safe method to find a session by ID
     *
     * @param userData  User session data
     * @param sessionId Session ID to find
     * @return Session if found, null otherwise
     */
    private Session findSession(UserSessionData userData, String sessionId) {
        List<Session> sessions = userData.getSessions();
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.getSessionId().equals(sessionId)) {
                    return session;
                }
            }
            return null;
        }
    }



    private Session createSession(AccountingRequestDto request) {
        return new Session(
                request.sessionId(),
                LocalDateTime.now(),
                null,
                "",
                request.sessionTime() - 1,
                0L,
                request.framedIPAddress(),
                request.nasIP()

        );
    }

    private Balance createBalance(ServiceBucketInfo bucket) {
        Balance balance = new Balance();
        balance.setBucketId(bucket.getBucketId());
        balance.setServiceId(bucket.getServiceId());
        balance.setServiceExpiry(bucket.getExpiryDate());
        balance.setPriority(bucket.getPriority());
        balance.setQuota(bucket.getCurrentBalance());
        balance.setInitialBalance(bucket.getInitialBalance());
        balance.setServiceStartDate(bucket.getServiceStartDate());
        balance.setServiceStatus(bucket.getStatus());
        return balance;
    }

    /**
     * Generates and sends CDR event asynchronously
     * This is a fire-and-forget operation that won't block the main processing flow
     */
    private void generateAndSendCDR(AccountingRequestDto request, Session session) {
        try {
            AccountingCDREvent cdrEvent = CdrMappingUtil.buildInterimCDREvent(request, session);

            // Fire and forget - run asynchronously without blocking
            accountProducer.produceAccountingCDREvent(cdrEvent)
                    .subscribe()
                    .with(
                            success -> log.infof("CDR event sent successfully for session: %s", request.sessionId()),
                            failure -> log.errorf(failure, "Failed to send CDR event for session: %s", request.sessionId())
                    );
        } catch (Exception e) {
            log.errorf(e, "Error building CDR event for session: %s", request.sessionId());
        }
    }

}