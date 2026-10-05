package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LedgerReportExporterTest {
    LedgerReportService reports;LedgerReportExporter exporter;ResourceBundleMessageSource messages;
    final LedgerReportService.Scope scope=new LedgerReportService.Scope("SYNTHETIC-I1","B1",false);
    final LedgerReportService.Parameters parameters=new LedgerReportService.Parameters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),OffsetDateTime.parse("2026-10-03T10:00:00+03:00"),0);
    final LedgerReportService.Coverage coverage=new LedgerReportService.Coverage(1,4,2);
    @BeforeEach void setup(){reports=mock(LedgerReportService.class);messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);exporter=new LedgerReportExporter(reports,new ReportExportLimiter(1),messages);}
    LedgerReportService.TrialBalance trial(int size) {
        var rows=new ArrayList<LedgerReportService.AccountBalance>();for(int i=0;i<size;i++)rows.add(new LedgerReportService.AccountBalance(UUID.randomUUID(),"A"+i,i==0?"  =SUM(1,2)":"Synthetic "+i,"ASSET","NONE",null,new BigDecimal("123456789012345.67"),new BigDecimal("0.01"),null));
        return new LedgerReportService.TrialBalance(scope,parameters,UUID.randomUUID(),7,List.copyOf(rows),new LedgerReportService.Totals(null,null,new BigDecimal("123456789012345.67"),new BigDecimal("0.01"),null,null),coverage,false);
    }
    @Test void csvKeepsFullSourceCoverageUnknownAndNeutralizesOnlyUserStrings(){when(reports.exportTrialBalance(null,parameters,false)).thenReturn(trial(31));var output=exporter.trial(null,parameters,false,LedgerReportExporter.Format.CSV,Locale.ENGLISH);String csv=new String(output.bytes(),StandardCharsets.UTF_8);assertThat(csv).contains("'  =SUM(1,2)","123456789012345.67","\"A30\"","SYNTHETIC-I1","B1","2026-10-03T10:00+03:00",messages.getMessage("financial.report.unknown",null,Locale.ENGLISH));assertThat(csv).doesNotContain("FINAL");verify(reports).exportTrialBalance(null,parameters,false);}
    @Test void xlsxPreservesLargeTzsAsExactTextAndNeverCreatesFormulaCells()throws Exception{when(reports.exportTrialBalance(null,parameters,false)).thenReturn(trial(2));var output=exporter.trial(null,parameters,false,LedgerReportExporter.Format.XLSX,Locale.ENGLISH);try(var book=new XSSFWorkbook(new ByteArrayInputStream(output.bytes()))){var sheet=book.getSheetAt(0);boolean foundLarge=false,foundName=false;for(var row:sheet)for(var cell:row){assertThat(cell.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.STRING){foundLarge|=cell.getStringCellValue().equals("123456789012345.67");foundName|=cell.getStringCellValue().equals("  =SUM(1,2)");}}assertThat(foundLarge).isTrue();assertThat(foundName).isTrue();}}
    @Test void multipageSwahiliPdfRetainsAllRowsHeadersAndExactAmounts()throws Exception{when(reports.exportTrialBalance(null,parameters,false)).thenReturn(trial(140));var output=exporter.trial(null,parameters,false,LedgerReportExporter.Format.PDF,Locale.forLanguageTag("sw"));try(var doc=Loader.loadPDF(output.bytes())){assertThat(doc.getNumberOfPages()).isGreaterThan(3);String text=new PDFTextStripper().getText(doc);assertThat(text).contains("A139","SYNTHETIC-I1","B1",messages.getMessage("financial.report.draft",null,Locale.forLanguageTag("sw")));assertThat(text.replaceAll("\\s+","")).contains("123456789012345.67");}}
    @Test void activityRetainsNegativeMoneyIndependentSourceTimesReversalAndEvidence()throws Exception{UUID id=UUID.randomUUID(),reversal=UUID.randomUUID();var row=new LedgerReportService.Activity(id,parameters.through(),parameters.recordedCutoff().minusHours(2),parameters.recordedCutoff().minusHours(1),"REVERSAL","@reference","Synthetic independently reviewed evidence",reversal,new BigDecimal("0.00"),new BigDecimal("25.01"));var source=new LedgerReportService.AccountActivity(scope,parameters,UUID.randomUUID(),"CASH","Synthetic cash",new BigDecimal("-25.01"),List.of(row),new BigDecimal("-50.02"),coverage,false);when(reports.exportAccountActivity(null,source.accountId(),parameters,false)).thenReturn(new LedgerReportService.AccountActivityExport(source,UUID.randomUUID(),7));String csv=new String(exporter.activity(null,source.accountId(),parameters,false,LedgerReportExporter.Format.CSV,Locale.ENGLISH).bytes(),StandardCharsets.UTF_8);assertThat(csv).contains("\"-25.01\"","\"-50.02\"",id.toString(),reversal.toString(),"'@reference","Synthetic independently reviewed evidence",row.recordedAt().toString(),row.postedAt().toString()).doesNotContain("'-25.01");}
    @Test void exportPermissionFailureOccursBeforeProducingAnyArtifact(){when(reports.exportTrialBalance(null,parameters,false)).thenThrow(new org.springframework.security.access.AccessDeniedException("Revoked"));assertThatThrownBy(()->exporter.trial(null,parameters,false,LedgerReportExporter.Format.PDF,Locale.ENGLISH)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
}
