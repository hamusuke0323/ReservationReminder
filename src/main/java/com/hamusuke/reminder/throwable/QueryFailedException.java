package com.hamusuke.reminder.throwable;

public final class QueryFailedException extends RuntimeException {
    public QueryFailedException() {
    }

    public QueryFailedException(final Throwable cause) {
        super(cause);
    }

    public QueryFailedException(final String message) {
        super(message);
    }
}
