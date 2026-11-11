package com.xiangjian.qqbot.domain;

import java.util.Map;

/**
 * Platform-neutral boundary for QQ transports. OneBot v11 is the first
 * implementation; an official QQ adapter can implement this contract later.
 */
public interface QqPlatformAdapter {
    String id();

    boolean connected();

    boolean send(OutboundMessage message);

    Map<String, Object> status();
}
