package de.status.service.query;

import de.status.service.config.StatusProperties.ServerConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Routes a server to the first querier that supports its game type and turns any
 * unexpected failure into an offline result.
 */
@Component
public class QueryDispatcher {

    private final List<ServerQuerier> queriers;

    public QueryDispatcher(List<ServerQuerier> queriers) {
        this.queriers = queriers;
    }

    public QueryResult query(ServerConfig server, Duration timeout) {
        return queriers.stream()
                .filter(querier -> querier.supports(server.getGame()))
                .findFirst()
                .map(querier -> safeQuery(querier, server, timeout))
                .orElseGet(() -> QueryResult.offline("No querier for game type " + server.getGame()));
    }

    private QueryResult safeQuery(ServerQuerier querier, ServerConfig server, Duration timeout) {
        try {
            return querier.query(server, timeout);
        } catch (Exception e) {
            String message = e.getMessage();
            return QueryResult.offline(message == null ? e.getClass().getSimpleName() : message);
        }
    }
}
