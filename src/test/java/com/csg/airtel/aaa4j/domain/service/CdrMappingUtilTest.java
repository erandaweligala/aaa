package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.cdr.*;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CdrMappingUtilTest {

    private AccountingRequestDto createTestRequest(AccountingRequestDto.ActionType actionType, int sessionTime) {
        return new AccountingRequestDto(
                "eventId1",
                "session1",
                "192.168.1.1",
                "testUser",
                actionType,
                1000,
                2000,
                sessionTime,
                Instant.now(),
                "port1",
                "10.0.0.1",
                0,
                5,
                10,
                "nas1"
        );
    }

    private Session createTestSession() {
        return new Session(
                "session1",
                LocalDateTime.now().minusHours(1),
                "bucket1",
                0,
                0L,
                "10.0.0.1",
                "192.168.1.1"
        );
    }

    @Test
    void testBuildStartCDREvent_Success() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.START, 0);
        Session session = createTestSession();

        // When
        AccountingCDREvent result = CdrMappingUtil.buildStartCDREvent(request, session);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo(EventTypes.ACCOUNTING_START.name());
        assertThat(result.getEventVersion()).isEqualTo("1.0");
        assertThat(result.getSource()).isEqualTo("AAA-Service");
        assertThat(result.getEventId()).isNotNull();
        assertThat(result.getEventTimestamp()).isNotNull();
        assertThat(result.getPayload()).isNotNull();

        Payload payload = result.getPayload();
        assertThat(payload.getSession()).isNotNull();
        assertThat(payload.getUser()).isNotNull();
        assertThat(payload.getNetwork()).isNotNull();
        assertThat(payload.getAccounting()).isNotNull();

        Accounting accounting = payload.getAccounting();
        assertThat(accounting.getAcctStatusType()).isEqualTo("Start");
        assertThat(accounting.getAcctSessionTime()).isEqualTo(0);
        assertThat(accounting.getAcctInputOctets()).isEqualTo(0L);
        assertThat(accounting.getAcctOutputOctets()).isEqualTo(0L);
        assertThat(accounting.getAcctInputGigawords()).isEqualTo(0);
        assertThat(accounting.getAcctOutputGigawords()).isEqualTo(0);
    }

    @Test
    void testBuildInterimCDREvent_Success() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.INTERIM_UPDATE, 3600);
        Session session = createTestSession();

        // When
        AccountingCDREvent result = CdrMappingUtil.buildInterimCDREvent(request, session);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo(EventTypes.ACCOUNTING_INTERIM.name());

        Payload payload = result.getPayload();
        Accounting accounting = payload.getAccounting();
        assertThat(accounting.getAcctStatusType()).isEqualTo("Interim-Update");
        assertThat(accounting.getAcctSessionTime()).isEqualTo(3600);
        assertThat(accounting.getAcctInputOctets()).isEqualTo(1000L);
        assertThat(accounting.getAcctOutputOctets()).isEqualTo(2000L);
        assertThat(accounting.getAcctInputGigawords()).isEqualTo(5);
        assertThat(accounting.getAcctOutputGigawords()).isEqualTo(10);
    }

    @Test
    void testBuildStopCDREvent_Success() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.STOP, 7200);
        Session session = createTestSession();

        // When
        AccountingCDREvent result = CdrMappingUtil.buildStopCDREvent(request, session);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo(EventTypes.ACCOUNTING_STOP.name());

        Payload payload = result.getPayload();
        Accounting accounting = payload.getAccounting();
        assertThat(accounting.getAcctStatusType()).isEqualTo("Stop");
        assertThat(accounting.getAcctSessionTime()).isEqualTo(7200);
        assertThat(accounting.getAcctInputOctets()).isEqualTo(1000L);
        assertThat(accounting.getAcctOutputOctets()).isEqualTo(2000L);
    }

    @Test
    void testBuildSessionCdr_Success() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.INTERIM_UPDATE, 1800);

        // When
        SessionCdr result = CdrMappingUtil.buildSessionCdr(request, 1800, EventTypes.ACCOUNTING_INTERIM.name());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionId()).isEqualTo("session1");
        assertThat(result.getSessionTime()).isEqualTo("1800");
        assertThat(result.getStartTime()).isNotNull();
        assertThat(result.getUpdateTime()).isNotNull();
        assertThat(result.getNasIdentifier()).isEqualTo("nas1");
        assertThat(result.getNasIpAddress()).isEqualTo("192.168.1.1");
        assertThat(result.getNasPort()).isEqualTo("port1");
        assertThat(result.getNasPortType()).isEqualTo("port1");
    }

    @Test
    void testBuildSessionCdr_StopEvent_IncludesStopTime() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.STOP, 3600);

        // When
        SessionCdr result = CdrMappingUtil.buildSessionCdr(request, 3600, "Stop");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionStopTime()).isNotNull();
    }

    @Test
    void testBuildSessionCdr_NonStopEvent_NoStopTime() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.START, 0);

        // When
        SessionCdr result = CdrMappingUtil.buildSessionCdr(request, 0, EventTypes.ACCOUNTING_START.name());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionStopTime()).isNull();
    }

    @Test
    void testBuildUserCdr_Success() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.START, 0);

        // When
        User result = CdrMappingUtil.buildUserCdr(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserName()).isEqualTo("testUser");
    }

    @Test
    void testBuildNetworkCdr_Success() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.START, 0);

        // When
        Network result = CdrMappingUtil.buildNetworkCdr(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFramedIpAddress()).isEqualTo("10.0.0.1");
        assertThat(result.getCalledStationId()).isEqualTo("192.168.1.1");
    }

    @Test
    void testBuildAccountingCdr_ForStart() {
        // Given
        CdrMappingUtil.AccountingMetrics metrics = CdrMappingUtil.AccountingMetrics.forStart();

        // When
        Accounting result = CdrMappingUtil.buildAccountingCdr(metrics);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAcctStatusType()).isEqualTo("Start");
        assertThat(result.getAcctSessionTime()).isEqualTo(0);
        assertThat(result.getAcctInputOctets()).isEqualTo(0L);
        assertThat(result.getAcctOutputOctets()).isEqualTo(0L);
        assertThat(result.getAcctInputGigawords()).isEqualTo(0);
        assertThat(result.getAcctOutputGigawords()).isEqualTo(0);
    }

    @Test
    void testBuildAccountingCdr_ForInterim() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.INTERIM_UPDATE, 3600);
        CdrMappingUtil.AccountingMetrics metrics = CdrMappingUtil.AccountingMetrics.forInterim(request);

        // When
        Accounting result = CdrMappingUtil.buildAccountingCdr(metrics);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAcctStatusType()).isEqualTo("Interim-Update");
        assertThat(result.getAcctSessionTime()).isEqualTo(3600);
        assertThat(result.getAcctInputOctets()).isEqualTo(1000L);
        assertThat(result.getAcctOutputOctets()).isEqualTo(2000L);
        assertThat(result.getAcctInputGigawords()).isEqualTo(5);
        assertThat(result.getAcctOutputGigawords()).isEqualTo(10);
    }

    @Test
    void testBuildAccountingCdr_ForStop() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.STOP, 7200);
        CdrMappingUtil.AccountingMetrics metrics = CdrMappingUtil.AccountingMetrics.forStop(request);

        // When
        Accounting result = CdrMappingUtil.buildAccountingCdr(metrics);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAcctStatusType()).isEqualTo("Stop");
        assertThat(result.getAcctSessionTime()).isEqualTo(7200);
        assertThat(result.getAcctInputOctets()).isEqualTo(1000L);
        assertThat(result.getAcctOutputOctets()).isEqualTo(2000L);
    }

    @Test
    void testAccountingMetrics_ForStart_HasCorrectValues() {
        // When
        CdrMappingUtil.AccountingMetrics metrics = CdrMappingUtil.AccountingMetrics.forStart();

        // Then
        assertThat(metrics.getAcctStatusType()).isEqualTo("Start");
        assertThat(metrics.getEventType()).isEqualTo(EventTypes.ACCOUNTING_START.name());
        assertThat(metrics.getSessionTime()).isEqualTo(0);
        assertThat(metrics.getInputOctets()).isEqualTo(0L);
        assertThat(metrics.getOutputOctets()).isEqualTo(0L);
        assertThat(metrics.getInputGigawords()).isEqualTo(0);
        assertThat(metrics.getOutputGigawords()).isEqualTo(0);
    }

    @Test
    void testAccountingMetrics_ForInterim_CopiesRequestData() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.INTERIM_UPDATE, 3600);

        // When
        CdrMappingUtil.AccountingMetrics metrics = CdrMappingUtil.AccountingMetrics.forInterim(request);

        // Then
        assertThat(metrics.getAcctStatusType()).isEqualTo("Interim-Update");
        assertThat(metrics.getEventType()).isEqualTo(EventTypes.ACCOUNTING_INTERIM.name());
        assertThat(metrics.getSessionTime()).isEqualTo(3600);
        assertThat(metrics.getInputOctets()).isEqualTo(1000L);
        assertThat(metrics.getOutputOctets()).isEqualTo(2000L);
        assertThat(metrics.getInputGigawords()).isEqualTo(5);
        assertThat(metrics.getOutputGigawords()).isEqualTo(10);
    }

    @Test
    void testAccountingMetrics_ForStop_CopiesRequestData() {
        // Given
        AccountingRequestDto request = createTestRequest(AccountingRequestDto.ActionType.STOP, 7200);

        // When
        CdrMappingUtil.AccountingMetrics metrics = CdrMappingUtil.AccountingMetrics.forStop(request);

        // Then
        assertThat(metrics.getAcctStatusType()).isEqualTo("Stop");
        assertThat(metrics.getEventType()).isEqualTo(EventTypes.ACCOUNTING_STOP.name());
        assertThat(metrics.getSessionTime()).isEqualTo(7200);
        assertThat(metrics.getInputOctets()).isEqualTo(1000L);
        assertThat(metrics.getOutputOctets()).isEqualTo(2000L);
    }
}
