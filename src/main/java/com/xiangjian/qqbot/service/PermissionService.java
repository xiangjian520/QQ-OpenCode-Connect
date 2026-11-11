package com.xiangjian.qqbot.service;

import com.xiangjian.qqbot.domain.ChatType;
import com.xiangjian.qqbot.domain.InboundMessage;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private final SettingsService settings;

    public PermissionService(SettingsService settings) {
        this.settings = settings;
    }

    public boolean canUse(InboundMessage message) {
        if (message.isPrivate()) {
            return settings.adminQqIds().contains(message.userId())
                    || settings.allowedPrivateIds().contains(message.userId());
        }
        if (message.isGroup()) {
            return settings.allowedGroupIds().contains(message.chatId());
        }
        return false;
    }

    public boolean isAdmin(String userId) {
        return userId != null && settings.adminQqIds().contains(userId);
    }

    public boolean canResetOrStop(ChatType type, String userId) {
        return type == ChatType.PRIVATE || isAdmin(userId);
    }

    public boolean canChangeAiSettings(ChatType type, String userId) {
        return isAdmin(userId) || (type == ChatType.PRIVATE && isAdmin(userId));
    }

    public boolean canEnableCoding(ChatType type, String userId) {
        return type == ChatType.PRIVATE && isAdmin(userId);
    }
}
