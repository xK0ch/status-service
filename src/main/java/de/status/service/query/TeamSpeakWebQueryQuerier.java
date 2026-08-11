package de.status.service.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Reads the online client count from a TeamSpeak 6 server through its HTTP
 * WebQuery API. TeamSpeak 6 requires an {@code x-api-key} for every request (it
 * has no unauthenticated login and no raw ServerQuery port), so a persistent
 * api key is created once via the SSH query and configured here.
 *
 * <p>A single request is made: {@code GET /{sid}/serverinfo}. The player count
 * is {@code virtualserver_clientsonline} minus {@code virtualserver_queryclientsonline}
 * so query connections (including this service's own) are not counted as voice
 * users.
 */
@Component
public class TeamSpeakWebQueryQuerier implements ServerQuerier {

    private static final int DEFAULT_VIRTUAL_SERVER_ID = 1;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public TeamSpeakWebQueryQuerier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(GameType game) {
        return game == GameType.TEAMSPEAK_WEBQUERY;
    }

    @Override
    public QueryResult query(ServerConfig server, Duration timeout) {
        if (server.getApiKey() == null || server.getApiKey().isBlank()) {
            return QueryResult.offline("No WebQuery api key configured");
        }

        long start = System.nanoTime();
        int sid = server.getVirtualServerId() != null ? server.getVirtualServerId() : DEFAULT_VIRTUAL_SERVER_ID;
        String url = "http://" + server.getHost() + ":" + server.getPort() + "/" + sid + "/serverinfo";

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("x-api-key", server.getApiKey())
                    .GET()
                    .build();
            Map<String, Object> info = firstBody(send(request));
            long latency = (System.nanoTime() - start) / 1_000_000;

            Integer online = asInt(info.get("virtualserver_clientsonline"));
            Integer queryClients = asInt(info.get("virtualserver_queryclientsonline"));
            Integer maxClients = asInt(info.get("virtualserver_maxclients"));

            Integer players = online == null
                    ? null
                    : Math.max(0, online - (queryClients == null ? 0 : queryClients));
            String name = info.get("virtualserver_name") instanceof String s && !s.isBlank() ? s : null;
            String version = shortVersion(info.get("virtualserver_version"));

            return QueryResult.online(players, maxClients, version, null, name, latency);
        } catch (Exception e) {
            String message = e.getMessage();
            return QueryResult.offline(message == null ? e.getClass().getSimpleName() : message);
        }
    }

    private String send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("WebQuery HTTP " + response.statusCode());
        }
        return response.body();
    }

    private Map<String, Object> firstBody(String json) throws Exception {
        WebQueryResponse response = mapper.readValue(json, WebQueryResponse.class);
        int code = response.status() == null ? -1 : asInt(response.status().get("code"));
        if (code != 0) {
            Object message = response.status() == null ? null : response.status().get("message");
            throw new IllegalStateException("WebQuery error " + code + ": " + message);
        }
        if (response.body() == null || response.body().isEmpty()) {
            throw new IllegalStateException("WebQuery returned an empty body");
        }
        return response.body().getFirst();
    }

    /** Trims the build suffix, e.g. "6.0.0-beta12.1 [Build: 1785239375]" -> "6.0.0-beta12.1". */
    private String shortVersion(Object value) {
        if (!(value instanceof String version) || version.isBlank()) {
            return null;
        }
        int bracket = version.indexOf(" [");
        return bracket > 0 ? version.substring(0, bracket) : version;
    }

    private Integer asInt(Object value) {
        return switch (value) {
            case null -> null;
            case Number number -> number.intValue();
            case String string when !string.isBlank() -> Integer.parseInt(string.trim());
            default -> null;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WebQueryResponse(List<Map<String, Object>> body, Map<String, Object> status) {
    }
}
