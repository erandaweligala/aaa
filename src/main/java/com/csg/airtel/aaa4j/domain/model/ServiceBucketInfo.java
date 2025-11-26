package com.csg.airtel.aaa4j.domain.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class ServiceBucketInfo {
    private String bucketUser;
    private String serviceId;
    private String rule;
    private long priority;
    private long initialBalance;
    private long currentBalance;
    private long usage;
    private LocalDateTime expiryDate;
    private LocalDateTime serviceStartDate;
    private String planId;
    private String status;
    private String bucketId;
    private long consumptionLimit;
    private long consumptionTimeWindow;
    private String timeWindow;
    private String sessionTimeout;
    private LocalDateTime bucketExpiryDate;

}
