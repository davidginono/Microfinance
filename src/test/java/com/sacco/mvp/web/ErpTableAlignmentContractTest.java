package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErpTableAlignmentContractTest {
    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");
    private static final Pattern TABLE_TAG = Pattern.compile("<table\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile(
        "class\\s*=\\s*([\\\"'])(.*?)\\1",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Test
    void sharedTableStylesAlignHeadersWithBodyCellsAndPreserveExplicitAlignment() throws Exception {
        String shellCss = Files.readString(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(shellCss)
            .containsPattern("(?s)\\.erp-table th \\{.*?text-align: left !important;.*?vertical-align: top;")
            .contains(".erp-table th.text-left,")
            .contains(".erp-table th.text-center,")
            .contains(".erp-table th.text-right,")
            .contains("font-variant-numeric: tabular-nums;");
    }

    @Test
    void laptopTableColumnLabelsStayOnOneLineInsideScrollableTables() throws Exception {
        String shellCss = Files.readString(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(shellCss)
            .contains("""
                @media (min-width: 768px) {
                    .erp-table thead th {
                        white-space: nowrap !important;
                        overflow-wrap: normal !important;
                        word-break: normal !important;
                    }
                }
                """)
            .containsPattern("(?s)\\.erp-table-scroll \\{.*?overflow-x: auto;");
    }

    @Test
    void atomicTableValuesStayOnOneLineWhileNarrativeCellsCanOptIntoWrapping() throws Exception {
        String shellCss = Files.readString(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(shellCss)
            .contains("""
                .erp-table tbody td:not([colspan]),
                .erp-table tfoot td:not([colspan]) {
                    white-space: nowrap;
                    overflow-wrap: normal;
                    word-break: normal;
                }
                """)
            .contains(".erp-table tbody td.erp-table-cell-wrap,")
            .contains(".erp-table tbody td[data-table-wrap=\"true\"]")
            .containsPattern("(?s)\\.erp-table-scroll \\{.*?overflow-x: auto;");
    }

    @Test
    void everyJspTableUsesTheSharedErpTableAndDedicatedScrollContract() throws Exception {
        try (Stream<Path> paths = Files.walk(JSP_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile)
                .filter(this::isJspView)
                .toList()) {
                String view = Files.readString(path);
                Matcher tableMatcher = TABLE_TAG.matcher(view);
                boolean hasTable = false;
                while (tableMatcher.find()) {
                    hasTable = true;
                    Matcher classMatcher = CLASS_ATTRIBUTE.matcher(tableMatcher.group());
                    assertThat(classMatcher.find())
                        .as("Table in %s must have a class attribute", path)
                        .isTrue();
                    assertThat(classMatcher.group(2).split("\\s+"))
                        .as("Table in %s must use erp-table", path)
                        .contains("erp-table");
                }
                if (hasTable) {
                    assertThat(view)
                        .as("Tables in %s must have a dedicated erp-table-scroll viewport", path)
                        .contains("erp-table-scroll");
                }

                Matcher classMatcher = CLASS_ATTRIBUTE.matcher(view);
                while (classMatcher.find()) {
                    String[] classes = classMatcher.group(2).split("\\s+");
                    assertThat(!(contains(classes, "erp-table-wrap") && contains(classes, "erp-table-scroll")))
                        .as("Table surface and scroll viewport must be separate in %s", path)
                        .isTrue();
                }
            }
        }
    }

    private boolean isJspView(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jsp") || name.endsWith(".jspf");
    }

    private boolean contains(String[] values, String expected) {
        for (String value : values) {
            if (expected.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
