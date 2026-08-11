package de.status.service.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Implements the modern (1.7+) Minecraft Server List Ping: a handshake plus a
 * status request over TCP, answered with a JSON blob describing the server.
 */
@Component
public class MinecraftQuerier implements ServerQuerier {

    private final ObjectMapper mapper;

    public MinecraftQuerier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(GameType game) {
        return game == GameType.MINECRAFT;
    }

    @Override
    public QueryResult query(ServerConfig server, Duration timeout) {
        long start = System.nanoTime();
        int timeoutMs = (int) timeout.toMillis();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            writePacket(out, buildHandshake(server));
            writePacket(out, statusRequest());

            readVarInt(in); // total packet length, not needed
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                return QueryResult.offline("Unexpected packet id " + packetId);
            }
            int jsonLength = readVarInt(in);
            byte[] jsonBytes = in.readNBytes(jsonLength);
            long latency = elapsedMs(start);

            SlpResponse response = mapper.readValue(new String(jsonBytes, StandardCharsets.UTF_8), SlpResponse.class);
            Integer online = response.players() == null ? null : response.players().online();
            Integer max = response.players() == null ? null : response.players().max();
            String version = response.version() == null ? null : response.version().name();
            String motd = flattenMotd(response.description());

            return QueryResult.online(online, max, version, null, motd, latency);
        } catch (Exception e) {
            return QueryResult.offline(describe(e));
        }
    }

    private byte[] buildHandshake(ServerConfig server) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(buffer);
        writeVarInt(data, 0x00);                 // packet id: handshake
        writeVarInt(data, -1);                   // protocol version: -1 = let the server decide
        byte[] host = server.getHost().getBytes(StandardCharsets.UTF_8);
        writeVarInt(data, host.length);
        data.write(host);
        data.writeShort(server.getPort());
        writeVarInt(data, 1);                     // next state: 1 = status
        return buffer.toByteArray();
    }

    private byte[] statusRequest() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writeVarInt(new DataOutputStream(buffer), 0x00);
        return buffer.toByteArray();
    }

    private void writePacket(DataOutputStream out, byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
        out.flush();
    }

    /** Best-effort flattening of the MOTD, which may be plain text or a chat component tree. */
    private String flattenMotd(Object description) {
        if (description == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        appendComponent(description, text);
        String result = clean(text.toString());
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private void appendComponent(Object node, StringBuilder text) {
        switch (node) {
            case String s -> text.append(s);
            case Map<?, ?> map -> {
                Object self = map.get("text");
                if (self instanceof String s) {
                    text.append(s);
                }
                if (map.get("extra") instanceof List<?> extra) {
                    extra.forEach(child -> appendComponent(child, text));
                }
            }
            default -> { /* numbers, booleans, null: nothing useful for a MOTD */ }
        }
    }

    private String clean(String value) {
        // Strip Minecraft section-sign colour codes and collapse whitespace.
        return value.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String describe(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + message;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int result = 0;
        int position = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) {
                throw new IOException("Connection closed while reading VarInt");
            }
            result |= (read & 0x7F) << (7 * position);
            position++;
            if (position > 5) {
                throw new IOException("VarInt is too big");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SlpResponse(Version version, Players players, Object description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Version(String name, Integer protocol) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Players(Integer online, Integer max) {
    }
}
