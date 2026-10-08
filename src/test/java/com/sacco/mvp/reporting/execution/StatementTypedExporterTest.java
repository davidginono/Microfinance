package com.sacco.mvp.reporting.execution;

import com.sacco.mvp.accounting.statements.*;
import com.sacco.mvp.accounting.statements.StatementDesignerService.*;
import com.sacco.mvp.reporting.OperationalReportExportService.Format;
import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.Layout;
import com.sacco.mvp.reporting.execution.service.StatementTypedExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementTypedExporterTest {
 private StatementTypedExporter exporter()throws IOException {var messages=new StaticMessageSource();messages.setUseCodeAsDefaultMessage(true);messages.addMessage("statement.export.status.UNAVAILABLE",Locale.ENGLISH,"Unavailable");messages.addMessage("statement.total.ASSETS",Locale.ENGLISH,"Assets");return new StatementTypedExporter(messages);}
 static Result result(){UUID account=UUID.randomUUID();var definition=new StatementDefinition(1,1,StatementDefinition.Kind.BALANCE_SHEET,"État ya fedha","Taarifa ya fedha",List.of(new StatementDefinition.Row("CASH","Cash","Fedha",StatementDefinition.RowKind.ACCOUNT_GROUP,StatementDefinition.Section.ASSETS,StatementDefinition.Unit.TZS,List.of(account),StatementDefinition.Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null)),List.of(),true);var rows=List.of(new RowValue("CASH","  =HYPERLINK(\"hostile\")","Fedha",StatementDefinition.RowKind.ACCOUNT_GROUP,StatementDefinition.Unit.TZS,new BigDecimal("-9999999999999999.99"),null,"KNOWN","UNAVAILABLE",true,false,false,null,null),new RowValue("RATIO","Ratio","Uwiano",StatementDefinition.RowKind.RATIO,StatementDefinition.Unit.RATIO,new BigDecimal("0.333333"),BigDecimal.ZERO,"KNOWN","KNOWN",true,false,false,null,null),new RowValue("HIDDEN","Hidden","Imefichwa",StatementDefinition.RowKind.NOTE,StatementDefinition.Unit.NONE,null,null,"TEXT","TEXT",false,false,false,"Do not export this optional hidden note",""),new RowValue("REQUIRED_ASSETS","ASSETS","ASSETS",StatementDefinition.RowKind.SUBTOTAL,StatementDefinition.Unit.TZS,new BigDecimal("0.01"),new BigDecimal("-0.02"),"KNOWN","KNOWN",false,true,true,null,null));OffsetDateTime now=OffsetDateTime.now();return new Result(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),2,1,definition,"SYNTHETIC","B1",now.toLocalDate(),now.toLocalDate(),now.toLocalDate().minusMonths(1),now.toLocalDate().minusDays(1),now,now.minusDays(1),UUID.randomUUID(),1,UUID.randomUUID(),"a".repeat(64),UUID.randomUUID(),"b".repeat(64),"REVIEWED_DIFFERENCES_RETAINED","FINAL",rows,Map.of("ASSETS",new BigDecimal("0.01")),List.of("RESTATEMENT","TZS; POSTED_ENTRIES; EFFECTIVE_DATE; RECORDED_CUTOFF; NOT_AN_AUDIT_OPINION"),UUID.randomUUID(),"Synthetic mapping review",now,UUID.randomUUID());}
 @Test void rejectsCSVOutput()throws Exception {assertThatThrownBy(()->exporter().export(result(),new Layout(1,"en",false,false),null,Format.CSV)).hasMessage("accounting.release.error.export");}
 @Test void rejectsXLSXOutput()throws Exception {assertThatThrownBy(()->exporter().export(result(),new Layout(1,"en",false,false),null,Format.XLSX)).hasMessage("accounting.release.error.export");}
 @Test void pdfRetainsUnicodeExactValuesAndUsesSelectedOrientation()throws Exception {for(boolean landscape:List.of(false,true)){try(var document=Loader.loadPDF(exporter().export(result(),new Layout(1,"en",landscape,false),null,Format.PDF))){String text=new PDFTextStripper().getText(document);assertThat(text).contains("État ya fedha","-9999999999999999.99","0.333333","Unavailable","Assets");var box=document.getPage(0).getMediaBox();assertThat(box.getWidth()>box.getHeight()).isEqualTo(landscape);}}assertThat(exporter().fontChecksum()).matches("[0-9a-f]{64}");}
 @Test void unsupportedLayoutVersionsAndLanguagesAreRejected()throws Exception {assertThatThrownBy(()->exporter().export(result(),new Layout(2,"en",false,false),null,Format.PDF)).hasMessage("accounting.release.error.layout");assertThatThrownBy(()->exporter().export(result(),new Layout(1,"fr",false,false),null,Format.PDF)).hasMessage("accounting.release.error.layout");}
 @Test void draftSourceCannotProduceFinalStatementFiles()throws Exception{Result draft=mock(Result.class);when(draft.status()).thenReturn("DRAFT");assertThatThrownBy(()->exporter().export(draft,new Layout(1,"en",false,false),null,Format.PDF)).hasMessage("accounting.release.error.source");}
 @Test void memoDisclosureAndRatioUnitsUseTheSelectedLanguage()throws Exception{var messages=new StaticMessageSource();messages.addMessage("statement.export.memo",Locale.ENGLISH,"Memo disclosures excluded from calculations");messages.addMessage("statement.export.memo",Locale.forLanguageTag("sw"),"Kumbukumbu hazijumuishwi katika hesabu");messages.addMessage("statement.export.ratio",Locale.forLanguageTag("sw"),"Uwiano");var renderer=new StatementTypedExporter(messages);assertThat(renderer.disclosure("MEMO_DISCLOSURES_EXCLUDED_FROM_CALCULATIONS",new Layout(1,"en",false,false))).isEqualTo("Memo disclosures excluded from calculations");assertThat(renderer.disclosure("MEMO_DISCLOSURES_EXCLUDED_FROM_CALCULATIONS",new Layout(1,"sw",false,false))).isEqualTo("Kumbukumbu hazijumuishwi katika hesabu");assertThat(renderer.unit(StatementDefinition.Unit.RATIO,new Layout(1,"sw",false,false))).isEqualTo("Uwiano");assertThat(renderer.unit(StatementDefinition.Unit.NONE,new Layout(1,"en",false,false))).isEmpty();}

 @Test void institutionContextRetainsIndividualSourceCutoffsAndNeverPrintsNullBranchClose()throws Exception {
  var base=result();var first=new BranchSource("B1",UUID.randomUUID(),1,base.recordedCutoff().minusSeconds(7),"c".repeat(64),UUID.randomUUID());var second=new BranchSource("B2",UUID.randomUUID(),2,base.recordedCutoff(),"d".repeat(64),UUID.randomUUID());
  var rows=new ArrayList<RowValue>(base.rows());for(var source:List.of(first,second)){String proof=source.branch()+" / "+source.recordedCutoff()+" / "+source.checksum();rows.add(new RowValue("BRANCH_SOURCE_CURRENT_"+source.reviewId(),"Current branch source","Chanzo cha tawi cha sasa",StatementDefinition.RowKind.NOTE,StatementDefinition.Unit.NONE,null,null,"TEXT","TEXT",false,false,true,proof,proof));}
  var institution=new Result(base.id(),base.versionId(),base.templateId(),base.mappingVersion(),base.calculationVersion(),base.definition(),base.institution(),base.branch(),base.from(),base.through(),base.comparisonFrom(),base.comparisonThrough(),base.recordedCutoff(),base.comparisonCutoff(),base.policyId(),base.policyVersion(),null,null,null,null,base.coverage(),base.status(),rows,base.reconciliations(),base.disclosures(),base.mappingReviewer(),base.mappingApprovalEvidence(),base.generatedAt(),base.priorResultId(),RegulatoryFormatCatalog.Scope.INSTITUTION,List.of(first,second),List.of(),UUID.randomUUID(),UUID.randomUUID(),"e".repeat(64),"f".repeat(64));
  for(String language:List.of("en","sw"))for(var format:List.of(Format.PDF)){byte[] bytes=exporter().export(institution,new Layout(1,language,true,false),null,format);String content;
   if(format==Format.CSV)content=new String(bytes,StandardCharsets.UTF_8);else if(format==Format.PDF){try(var pdf=Loader.loadPDF(bytes)){content=new PDFTextStripper().getText(pdf);}}else{try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var text=new StringBuilder();for(Row row:book.getSheetAt(0))for(Cell cell:row)if(cell.getCellType()==CellType.STRING)text.append(cell.getStringCellValue()).append(" ");content=text.toString();}}
   assertThat(content).contains("statement.dimension.INSTITUTION","statement.latestBranchCutoff","statement.latestComparisonBranchCutoff",first.recordedCutoff().toString(),second.recordedCutoff().toString(),"e".repeat(64),"f".repeat(64)).doesNotContain("null / null");
  }
 }

 @Test void largeRequiredEvidenceIsRetainedAndExcessRowsFailExplicitly()throws Exception {
  var base=result();var rows=new ArrayList<RowValue>();
  for(int i=0;i<9000;i++)rows.add(new RowValue("TRANSFER_PROOF_"+i,"Required transfer source","Chanzo cha lazima cha uhamisho",StatementDefinition.RowKind.NOTE,StatementDefinition.Unit.NONE,null,null,"TEXT","TEXT",false,false,true,"proof-marker-"+i+"-end","proof-marker-"+i+"-end"));
  var large=withRows(base,rows);String csv;try(var pdf=Loader.loadPDF(exporter().export(large,new Layout(1,"en",false,false),null,Format.PDF))){csv=new PDFTextStripper().getText(pdf);}
  var markers=java.util.regex.Pattern.compile("proof-marker-[0-9]+-end").matcher(csv).results().map(java.util.regex.MatchResult::group).collect(java.util.stream.Collectors.toSet());assertThat(markers).containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.range(0,9000).mapToObj(i->"proof-marker-"+i+"-end").toList());
  while(rows.size()<=10000)rows.add(rows.get(0));var excess=withRows(base,rows);
  assertThatThrownBy(()->exporter().export(excess,new Layout(1,"en",false,false),null,Format.PDF)).hasMessage("accounting.release.error.source");
 }
 static Result withRows(Result b,List<RowValue> rows){return new Result(b.id(),b.versionId(),b.templateId(),b.mappingVersion(),b.calculationVersion(),b.definition(),b.institution(),b.branch(),b.from(),b.through(),b.comparisonFrom(),b.comparisonThrough(),b.recordedCutoff(),b.comparisonCutoff(),b.policyId(),b.policyVersion(),b.closeReviewId(),b.closeChecksum(),b.comparisonCloseId(),b.comparisonCloseChecksum(),b.coverage(),b.status(),rows,b.reconciliations(),b.disclosures(),b.mappingReviewer(),b.mappingApprovalEvidence(),b.generatedAt(),b.priorResultId());}
}
