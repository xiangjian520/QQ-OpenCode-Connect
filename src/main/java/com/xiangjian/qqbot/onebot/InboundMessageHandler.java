package com.xiangjian.qqbot.onebot;

import com.xiangjian.qqbot.domain.InboundMessage;

@FunctionalInterface
public interface InboundMessageHandler {
    void handle(InboundMessage message);
}
