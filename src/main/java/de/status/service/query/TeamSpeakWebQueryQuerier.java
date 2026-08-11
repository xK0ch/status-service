package de.status.service.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Reads the online client count from a TeamSpeak 6 server through its HTTP
 * WebQuery API. Authenticates the serveradmin account to obtain a session api
 * key, then calls {@code serverinfo} on the virtual server.
 *
 * <p>The exact request shape is documented in the project README; the two HTTP
 * calls are isolated in {@link #login} and {@link #serverInfo} so they are easy
 * to verify against a live server with curl.
 */
@Component
public class TeamSpeakWebQueryQuerier implements ServerQuerier {

    private static final String ADMIN_USER = "serveradmin";
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
        if (server.getPassword() == null || server.getPassword().isBlank()) {
            return QueryResult.offline("No WebQuery password configured");
        }

        long start = System.nanoTime();
        String base = "http://" + server.getHost() + ":" + server.getPort();
        int sid = server.getVirtualServerId() != null ? server.getVirtualServerId() : DEFAULT_VIRTUAL_SERVER_ID;

        try {
            String apiKey = login(base, server.getPassword(), timeout);
            Map<String, Object> info = serverInfo(base, sid, apiKey, timeout);
            long latency = (System.nanoTime() - start) / 1_000_000;

            Integer online = asInt(info.get("virtualserver_clientsonline"));
            Integer queryClients = asInt(info.get("virtualserver_queryclientsonline"));
            Integer maxClients = asInt(info.get("virtualserver_maxclients"));

            // clientsonline includes our own query connection and any other query
            // clients, so subtract them to get the real voice user count.
            Integer players = online == null ? null : online - (queryClients == null ? 0 : queryClients);
            String name = info.get("virtualserver_name") instanceof String s ? s : null;

            return QueryResult.online(players, maxClients, null, null, name, latency);
        } catch (Exception e) {
            String message = e.getMessage();
            return QueryResult.offline(message == null ? e.getClass().getSimpleName() : message);
        }
    }

    private String login(String base, String password, Duration timeout) throws Exception {
        String url = base + "/login"
                + "?client_login_name=" + ADMIN_USER
                + "&client_login_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        Map<String, Object> body = firstBody(send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build()));
        Object apiKey = body.get("apikey");
        if (apiKey == null) {
            throw new IllegalStateException("WebQuery login returned no apikey");
        }
        return apiKey.toString();
    }

    private Map<String, Object> serverInfo(String base, int sid, String apiKey, Duration timeout) throws Exception {
        String url = base + "/" + sid + "/serverinfo";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("x-api-key", apiKey)
                .GET()
                .build();
        return firstBody(send(request));
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
