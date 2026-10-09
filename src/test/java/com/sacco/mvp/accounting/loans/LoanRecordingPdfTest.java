package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.Document;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class LoanRecordingPdfTest {
    @Test void rendersCompletePaginatedDocumentWithEmbeddedMultilingualFont() throws Exception {
        var renderer = new LoanRecordingPdfService(new ReportExportLimiter(1));
        var lines = IntStream.rangeClosed(1,150)
            .mapToObj(n -> "Malipo / payment " + n + " | TZS 1,000.01 | Rejea ya malipo — taarifa ya mteja é").toList();
        byte[] result = renderer.render(new Document("Taarifa ya mkopo", "Taasisi ya majaribio", "Tawi la kwanza", "statement.pdf", lines));
        try (var pdf = Loader.loadPDF(result)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            assertThat(new PDFTextStripper().getText(pdf)).contains("payment 1", "payment 150", "Taasisi ya majaribio", "taarifa ya mteja é");
            for (var page : pdf.getPages()) assertThat(page.getResources().getFontNames()).isNotEmpty();
        }
    }
}
