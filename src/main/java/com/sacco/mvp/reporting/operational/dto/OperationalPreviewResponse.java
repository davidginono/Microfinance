package com.sacco.mvp.reporting.operational.dto;

import java.math.BigDecimal;
import java.util.*;

/** Browser JSON keeps decimal cells as strings, so JavaScript never rounds away financial cents. */
public record OperationalPreviewResponse(List<OperationalReportResult.Column> columns,
        List<Map<String,String>> rows,long rowCount,ReportDefinition definition,
        OperationalReportResult.Coverage coverage,Map<String,Total> totals,List<Map<String,String>> groups,Map<String,String> labels) {
    public record Total(String value,long availableRows,long unavailableRows) { }
    public static OperationalPreviewResponse from(OperationalReportResult result) {
        var rows=strings(result.rows());
        Map<String,Total> totals=new LinkedHashMap<>();result.totals().forEach((key,total) -> totals.put(key,
            new Total(total.value()==null ? null : total.value().toPlainString(),total.availableRows(),total.unavailableRows())));
        return new OperationalPreviewResponse(result.columns(),rows,result.rowCount(),result.definition(),result.coverage(),Collections.unmodifiableMap(totals),strings(result.groups()),result.labels());
    }
    private static List<Map<String,String>> strings(List<Map<String,Object>> rows) {
        return rows.stream().map(row -> {
            Map<String,String> cells=new LinkedHashMap<>();row.forEach((key,value) -> cells.put(key,
                value == null ? null : value instanceof BigDecimal amount ? amount.toPlainString() : value.toString()));
            return Collections.unmodifiableMap(cells);
        }).toList();
    }
}
