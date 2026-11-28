package fr.thegostsniper.arffornia_proxy.listener;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.vexsoftware.votifier.velocity.event.VotifierEvent;
import fr.thegostsniper.arffornia_proxy.vote.VoteDispatcher;

import org.slf4j.Logger;

/**
 * Listens for NuVotifier events and queues them for delivery to the web API.
 */
public class VoteListener {

    private final Logger logger;
    private final VoteDispatcher dispatcher;

    @Inject
    public VoteListener(Logger logger, VoteDispatcher dispatcher) {
        this.logger = logger;
        this.dispatcher = dispatcher;
    }

    @Subscribe
    public void onVotifierEvent(VotifierEvent event) {
        logger.info("Received vote from service '{}' for player '{}'. Queued for the web API.",
                event.getVote().getServiceName(),
                event.getVote().getUsername());

        dispatcher.submit(event.getVote().getUsername(), event.getVote().getServiceName(), event.getVote().getAddress());
    }
}