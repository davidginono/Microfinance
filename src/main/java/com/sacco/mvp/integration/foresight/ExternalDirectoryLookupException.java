package com.sacco.mvp.integration.foresight;

import lombok.Getter;

@Getter
public class ExternalDirectoryLookupException extends IllegalStateException {
    private final int statusCode;

    public ExternalDirectoryLookupException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
