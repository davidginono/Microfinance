package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class TableSortingCoverageTest {
    @Test void everyColumnHeaderTableDeclaresWorkingSortingOrItsFixedOrderPurpose() throws Exception {
        var tablePattern=Pattern.compile("<table\\b[\\s\\S]*?</table>");
        var headPattern=Pattern.compile("<thead\\b[\\s\\S]*?</thead>");
        var fixedPurpose=Pattern.compile("data-table-sort-reason=\"(?:chart|editor|picker|property-layout|financial-statement|document|retired-report)\"");
        var missing=new ArrayList<String>();
        try(var files=Files.walk(Path.of("src/main/webapp/WEB-INF/jsp"))) {
            for(var file:files.filter(path->path.toString().matches(".*\\.jspf?")).toList()) {
                var tables=tablePattern.matcher(Files.readString(file));int index=0;
                while(tables.find()) {
                    String table=tables.group();var head=headPattern.matcher(table);index++;
                    if(!head.find())continue;
                    String opening=table.substring(0,table.indexOf('>')+1);
                    boolean fixed=opening.contains("data-table-sort-mode=\"none\"") && fixedPurpose.matcher(opening).find();
                    boolean complete=opening.contains("data-table-sort-mode=\"local\"");
                    boolean server=head.group().contains("data-table-order=") || head.group().contains("data-table-sort");
                    if(!(fixed || complete || server))missing.add(file+" table "+index);
                }
            }
        }
        assertThat(missing).as("Tables need real sorting or an explicit fixed-order purpose").isEmpty();
    }
}
