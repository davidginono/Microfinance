package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveDateFilterViewContractTest {

    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");

    @Test
    void everyArchiveViewProvidesResponsiveFromAndToDateControls() throws Exception {
        for (String relativePath : List.of(
            "manager/archive.jsp",
            "accountant/archive.jsp",
            "disbursement/archive.jsp",
            "board/assigned.jsp",
            "admin/support-archive.jsp",
            "app/support-archive.jsp"
        )) {
            String jsp = Files.readString(JSP_ROOT.resolve(relativePath));

            assertThat(jsp)
                .as(relativePath)
                .contains("data-aws-filter-toolbar")
                .contains("name=\"fromDate\"")
                .contains("name=\"toDate\"");
        }

        String memberArchives = Files.readString(JSP_ROOT.resolve("app/archives.jsp"));
        assertThat(occurrences(memberArchives, "type=\"date\" name=\"fromDate\"")).isEqualTo(2);
        assertThat(occurrences(memberArchives, "type=\"date\" name=\"toDate\"")).isEqualTo(2);
    }

    @Test
    void archivePaginationPreservesTheSelectedRange() throws Exception {
        for (String relativePath : List.of(
            "manager/archive.jsp",
            "accountant/archive.jsp",
            "disbursement/archive.jsp",
            "board/assigned.jsp",
            "admin/support-archive.jsp",
            "app/support-archive.jsp",
            "app/archives.jsp"
        )) {
            String jsp = Files.readString(JSP_ROOT.resolve(relativePath));

            assertThat(jsp)
                .as(relativePath)
                .contains("<c:param name=\"fromDate\"")
                .contains("<c:param name=\"toDate\"");
        }
    }

    private int occurrences(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
