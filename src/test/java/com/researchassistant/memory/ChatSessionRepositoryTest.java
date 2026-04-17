package com.researchassistant.memory;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void findOrCreateUsesGeneratedIdWhenDriverReturnsMultipleColumns() {
        when(jdbcTemplate.query(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<Optional<WorkingMemory>>>any(),
                ArgumentMatchers.eq("new-session")))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1, KeyHolder.class);
            ((GeneratedKeyHolder) keyHolder).getKeyList().add(Map.of(
                    "id", 11L,
                    "session_key", "new-session"
            ));
            return 1;
        }).when(jdbcTemplate).update(
                ArgumentMatchers.any(org.springframework.jdbc.core.PreparedStatementCreator.class),
                ArgumentMatchers.any(KeyHolder.class)
        );

        ChatSessionRepository repository = new ChatSessionRepository(jdbcTemplate);

        WorkingMemory memory = repository.findOrCreate("new-session");

        assertThat(memory.sessionId()).isEqualTo(11L);
        assertThat(memory.sessionKey()).isEqualTo("new-session");
        assertThat(memory.messageCount()).isZero();
    }
}
