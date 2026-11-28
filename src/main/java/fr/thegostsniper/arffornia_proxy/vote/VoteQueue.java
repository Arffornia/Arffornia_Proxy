package fr.thegostsniper.arffornia_proxy.vote;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded FIFO of votes not yet acknowledged by the website, mirrored to a JSON file after every change so that
 * a proxy restart does not lose them. When full, the oldest vote is dropped.
 */
public class VoteQueue {

    private final Logger logger;
    private final Path file;
    private final int maxSize;
    private final Gson gson = new Gson();
    private final Deque<PendingVote> votes = new ArrayDeque<>();

    public VoteQueue(Logger logger, Path file, int maxSize) {
        this.logger = logger;
        this.file = file;
        this.maxSize = maxSize;
        load();
    }

    /** @return the vote dropped to make room, or {@code null}. */
    public synchronized PendingVote add(PendingVote vote) {
        votes.addLast(vote);
        PendingVote dropped = votes.size() > maxSize ? votes.pollFirst() : null;
        persist();
        return dropped;
    }

    public synchronized PendingVote peek() {
        return votes.peekFirst();
    }

    public synchronized void remove(String id) {
        if (votes.removeIf(vote -> vote.id().equals(id))) {
            persist();
        }
    }

    public synchronized int size() {
        return votes.size();
    }

    private void load() {
        if (Files.notExists(file)) {
            return;
        }
        try {
            PendingVote[] stored = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), PendingVote[].class);
            if (stored != null) {
                for (PendingVote vote : stored) {
                    votes.addLast(vote);
                }
            }
            logger.info("Restored {} pending vote(s) from {}.", votes.size(), file);
        } catch (IOException | JsonParseException e) {
            Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt");
            logger.error("Could not read the pending votes file, moving it to {}: {}", corrupt, e.getMessage());
            try {
                Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                logger.error("Could not move the unreadable pending votes file: {}", moveError.getMessage());
            }
            votes.clear();
        }
    }

    private void persist() {
        List<PendingVote> snapshot = new ArrayList<>(votes);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(temp, gson.toJson(snapshot), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Could not save the pending votes file {}: {}", file, e.getMessage());
        }
    }
}
