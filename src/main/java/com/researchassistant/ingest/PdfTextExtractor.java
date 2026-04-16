package com.researchassistant.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    public String extract(Path path) throws IOException {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        String text;

        if (fileName.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(path.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(document);
            }
        } else {
            text = Files.readString(path);
        }

        return normalizeNullCharacters(text);
    }

    private String normalizeNullCharacters(String text) {
        return text == null ? "" : text.replace("\u0000", "");
    }
}
