package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;

import java.time.LocalDateTime;
import java.util.Map;

public class MappingUtil {

    private MappingUtil() {
    }

    public static AccountingResponseEvent createResponse(
            AccountingRequestDto request,
            String message,
            AccountingResponseEvent.EventType eventType,
            AccountingResponseEvent.ResponseAction responseAction) {

        Map<String, String> attributes = eventType == AccountingResponseEvent.EventType.COA
                ? Map.of(
                "username", request.username(),
                "sessionId", request.sessionId(),
                "nasIP", request.nasIP(),
                "framedIP", request.framedIPAddress()
        )
                : Map.of();

        return new AccountingResponseEvent(
                eventType,
                LocalDateTime.now(),
                request.sessionId(),
                responseAction,
                message,
                0L,
                attributes);
    }

    public static AccountingResponseEvent createResponse(
            String sessionId,
            String message,
            String nasIP,
            String framedIPAddress,
            String userName) {

        Map<String, String> attributes =
                 Map.of(
                "username", userName,
                "sessionId", sessionId,
                "nasIP", nasIP,
                "framedIP", framedIPAddress
        );

        return new AccountingResponseEvent(
                AccountingResponseEvent.EventType.COA,
                LocalDateTime.now(),
                sessionId,
                AccountingResponseEvent.ResponseAction.DISCONNECT,
                message,
                0L,
                attributes);
    }



}
