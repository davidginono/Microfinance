package com.sacco.mvp.reporting.operational.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The export/job boundary: only authorized scalar cells, no entities or SQL. Null remains unavailable. */
public record OperationalReportResult(UUID templateId, int templateVersion, ReportDefinition definition,
        OffsetDateTime cutoff, String institutionId, String branchId, List<Column> columns,
        List<Map<String, Object>> rows, long rowCount, int page, int pageSize,
        Map<String, Total> totals, List<Map<String, Object>> groups, Coverage coverage, Map<String,String> labels) {
    public record Column(String key, String label, ReportDefinition.Format format, int width) { }
    public record Total(BigDecimal value, long availableRows, long unavailableRows) { }
    public record Coverage(long trackedLoans, long untrackedLoans, long unknownDateLoans, String messageKey) {
        public Coverage(long trackedLoans,long untrackedLoans,String messageKey) { this(trackedLoans,untrackedLoans,0,messageKey); }
    }
    public OperationalReportResult(UUID templateId,int templateVersion,ReportDefinition definition,OffsetDateTime cutoff,
            String institutionId,String branchId,List<Column> columns,List<Map<String,Object>> rows,long rowCount,int page,int pageSize,
            Map<String,Total> totals,List<Map<String,Object>> groups,Coverage coverage) {
        this(templateId,templateVersion,definition,cutoff,institutionId,branchId,columns,rows,rowCount,page,pageSize,totals,groups,coverage,Map.of());
    }
    public boolean getHasNext() { return ((long) page + 1) * pageSize < rowCount; }
    public boolean getHasPrevious() { return page > 0; }
}
