package de.status.service.query;

/**
 * Outcome of a single query against one server. All player/version fields are
 * optional because not every protocol reports them.
 */
public record QueryResult(
        boolean online,
        Integer players,
        Integer maxPlayers,
        String version,
        String map,
        String motd,
        Long latencyMs,
        String error) {

    public static QueryResult online(Integer players, Integer maxPlayers, String version,
                                     String map, String motd, long latencyMs) {
        return new QueryResult(true, players, maxPlayers, version, map, motd, latencyMs, null);
    }

    public static QueryResult offline(String error) {
        return new QueryResult(false, null, null, null, null, null, null, error);
    }
}
