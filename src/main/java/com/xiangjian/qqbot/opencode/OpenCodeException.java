package com.xiangjian.qqbot.opencode;

public class OpenCodeException extends RuntimeException {
    private final int statusCode;

    public OpenCodeException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public OpenCodeException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int statusCode() {
        return statusCode;
    }
}
