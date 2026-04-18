package com.researchassistant.ingest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DocumentStructureAnalyzer {

    private static final String HEADING_KEYWORDS =
            "abstract|introduction|background|method|methods|approach|experiments?|results?|discussion|"
                    + "conclusion|related work|references|contribution|contributions|"
                    + "\u6458\u8981|\u5f15\u8a00|\u65b9\u6cd5|\u5b9e\u9a8c|\u7ed3\u679c|\u7ed3\u8bba|\u8d21\u732e";

    private static final Pattern NUMBERED_HEADING_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)*\\s+.+$");

    private static final Pattern KEYWORD_HEADING_PATTERN = Pattern.compile(
            "^(?:" + HEADING_KEYWORDS + ")(?:\\s*[:\\uFF1A-].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+");

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "that", "with", "this", "from", "into", "using", "our", "are", "was", "were",
            "through", "their", "they", "them", "paper", "study", "assistant", "assistants", "research", "abstract",
            "introduction", "method", "methods", "results", "conclusion", "contributions", "contribution",
            "framework", "guided", "horizon", "long", "propose", "proposed", "main", "based", "more",
            "a", "an", "to", "of", "in", "on", "by", "we"
    );

    public DocumentAnalysisDraft analyze(String title, String rawText) {
        List<String> lines = normalizedLines(rawText);
        List<String> outline = extractOutline(lines);
        String abstractText = extractAbstract(lines);
        List<String> paragraphs = paragraphs(rawText);

        List<String> methods = collectSentences(paragraphs,
                "method", "methods", "approach", "framework", "algorithm", "model", "pipeline",
                "\u65b9\u6cd5", "\u6846\u67b6", "\u6a21\u578b", "\u7b97\u6cd5", "\u6d41\u7a0b");
        List<String> contributions = collectSentences(paragraphs,
                "contribution", "contributions", "we show", "we demonstrate", "our main",
                "\u8d21\u732e", "\u4e3b\u8981\u5de5\u4f5c", "\u7ed3\u679c\u8868\u660e");
        List<String> keywords = extractKeywords(title, abstractText, paragraphs, methods, contributions);
        String summary = buildSummary(abstractText, paragraphs);

        return new DocumentAnalysisDraft(abstractText, summary, methods, contributions, keywords, outline);
    }

    private List<String> normalizedLines(String rawText) {
        return rawText == null ? List.of() : rawText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private List<String> extractOutline(List<String> lines) {
        return lines.stream()
                .filter(this::isHeading)
                .limit(12)
                .toList();
    }

    private String extractAbstract(List<String> lines) {
        int abstractIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).toLowerCase(Locale.ROOT);
            if (line.equals("abstract") || line.startsWith("abstract ") || line.equals("\u6458\u8981")) {
                abstractIndex = i;
                break;
            }
        }

        if (abstractIndex >= 0) {
            List<String> collected = new ArrayList<>();
            for (int i = abstractIndex + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isHeading(line) && !line.equalsIgnoreCase("abstract")) {
                    break;
                }
                collected.add(line);
                if (String.join(" ", collected).length() >= 900) {
                    break;
                }
            }
            if (!collected.isEmpty()) {
                return String.join(" ", collected).trim();
            }
        }

        return lines.stream().limit(4).collect(Collectors.joining(" "));
    }

    private boolean isHeading(String line) {
        String normalized = line == null ? "" : line.trim();
        if (normalized.isBlank() || normalized.length() > 80) {
            return false;
        }
        if (normalized.endsWith(".") || normalized.endsWith("\u3002")) {
            return false;
        }
        if (NUMBERED_HEADING_PATTERN.matcher(normalized).matches()) {
            String tail = normalized.replaceFirst("^\\d+(?:\\.\\d+)*\\s+", "");
            return KEYWORD_HEADING_PATTERN.matcher(tail).matches();
        }
        return KEYWORD_HEADING_PATTERN.matcher(normalized).matches();
    }

    private List<String> paragraphs(String rawText) {
        return rawText == null ? List.of() : Pattern.compile("(?:\\r?\\n){2,}")
                .splitAsStream(rawText)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private List<String> collectSentences(List<String> paragraphs, String... terms) {
        List<String> matches = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String normalized = paragraph.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (normalized.contains(term.toLowerCase(Locale.ROOT))) {
                    matches.add(paragraph.replaceAll("\\s+", " ").trim());
                    break;
                }
            }
            if (matches.size() >= 4) {
                break;
            }
        }
        return matches;
    }

    private List<String> extractKeywords(String title,
                                         String abstractText,
                                         List<String> paragraphs,
                                         List<String> methods,
                                         List<String> contributions) {
        String globalText = String.join(" ", List.of(
                title == null ? "" : title,
                abstractText == null ? "" : abstractText,
                paragraphs.stream().limit(4).collect(Collectors.joining(" "))
        ));
        String focusedText = String.join(" ", List.of(
                String.join(" ", methods),
                String.join(" ", contributions)
        ));

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(topKeywords(focusedText, 4));
        merged.addAll(topKeywords(globalText, 8));

        return merged.stream().limit(8).toList();
    }

    private List<String> topKeywords(String text, int limit) {
        Map<String, Long> frequencies = TOKEN_SPLITTER.splitAsStream(text.toLowerCase(Locale.ROOT))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()));

        return frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String buildSummary(String abstractText, List<String> paragraphs) {
        if (abstractText != null && !abstractText.isBlank()) {
            return truncate(abstractText, 600);
        }
        if (!paragraphs.isEmpty()) {
            return truncate(paragraphs.get(0).replaceAll("\\s+", " ").trim(), 600);
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1).trim() + "...";
    }
}
