package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMinorAdminsJspLayoutTest {

    @Test
    void createFormKeepsRegisterActionInItsOwnFooter() throws Exception {
        String minorAdminsJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/minor-admins.jsp"));
        String componentCss = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(minorAdminsJsp)
            .contains("class=\"minor-admin-create-form\"")
            .contains("class=\"erp-panel-body minor-admin-create-grid\"")
            .contains("class=\"minor-admin-create-actions\" data-aws-action-pin=\"true\"")
            .contains("formaction=\"/admin/saccos/minor-admins/change\"")
            .contains("Change SACCOS Admin")
            .contains("class=\"erp-panel overflow-hidden minor-admin-list-panel\"")
            .doesNotContain("md:col-span-2 xl:col-span-3 flex flex-wrap items-center justify-end gap-3");
        assertThat(componentCss)
            .contains(".minor-admin-create-grid")
            .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
            .contains(".minor-admin-create-actions")
            .contains("justify-content: flex-end")
            .contains(".minor-admin-list-panel")
            .contains("@media (max-width: 1023px)")
            .contains("@media (max-width: 640px)");
    }
}
