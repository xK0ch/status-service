package de.status.service.query;

import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Implements the Steam A2S_INFO query over UDP, including the challenge/response
 * handshake that Valve added in 2020. Works for Palworld, Enshrouded and other
 * Source-engine style servers.
 */
@Component
public class SteamA2sQuerier implements ServerQuerier {

    private static final byte[] REQUEST_HEADER = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte A2S_INFO = 0x54;
    private static final byte RESPONSE_INFO = 0x49;
    private static final byte RESPONSE_CHALLENGE = 0x41;
    private static final String PAYLOAD = "Source Engine Query";

    @Override
    public boolean supports(GameType game) {
        return game == GameType.STEAM_A2S;
    }

    @Override
    public QueryResult query(ServerConfig server, Duration timeout) {
        long start = System.nanoTime();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout((int) timeout.toMillis());
            InetAddress address = InetAddress.getByName(server.getHost());

            send(socket, address, server.getPort(), buildRequest(null));
            ByteBuffer response = receive(socket);

            response.getInt();          // header 0xFFFFFFFF
            byte type = response.get();
            if (type == RESPONSE_CHALLENGE) {
                byte[] challenge = new byte[4];
                response.get(challenge);
                send(socket, address, server.getPort(), buildRequest(challenge));
                response = receive(socket);
                response.getInt();      // header again
                type = response.get();
            }
            if (type != RESPONSE_INFO) {
                return QueryResult.offline("Unexpected A2S response 0x" + Integer.toHexString(type & 0xFF));
            }

            response.get();             // protocol version byte
            String name = readCString(response);
            String map = readCString(response);
            readCString(response);      // folder
            readCString(response);      // game
            response.getShort();        // steam app id
            int players = response.get() & 0xFF;
            int maxPlayers = response.get() & 0xFF;
            long latency = (System.nanoTime() - start) / 1_000_000;

            return QueryResult.online(players, maxPlayers, null, blankToNull(map), blankToNull(name), latency);
        } catch (Exception e) {
            String message = e.getMessage();
            return QueryResult.offline(message == null ? e.getClass().getSimpleName() : message);
        }
    }

    private byte[] buildRequest(byte[] challenge) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.writeBytes(REQUEST_HEADER);
        buffer.write(A2S_INFO);
        buffer.writeBytes(PAYLOAD.getBytes(StandardCharsets.US_ASCII));
        buffer.write(0x00);
        if (challenge != null) {
            buffer.writeBytes(challenge);
        }
        return buffer.toByteArray();
    }

    private void send(DatagramSocket socket, InetAddress address, int port, byte[] payload) throws java.io.IOException {
        socket.send(new DatagramPacket(payload, payload.length, address, port));
    }

    private ByteBuffer receive(DatagramSocket socket) throws java.io.IOException {
        byte[] buffer = new byte[1400];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return ByteBuffer.wrap(buffer, 0, packet.getLength()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private String readCString(ByteBuffer buffer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte b;
        while (buffer.hasRemaining() && (b = buffer.get()) != 0) {
            out.write(b);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
