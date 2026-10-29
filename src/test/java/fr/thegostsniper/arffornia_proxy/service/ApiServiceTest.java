package fr.thegostsniper.arffornia_proxy.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiServiceTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger authCalls = new AtomicInteger();
    private final AtomicInteger voteCalls = new AtomicInteger();
    private volatile int voteStatus = 200;
    private volatile int authStatus = 200;
    private volatile long authDelayMs = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/auth/token/svc", exchange -> {
            int n = authCalls.incrementAndGet();
            sleep(authDelayMs);
            respond(exchange, authStatus, "{\"token\":\"token-" + n + "\"}");
        });
        server.createContext("/api/vote/nuvotifier", exchange -> {
            voteCalls.incrementAndGet();
            respond(exchange, voteStatus, "{}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ApiService service() {
        return new ApiService(NOPLogger.NOP_LOGGER, baseUrl, "id", "secret");
    }

    private static ApiService.VoteResult vote(ApiService service) throws Exception {
        return service.sendVote("player", "service", "127.0.0.1").get(15, TimeUnit.SECONDS);
    }

    @Test
    void authenticatesOnceAndReusesToken() throws Exception {
        ApiService service = service();
        assertEquals(ApiService.VoteResult.SENT, vote(service));
        assertEquals(ApiService.VoteResult.SENT, vote(service));
        assertEquals(1, authCalls.get());
        assertEquals(2, voteCalls.get());
    }

    @Test
    void unauthorizedTriggersExactlyOneReauthAndOneRetry() throws Exception {
        voteStatus = 401;
        ApiService service = service();
        assertEquals(ApiService.VoteResult.AUTH_FAILED, vote(service));
        assertEquals(2, authCalls.get());
        assertEquals(2, voteCalls.get());
    }

    @Test
    void serverErrorIsNotRetried() throws Exception {
        voteStatus = 500;
        assertEquals(ApiService.VoteResult.REJECTED, vote(service()));
        assertEquals(1, voteCalls.get());
    }

    @Test
    void failedAuthenticationDoesNotSendTheVote() throws Exception {
        authStatus = 403;
        assertEquals(ApiService.VoteResult.AUTH_FAILED, vote(service()));
        assertEquals(0, voteCalls.get());
    }

    @Test
    void concurrentVotesShareOneAuthentication() throws Exception {
        authDelayMs = 300;
        ApiService service = service();
        List<CompletableFuture<ApiService.VoteResult>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(service.sendVote("player" + i, "service", "127.0.0.1"));
        }
        for (CompletableFuture<ApiService.VoteResult> f : futures) {
            assertEquals(ApiService.VoteResult.SENT, f.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, authCalls.get());
        assertEquals(5, voteCalls.get());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
