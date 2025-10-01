package com.hamusuke.reminder.util;

import java.util.function.Consumer;

public class Util {
    public static <T> T make(final T t, final Consumer<T> action) {
        action.accept(t);
        return t;
    }
}
