package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.*;
import com.csg.airtel.aaa4j.domain.model.cdr.*;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import com.csg.airtel.aaa4j.domain.produce.AccountProducer;
import com.csg.airtel.aaa4j.external.clients.CacheClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@ApplicationScoped
public class StopHandler {

    private static final Logger log = Logger.getLogger(StopHandler.class);


    private final CacheClient cacheUtil;
    private final AccountProducer accountProducer;
    private final AccountingUtil accountingUtil;

    @Inject
    public StopHandler(CacheClient cacheUtil, AccountProducer accountProducer, AccountingUtil accountingUtil) {
        this.cacheUtil = cacheUtil;
        this.accountProducer = accountProducer;
        this.accountingUtil = accountingUtil;
    }

    public Uni<Void> stopProcessing(AccountingRequestDto request, AccountingResponseEvent.EventType eventType,AccountingResponseEvent.ResponseAction action,String bucketId,String traceId) {
        log.infof("[traceId: %s] Processing accounting stop for user: %s, sessionId: %s",
                traceId, request.username(), request.sessionId());
        return cacheUtil.getUserData(request.username())
                .onItem().invoke(() -> log.infof("[traceId: %s] User data retrieved for user: %s", request.username()))
                .onItem().transformToUni(userSessionData ->
                        userSessionData != null ?
                                 processAccountingStop(userSessionData, request,bucketId).invoke(() -> log.infof("[traceId: %s] Completed processing for eventType=%s, action=%s, bucketId=%s", traceId,eventType, action, bucketId)): null
                )
                .onFailure().recoverWithUni(throwable -> {
                    log.errorf(throwable, "Error processing accounting for user: %s", request.username());
                    return Uni.createFrom().voidItem();
                });
    }

    public Uni<Void> processAccountingStop(
            UserSessionData userSessionData,AccountingRequestDto request
            ,String bucketId) {

        if (userSessionData.getSessions() == null || userSessionData.getSessions().isEmpty()) {
            log.infof("[traceId: %s] No active sessions found for user: %s", request.username());
            return Uni.createFrom().voidItem();

        }

        Session session = findSessionById(userSessionData.getSessions(), request.sessionId());

        if (session == null) {
            log.infof( "[traceId: %s] Session not found for sessionId: %s", request.username(), request.sessionId());
                return Uni.createFrom().voidItem();
        }

        Map<String, Object> columnValues = HashMap.newHashMap(5);
        Map<String, Object> whereConditions = HashMap.newHashMap(2);

        return cleanSessionAndUpdateBalance(userSessionData, columnValues, whereConditions,bucketId,request,session)
                .call(() -> {

                    DBWriteRequest dbWriteRequest = buildDBWriteRequest(
                            request.sessionId(),
                            columnValues,
                            whereConditions,
                            request.username()
                    );

                    return accountProducer.produceDBWriteEvent(dbWriteRequest)
                            .onFailure().invoke(throwable ->
                                    log.errorf(throwable, "Failed to produce DB write event for session: %s",
                                            request.sessionId())
                            );

                })
                .invoke(() -> {
                    // Generate and send CDR event asynchronously (fire and forget)
                    generateAndSendCDR(request, session);
                })
                .invoke(() -> userSessionData.getSessions().remove(session))
                .call(() -> {
                    log.infof("[traceId: %s] Updating cache for user: %s", request.username());
                    // Update cache
                    return cacheUtil.updateUserAndRelatedCaches(request.username(), userSessionData)
                            .onFailure().invoke(throwable ->
                                    log.errorf(throwable, "Failed to update cache for user: %s",
                                            request.username())
                            )
                            .onFailure().recoverWithNull(); // Cache failure can still be swallowed
                })
                .invoke(() -> {
                    if (log.isDebugEnabled()) {
                        log.debugf("Session and balance cleaned for session: %s", request.sessionId());
                    }
                })
                .onFailure().recoverWithUni(throwable -> {
                    log.errorf(throwable, "Failed to process accounting stop for session: %s",
                            request.sessionId());
                    return Uni.createFrom().voidItem();
                });
    }

    private Session findSessionById(List<Session> sessions, String sessionId) {
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                return session;
            }
        }
        return null;
    }


    private Uni<Void> cleanSessionAndUpdateBalance(
            UserSessionData userSessionData,
            Map<String, Object> columnValues,
            Map<String, Object> whereConditions,String bucketId,AccountingRequestDto request,Session session) {

        return accountingUtil.updateSessionAndBalance(userSessionData, session, request, bucketId)
                .onItem()
                .transformToUni(updateResult -> {

                    if (!updateResult.success()) {
                        log.warnf("update failed for sessionId: %s", request.sessionId());
                    }
                    populateWhereConditions(whereConditions, updateResult.balance());
                    populateColumnValues(columnValues, updateResult.balance());
                    return Uni.createFrom().voidItem();
                });
    }

    // Separate methods for clarity and potential reuse
    private void populateWhereConditions(Map<String, Object> whereConditions, Balance balance) {
        whereConditions.put("SERVICE_ID", balance.getServiceId());
        whereConditions.put("BUCKET_ID", balance.getBucketId());
    }

    private void populateColumnValues(Map<String, Object> columnValues, Balance balance) {
        columnValues.put("CURRENT_BALANCE", balance.getQuota());
        columnValues.put("USAGE", balance.getInitialBalance()- balance.getQuota());
        columnValues.put("UPDATED_AT", LocalDateTime.now());
    }

    // Extract to builder method for clarity and reusability
    private DBWriteRequest buildDBWriteRequest(
            String sessionId,
            Map<String, Object> columnValues,
            Map<String, Object> whereConditions,
            String userName) {

        DBWriteRequest dbWriteRequest = new DBWriteRequest();
        dbWriteRequest.setSessionId(sessionId);
        dbWriteRequest.setUserName(userName);
        dbWriteRequest.setEventType(EventType.UPDATE_EVENT);
        dbWriteRequest.setWhereConditions(whereConditions);
        dbWriteRequest.setColumnValues(columnValues);
        dbWriteRequest.setTableName("CSGAAA.BUCKET_TABLE");
        dbWriteRequest.setEventId(UUID.randomUUID().toString());
        dbWriteRequest.setTimestamp(LocalDateTime.now());

        return dbWriteRequest;
    }

    /**
     * Generates and sends CDR event asynchronously
     * This is a fire-and-forget operation that won't block the main processing flow
     */
    private void generateAndSendCDR(AccountingRequestDto request, Session session) {
        try {
            AccountingCDREvent cdrEvent = buildCDREvent(request, session);

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

    /**
     * Builds an AccountingCDREvent from the request and session data
     */
    private AccountingCDREvent buildCDREvent(AccountingRequestDto request, Session session) {
        // Build Session CDR object
        com.csg.airtel.aaa4j.domain.model.cdr.Session cdrSession = com.csg.airtel.aaa4j.domain.model.cdr.Session.builder()
                .sessionId(request.sessionId())
                .sessionTime(request.sessionTime() != null ? String.valueOf(request.sessionTime()) : "0")
                .startTime(session.getStartTime())
                .updateTime(LocalDateTime.now())
                .nasIdentifier(request.nasIP())
                .nasIpAddress(request.nasIP())
                .nasPort(request.nasPortId())
                .nasPortType("Async")
                .build();

        // Build User CDR object
        com.csg.airtel.aaa4j.domain.model.cdr.User cdrUser = com.csg.airtel.aaa4j.domain.model.cdr.User.builder()
                .userName(request.username())
                .build();

        // Build Network CDR object
        com.csg.airtel.aaa4j.domain.model.cdr.Network cdrNetwork = com.csg.airtel.aaa4j.domain.model.cdr.Network.builder()
                .framedIpAddress(request.framedIPAddress())
                .framedProtocol("PPP")
                .serviceType("Framed-User")
                .calledStationId(request.nasIP())
                .build();

        // Build Accounting CDR object with octets calculation
        Long inputOctets = calculateTotalOctets(request.inputOctets(), request.inputGigaWords());
        Long outputOctets = calculateTotalOctets(request.outputOctets(), request.outputGigaWords());

        com.csg.airtel.aaa4j.domain.model.cdr.Accounting cdrAccounting = com.csg.airtel.aaa4j.domain.model.cdr.Accounting.builder()
                .acctStatusType("Stop")
                .acctSessionTime(request.sessionTime())
                .acctInputOctets(inputOctets)
                .acctOutputOctets(outputOctets)
                .acctInputPackets(0)
                .acctOutputPackets(0)
                .acctInputGigawords(request.inputGigaWords())
                .acctOutputGigawords(request.outputGigaWords())
                .build();

        // Build Payload
        Payload payload = Payload.builder()
                .session(cdrSession)
                .user(cdrUser)
                .network(cdrNetwork)
                .accounting(cdrAccounting)
                .build();

        // Build and return AccountingCDREvent
        return AccountingCDREvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventTypes.ACCOUNTING_STOP.name())
                .eventVersion("1.0")
                .eventTimestamp(Instant.now())
                .source("AAA-Service")
                .payload(payload)
                .build();
    }

    /**
     * Calculates total octets from octets and gigawords
     * Formula: totalOctets = (gigawords * 2^32) + octets
     */
    private Long calculateTotalOctets(Integer octets, Integer gigawords) {
        long total = 0L;
        if (gigawords != null && gigawords > 0) {
            total = (long) gigawords * 4294967296L; // 2^32
        }
        if (octets != null) {
            total += octets;
        }
        return total;
    }

}

