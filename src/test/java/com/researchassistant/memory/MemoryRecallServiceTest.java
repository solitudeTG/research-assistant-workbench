package com.researchassistant.memory;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryRecallServiceTest {

    @Mock
    private MemoryEntryRepository memoryEntryRepository;

    @InjectMocks
    private MemoryRecallService memoryRecallService;

    @Test
    void recallRanksRecentKeywordMatchingEntriesFirst() {
        MemoryEntry recent = new MemoryEntry(
                1L, 7L, "COMPACTION", "Satellite selection",
                "Recent satellite selection summary",
                List.of("Compared online selection"),
                List.of("How to validate?"),
                List.of("satellite", "selection"),
                1L, 4L,
                OffsetDateTime.now().minusHours(2),
                OffsetDateTime.now().minusHours(2)
        );
        MemoryEntry old = new MemoryEntry(
                2L, 2L, "COMPACTION", "Beam planning",
                "Older planning note",
                List.of("Investigated beam planning"),
                List.of(),
                List.of("beam", "planning"),
                5L, 8L,
                OffsetDateTime.now().minusDays(5),
                OffsetDateTime.now().minusDays(5)
        );
        when(memoryEntryRepository.search("继续卫星选择研究", 6)).thenReturn(List.of(old, recent));

        MemoryRecallResult result = memoryRecallService.recall(7L, "继续卫星选择研究", 3);

        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).entry().id()).isEqualTo(1L);
    }
}
