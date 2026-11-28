package fr.thegostsniper.arffornia_proxy.vote;

import fr.thegostsniper.arffornia_proxy.service.ApiService.VoteResult;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Delivers queued votes one at a time, oldest first. A vote leaves the queue only once the website acknowledged it
 * or refused it for good; any other failure keeps it and delays the next attempt with exponential backoff.
 * All state changes run on the single-threaded executor.
 */
public class VoteDispatcher {

    /** Sends one vote. */
    public interface Sender {
        CompletableFuture<VoteResult> send(PendingVote vote);
    }

    private final Logger logger;
    private final VoteQueue queue;
    private final Sender sender;
    private final ScheduledExecutorService executor;
    private final Duration baseDelay;
    private final Duration maxDelay;

    private boolean inFlight;
    private boolean retryScheduled;
    private int consecutiveFailures;

    public VoteDispatcher(Logger logger, VoteQueue queue, Sender sender, ScheduledExecutorService executor,
                          Duration baseDelay, Duration maxDelay) {
        this.logger = logger;
        this.queue = queue;
        this.sender = sender;
        this.executor = executor;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    /** Delivers the votes restored from disk. */
    public void start() {
        executor.execute(this::drain);
    }

    public void submit(String username, String serviceName, String address) {
        PendingVote vote = new PendingVote(UUID.randomUUID().toString(), username, serviceName, address);
        PendingVote dropped = queue.add(vote);
        if (dropped != null) {
            logger.error("Pending vote queue is full, dropped the oldest vote (player {}, service {}).",
                    dropped.username(), dropped.serviceName());
        }
        executor.execute(this::drain);
    }

    Duration delayAfter(int failures) {
        long factor = 1L << Math.min(failures - 1, 20);
        long millis = Math.min(maxDelay.toMillis(), baseDelay.toMillis() * factor);
        return Duration.ofMillis(millis);
    }

    private void drain() {
        if (inFlight || retryScheduled) {
            return;
        }
        PendingVote vote = queue.peek();
        if (vote == null) {
            return;
        }
        inFlight = true;
        CompletableFuture<VoteResult> attempt;
        try {
            attempt = sender.send(vote);
        } catch (RuntimeException e) {
            attempt = CompletableFuture.failedFuture(e);
        }
        attempt.whenComplete((result, error) -> executor.execute(() -> onAttempt(vote, error == null ? result : VoteResult.ERROR)));
    }

    private void onAttempt(PendingVote vote, VoteResult result) {
        inFlight = false;
        if (result.isFinal()) {
            queue.remove(vote.id());
            consecutiveFailures = 0;
            drain();
            return;
        }
        consecutiveFailures++;
        Duration delay = delayAfter(consecutiveFailures);
        logger.warn("Vote for {} not delivered ({}), {} vote(s) pending, retrying in {}s.",
                vote.username(), result, queue.size(), delay.toSeconds());
        retryScheduled = true;
        executor.schedule(() -> {
            retryScheduled = false;
            drain();
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
