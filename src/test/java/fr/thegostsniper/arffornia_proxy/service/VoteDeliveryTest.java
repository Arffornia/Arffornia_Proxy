package fr.thegostsniper.arffornia_proxy.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.thegostsniper.arffornia_proxy.vote.VoteDispatcher;
import fr.thegostsniper.arffornia_proxy.vote.VoteQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoteDeliveryTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private String baseUrl;
    private volatile int voteStatus = 503;
    private final List<String> receivedKeys = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/token/svc", exchange -> respond(exchange, 200, "{\"token\":\"t\"}"));
        server.createContext("/api/vote/nuvotifier", exchange -> {
            receivedKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, voteStatus, "{}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private VoteDispatcher dispatcher(VoteQueue queue) {
        ApiService api = new ApiService(NOPLogger.NOP_LOGGER, baseUrl, "id", "secret");
        return new VoteDispatcher(NOPLogger.NOP_LOGGER, queue,
                vote -> api.sendVote(vote.id(), vote.username(), vote.serviceName(), vote.address()),
                executor, Duration.ofMillis(20), Duration.ofMillis(80));
    }

    @Test
    void voteReceivedWhileTheWebsiteIsDownIsDeliveredOnceItRecoversWithTheSameIdempotencyKey() throws Exception {
        VoteQueue queue = new VoteQueue(NOPLogger.NOP_LOGGER, dir.resolve("pending-votes.json"), 100);
        VoteDispatcher dispatcher = dispatcher(queue);

        dispatcher.submit("player", "service", "127.0.0.1");
        await(() -> receivedKeys.size() >= 2);
        voteStatus = 200;

        await(() -> queue.size() == 0);
        assertEquals(1, receivedKeys.stream().distinct().count());
    }

    @Test
    void voteQueuedBeforeARestartIsDeliveredAfterIt() throws Exception {
        VoteQueue beforeRestart = new VoteQueue(NOPLogger.NOP_LOGGER, dir.resolve("pending-votes.json"), 100);
        beforeRestart.add(new fr.thegostsniper.arffornia_proxy.vote.PendingVote("key-1", "player", "service", "127.0.0.1"));
        voteStatus = 200;
        VoteQueue afterRestart = new VoteQueue(NOPLogger.NOP_LOGGER, dir.resolve("pending-votes.json"), 100);

        dispatcher(afterRestart).start();

        await(() -> afterRestart.size() == 0);
        assertEquals(List.of("key-1"), receivedKeys);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 15s");
            }
            Thread.sleep(10);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
