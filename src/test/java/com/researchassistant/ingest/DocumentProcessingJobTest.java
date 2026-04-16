package com.researchassistant.ingest;

import com.researchassistant.ingest.model.DocumentStatus;
import com.researchassistant.support.PostgresIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.storage.root=target/test-storage")
class DocumentProcessingJobTest extends PostgresIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentProcessingJob documentProcessingJob;

    @Test
    void processDocumentExtractsTextAndIndexesChunks() throws Exception {
        Path pdfPath = Files.createTempFile("document-processing-", ".pdf");
        createPdf(pdfPath, "Async pipeline test");

        long documentId = documentRepository.insert("sample.pdf", "sample.pdf", pdfPath.toAbsolutePath().toString());

        documentProcessingJob.processDocument(documentId).join();

        assertThat(documentRepository.findById(documentId)).isPresent();
        assertThat(documentRepository.findById(documentId).orElseThrow().status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(documentChunkRepository.findByDocumentId(documentId)).isNotEmpty();
    }

    @Test
    void processDocumentIndexesPlainTextFiles() throws Exception {
        Path textPath = Files.createTempFile("document-processing-", ".txt");
        Files.writeString(textPath, "Plain text upload for Task 9.");

        long documentId = documentRepository.insert("paper.txt", "paper.txt", textPath.toAbsolutePath().toString());

        documentProcessingJob.processDocument(documentId).join();

        assertThat(documentRepository.findById(documentId)).isPresent();
        assertThat(documentRepository.findById(documentId).orElseThrow().status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(documentChunkRepository.findByDocumentId(documentId)).isNotEmpty();
    }

    private void createPdf(Path pdfPath, String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(pdfPath.toFile());
        }
    }
}
