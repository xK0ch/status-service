package de.status.service.query;

import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryDispatcherTest {

    private final Duration timeout = Duration.ofSeconds(1);

    @Test
    void routesToTheQuerierThatSupportsTheGameType() {
        QueryDispatcher dispatcher = new QueryDispatcher(List.of(
                fixed(GameType.MINECRAFT, QueryResult.online(3, 10, "1.21", null, "hi", 12)),
                fixed(GameType.STEAM_A2S, QueryResult.offline("nope"))));

        QueryResult result = dispatcher.query(server(GameType.MINECRAFT), timeout);

        assertThat(result.online()).isTrue();
        assertThat(result.players()).isEqualTo(3);
    }

    @Test
    void returnsOfflineWhenNoQuerierSupportsTheGameType() {
        QueryDispatcher dispatcher = new QueryDispatcher(List.of());

        QueryResult result = dispatcher.query(server(GameType.TCP), timeout);

        assertThat(result.online()).isFalse();
        assertThat(result.error()).contains("No querier");
    }

    @Test
    void turnsAnUnexpectedExceptionIntoAnOfflineResult() {
        QueryDispatcher dispatcher = new QueryDispatcher(List.of(new ServerQuerier() {
            @Override
            public boolean supports(GameType game) {
                return true;
            }

            @Override
            public QueryResult query(ServerConfig server, Duration timeout) {
                throw new IllegalStateException("boom");
            }
        }));

        QueryResult result = dispatcher.query(server(GameType.TCP), timeout);

        assertThat(result.online()).isFalse();
        assertThat(result.error()).isEqualTo("boom");
    }

    private ServerQuerier fixed(GameType type, QueryResult result) {
        return new ServerQuerier() {
            @Override
            public boolean supports(GameType game) {
                return game == type;
            }

            @Override
            public QueryResult query(ServerConfig server, Duration timeout) {
                return result;
            }
        };
    }

    private ServerConfig server(GameType type) {
        ServerConfig config = new ServerConfig();
        config.setId("test");
        config.setName("Test");
        config.setGame(type);
        config.setHost("localhost");
        config.setPort(1234);
        return config;
    }
}
