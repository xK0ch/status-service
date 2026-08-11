package de.status.service.config;

import de.status.service.status.GameType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the servers to monitor and how often to poll them.
 * Bound from the {@code status.*} block in application.yml.
 */
@Data
@ConfigurationProperties(prefix = "status")
public class StatusProperties {

    /** How long to wait between two poll cycles. */
    private Duration refreshInterval = Duration.ofSeconds(30);

    /** Socket timeout for a single server query. */
    private Duration timeout = Duration.ofSeconds(5);

    /** Origins allowed to call the API in the browser (only relevant for local dev). */
    private List<String> corsAllowedOrigins = new ArrayList<>();

    /** The servers displayed on the status page. */
    private List<ServerConfig> servers = new ArrayList<>();

    @Data
    public static class ServerConfig {
        /** Stable identifier, also used as the key in the UI. */
        private String id;
        /** Display name shown on the card. */
        private String name;
        /** Which query protocol to use. */
        private GameType game;
        /** Host to reach the server on (usually host.docker.internal). */
        private String host;
        /** Port the query protocol talks to. */
        private int port;
        /** Optional slot count, used as a fallback when the protocol does not report it. */
        private Integer maxPlayers;
    }
}
