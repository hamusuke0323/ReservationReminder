package com.hamusuke.reminder.throwable;

public final class LoginFailedException extends RuntimeException {
    public LoginFailedException() {
    }

    public LoginFailedException(final Throwable cause) {
        super(cause);
    }
}
