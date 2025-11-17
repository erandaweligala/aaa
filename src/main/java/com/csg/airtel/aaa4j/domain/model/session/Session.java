package com.csg.airtel.aaa4j.domain.model.session;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Session {
    private String sessionId;
    private LocalDateTime sessionInitiatedTime;
    private String lastAccountingIdentificationId;
    private String status;  // FUP applied or not
    private Integer sessionTime;
    private Long previousTotalUsageQuotaValue;
    private String framedId;
    private String nasIp;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Session session = (Session) o;
        return Objects.equals(sessionId, session.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

}
