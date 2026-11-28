package fr.thegostsniper.arffornia_proxy.vote;

/** A vote waiting to be delivered. The id doubles as the idempotency key sent to the website. */
public final class PendingVote {
    private final String id;
    private final String username;
    private final String serviceName;
    private final String address;

    public PendingVote(String id, String username, String serviceName, String address) {
        this.id = id;
        this.username = username;
        this.serviceName = serviceName;
        this.address = address;
    }

    public String id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String serviceName() {
        return serviceName;
    }

    public String address() {
        return address;
    }
}
