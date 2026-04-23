package com.aidecomposer.util;

/**
 * 分布式 Snowflake ID 生成器（64-bit）。
 *
 * - 41 bits: 时间戳（毫秒，相对 epoch）
 * - 10 bits: datacenterId
 * - 10 bits: workerId（这里合并/简化布局，不追求严格兼容其它实现）
 * - 3 bits: 序列（同毫秒最大并发 8 次；足够你们早期场景）
 *
 * 如果后续要更高并发，可调整 bits 分配。
 */
public class SnowflakeIdWorker {
    private final long epochMilli;
    private final long datacenterId;
    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdWorker(long workerId, long datacenterId, long epochMilli) {
        if (workerId < 0 || workerId > 1023) throw new IllegalArgumentException("workerId out of range");
        if (datacenterId < 0 || datacenterId > 1023) throw new IllegalArgumentException("datacenterId out of range");
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epochMilli = epochMilli;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. refuse generating id.");
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & 0x7L; // 3 bits
            if (sequence == 0) {
                // 序列耗尽，等下一毫秒
                while (now <= lastTimestamp) {
                    now = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = now;

        long tsPart = (now - epochMilli) & ((1L << 41) - 1);
        long dcPart = (datacenterId & 0x3FF) << 13; // 10 bits, shift by 10bits(worker?) + 3 bits(seq)
        long workerPart = (workerId & 0x3FF) << 3;
        long seqPart = sequence & 0x7L;
        return tsPart | dcPart | workerPart | seqPart;
    }
}

