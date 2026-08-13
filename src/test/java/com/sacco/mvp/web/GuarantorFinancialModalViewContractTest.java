package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuarantorFinancialModalViewContractTest {
    private static final Path MODAL_FRAGMENT = Path.of(
        "src/main/webapp/WEB-INF/jsp/fragments/guarantor-financial-fetch.jspf"
    );

    @Test
    void financialStatusModalUsesViewportOverlayAndAccessibleDialogPanel() throws Exception {
        String fragment = Files.readString(MODAL_FRAGMENT);

        assertThat(fragment)
            .contains("class=\"app-modal-overlay\" aria-hidden=\"true\"")
            .contains("class=\"app-modal-panel app-modal-panel--compact\" role=\"dialog\" aria-modal=\"true\"")
            .contains("document.body.appendChild(modal)")
            .contains("event.target === modal")
            .doesNotContain("class=\"app-modal-overlay\" role=\"dialog\"");
    }
}
