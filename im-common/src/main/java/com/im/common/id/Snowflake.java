package com.im.common.id;

/**
 * 轻量雪花算法 ID 生成器（毫秒时间戳 + workerId + 序列）。
 * P0 用于生成全局 msgId。workerId 各服务实例配置区分。
 */
public class Snowflake {

    private final long epoch = 1704067200000L; // 2024-01-01
    private final long workerIdBits = 10L;
    private final long sequenceBits = 12L;
    private final long maxWorkerId = ~(-1L << workerIdBits);
    private final long workerIdShift = sequenceBits;
    private final long timestampShift = sequenceBits + workerIdBits;
    private final long sequenceMask = ~(-1L << sequenceBits);

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public Snowflake(long workerId) {
        if (workerId < 0 || workerId > maxWorkerId) {
            throw new IllegalArgumentException("workerId out of range: " + workerId);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            // 时钟回拨，简单等待
            ts = lastTimestamp;
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                ts = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = ts;
        return ((ts - epoch) << timestampShift) | (workerId << workerIdShift) | sequence;
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
