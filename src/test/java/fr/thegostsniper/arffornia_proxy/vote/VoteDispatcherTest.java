package fr.thegostsniper.arffornia_proxy.vote;

import fr.thegostsniper.arffornia_proxy.service.ApiService.VoteResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteDispatcherTest {

    @TempDir
    Path dir;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<String> sentIds = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        executor.shutdownNow();
    }

    private VoteQueue queue() {
        return new VoteQueue(NOPLogger.NOP_LOGGER, dir.resolve("pending-votes.json"), 100);
    }

    private VoteDispatcher dispatcher(VoteQueue queue, VoteDispatcher.Sender sender) {
        return new VoteDispatcher(NOPLogger.NOP_LOGGER, queue, sender, executor, Duration.ofMillis(20), Duration.ofMillis(80));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 10s");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void deliversAVoteAndEmptiesTheQueue() throws Exception {
        VoteQueue queue = queue();
        VoteDispatcher dispatcher = dispatcher(queue, vote -> {
            sentIds.add(vote.id());
            return CompletableFuture.completedFuture(VoteResult.SENT);
        });

        dispatcher.submit("player", "service", "127.0.0.1");

        await(() -> sentIds.size() == 1 && queue.size() == 0);
    }

    @Test
    void keepsRetryingTheSameVoteUntilTheApiRecovers() throws Exception {
        VoteQueue queue = queue();
        VoteDispatcher dispatcher = dispatcher(queue, vote -> {
            sentIds.add(vote.id());
            return CompletableFuture.completedFuture(sentIds.size() < 3 ? VoteResult.UNAVAILABLE : VoteResult.SENT);
        });

        dispatcher.submit("player", "service", "127.0.0.1");

        await(() -> sentIds.size() == 3 && queue.size() == 0);
        assertEquals(1, sentIds.stream().distinct().count());
    }

    @Test
    void retriesAfterNetworkErrorsAndSenderExceptions() throws Exception {
        VoteQueue queue = queue();
        VoteDispatcher dispatcher = dispatcher(queue, vote -> {
            sentIds.add(vote.id());
            return switch (sentIds.size()) {
                case 1 -> CompletableFuture.failedFuture(new IllegalStateException("boom"));
                case 2 -> CompletableFuture.completedFuture(VoteResult.ERROR);
                default -> CompletableFuture.completedFuture(VoteResult.SENT);
            };
        });

        dispatcher.submit("player", "service", "127.0.0.1");

        await(() -> sentIds.size() == 3 && queue.size() == 0);
    }

    @Test
    void dropsAVoteTheApiRefusedForGood() throws Exception {
        VoteQueue queue = queue();
        VoteDispatcher dispatcher = dispatcher(queue, vote -> {
            sentIds.add(vote.id());
            return CompletableFuture.completedFuture(VoteResult.REJECTED);
        });

        dispatcher.submit("player", "service", "127.0.0.1");

        await(() -> queue.size() == 0);
        assertEquals(1, sentIds.size());
    }

    @Test
    void deliversVotesRestoredFromDiskAfterARestart() throws Exception {
        VoteQueue before = queue();
        before.add(new PendingVote("old-1", "alice", "service", "127.0.0.1"));
        before.add(new PendingVote("old-2", "bob", "service", "127.0.0.1"));
        VoteQueue after = queue();
        VoteDispatcher dispatcher = dispatcher(after, vote -> {
            sentIds.add(vote.id());
            return CompletableFuture.completedFuture(VoteResult.SENT);
        });

        dispatcher.start();

        await(() -> after.size() == 0);
        assertEquals(List.of("old-1", "old-2"), sentIds);
    }

    @Test
    void sendsVotesInSubmissionOrderEvenWhileTheHeadIsFailing() throws Exception {
        VoteQueue queue = queue();
        VoteDispatcher dispatcher = dispatcher(queue, vote -> {
            sentIds.add(vote.username());
            boolean firstAttemptOfFirstVote = vote.username().equals("first") && sentIds.size() == 1;
            return CompletableFuture.completedFuture(firstAttemptOfFirstVote ? VoteResult.UNAVAILABLE : VoteResult.SENT);
        });

        dispatcher.submit("first", "service", "127.0.0.1");
        dispatcher.submit("second", "service", "127.0.0.1");

        await(() -> queue.size() == 0);
        assertEquals(List.of("first", "first", "second"), sentIds);
    }

    @Test
    void backoffDoublesUpToTheMaximum() {
        VoteDispatcher dispatcher = dispatcher(queue(), vote -> CompletableFuture.completedFuture(VoteResult.SENT));

        assertEquals(Duration.ofMillis(20), dispatcher.delayAfter(1));
        assertEquals(Duration.ofMillis(40), dispatcher.delayAfter(2));
        assertEquals(Duration.ofMillis(80), dispatcher.delayAfter(3));
        assertEquals(Duration.ofMillis(80), dispatcher.delayAfter(50));
        assertTrue(dispatcher.delayAfter(Integer.MAX_VALUE).compareTo(Duration.ofMillis(80)) <= 0);
    }
}
