package com.csg.airtel.aaa4j.domain.model.session;

import lombok.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class UserSessionData {
    private List<Balance> balance;
    private List<Session> sessions;
    private QosParam qosParam;

    /**
     * Version field for optimistic locking to prevent concurrent modification conflicts.
     * Incremented on every update to detect stale reads.
     */
    private Long version;

    /**
     * Timestamp of last modification for cache invalidation and debugging.
     */
    private Long lastModifiedTimestamp;

    /**
     * Initialize version if not already set
     */
    public void initializeVersion() {
        if (this.version == null) {
            this.version = 1L;
            this.lastModifiedTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Increment version for optimistic locking on every modification
     */
    public void incrementVersion() {
        if (this.version == null) {
            this.version = 1L;
        } else {
            this.version++;
        }
        this.lastModifiedTimestamp = System.currentTimeMillis();
    }

}
