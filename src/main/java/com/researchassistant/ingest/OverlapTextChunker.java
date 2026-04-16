package com.researchassistant.ingest;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OverlapTextChunker {

    static final int MAX_CHARS = 1200;
    static final int OVERLAP_CHARS = 200;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHARS, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            int nextStart = Math.max(end - OVERLAP_CHARS, start + 1);
            start = nextStart;
        }

        return chunks;
    }
}
