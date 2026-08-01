package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemberApplicationProgressJspLayoutTest {
    @Test
    void progressFragmentSupportsRejectedStageRendering() throws Exception {
        String fragment = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/member-application-progress.jspf"));
        String styles = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(fragment).contains("${step.metaClasses}");
        assertThat(styles).contains(".member-dashboard-flow-node--rejected");
        assertThat(styles).contains(".member-dashboard-flow-node--closed");
        assertThat(styles).contains("content: \"\\00d7\"");
        assertThat(styles).contains(".member-dashboard-flow-text--rejected");
        assertThat(styles).contains(".member-dashboard-flow-text--closed");
        assertThat(styles).contains(".member-dashboard-flow-dot--closed");
        assertThat(styles).contains(".member-dashboard-mobile-meta.member-dashboard-flow-meta--rejected");
    }
}
