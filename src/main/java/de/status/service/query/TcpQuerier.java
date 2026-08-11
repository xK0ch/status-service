package de.status.service.query;

import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * A plain TCP connect check. Used for TeamSpeak (which does not expose player
 * counts without an authenticated ServerQuery session) and as a generic
 * fallback: a successful connection means the server is up.
 */
@Component
public class TcpQuerier implements ServerQuerier {

    @Override
    public boolean supports(GameType game) {
        return game == GameType.TEAMSPEAK || game == GameType.TCP;
    }

    @Override
    public QueryResult query(ServerConfig server, Duration timeout) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), (int) timeout.toMillis());
            long latency = (System.nanoTime() - start) / 1_000_000;
            return QueryResult.online(null, server.getMaxPlayers(), null, null, null, latency);
        } catch (Exception e) {
            String message = e.getMessage();
            return QueryResult.offline(message == null ? e.getClass().getSimpleName() : message);
        }
    }
}
