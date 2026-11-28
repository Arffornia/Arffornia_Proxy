package fr.thegostsniper.arffornia_proxy.vote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteQueueTest {

    @TempDir
    Path dir;

    private VoteQueue open(int maxSize) {
        return new VoteQueue(NOPLogger.NOP_LOGGER, dir.resolve("pending-votes.json"), maxSize);
    }

    private static PendingVote vote(String id) {
        return new PendingVote(id, "player-" + id, "service", "127.0.0.1");
    }

    @Test
    void keepsVotesAcrossRestartsInOrder() {
        VoteQueue first = open(10);
        first.add(vote("a"));
        first.add(vote("b"));

        VoteQueue reopened = open(10);

        assertEquals(2, reopened.size());
        assertEquals("a", reopened.peek().id());
        assertEquals("player-a", reopened.peek().username());
    }

    @Test
    void removedVotesStayGoneAfterRestart() {
        VoteQueue first = open(10);
        first.add(vote("a"));
        first.add(vote("b"));
        first.remove("a");

        VoteQueue reopened = open(10);

        assertEquals(1, reopened.size());
        assertEquals("b", reopened.peek().id());
    }

    @Test
    void dropsTheOldestVoteWhenFull() {
        VoteQueue queue = open(2);
        queue.add(vote("a"));
        queue.add(vote("b"));

        PendingVote dropped = queue.add(vote("c"));

        assertEquals("a", dropped.id());
        assertEquals("b", queue.peek().id());
        assertEquals(2, queue.size());
    }

    @Test
    void doesNotDropAnythingBelowTheLimit() {
        assertNull(open(2).add(vote("a")));
    }

    @Test
    void movesAnUnreadableFileAsideAndStartsEmpty() throws IOException {
        Files.writeString(dir.resolve("pending-votes.json"), "{not json");

        VoteQueue queue = open(10);

        assertEquals(0, queue.size());
        assertTrue(Files.exists(dir.resolve("pending-votes.json.corrupt")));
    }

    @Test
    void leavesNoTemporaryFileBehind() throws IOException {
        VoteQueue queue = open(10);
        queue.add(vote("a"));
        queue.remove("a");

        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }
}
