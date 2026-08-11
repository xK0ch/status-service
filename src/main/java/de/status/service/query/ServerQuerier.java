package de.status.service.query;

import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;

import java.time.Duration;

/**
 * A protocol-specific way to check one server. Implementations must not throw:
 * any failure is reported as {@link QueryResult#offline(String)}.
 */
public interface ServerQuerier {

    boolean supports(GameType game);

    QueryResult query(ServerConfig server, Duration timeout);
}
