package com.hamusuke.reminder.throwable;

public class QueryFailedException extends RuntimeException {
    public QueryFailedException() {
    }

    public QueryFailedException(Throwable cause) {
        super(cause);
    }

    public QueryFailedException(String message) {
        super(message);
    }
}
