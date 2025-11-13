package com.csg.airtel.aaa4j.domain.model.session;

import java.time.LocalDateTime;

public class Balance {
    private Long initialBalance;
    private Long quota;
    private LocalDateTime serviceExpiry;
    private String bucketId;
    private String serviceId;
    private Long priority;
    private LocalDateTime serviceStartDate;
    private String serviceStatus;
    private String timeWindow;
    private Long consumptionLimit;

    public String getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(String timeWindow) {
        this.timeWindow = timeWindow;
    }

    public Long getConsumptionLimit() {
        return consumptionLimit;
    }

    public void setConsumptionLimit(Long consumptionLimit) {
        this.consumptionLimit = consumptionLimit;
    }

    public Long getQuota() {
        return quota;
    }

    public void setQuota(Long quota) {
        this.quota = quota;
    }

    public Long getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(Long initialBalance) {
        this.initialBalance = initialBalance;
    }

    public LocalDateTime getServiceExpiry() {
        return serviceExpiry;
    }

    public void setServiceExpiry(LocalDateTime serviceExpiry) {
        this.serviceExpiry = serviceExpiry;
    }

    public String getBucketId() {
        return bucketId;
    }

    public void setBucketId(String bucketId) {
        this.bucketId = bucketId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public Long getPriority() {
        return priority;
    }

    public void setPriority(Long priority) {
        this.priority = priority;
    }

    public LocalDateTime getServiceStartDate() {
        return serviceStartDate;
    }

    public void setServiceStartDate(LocalDateTime serviceStartDate) {
        this.serviceStartDate = serviceStartDate;
    }

    public String getServiceStatus() {
        return serviceStatus;
    }

    public void setServiceStatus(String serviceStatus) {
        this.serviceStatus = serviceStatus;
    }
}
