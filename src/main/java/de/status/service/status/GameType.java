package de.status.service.status;

/**
 * The query protocol used to reach a server. Relaxed binding maps YAML values
 * like {@code steam_a2s} to {@link #STEAM_A2S}.
 */
public enum GameType {

    /** Minecraft Server List Ping over TCP. */
    MINECRAFT,

    /** Steam A2S_INFO over UDP (Palworld, Enshrouded, most Source-based servers). */
    STEAM_A2S,

    /** TeamSpeak: TCP liveness check on the file-transfer port. */
    TEAMSPEAK,

    /** TeamSpeak 6 HTTP WebQuery: reports the online client count. */
    TEAMSPEAK_WEBQUERY,

    /** Generic TCP connect check. */
    TCP
}
