package com.hamusuke.reminder.web.reservation;

import com.google.common.util.concurrent.RateLimiter;
import com.hamusuke.reminder.profiler.DebugProfiler;
import com.hamusuke.reminder.throwable.QueryFailedException;
import com.hamusuke.reminder.web.CampusWeb;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static com.hamusuke.reminder.web.reservation.DurationContext.FORMATTER;
import static com.hamusuke.reminder.web.reservation.DurationContext.TIME_FORMATTER;

public final class RateLimitedRoomReservationRetriever {
    public static final long CAMPUS_WEB_ACCESS_INTERVAL = 10L;
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final String ROOM = "F1会議室";

    public static void startRetrievingBlocking(final List<DurationContext> durationContexts, final CampusWeb campusWeb, final DebugProfiler debugProfiler, final ProgressListener progressListener, final RetrievalListener retrievalListener, final Runnable onCompleteSuccessfully, final ErrorListener errorListener) {
        if (!LOCK.tryLock()) {
            errorListener.onError("処理が完了するまでお待ちください...");
            return;
        }

        try {
            final int size = durationContexts.size();
            progressListener.onProgress(0, size);

            final var limiter = RateLimiter.create(1.0D / CAMPUS_WEB_ACCESS_INTERVAL);
            limiter.acquire();
            for (int i = 0; i < size; i++) {
                try {
                    final var ctx = durationContexts.get(i);
                    debugProfiler.start();
                    debugProfiler.appendLine("Input line: " + ctx.hyphenatedDuration());
                    debugProfiler.appendLine("Parsed value: " + ctx.start().format(FORMATTER) + "-" + ctx.end().format(TIME_FORMATTER));

                    final var reservations = campusWeb.queryRoomReservation(ROOM, ctx.start().toLocalDate(), debugProfiler);
                    final var reservation = reservations.findOtherReservation(ctx.start(), ctx.end());
                    retrievalListener.onRetrieved(ctx, reservation);

                    progressListener.onProgress(i + 1, size);
                    limiter.acquire();
                } catch (QueryFailedException e) {
                    System.err.println("Error occurred while trying to retrieve: " + e.getMessage());
                    debugProfiler.appendLine("Latest Exception: " + e.getMessage());
                    errorListener.onError("処理中にエラーが発生しました。");
                    return;
                }
            }

            onCompleteSuccessfully.run();
        } finally {
            LOCK.unlock();
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(final int cur, final int total);
    }

    @FunctionalInterface
    public interface RetrievalListener {
        void onRetrieved(final DurationContext ctx, final @Nullable RoomReservation other);
    }

    @FunctionalInterface
    public interface ErrorListener {
        void onError(final String message);
    }
}
