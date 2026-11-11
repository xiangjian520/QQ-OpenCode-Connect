package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.repository.AuditRepository;
import com.xiangjian.qqbot.repository.JobRepository;
import com.xiangjian.qqbot.repository.MessageRepository;
import com.xiangjian.qqbot.web.UiEventHub;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RetentionService {
    private final SettingsService settings;
    private final MessageRepository messages;
    private final JobRepository jobs;
    private final AuditRepository audit;
    private final UiEventHub events;

    public RetentionService(SettingsService settings, MessageRepository messages,
                            JobRepository jobs, AuditRepository audit, UiEventHub events) {
        this.settings = settings;
        this.messages = messages;
        this.jobs = jobs;
        this.audit = audit;
        this.events = events;
    }

    @Scheduled(cron = "${qqbot.retention.cron:0 15 3 * * *}")
    public void purgeExpiredData() {
        long cutoff = System.currentTimeMillis() - Duration.ofDays(settings.retentionDays()).toMillis();
        int deletedMessages = messages.deleteOlderThan(cutoff);
        int deletedJobs = jobs.deleteCompletedOlderThan(cutoff);
        int deletedAudit = audit.deleteOlderThan(cutoff);
        if (deletedMessages + deletedJobs + deletedAudit > 0) {
            events.log("INFO", "已清理过期数据：消息 " + deletedMessages + "，任务 " + deletedJobs + "，审计 " + deletedAudit);
        }
    }
}
