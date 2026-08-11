package de.status.service.status;

import de.status.service.config.StatusProperties;
import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.query.QueryDispatcher;
import de.status.service.query.QueryResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds the most recent snapshot of every server's status, refreshes it on a
 * fixed schedule (driven by {@code SchedulingConfig}) and fans updates out to
 * any connected SSE clients.
 */
@Service
public class StatusService {

    private static final Logger log = LoggerFactory.getLogger(StatusService.class);

    private final StatusProperties properties;
    private final QueryDispatcher dispatcher;

    // One virtual thread per query: cheap, and the work is blocking socket I/O.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private volatile List<ServerStatus> snapshot = List.of();

    public StatusService(StatusProperties properties, QueryDispatcher dispatcher) {
        this.properties = properties;
        this.dispatcher = dispatcher;
    }

    /** Queries every configured server in parallel and publishes the new snapshot. */
    public void refresh() {
        List<CompletableFuture<ServerStatus>> futures = properties.getServers().stream()
                .map(server -> CompletableFuture.supplyAsync(() -> queryOne(server), executor))
                .toList();

        List<ServerStatus> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        this.snapshot = results;
        broadcast(results);
    }

    private ServerStatus queryOne(ServerConfig server) {
        QueryResult result = dispatcher.query(server, properties.getTimeout());
        Integer maxPlayers = result.maxPlayers() != null ? result.maxPlayers() : server.getMaxPlayers();
        return new ServerStatus(
                server.getId(),
                server.getName(),
                server.getGame(),
                result.online(),
                result.players(),
                maxPlayers,
                result.version(),
                result.map(),
                result.motd(),
                result.latencyMs(),
                result.error(),
                Instant.now());
    }

    public List<ServerStatus> current() {
        return snapshot;
    }

    /** Registers a new SSE client and immediately sends it the current snapshot. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // never time out on our side
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        trySend(emitter, snapshot);
        return emitter;
    }

    private void broadcast(List<ServerStatus> results) {
        for (SseEmitter emitter : emitters) {
            trySend(emitter, results);
        }
    }

    private void trySend(SseEmitter emitter, List<ServerStatus> results) {
        try {
            emitter.send(SseEmitter.event().name("status").data(results));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        log.info("Status query executor shut down");
    }
}
