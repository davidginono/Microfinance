package com.sacco.mvp.reporting.execution.dto;

import com.sacco.mvp.accounting.statements.StatementDesignerService.Result;
import com.sacco.mvp.reporting.OperationalReportService.Branding;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.*;

public final class StatementOutputDtos {
 private StatementOutputDtos() { }
 public record Layout(int version,String language,boolean landscape,boolean institutionLogo) {
  public void validate(){if(version!=1||!Set.of("en","sw").contains(language))throw new IllegalArgumentException("accounting.release.error.layout");}
 }
 public record Output(UUID id,String institution,String branch,UUID resultId,String sourceChecksum,Result result,
  Layout layout,String layoutChecksum,String fontChecksum,Branding branding,UUID generatedBy,OffsetDateTime generatedAt,
  UUID reviewer,String reviewEvidence,OffsetDateTime reviewedAt) { }
 /** Registry projection excludes the retained result, logo, and file payloads. */
 public record Summary(UUID id,String titleEn,String titleSw,LocalDate from,LocalDate through,
  OffsetDateTime generatedAt,UUID reviewer) { }
}
