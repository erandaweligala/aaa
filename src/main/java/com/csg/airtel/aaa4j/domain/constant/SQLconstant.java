package com.csg.airtel.aaa4j.domain.constant;

public class SQLConstant {
    private SQLConstant() {
    }
    public static final String QUERY_BALANCE = """
            SELECT
               s.SERVICE_ID,
               b.RULE,
               b.PRIORITY,
               b.INITIAL_BALANCE,
               b.CURRENT_BALANCE,
               b.USAGE,
               s.EXPIRY_DATE,
               s.SERVICE_START_DATE,
               s.PLAN_ID,
               b.BUCKET_ID,
               s.STATUS,
               s.USER_NAME AS BUCKET_USER,
               b.CONSUMPTION_LIMIT,
               u.SESSION_TIMEOUT,
               b.TIME_WINDOW,
               b.CONSUMPTION_LIMIT_WINDOW
            FROM CSGAAA.SERVICE_TABLE s
            JOIN CSGAAA.USER_TABLE  u
              ON s.USER_NAME = u.username
              OR (u.group_id IS NOT NULL AND s.USER_NAME = u.group_id)
            LEFT JOIN CSGAAA.BUCKET_TABLE b
              ON s.service_id = b.service_id
            WHERE u.username = :1
            """;
}
