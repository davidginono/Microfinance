package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class SelectEnhancerJspLayoutTest {
    @Test void sharedShellLoadsTheExternalSelectAssetsAfterLegacyStyles() throws Exception {
        String fragment=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/select-enhancer.jspf"));
        String header=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/header.jspf"));
        assertThat(fragment).contains("/css/select-enhancer.css", "/js/select-enhancer.js", "defer");
        assertThat(header.indexOf("select-enhancer.jspf")).isGreaterThan(header.indexOf("/css/console-components.css"));
        assertThat(header.indexOf("select-enhancer.jspf")).isGreaterThan(header.indexOf("/css/shell.css"));
    }

    @Test void bodyMountedMenusUseTheirCurrentStylesWithoutClippingOptions() throws Exception {
        String script=Files.readString(Path.of("src/main/resources/static/js/select-enhancer.js"));
        String styles=Files.readString(Path.of("src/main/resources/static/css/select-enhancer.css"));
        assertThat(script).contains("document.body.appendChild(menu)", "window.visualViewport", "state.button.getBoundingClientRect()");
        assertThat(styles).contains("position:fixed", ".neo-select-menu[hidden]", ".neo-select-options", "overflow:auto", "white-space:normal", "overflow-wrap:anywhere");
    }
}
