package fr.thegostsniper.arffornia_proxy.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.proxy.ProxyServer;
import com.vexsoftware.votifier.model.Vote;

import fr.thegostsniper.arffornia_proxy.config.PluginConfig;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to handle communication with the Arffornia web API.
 * Manages authentication and sending vote data.
 */
public class ApiService {

    private final Logger logger;
    private final PluginConfig config;
    private final HttpClient httpClient;
    private final Gson gson;
    private final AtomicReference<String> authToken = new AtomicReference<>(null);

    @Inject
    public ApiService(Logger logger, ProxyServer proxyServer, PluginConfig config) {
        this.logger = logger;
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    /**
     * Authenticates with the API using service credentials and stores the token.
     *
     * @return A CompletableFuture that completes when authentication is done.
     */
    private CompletableFuture<Void> authenticate() {
        JsonObject payload = new JsonObject();
        payload.addProperty("client_id", config.getApiClientId());
        payload.addProperty("client_secret", config.getApiClientSecret());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/auth/token/svc"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        String token = jsonResponse.get("token").getAsString();
                        this.authToken.set(token);
                        logger.info("Successfully authenticated with the API.");
                    } else {
                        logger.error("Failed to authenticate with API. Status: {}, Body: {}", response.statusCode(), response.body());
                        this.authToken.set(null);
                    }
                }).exceptionally(e -> {
                    logger.error("Error during API authentication.", e);
                    this.authToken.set(null);
                    return null;
                });
    }

    /**
     * Sends a vote to the web API. It will attempt to authenticate if no token is present.
     *
     * @param vote The vote event from NuVotifier.
     */
    public void sendVote(Vote vote) {
        if (authToken.get() == null) {
            logger.info("No auth token, attempting to authenticate first...");
            authenticate().thenRun(() -> {
                if (authToken.get() != null) {
                    executeSendVote(vote);
                } else {
                    logger.error("Cannot send vote, authentication failed.");
                }
            });
        } else {
            executeSendVote(vote);
        }
    }

    /**
     * Executes the actual HTTP request to send the vote data.
     *
     * @param vote The vote event from NuVotifier.
     */
    private void executeSendVote(Vote vote) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", vote.getUsername());
        payload.addProperty("service_name", vote.getServiceName());
        payload.addProperty("ip_address", vote.getAddress());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + "/vote/nuvotifier"))
                .header("Authorization", "Bearer " + authToken.get())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 401) {
                        logger.warn("API token is invalid or expired. Re-authenticating...");
                        authToken.set(null);
                        sendVote(vote); // Retry sending the vote after re-authentication
                    } else if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Successfully sent vote for player {} from {}.", vote.getUsername(), vote.getServiceName());
                    } else {
                        logger.error("Failed to send vote for {}. Status: {}, Body: {}", vote.getUsername(), response.statusCode(), response.body());
                    }
                }).exceptionally(e -> {
                    logger.error("An error occurred while sending vote for {}.", vote.getUsername(), e);
                    return null;
                });
    }
}