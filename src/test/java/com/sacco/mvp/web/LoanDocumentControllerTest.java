package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoanDocumentControllerTest {

    @Test
    void attachmentContentDispositionEncodesUnsafeUploadFileNames() {
        String header = LoanDocumentController.attachmentContentDisposition("receipt \"final\"\r\n2026.pdf", false);

        assertThat(header).startsWith("attachment;");
        assertThat(header).doesNotContain("\r", "\n");
        assertThat(header).contains("filename*=");
    }

    @Test
    void attachmentContentDispositionSupportsInlinePreviewHeaders() {
        String header = LoanDocumentController.attachmentContentDisposition("proof.pdf", true);

        assertThat(header).startsWith("inline;");
        assertThat(header).contains("proof.pdf");
    }
}
