package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileUploadControlViewContractTest {
    @Test
    void profileAndSaccoLogoUploadsUseSharedAwsFilePickerControls() throws Exception {
        String profile = read(Path.of("src/main/webapp/WEB-INF/jsp/profile.jsp"));
        String saccoRegistry = read(Path.of("src/main/webapp/WEB-INF/jsp/admin/sacco-registry.jsp"));
        String shellScript = read(Path.of("src/main/resources/static/js/shell.js"));
        String componentCss = read(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(profile)
            .contains("class=\"profile-photo-dropzone aws-file-dropzone\"")
            .contains("class=\"aws-file-picker-control profile-photo-picker\"")
            .contains("data-breadcrumb-root-href=\"${profileWorkspaceHome}\"")
            .contains("class=\"profile-page-layout\"")
            .contains("href=\"${profileWorkspaceHome}\"")
            .doesNotContain("javascript:history.back()")
            .contains("data-file-picker-input")
            .contains("data-file-picker-name")
            .contains("input.dispatchEvent(new Event('change', { bubbles: true }))")
            .doesNotContain("class=\"mt-4 block w-full rounded-md border");

        assertThat(countOccurrences(saccoRegistry, "class=\"sacco-logo-upload-card\" data-logo-upload-card")).isEqualTo(3);
        assertThat(countOccurrences(saccoRegistry, "data-file-picker-input")).isEqualTo(3);
        assertThat(saccoRegistry)
            .contains("class=\"aws-file-picker\"")
            .contains("class=\"aws-file-paste-target\"")
            .contains("class=\"aws-file-paste-shortcut\"")
            .contains("fileInput.dispatchEvent(new Event('change', { bubbles: true }))")
            .contains("document.querySelectorAll('[data-logo-upload-card]')")
            .doesNotContain("file:mr-3")
            .doesNotContain(".app-modal-body .rounded.border.border-slate-200.bg-slate-50.p-4");

        assertThat(shellScript)
            .contains("initAwsFilePickers(document)")
            .contains("data-file-picker-input")
            .contains("refreshAwsFilePicker")
            .contains("data-breadcrumb-root-href")
            .contains("index === 0 && explicitRootHref")
            .contains("window.SaccosFilePickers");

        assertThat(componentCss)
            .contains(".profile-page-layout")
            .contains("grid-template-columns: minmax(13rem, 15rem) minmax(0, 1fr)")
            .contains(".profile-identity-panel dl")
            .contains("@media (max-width: 767px)");

        assertThat(componentCss)
            .contains(".aws-file-picker-control")
            .contains(".aws-file-picker-input")
            .contains(".aws-file-dropzone")
            .contains(".sacco-logo-upload-card")
            .contains(".sacco-logo-preview-shell")
            .contains(".aws-file-paste-target.is-ready")
            .contains("border-radius: 2px");
    }

    @Test
    void attachmentDropzonesKeepHiddenInputsAndAwsDragStyling() throws Exception {
        String loanApplication = read(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));
        String managerDetail = read(Path.of("src/main/webapp/WEB-INF/jsp/manager/detail.jsp"));
        String attachmentFragment = read(Path.of("src/main/webapp/WEB-INF/jsp/fragments/attachment-dropzone.jspf"));
        String componentCss = read(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(loanApplication)
            .contains("class=\"attachment-dropzone\" data-attachment-dropzone")
            .contains("class=\"attachment-dropzone-input\" data-attachment-input")
            .contains("existingApplicationAttachments")
            .contains("Existing Attachments")
            .contains("/documents/loan-applications/${formValues['applicationId']}/attachments/${file.id}")
            .contains("data-download-action=\"true\"><spring:message code=\"common.download\"")
            .contains("data-existing-attachment-files=\"${fn:escapeXml(existingApplicationAttachmentNames)}\"");
        assertThat(managerDetail)
            .contains("class=\"attachment-dropzone\" data-attachment-dropzone")
            .contains("class=\"attachment-dropzone-input\"")
            .contains("data-attachment-input");
        assertThat(attachmentFragment)
            .contains("input.files = event.dataTransfer.files")
            .contains("input.dispatchEvent(new Event(\"change\", { bubbles: true }))")
            .contains("refreshDropzone(dropzone)");
        assertThat(componentCss)
            .contains(".attachment-dropzone:focus-within")
            .contains("border-radius: 2px")
            .contains("background: #f1faff")
            .contains(".attachment-dropzone-action");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static int countOccurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
