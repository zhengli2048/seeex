package com.seeex.tech.web.wecom;

import org.springframework.stereotype.Component;

import java.time.Instant;

/** 事件重试退避调度器。 */
@Component
public class RetryScheduler {

    /** 最大重试次数。 */
    private static final int MAX_RETRY = 5;

    /** 基础退避秒数。 */
    private static final long BASE_DELAY_SECONDS = 30;

    /** 最大退避秒数（15 分钟）。 */
    private static final long MAX_DELAY_SECONDS = 15 * 60;

    /** 计算下次重试时间：min(15min, 30s × 2^n) + jitter(0~10s)。 */
    public Instant nextRetryAt(int retryCount) {
        long shift = Math.min(retryCount, 10);
        long delay = Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS * (1L << shift));
        long jitter = (long) (Math.random() * 10);
        return Instant.now().plusSeconds(delay + jitter);
    }

    /** 判断是否已达最大重试次数，应进入死信。 */
    public boolean isDeadLetter(int retryCount) {
        return retryCount >= MAX_RETRY;
    }
}
