package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.EventTypes;
import com.csg.airtel.aaa4j.domain.model.cdr.*;
import com.csg.airtel.aaa4j.domain.model.session.Session;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Utility class for mapping AccountingRequestDto and Session data to CDR events.
 * Provides common mapping logic used across all accounting handlers (Start, Interim, Stop).
 */
public class CdrMappingUtil {

    private CdrMappingUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Builds a complete AccountingCDREvent for START events
     */
    public static AccountingCDREvent buildStartCDREvent(AccountingRequestDto request, Session session) {
        return buildCDREvent(
                request,
                session,
                "Start",
                EventTypes.ACCOUNTING_START.name(),
                0,
                0L,
                0L,
                0,
                0
        );
    }

    /**
     * Builds a complete AccountingCDREvent for INTERIM-UPDATE events
     */
    public static AccountingCDREvent buildInterimCDREvent(AccountingRequestDto request, Session session) {
        Long inputOctets = calculateTotalOctets(request.inputOctets(), request.inputGigaWords());
        Long outputOctets = calculateTotalOctets(request.outputOctets(), request.outputGigaWords());

        return buildCDREvent(
                request,
                session,
                "Interim-Update",
                EventTypes.ACCOUNTING_INTERIM.name(),
                request.sessionTime(),
                inputOctets,
                outputOctets,
                request.inputGigaWords(),
                request.outputGigaWords()
        );
    }

    /**
     * Builds a complete AccountingCDREvent for STOP events
     */
    public static AccountingCDREvent buildStopCDREvent(AccountingRequestDto request, Session session) {
        Long inputOctets = calculateTotalOctets(request.inputOctets(), request.inputGigaWords());
        Long outputOctets = calculateTotalOctets(request.outputOctets(), request.outputGigaWords());

        return buildCDREvent(
                request,
                session,
                "Stop",
                EventTypes.ACCOUNTING_STOP.name(),
                request.sessionTime(),
                inputOctets,
                outputOctets,
                request.inputGigaWords(),
                request.outputGigaWords()
        );
    }

    /**
     * Internal method to build a complete AccountingCDREvent with all components
     */
    private static AccountingCDREvent buildCDREvent(
            AccountingRequestDto request,
            Session session,
            String acctStatusType,
            String eventType,
            Integer sessionTime,
            Long inputOctets,
            Long outputOctets,
            Integer inputGigawords,
            Integer outputGigawords) {

        com.csg.airtel.aaa4j.domain.model.cdr.Session cdrSession = buildSessionCdr(request, session, sessionTime);
        com.csg.airtel.aaa4j.domain.model.cdr.User cdrUser = buildUserCdr(request);
        com.csg.airtel.aaa4j.domain.model.cdr.Network cdrNetwork = buildNetworkCdr(request);
        com.csg.airtel.aaa4j.domain.model.cdr.Accounting cdrAccounting = buildAccountingCdr(
                acctStatusType,
                sessionTime,
                inputOctets,
                outputOctets,
                inputGigawords,
                outputGigawords
        );

        Payload payload = Payload.builder()
                .session(cdrSession)
                .user(cdrUser)
                .network(cdrNetwork)
                .accounting(cdrAccounting)
                .build();

        return AccountingCDREvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventVersion("1.0")
                .eventTimestamp(Instant.now())
                .source("AAA-Service")
                .payload(payload)
                .build();
    }

    /**
     * Builds a Session CDR object from request and session data
     */
    public static com.csg.airtel.aaa4j.domain.model.cdr.Session buildSessionCdr(
            AccountingRequestDto request,
            Session session,
            Integer sessionTime) {

        String sessionTimeStr = sessionTime != null ? String.valueOf(sessionTime) : "0";

        return com.csg.airtel.aaa4j.domain.model.cdr.Session.builder()
                .sessionId(request.sessionId())
                .sessionTime(sessionTimeStr)
                .startTime(session.getStartTime())
                .updateTime(LocalDateTime.now())
                .nasIdentifier(request.nasIP())
                .nasIpAddress(request.nasIP())
                .nasPort(request.nasPortId())
                .nasPortType("Async")
                .build();
    }

    /**
     * Builds a User CDR object from request data
     */
    public static com.csg.airtel.aaa4j.domain.model.cdr.User buildUserCdr(AccountingRequestDto request) {
        return com.csg.airtel.aaa4j.domain.model.cdr.User.builder()
                .userName(request.username())
                .build();
    }

    /**
     * Builds a Network CDR object from request data
     */
    public static com.csg.airtel.aaa4j.domain.model.cdr.Network buildNetworkCdr(AccountingRequestDto request) {
        return com.csg.airtel.aaa4j.domain.model.cdr.Network.builder()
                .framedIpAddress(request.framedIPAddress())
                .framedProtocol("PPP")
                .serviceType("Framed-User")
                .calledStationId(request.nasIP())
                .build();
    }

    /**
     * Builds an Accounting CDR object with the specified values
     */
    public static com.csg.airtel.aaa4j.domain.model.cdr.Accounting buildAccountingCdr(
            String acctStatusType,
            Integer sessionTime,
            Long inputOctets,
            Long outputOctets,
            Integer inputGigawords,
            Integer outputGigawords) {

        return com.csg.airtel.aaa4j.domain.model.cdr.Accounting.builder()
                .acctStatusType(acctStatusType)
                .acctSessionTime(sessionTime != null ? sessionTime : 0)
                .acctInputOctets(inputOctets)
                .acctOutputOctets(outputOctets)
                .acctInputPackets(0)
                .acctOutputPackets(0)
                .acctInputGigawords(inputGigawords != null ? inputGigawords : 0)
                .acctOutputGigawords(outputGigawords != null ? outputGigawords : 0)
                .build();
    }

    /**
     * Calculates total octets from octets and gigawords
     * Formula: totalOctets = (gigawords * 2^32) + octets
     *
     * @param octets The number of octets
     * @param gigawords The number of gigawords (each gigaword = 2^32 octets)
     * @return The total number of octets
     */
    public static Long calculateTotalOctets(Integer octets, Integer gigawords) {
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
