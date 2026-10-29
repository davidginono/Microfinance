package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMinorAdminsJspLayoutTest {

    @Test
    void registrationPageResolvesRequiredMessagesInBothLanguages() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/minor-admins.jsp"));
        var messages = new ReloadableResourceBundleMessageSource();
        messages.setBasename(Path.of("src/main/resources/messages").toAbsolutePath().toUri().toString());
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        var tags = Pattern.compile("<spring:message\\s+([^>]+?)/>").matcher(jsp);
        while (tags.find()) {
            String attributes = tags.group(1);
            var code = Pattern.compile("\\bcode=([\"'])(.*?)\\1").matcher(attributes);
            if (!code.find() || code.group(2).contains("${")) continue;
            if (attributes.contains("text=") && !code.group(2).startsWith("admin.minorAdmins.")) continue;
            for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.of("sw")}) {
                assertThat(messages.getMessage(code.group(2), null, locale))
                    .as("Required administrator-page label %s in %s", code.group(2), locale).isNotBlank();
            }
        }
    }

    @Test
    void createFormKeepsRegisterActionInItsOwnFooter() throws Exception {
        String minorAdminsJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/minor-admins.jsp"));
        String componentCss = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(minorAdminsJsp)
            .contains("class=\"minor-admin-create-form\"")
            .contains("class=\"erp-panel-body minor-admin-create-grid\"")
            .contains("class=\"minor-admin-create-actions\" data-aws-action-pin=\"true\"")
            .contains("href=\"/admin/saccos/minor-admins/change\"")
            .contains("Change Institution Admin")
            .contains("class=\"erp-panel overflow-hidden minor-admin-list-panel\"")
            .doesNotContain("md:col-span-2 xl:col-span-3 flex flex-wrap items-center justify-end gap-3");
        assertThat(componentCss)
            .contains(".minor-admin-create-grid")
            .contains("grid-template-columns: minmax(0, 1fr)")
            .contains(".minor-admin-create-actions")
            .contains("justify-content: flex-end")
            .contains(".minor-admin-list-panel")
            .contains("@media (max-width: 640px)");
    }
}
