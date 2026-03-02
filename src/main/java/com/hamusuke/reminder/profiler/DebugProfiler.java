package com.hamusuke.reminder.profiler;

import java.util.Arrays;
import java.util.stream.Collectors;

public class DebugProfiler {
    public static final DebugProfiler EMPTY = new DebugProfiler() {
        @Override
        public void start() {
        }

        @Override
        public void appendLine(String line) {
        }
    };

    private final StringBuilder builder = new StringBuilder();

    public void start() {
        this.builder.setLength(0);
    }

    public void appendLine(final String line) {
        this.builder.append(line).append('\n');
    }

    @Override
    public String toString() {
        return Arrays.stream(this.builder.toString().split("\n"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
