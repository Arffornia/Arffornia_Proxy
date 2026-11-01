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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to handle communication with the Arffornia web API.
 * Manages authentication and sending vote data.
 */
public class ApiService {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Outcome of a vote submission, mainly for logging and tests. */
    public enum VoteResult { SENT, AUTH_FAILED, REJECTED, ERROR }

    private final Logger logger;
    private final String apiUrl;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final AtomicReference<String> authToken = new AtomicReference<>(null);
    /** The authentication currently in flight, shared by concurrent callers. */
    private final AtomicReference<CompletableFuture<String>> pendingAuth = new AtomicReference<>(null);

    @Inject
    public ApiService(Logger logger, ProxyServer proxyServer, PluginConfig config) {
        this(logger, config.getApiUrl(), config.getApiClientId(), config.getApiClientSecret());
    }

    ApiService(Logger logger, String apiUrl, String clientId, String clientSecret) {
        this.logger = logger;
        this.apiUrl = apiUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * Returns the cached token, or authenticates. Concurrent callers share one authentication request.
     *
     * @return the token, or {@code null} when authentication failed.
     */
    private CompletableFuture<String> getToken() {
        String token = authToken.get();
        if (token != null) {
            return CompletableFuture.completedFuture(token);
        }

        CompletableFuture<String> mine = new CompletableFuture<>();
        if (!pendingAuth.compareAndSet(null, mine)) {
            CompletableFuture<String> existing = pendingAuth.get();
            return existing != null ? existing : getToken();
        }

        authenticate().whenComplete((result, error) -> {
            pendingAuth.compareAndSet(mine, null);
            mine.complete(error == null ? result : null);
        });
        return mine;
    }

    /**
     * Authenticates with the API using service credentials and stores the token.
     */
    private CompletableFuture<String> authenticate() {
        JsonObject payload = new JsonObject();
        payload.addProperty("client_id", clientId);
        payload.addProperty("client_secret", clientSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/auth/token/svc"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        String token = jsonResponse != null && jsonResponse.has("token") ? jsonResponse.get("token").getAsString() : null;
                        if (token == null) {
                            logger.error("API authentication response did not contain a token.");
                            return null;
                        }
                        this.authToken.set(token);
                        logger.info("Successfully authenticated with the API.");
                        return token;
                    }
                    logger.error("Failed to authenticate with API. Status: {}", response.statusCode());
                    return (String) null;
                }).exceptionally(e -> {
                    logger.error("Error during API authentication: {}", e.getMessage());
                    return null;
                });
    }

    /**
     * Sends a vote to the web API. Authenticates first if needed. A 401 response clears the token and triggers
     * exactly one re-authentication and one retry.
     *
     * @param vote The vote event from NuVotifier.
     */
    public CompletableFuture<VoteResult> sendVote(Vote vote) {
        return sendVote(vote.getUsername(), vote.getServiceName(), vote.getAddress());
    }

    CompletableFuture<VoteResult> sendVote(String username, String serviceName, String address) {
        return attemptSend(username, serviceName, address, true)
                .exceptionally(e -> {
                    logger.error("An error occurred while sending vote for {}: {}", username, e.getMessage());
                    return VoteResult.ERROR;
                });
    }

    private CompletableFuture<VoteResult> attemptSend(String username, String serviceName, String address, boolean retryOnUnauthorized) {
        return getToken().thenCompose(token -> {
            if (token == null) {
                logger.error("Cannot send vote for {}, authentication failed.", username);
                return CompletableFuture.completedFuture(VoteResult.AUTH_FAILED);
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("username", username);
            payload.addProperty("service_name", serviceName);
            payload.addProperty("ip_address", address);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/vote/nuvotifier"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        int status = response.statusCode();
                        if (status == 401) {
                            authToken.compareAndSet(token, null);
                            if (retryOnUnauthorized) {
                                logger.warn("API token is invalid or expired. Re-authenticating once...");
                                return attemptSend(username, serviceName, address, false);
                            }
                            logger.error("Vote for {} rejected with 401 after re-authentication; giving up.", username);
                            return CompletableFuture.completedFuture(VoteResult.AUTH_FAILED);
                        }
                        if (status >= 200 && status < 300) {
                            logger.info("Successfully sent vote for player {} from {}.", username, serviceName);
                            return CompletableFuture.completedFuture(VoteResult.SENT);
                        }
                        logger.error("Failed to send vote for {}. Status: {}, Body: {}", username, status, response.body());
                        return CompletableFuture.completedFuture(VoteResult.REJECTED);
                    });
        });
    }
}
