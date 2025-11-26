package com.csg.airtel.aaa4j.domain.model;

import com.csg.airtel.aaa4j.domain.model.cdr.*;
import com.csg.airtel.aaa4j.domain.model.response.ApiResponse;
import com.csg.airtel.aaa4j.domain.model.session.Balance;
import com.csg.airtel.aaa4j.domain.model.session.ConsumptionRecord;
import com.csg.airtel.aaa4j.domain.model.session.Session;
import com.csg.airtel.aaa4j.domain.model.session.UserSessionData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void testAccountingRequestDto_Creation() {
        AccountingRequestDto dto = new AccountingRequestDto(
                "event1",
                "session1",
                "192.168.1.1",
                "user1",
                AccountingRequestDto.ActionType.START,
                100,
                200,
                60,
                Instant.now(),
                "port1",
                "10.0.0.1",
                0,
                5,
                10,
                "nas1"
        );

        assertThat(dto.eventId()).isEqualTo("event1");
        assertThat(dto.sessionId()).isEqualTo("session1");
        assertThat(dto.username()).isEqualTo("user1");
        assertThat(dto.actionType()).isEqualTo(AccountingRequestDto.ActionType.START);
    }

    @Test
    void testBalance_GettersAndSetters() {
        Balance balance = new Balance();
        balance.setBucketId("bucket1");
        balance.setServiceId("service1");
        balance.setQuota(1000L);
        balance.setPriority(5L);

        assertThat(balance.getBucketId()).isEqualTo("bucket1");
        assertThat(balance.getServiceId()).isEqualTo("service1");
        assertThat(balance.getQuota()).isEqualTo(1000L);
        assertThat(balance.getPriority()).isEqualTo(5L);
    }

    @Test
    void testSession_Constructor() {
        Session session = new Session(
                "session1",
                LocalDateTime.now(),
                "bucket1",
                60,
                500L,
                "10.0.0.1",
                "192.168.1.1"
        );

        assertThat(session.getSessionId()).isEqualTo("session1");
        assertThat(session.getPreviousUsageBucketId()).isEqualTo("bucket1");
        assertThat(session.getSessionTime()).isEqualTo(60);
    }

    @Test
    void testUserSessionData_Builder() {
        List<Balance> balances = new ArrayList<>();
        List<Session> sessions = new ArrayList<>();

        UserSessionData userData = UserSessionData.builder()
                .userName("testUser")
                .groupId("group1")
                .balance(balances)
                .sessions(sessions)
                .build();

        assertThat(userData.getUserName()).isEqualTo("testUser");
        assertThat(userData.getGroupId()).isEqualTo("group1");
        assertThat(userData.getBalance()).isEqualTo(balances);
        assertThat(userData.getSessions()).isEqualTo(sessions);
    }

    @Test
    void testUpdateResult_Success() {
        Balance balance = new Balance();
        UpdateResult result = UpdateResult.success(900L, "bucket1", balance, "bucket1");

        assertThat(result.success()).isTrue();
        assertThat(result.newQuota()).isEqualTo(900L);
        assertThat(result.bucketId()).isEqualTo("bucket1");
        assertThat(result.balance()).isEqualTo(balance);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void testUpdateResult_Failure() {
        UpdateResult result = UpdateResult.failure("Error occurred");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Error occurred");
        assertThat(result.newQuota()).isNull();
        assertThat(result.bucketId()).isNull();
    }

    @Test
    void testDBWriteRequest_GettersAndSetters() {
        DBWriteRequest request = new DBWriteRequest();
        request.setSessionId("session1");
        request.setUserName("user1");
        request.setEventType(EventType.UPDATE_EVENT);
        request.setTableName("BUCKET_INSTANCE");

        assertThat(request.getSessionId()).isEqualTo("session1");
        assertThat(request.getUserName()).isEqualTo("user1");
        assertThat(request.getEventType()).isEqualTo(EventType.UPDATE_EVENT);
        assertThat(request.getTableName()).isEqualTo("BUCKET_INSTANCE");
    }

    @Test
    void testAccountingResponseEvent_Record() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("key1", "value1");

        AccountingResponseEvent event = new AccountingResponseEvent(
                AccountingResponseEvent.EventType.COA,
                LocalDateTime.now(),
                "session1",
                AccountingResponseEvent.ResponseAction.DISCONNECT,
                "Test message",
                0L,
                attributes
        );

        assertThat(event.eventType()).isEqualTo(AccountingResponseEvent.EventType.COA);
        assertThat(event.sessionId()).isEqualTo("session1");
        assertThat(event.responseAction()).isEqualTo(AccountingResponseEvent.ResponseAction.DISCONNECT);
        assertThat(event.message()).isEqualTo("Test message");
        assertThat(event.attributes()).containsKey("key1");
    }

    @Test
    void testServiceBucketInfo_GettersAndSetters() {
        ServiceBucketInfo info = new ServiceBucketInfo();
        info.setBucketId("bucket1");
        info.setServiceId("service1");
        info.setCurrentBalance(1000L);
        info.setInitialBalance(2000L);
        info.setPriority(5L);

        assertThat(info.getBucketId()).isEqualTo("bucket1");
        assertThat(info.getServiceId()).isEqualTo("service1");
        assertThat(info.getCurrentBalance()).isEqualTo(1000L);
        assertThat(info.getInitialBalance()).isEqualTo(2000L);
        assertThat(info.getPriority()).isEqualTo(5L);
    }

    @Test
    void testConsumptionRecord_Constructor() {
        LocalDateTime timestamp = LocalDateTime.now();
        ConsumptionRecord record = new ConsumptionRecord(timestamp, 500L);

        assertThat(record.getTimestamp()).isEqualTo(timestamp);
        assertThat(record.getBytesConsumed()).isEqualTo(500L);
    }

    @Test
    void testApiResponse_GettersAndSetters() {
        ApiResponse<String> response = new ApiResponse<>();
        response.setMessage("Success");
        response.setData("test data");
        response.setTimestamp(Instant.now());

        assertThat(response.getMessage()).isEqualTo("Success");
        assertThat(response.getData()).isEqualTo("test data");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void testAccountingCDREvent_Builder() {
        Payload payload = new Payload();

        AccountingCDREvent event = AccountingCDREvent.builder()
                .eventId("event1")
                .eventType("ACCOUNTING_START")
                .eventVersion("1.0")
                .source("AAA-Service")
                .eventTimestamp(Instant.now())
                .payload(payload)
                .build();

        assertThat(event.getEventId()).isEqualTo("event1");
        assertThat(event.getEventType()).isEqualTo("ACCOUNTING_START");
        assertThat(event.getEventVersion()).isEqualTo("1.0");
        assertThat(event.getSource()).isEqualTo("AAA-Service");
        assertThat(event.getPayload()).isEqualTo(payload);
    }

    @Test
    void testAccounting_Builder() {
        Accounting accounting = Accounting.builder()
                .acctStatusType("Start")
                .acctSessionTime(0)
                .acctInputOctets(0L)
                .acctOutputOctets(0L)
                .acctInputGigawords(0)
                .acctOutputGigawords(0)
                .build();

        assertThat(accounting.getAcctStatusType()).isEqualTo("Start");
        assertThat(accounting.getAcctSessionTime()).isEqualTo(0);
        assertThat(accounting.getAcctInputOctets()).isEqualTo(0L);
        assertThat(accounting.getAcctOutputOctets()).isEqualTo(0L);
    }

    @Test
    void testUser_Builder() {
        User user = User.builder()
                .userName("testUser")
                .build();

        assertThat(user.getUserName()).isEqualTo("testUser");
    }

    @Test
    void testNetwork_Builder() {
        Network network = Network.builder()
                .framedIpAddress("10.0.0.1")
                .calledStationId("192.168.1.1")
                .build();

        assertThat(network.getFramedIpAddress()).isEqualTo("10.0.0.1");
        assertThat(network.getCalledStationId()).isEqualTo("192.168.1.1");
    }

    @Test
    void testSessionCdr_Builder() {
        SessionCdr sessionCdr = SessionCdr.builder()
                .sessionId("session1")
                .sessionTime("3600")
                .startTime(Instant.now())
                .updateTime(Instant.now())
                .nasIdentifier("nas1")
                .nasIpAddress("192.168.1.1")
                .build();

        assertThat(sessionCdr.getSessionId()).isEqualTo("session1");
        assertThat(sessionCdr.getSessionTime()).isEqualTo("3600");
        assertThat(sessionCdr.getNasIdentifier()).isEqualTo("nas1");
    }

    @Test
    void testPayload_Builder() {
        SessionCdr session = SessionCdr.builder().build();
        User user = User.builder().build();
        Network network = Network.builder().build();
        Accounting accounting = Accounting.builder().build();

        Payload payload = Payload.builder()
                .session(session)
                .user(user)
                .network(network)
                .accounting(accounting)
                .build();

        assertThat(payload.getSession()).isEqualTo(session);
        assertThat(payload.getUser()).isEqualTo(user);
        assertThat(payload.getNetwork()).isEqualTo(network);
        assertThat(payload.getAccounting()).isEqualTo(accounting);
    }
}
