package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SelectEnhancerJspLayoutTest {
    @Test
    void enhancedSelectMenusStayFieldBoundAndMatchControlWidth() throws Exception {
        String fragment = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/select-enhancer.jspf"));
        String styles = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));
        String shellStyles = Files.readString(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(fragment)
            .contains("wrapper.appendChild(menu)")
            .contains("makeRoomBelow(state)")
            .contains("scrollIntoView({ block: 'center'")
            .contains("const maxVisibleRows = 6")
            .contains("const compactRowHeight = 30")
            .contains("state.menu.style.overflowY = state.menu.scrollHeight > menuHeight + 1 ? 'auto' : 'hidden'")
            .doesNotContain("document.body.appendChild(menu)")
            .doesNotContain("--neo-select-menu-width")
            .doesNotContain("Math.max(rect.width, 220)")
            .doesNotContain("openUpward")
            .doesNotContain("availableAbove")
            .doesNotContain("is-open-upward");
        assertThat(styles)
            .contains(".neo-select-menu")
            .contains("position: absolute")
            .contains("box-sizing: border-box")
            .contains("top: calc(100% - 1px)")
            .contains("width: 100%")
            .contains("min-width: 100%")
            .contains("max-width: 100%")
            .contains("max-height: 12rem")
            .contains("z-index: 180")
            .doesNotContain("width: min(var(--neo-select-menu-width")
            .doesNotContain(".neo-select-menu.is-open-upward");
        assertThat(shellStyles)
            .contains(".aws-console .aws-filter-toolbar:has(.neo-select--open)")
            .contains(".aws-console .erp-table-wrap:has(.neo-select--open)")
            .contains("overflow: visible !important")
            .contains(".aws-console .erp-table-wrap > .aws-filter-toolbar:has(.neo-select--open)")
            .contains("z-index: 60");
    }

    @Test
    void enhancedSelectsUseNormalAwsDropdownStylingWithoutTruncatedLabels() throws Exception {
        String styles = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));
        String shellStyles = Files.readString(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(styles)
            .contains(".neo-select-button-text")
            .contains("overflow: visible")
            .contains("text-overflow: clip")
            .contains("scrollbar-width: thin")
            .contains(".neo-select-option.is-selected")
            .contains("background: #f2f3f3")
            .contains("background: #eaeded")
            .contains("color: var(--aws-ink, #16191f)")
            .contains("box-shadow: inset 3px 0 0 var(--aws-line-dark, #879596)")
            .contains("min-height: 30px")
            .contains("padding: 5px 8px")
            .contains("line-height: 18px")
            .doesNotContain("background: #43699d")
            .doesNotContain("background: #3d6191");
        assertThat(shellStyles)
            .contains(".aws-console .aws-filter-toolbar .neo-select-button")
            .contains("height: var(--sacco-control-height) !important")
            .contains(".aws-console .neo-select-option")
            .contains("min-height: 30px !important")
            .contains("font-size: 13px !important")
            .contains("font-weight: 400 !important");
    }
}
