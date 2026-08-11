package de.status.service.status;

import java.time.Instant;

/**
 * The current status of one monitored server, as returned by the API and pushed
 * over the SSE stream.
 */
public record ServerStatus(
        String id,
        String name,
        GameType game,
        boolean online,
        Integer players,
        Integer maxPlayers,
        String version,
        String map,
        String motd,
        Long latencyMs,
        String error,
        Instant lastChecked) {
}
