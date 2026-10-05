package com.sacco.mvp.reporting.execution.dto;

import com.sacco.mvp.reporting.OperationalReportDefinition;
import com.sacco.mvp.reporting.OperationalReportExportService.Format;
import java.time.*;
import java.util.*;

public final class ReportRunDtos {
 private ReportRunDtos() { }
 public record Request(UUID requestKey,UUID templateVersion,LocalDate from,LocalDate through,
  OffsetDateTime recordedCutoff,Set<Format> formats,UUID restates,String reason) { }
 public record Run(UUID id,String institution,String branch,UUID requester,UUID templateVersion,int version,
  OperationalReportDefinition definition,LocalDate from,LocalDate through,OffsetDateTime cutoff,Set<Format> formats,
  String status,OffsetDateTime requestedAt,OffsetDateTime generatedAt,int attempts,boolean cancellation,
  String failure,long rows,long untracked,String coverage,String totals,String snapshot,String checksum,
  UUID approvedBy,OffsetDateTime approvedAt,String approvalEvidence,UUID restates,String restatementReason,UUID workerToken) { }
 public record Artifact(UUID id,Format format,String checksum,long bytes) { }
 public record Download(Format format,byte[] bytes,String checksum) {
  public Download { bytes=bytes.clone(); }
  @Override public byte[] bytes(){return bytes.clone();}
 }
 public record FrozenPage(int page,int rows,String payload,String checksum) { }
}
