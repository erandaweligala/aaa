package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.AccountingRequestDto;
import com.csg.airtel.aaa4j.domain.model.AccountingResponseEvent;
import com.csg.airtel.aaa4j.domain.model.ServiceBucketInfo;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MappingUtilTest {

    private AccountingRequestDto createTestRequest() {
        return new AccountingRequestDto(
                "eventId1",
                "session1",
                "192.168.1.1",
                "testUser",
                AccountingRequestDto.ActionType.START,
                0,
                0,
                0,
                Instant.now(),
                "port1",
                "10.0.0.1",
                0,
                0,
                0,
                "nas1"
        );
    }

    private ServiceBucketInfo createServiceBucketInfo() {
        ServiceBucketInfo info = new ServiceBucketInfo();
        info.setBucketId("bucket1");
        info.setServiceId("service1");
        info.setCurrentBalance(1000L);
        info.setInitialBalance(2000L);
        info.setPriority(5L);
        info.setStatus("Active");
        info.setServiceStartDate(LocalDateTime.now().minusDays(1));
        info.setExpiryDate(LocalDateTime.now().plusDays(30));
        info.setBucketUser("testUser");
        info.setTimeWindow("00:00-23:59");
        info.setConsumptionLimit(500L);
        info.setConsumptionTimeWindow(24L);
        info.setBucketExpiryDate(LocalDateTime.now().plusDays(25));
        return info;
    }

    @Test
    void testCreateResponse_WithCOA_IncludesAttributes() {
        // Given
        AccountingRequestDto request = createTestRequest();

        // When
        AccountingResponseEvent response = MappingUtil.createResponse(
                request,
                "Test message",
                AccountingResponseEvent.EventType.COA,
                AccountingResponseEvent.ResponseAction.DISCONNECT
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.eventType()).isEqualTo(AccountingResponseEvent.EventType.COA);
        assertThat(response.sessionId()).isEqualTo("session1");
        assertThat(response.responseAction()).isEqualTo(AccountingResponseEvent.ResponseAction.DISCONNECT);
        assertThat(response.message()).isEqualTo("Test message");
        assertThat(response.attributes()).isNotEmpty();
        assertThat(response.attributes()).containsKeys("username", "sessionId", "nasIP", "framedIP");
        assertThat(response.attributes().get("username")).isEqualTo("testUser");
        assertThat(response.attributes().get("sessionId")).isEqualTo("session1");
    }

    @Test
    void testCreateResponse_WithNonCOA_EmptyAttributes() {
        // Given
        AccountingRequestDto request = createTestRequest();

        // When
        AccountingResponseEvent response = MappingUtil.createResponse(
                request,
                "Test message",
                AccountingResponseEvent.EventType.ACKNOWLEDGMENT,
                AccountingResponseEvent.ResponseAction.ACCEPT
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.eventType()).isEqualTo(AccountingResponseEvent.EventType.ACKNOWLEDGMENT);
        assertThat(response.attributes()).isEmpty();
    }

    @Test
    void testCreateResponse_WithSessionIdAndNasIP_Success() {
        // When
        AccountingResponseEvent response = MappingUtil.createResponse(
                "session1",
                "Disconnect",
                "192.168.1.1",
                "10.0.0.1",
                "testUser"
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.eventType()).isEqualTo(AccountingResponseEvent.EventType.COA);
        assertThat(response.responseAction()).isEqualTo(AccountingResponseEvent.ResponseAction.DISCONNECT);
        assertThat(response.sessionId()).isEqualTo("session1");
        assertThat(response.message()).isEqualTo("Disconnect");
        assertThat(response.attributes()).containsKeys("username", "sessionId", "nasIP", "framedIP");
        assertThat(response.attributes().get("username")).isEqualTo("testUser");
        assertThat(response.attributes().get("nasIP")).isEqualTo("192.168.1.1");
        assertThat(response.attributes().get("framedIP")).isEqualTo("10.0.0.1");
    }

    @Test
    void testCreateBalance_Success() {
        // Given
        ServiceBucketInfo bucketInfo = createServiceBucketInfo();

        // When
        Balance balance = MappingUtil.createBalance(bucketInfo);

        // Then
        assertThat(balance).isNotNull();
        assertThat(balance.getBucketId()).isEqualTo("bucket1");
        assertThat(balance.getServiceId()).isEqualTo("service1");
        assertThat(balance.getQuota()).isEqualTo(1000L);
        assertThat(balance.getInitialBalance()).isEqualTo(2000L);
        assertThat(balance.getPriority()).isEqualTo(5L);
        assertThat(balance.getServiceStatus()).isEqualTo("Active");
        assertThat(balance.getServiceStartDate()).isNotNull();
        assertThat(balance.getServiceExpiry()).isNotNull();
        assertThat(balance.getTimeWindow()).isEqualTo("00:00-23:59");
        assertThat(balance.getConsumptionLimit()).isEqualTo(500L);
        assertThat(balance.getConsumptionLimitWindow()).isEqualTo(24L);
        assertThat(balance.getBucketUsername()).isEqualTo("testUser");
        assertThat(balance.getBucketExpiryDate()).isNotNull();
    }

    @Test
    void testCreateBalance_CopiesAllFields() {
        // Given
        ServiceBucketInfo bucketInfo = createServiceBucketInfo();
        LocalDateTime startDate = LocalDateTime.now().minusDays(5);
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(15);
        LocalDateTime bucketExpiry = LocalDateTime.now().plusDays(10);

        bucketInfo.setServiceStartDate(startDate);
        bucketInfo.setExpiryDate(expiryDate);
        bucketInfo.setBucketExpiryDate(bucketExpiry);

        // When
        Balance balance = MappingUtil.createBalance(bucketInfo);

        // Then
        assertThat(balance.getServiceStartDate()).isEqualTo(startDate);
        assertThat(balance.getServiceExpiry()).isEqualTo(expiryDate);
        assertThat(balance.getBucketExpiryDate()).isEqualTo(bucketExpiry);
    }
}
