package com.seeex.tech.web.wecom;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 事件异步处理器：驱动事件状态机。 */
@Component
@RequiredArgsConstructor
public class SyncEventProcessor {

    private final SyncEventMapper eventMapper;
    private final RetryScheduler retryScheduler;
    private final WeComSyncService sync;

    /** 处理单个事件：RECEIVED → PROCESSING → SUCCESS / RETRY_WAIT / DEAD_LETTER。 */
    public void process(long eventId) {
        SyncEventDO event = eventMapper.selectById(eventId);
        if (event == null) {
            return;
        }
        if (!java.util.Set.of("RECEIVED", "RETRY_WAIT").contains(event.getStatus())) { return; }
        int claimed = eventMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<SyncEventDO>lambdaUpdate().eq(SyncEventDO::getId, eventId)
                .eq(SyncEventDO::getStatus, event.getStatus()).set(SyncEventDO::getStatus, "PROCESSING").set(SyncEventDO::getProcessedAt, LocalDateTime.now()));
        if (claimed != 1) { return; }
        event.setStatus("PROCESSING");
        try {
            sync.applyEvent(event.getPayload());
            event.setStatus("SUCCESS");
            event.setProcessedAt(LocalDateTime.now());
            eventMapper.updateById(event);
        } catch (RuntimeException ex) {
            int retry = event.getRetryCount() == null ? 0 : event.getRetryCount();
            event.setErrorMessage("事件处理失败，请检查企业微信配置及同步日志");
            if (retryScheduler.isDeadLetter(retry)) {
                event.setStatus("DEAD_LETTER");
            } else {
                event.setStatus("RETRY_WAIT");
                event.setRetryCount(retry + 1);
                event.setNextRetryAt(retryScheduler.nextRetryAt(retry).atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            eventMapper.updateById(event);
        }
    }
    /** 调度收到的事件和已到期重试，回收进程中断后滞留的处理状态。 */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000)
    public void drain() {
        eventMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<SyncEventDO>lambdaUpdate().eq(SyncEventDO::getStatus, "PROCESSING")
                .lt(SyncEventDO::getProcessedAt, LocalDateTime.now().minusMinutes(10)).set(SyncEventDO::getStatus, "RETRY_WAIT").set(SyncEventDO::getNextRetryAt, LocalDateTime.now()));
        eventMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SyncEventDO>lambdaQuery().and(w -> w.eq(SyncEventDO::getStatus, "RECEIVED")
                    .or(n -> n.eq(SyncEventDO::getStatus, "RETRY_WAIT").le(SyncEventDO::getNextRetryAt, LocalDateTime.now()))).orderByAsc(SyncEventDO::getId))
                .getRecords().forEach(row -> process(row.getId()));
    }
}
