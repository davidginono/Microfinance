package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SelectEnhancerJspLayoutTest {
    @Test
    void enhancedSelectMenusAlwaysOpenBelowTheirControls() throws Exception {
        String fragment = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/select-enhancer.jspf"));
        String styles = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(fragment)
            .contains("const top = rect.bottom - 1;")
            .contains("makeRoomBelow(state)")
            .contains("scrollIntoView({ block: 'center'")
            .doesNotContain("openUpward")
            .doesNotContain("availableAbove")
            .doesNotContain("is-open-upward");
        assertThat(styles).doesNotContain(".neo-select-menu.is-open-upward");
    }
}
