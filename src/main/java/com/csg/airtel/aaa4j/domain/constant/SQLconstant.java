package com.csg.airtel.aaa4j.domain.constant;

public class SQLconstant {
    private SQLconstant() {
    }
    public static final String QUERY_BALANCE = """
            SELECT s.service_id,
                b.rule,
                b.priority,
                b.initial_balance,
                b.current_balance,
                b.usage,
                s.expiry_date,
                s.service_start_date,
                s.plan_id,
                b.bucket_id,
                s.status
            FROM csgaaa.user_table u
                INNER JOIN csgaaa.service_table s ON u.username = s.user_name
                INNER JOIN csgaaa.bucket_table b ON s.service_id = b.service_id
            WHERE u.username = :1
            ORDER BY TO_NUMBER(b.priority) DESC
            """;
}
