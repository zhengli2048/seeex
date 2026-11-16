package com.seeex.tech.web.wecom;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** 调度任务只领取租约，不把HTTP读取包在数据库事务中。 */
@Component @RequiredArgsConstructor
public class WeComSyncWorker {
    private final SyncRunStateService states;
    private final WeComSyncService sync;
    private final WeComClient client;
    /** 定期恢复过期任务并使用新令牌执行一个批次。 */
    @Scheduled(fixedDelay=5000)
    public void drain() { var lease=states.claim(); if(lease!=null) sync.executeFullSync(lease); }
    /** 每日校准复用同一批次机制。 */
    @Scheduled(cron="0 0 3 * * *",zone="Asia/Shanghai")
    public void reconcile() { if(client.configured()) sync.startFullSync(); }
}
