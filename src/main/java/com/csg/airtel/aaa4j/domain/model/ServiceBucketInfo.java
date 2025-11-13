package com.csg.airtel.aaa4j.domain.model;

import java.time.LocalDateTime;

public class ServiceBucketInfo {
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


    @Override
    public String toString() {
        return "ServiceBucketInfo{" +
                "serviceId='" + serviceId + '\'' +
                ", rule='" + rule + '\'' +
                ", priority=" + priority +
                ", initialBalance=" + initialBalance +
                ", currentBalance=" + currentBalance +
                ", usage=" + usage +
                ", expiryDate=" + expiryDate +
                ", serviceStartDate=" + serviceStartDate +
                ", planId='" + planId + '\'' +
                ", status='" + status + '\'' +
                ", bucketId='" + bucketId + '\'' +
                '}';
    }

    public LocalDateTime getServiceStartDate() {
        return serviceStartDate;
    }

    public void setServiceStartDate(LocalDateTime serviceStartDate) {
        this.serviceStartDate = serviceStartDate;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public long getPriority() {
        return priority;
    }

    public void setPriority(long priority) {
        this.priority = priority;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(long initialBalance) {
        this.initialBalance = initialBalance;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(long currentBalance) {
        this.currentBalance = currentBalance;
    }

    public long getUsage() {
        return usage;
    }

    public void setUsage(long usage) {
        this.usage = usage;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }
}
