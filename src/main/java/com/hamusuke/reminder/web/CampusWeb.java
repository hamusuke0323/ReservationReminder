package com.hamusuke.reminder.web;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hamusuke.reminder.profiler.DebugProfiler;
import com.hamusuke.reminder.throwable.LoginFailedException;
import com.hamusuke.reminder.throwable.QueryFailedException;
import com.hamusuke.reminder.util.Either;
import com.hamusuke.reminder.web.reservation.RoomReservations;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class CampusWeb implements AutoCloseable {
    private static final URI HOST;
    private static final String MID_PATH = "campusweb";
    private static final String PORTAL_PATH = MID_PATH + "/campusportal.do";
    private static final String SQUARE_PATH = MID_PATH + "/campussquare.do";
    private static final String ROOM_RESERVATION_FLOW_ID = "KHW0001300-flow";
    private static final String MEETING_ROOM_GROUP_CODE = "04";
    private static final DateTimeFormatter TO_STRING = DateTimeFormatter.ofPattern("yyyy/MM/dd(E)");
    private static final String CACHE_KEY = "key";
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    @Nullable
    private ScheduledFuture<?> task;
    private final HttpClient client;
    private final Cache<String, String> executionKeyCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    static {
        try {
            HOST = new URI("https://csweb.u-aizu.ac.jp/");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public CampusWeb() {
        this.client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    @Override
    public void close() {
        this.executor.shutdownNow();
    }

    /**
     *
     * @param sid    student id
     * @param rawPwd raw password
     * @return student name
     * @throws LoginFailedException if failed to log in
     */
    public String login(String sid, String rawPwd) throws LoginFailedException {
        try {
            var encodedPwd = URLEncoder.encode(rawPwd, StandardCharsets.UTF_8);
            var rsp = this.client.send(HttpRequest.newBuilder()
                    .uri(HOST.resolve(PORTAL_PATH))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString("wfId=nwf_PTW0000002_login&userName=" + sid + "&password=" + encodedPwd + "&locale=ja_JP&undefined=&action=rwf&tabId=home")).build(), HttpResponse.BodyHandlers.ofString());
            var body = rsp.body();
            boolean canLoggedIn = body != null && body.contains("now loading");
            if (!canLoggedIn) {
                throw new LoginFailedException();
            }

            final var doc = this.getMainPage();
            var e = doc.body().selectFirst("#portaluser li.txt");
            if (e == null) {
                throw new LoginFailedException();
            }

            this.startSessionExtensionTimer();
            return e.text();
        } catch (IOException | InterruptedException e) {
            throw new LoginFailedException(e);
        }
    }

    public void loginWithSession(String sessionId) throws Exception {
        this.client.cookieHandler()
                .orElseThrow()
                .put(URI.create(HOST.getHost()), Map.of("set-cookie", Collections.singletonList("JSESSIONID=" + sessionId + "; Domain=csweb.u-aizu.ac.jp; Path=/campusweb; Secure; HttpOnly")));
    }

    private void startSessionExtensionTimer() {
        this.stopSessionExtensionTimer();

        this.task = this.executor.scheduleWithFixedDelay(() -> {
            try {
                this.extendSession();
            } catch (Exception e) {
                System.err.println("Error occurred: " + e);
            }
        }, 25L, 25L, TimeUnit.MINUTES);
    }

    private void stopSessionExtensionTimer() {
        if (this.task == null) {
            return;
        }

        this.task.cancel(false);
    }

    private void extendSession() throws Exception {
        this.client.send(HttpRequest.newBuilder()
                .uri(HOST.resolve(PORTAL_PATH + "?page=main&action=rwf&tabId=home&wfId=dummy"))
                .GET()
                .build(), HttpResponse.BodyHandlers.discarding());
    }

    public Document getMainPage() throws IOException, InterruptedException {
        var rsp = this.client.send(HttpRequest.newBuilder()
                .uri(HOST.resolve(PORTAL_PATH + "?page=main&tabId=home&locale=ja_JP"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        return Jsoup.parse(rsp.body());
    }

    private static String parseExecutionKey(final String url) {
        final var split = url.split("_flowExecutionKey=");
        if (split.length < 2) {
            return "";
        }

        final int ampersand = split[1].indexOf('&');
        if (ampersand < 0) {
            return split[1];
        }

        return split[1].substring(0, ampersand);
    }

    private String getFlowExecutionKey() {
        final var k = this.executionKeyCache.getIfPresent(CACHE_KEY);
        if (k != null) {
            return k;
        }

        try {
            final var loc = this.getLocation(HOST.resolve(SQUARE_PATH + "?_flowId=" + ROOM_RESERVATION_FLOW_ID), HttpRequest.Builder::GET).getRight();
            final String key;
            if (loc.isEmpty() || (key = parseExecutionKey(loc.get())).isBlank()) {
                throw new QueryFailedException("Could not get flow execution key.");
            }

            this.cacheExecutionKey(key);
            return key;
        } catch (IOException | InterruptedException e) {
            throw new QueryFailedException(e);
        }
    }

    private void cacheExecutionKey(final String key) {
        this.executionKeyCache.put(CACHE_KEY, key);
    }

    public RoomReservations queryRoomReservation(final String room, final LocalDate date, final DebugProfiler debugProfiler) throws QueryFailedException {
        try {
            final var location = this.getLocation(HOST.resolve(SQUARE_PATH), builder -> {
                builder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                final var body = "_eventId=searchShow&_flowExecutionKey=%s&shozokuCode=&shisetsuGroupCd=%s&tatemonoCd=&displayDateStr=%s".formatted(this.getFlowExecutionKey(), MEETING_ROOM_GROUP_CODE, date.format(TO_STRING));
                debugProfiler.appendLine("Request body: " + body);
                builder.POST(HttpRequest.BodyPublishers.ofString(body));
            }).getRight();

            final String newKey;
            if (location.isEmpty() || (newKey = parseExecutionKey(location.get())).isBlank()) {
                throw new QueryFailedException();
            }

            this.cacheExecutionKey(newKey);
            final var uri = HOST.resolve(location.get());
            debugProfiler.appendLine("Response URL: " + uri.toURL());
            final var status = this.client.send(HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());

            debugProfiler.appendLine("Response body: ");
            debugProfiler.appendLine("```html");
            debugProfiler.appendLine(status.body());
            debugProfiler.appendLine("```");
            final var result = RoomReservations.parseFrom(room, date, Jsoup.parse(status.body()).body());
            debugProfiler.appendLine("Reservations: " + result.reservations());
            return result;
        } catch (IOException | InterruptedException e) {
            throw new QueryFailedException(e);
        }
    }

    private Either<Document, String> getLocation(URI uri, Consumer<HttpRequest.Builder> builderTinkerer) throws IOException, InterruptedException {
        var b = HttpRequest.newBuilder(uri);
        builderTinkerer.accept(b);

        var rsp = this.client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (rsp.statusCode() == 200) {
            return Either.left(Jsoup.parse(rsp.body()));
        }

        return Either.right(rsp.headers().firstValue("location").orElse(null));
    }

    public void logout() throws Exception {
        this.client.send(HttpRequest.newBuilder()
                .uri(HOST.resolve(PORTAL_PATH))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString("wfId=nwf_PTW0000003_logout&action=rwf&tabId=home&page=main")).build(), HttpResponse.BodyHandlers.discarding());

        this.stopSessionExtensionTimer();
    }
}
