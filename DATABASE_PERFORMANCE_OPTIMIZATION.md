# Database Performance Optimization for Balance Query

## Overview
This document provides index recommendations and performance optimization strategies for the `QUERY_BALANCE` SQL query used in the AAA4J application.

## Query Analysis

### Current Query
```sql
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
ORDER BY b.priority DESC
```

### Recent Optimizations Applied
- **Removed TO_NUMBER conversion**: Changed `ORDER BY TO_NUMBER(b.priority) DESC` to `ORDER BY b.priority DESC`
  - This allows the database to use an index on the priority column
  - Eliminates runtime type conversion overhead

## Required Database Indexes

### 1. Primary Access Path - User Lookup (CRITICAL)
```sql
-- Index on user_table.username for fast WHERE clause filtering
CREATE INDEX idx_user_username ON csgaaa.user_table(username);

-- Alternative: If username is unique, consider making it a unique index
CREATE UNIQUE INDEX idx_user_username ON csgaaa.user_table(username);
```

**Impact**: This is the most critical index as it's used in the WHERE clause for every query execution.

### 2. Join Optimization - Service Table
```sql
-- Index on service_table.user_name for efficient join with user_table
CREATE INDEX idx_service_username ON csgaaa.service_table(user_name);

-- Composite index including service_id for covering index optimization
CREATE INDEX idx_service_username_id ON csgaaa.service_table(user_name, service_id);
```

**Impact**: Speeds up the join between user_table and service_table.

### 3. Join Optimization - Bucket Table
```sql
-- Index on bucket_table.service_id for efficient join with service_table
CREATE INDEX idx_bucket_service_id ON csgaaa.bucket_table(service_id);

-- Composite index including priority for covering the ORDER BY
CREATE INDEX idx_bucket_service_priority ON csgaaa.bucket_table(service_id, priority DESC);
```

**Impact**: Speeds up the join and helps with the ORDER BY clause.

### 4. Sort Optimization
```sql
-- Index specifically for ORDER BY optimization (if not using composite above)
CREATE INDEX idx_bucket_priority ON csgaaa.bucket_table(priority DESC);
```

**Impact**: Eliminates or reduces sort operations.

## Advanced Optimization: Covering Indexes

For maximum performance, consider creating covering indexes that include all columns needed by the query:

```sql
-- Covering index for bucket_table
CREATE INDEX idx_bucket_covering ON csgaaa.bucket_table(
    service_id,
    priority DESC,
    rule,
    initial_balance,
    current_balance,
    usage,
    bucket_id
);

-- Covering index for service_table
CREATE INDEX idx_service_covering ON csgaaa.service_table(
    user_name,
    service_id,
    expiry_date,
    service_start_date,
    plan_id,
    status
);
```

**Impact**: Allows Oracle to satisfy the query entirely from the index without accessing the table (index-only scan).

## Data Type Verification

Ensure the following columns are using appropriate numeric types:
- `bucket_table.priority` → Should be NUMBER or INTEGER (not VARCHAR2)
- `bucket_table.initial_balance` → NUMBER
- `bucket_table.current_balance` → NUMBER
- `bucket_table.usage` → NUMBER

**If priority is currently VARCHAR2**, consider:
1. Converting it to a numeric type, OR
2. Creating a function-based index:
```sql
CREATE INDEX idx_bucket_priority_num ON csgaaa.bucket_table(TO_NUMBER(priority) DESC);
```

## Table Statistics

Ensure Oracle optimizer has current statistics:

```sql
-- Gather statistics for all three tables
EXEC DBMS_STATS.GATHER_TABLE_STATS('CSGAAA', 'USER_TABLE');
EXEC DBMS_STATS.GATHER_TABLE_STATS('CSGAAA', 'SERVICE_TABLE');
EXEC DBMS_STATS.GATHER_TABLE_STATS('CSGAAA', 'BUCKET_TABLE');

-- Schedule automatic statistics gathering if not already configured
BEGIN
    DBMS_SCHEDULER.CREATE_JOB (
        job_name        => 'GATHER_AAA_STATS',
        job_type        => 'PLSQL_BLOCK',
        job_action      => 'BEGIN
                              DBMS_STATS.GATHER_SCHEMA_STATS(''CSGAAA'');
                            END;',
        start_date      => SYSTIMESTAMP,
        repeat_interval => 'FREQ=DAILY; BYHOUR=2',
        enabled         => TRUE
    );
END;
/
```

## Performance Monitoring

### Execution Plan Analysis
```sql
-- Check the execution plan
EXPLAIN PLAN FOR
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
WHERE u.username = 'test_user'
ORDER BY b.priority DESC;

SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);
```

### Expected Execution Plan (After Optimization)
Look for:
- `INDEX UNIQUE SCAN` or `INDEX RANGE SCAN` on user_table(username)
- `NESTED LOOPS` join type (efficient for selective queries)
- `INDEX RANGE SCAN` on bucket_table with no separate SORT operation

### Red Flags in Execution Plan
- `TABLE ACCESS FULL` on any of these tables
- `SORT ORDER BY` operation (indicates ORDER BY isn't using an index)
- `HASH JOIN` (may indicate missing indexes on join columns)

## Implementation Priority

1. **High Priority** (Implement immediately):
   - `idx_user_username` - Critical for WHERE clause
   - `idx_service_username` - Critical for first join
   - `idx_bucket_service_id` - Critical for second join

2. **Medium Priority** (Implement within a week):
   - `idx_bucket_service_priority` - Improves sorting performance
   - Table statistics gathering

3. **Low Priority** (Performance tuning phase):
   - Covering indexes (if query is frequently executed)
   - Automated statistics gathering job

## Estimated Performance Improvement

With proper indexes in place:
- **Current query time**: Likely 100ms - 1000ms+ (depending on table size)
- **Expected query time**: 5ms - 50ms (90-95% improvement)
- **Benefit**: Consistent sub-100ms response times even with millions of rows

## Additional Recommendations

1. **Partition Tables** (if tables are very large):
   ```sql
   -- Example: Partition service_table by service_start_date
   -- Consider partitioning if tables exceed 10M rows
   ```

2. **Connection Pooling**: Ensure the Vert.x SQL client pool is properly configured
   - Current circuit breaker settings look good
   - Monitor pool exhaustion

3. **Query Result Caching**: Consider caching frequently accessed user bucket data
   - Implement application-level caching (Redis/Caffeine)
   - Set appropriate TTL based on data update frequency

4. **Monitoring**: Add query execution time logging
   - The repository already logs execution time (good!)
   - Set up alerts for queries exceeding 100ms

## Testing Recommendations

After applying indexes:
1. Run EXPLAIN PLAN to verify index usage
2. Test with production-like data volume
3. Monitor query execution time via application logs
4. Load test with expected TPS (based on circuit breaker threshold of 20)

## Rollback Plan

If indexes cause issues:
```sql
DROP INDEX idx_user_username;
DROP INDEX idx_service_username;
DROP INDEX idx_bucket_service_id;
DROP INDEX idx_bucket_service_priority;
```

---
**Document Version**: 1.0
**Last Updated**: 2025-11-19
**Related Files**:
- `src/main/java/com/csg/airtel/aaa4j/domain/constant/SQLconstant.java:6`
- `src/main/java/com/csg/airtel/aaa4j/external/repository/UserBucketRepository.java:54`
