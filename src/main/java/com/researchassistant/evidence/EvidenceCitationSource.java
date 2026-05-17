package com.researchassistant.evidence;

import java.util.Objects;

public record EvidenceCitationSource(
        String sourceType,
        String text,
        Long documentId,
        Long chunkId,
        Integer chunkIndex,
        String title,
        String url,
        String provider,
        Integer rank,
        double score
) {

    public EvidenceCitationSource {
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        text = Objects.requireNonNull(text, "text");
    }

    public static EvidenceCitationSource paper(
            String text,
            long documentId,
            long chunkId,
            int chunkIndex,
            double score
    ) {
        return new EvidenceCitationSource(
                "paper",
                text,
                documentId,
                chunkId,
                chunkIndex,
                null,
                null,
                null,
                null,
                score
        );
    }

    public static EvidenceCitationSource web(
            String text,
            String title,
            String url,
            String provider,
            int rank,
            double score
    ) {
        return new EvidenceCitationSource(
                "web",
                text,
                null,
                null,
                null,
                title,
                url,
                provider,
                rank,
                score
        );
    }
}
