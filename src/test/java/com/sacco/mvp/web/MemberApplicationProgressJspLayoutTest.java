package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemberApplicationProgressJspLayoutTest {
    @Test
    void progressPathUsesCompactCenterToCenterConnectors() throws Exception {
        String fragment = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/member-application-progress.jspf"));
        String shellStyles = Files.readString(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(fragment).contains("Step ${step.stepNumber}");
        assertThat(shellStyles)
            .contains(".aws-console .member-dashboard-flow-step-wrap")
            .contains("left: 50%;")
            .contains("width: 100% !important;")
            .contains("min-width: 720px;")
            .contains("width: 24px !important;")
            .doesNotContain(".aws-console .member-dashboard-flow-list { min-width: max-content; }");
    }
}
