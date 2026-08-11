package de.status.service.query;

import de.status.service.config.StatusProperties.ServerConfig;
import de.status.service.status.GameType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TeamSpeakWebQueryQuerierTest {

    private final TeamSpeakWebQueryQuerier querier = new TeamSpeakWebQueryQuerier(JsonMapper.builder().build());

    @Test
    void supportsOnlyTheWebQueryGameType() {
        assertThat(querier.supports(GameType.TEAMSPEAK_WEBQUERY)).isTrue();
        assertThat(querier.supports(GameType.TEAMSPEAK)).isFalse();
    }

    @Test
    void returnsOfflineWhenNoApiKeyConfigured() {
        ServerConfig server = new ServerConfig();
        server.setGame(GameType.TEAMSPEAK_WEBQUERY);
        server.setHost("teamspeak-server");
        server.setPort(10080);

        QueryResult result = querier.query(server, Duration.ofSeconds(1));

        assertThat(result.online()).isFalse();
        assertThat(result.error()).contains("api key");
    }
}
