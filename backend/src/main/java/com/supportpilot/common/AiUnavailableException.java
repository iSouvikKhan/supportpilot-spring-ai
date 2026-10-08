package com.supportpilot.common;

/** The configured AI provider could not be reached or returned an error. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
