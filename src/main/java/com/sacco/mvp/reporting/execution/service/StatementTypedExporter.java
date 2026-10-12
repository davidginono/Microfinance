package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.accounting.statements.StatementDesignerService.*;
import com.sacco.mvp.accounting.statements.StatementDefinition.Unit;
import com.sacco.mvp.accounting.statements.StatementDefinition.RowKind;
import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.reporting.OperationalReportExportService.Format;
import com.sacco.mvp.reporting.OperationalReportService.Branding;
import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.Layout;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One frozen typed financial result feeds every format. Rows are bounded by the approved statement language. */
@Service
public class StatementTypedExporter {
 private final MessageSource messages;
 private final byte[] font;
 public StatementTypedExporter(MessageSource messages)throws IOException{this.messages=messages;try(var in=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream()){font=in.readNBytes(2_000_001);if(font.length>2_000_000)throw new IOException("Report font exceeds its bounded asset size");}}
 public String fontChecksum(){return ReportRunService.sha256(font);}
 public String label(RowValue row,Layout layout){return row.mandatory()&&row.id().startsWith("REQUIRED_")?message("statement.total."+row.id().substring(9),layout):layout.language().equals("sw")?row.labelSw():row.labelEn();}
 public String unit(Unit unit,Layout layout){return unit==Unit.NONE?"":unit==Unit.RATIO?message("statement.export.ratio",layout):"TZS";}
 public String value(BigDecimal value,Unit unit){return value==null?"—":value.setScale(unit==Unit.RATIO?6:2,RoundingMode.UNNECESSARY).toPlainString();}
 public byte[] export(Result result,Layout layout,Branding branding,Format format){
  if(format!=Format.PDF)throw new IllegalArgumentException("accounting.release.error.export");
  layout.validate();if(!"FINAL".equals(result.status())||result.id()==null||result.rows().size()>10_000||result.disclosures().size()>2500)throw new IllegalArgumentException("accounting.release.error.source");
  try{return switch(format){case CSV->csv(result,layout);case XLSX->xlsx(result,layout,branding);case PDF->pdf(result,layout,branding);};}
  catch(IOException|ArithmeticException|IllegalArgumentException ex){throw new IllegalArgumentException("accounting.release.error.export",ex);}
 }
 /** Draft files remain explicitly marked drafts and never enter the retained final-output workflow. */
 public byte[] exportDraft(Result result,Layout layout){
  layout.validate();if(layout.version()!=3||!"DRAFT".equals(result.status())||result.id()!=null||result.rows().size()>10_000||result.disclosures().size()>2500)throw new IllegalArgumentException("accounting.release.error.source");
  try{return pdf(result,layout,null);}catch(IOException|ArithmeticException|IllegalArgumentException ex){throw new IllegalArgumentException("accounting.release.error.export",ex);}
 }
 private String message(String key,Layout layout){return messages.getMessage(key,null,key,Locale.forLanguageTag(layout.language()));}
 private List<String> context(Result r,Layout l){var context=new ArrayList<String>();context.add(l.language().equals("sw")?r.definition().titleSw():r.definition().titleEn());context.add(message("report.scope",l)+": "+r.institution()+" / "+r.branch());context.add(message("statement.dimension",l)+": "+message("statement.dimension."+r.dimension().name(),l));context.add(message("report.period",l)+": "+r.from()+" / "+r.through());context.add(message(r.dimension()==com.sacco.mvp.accounting.statements.RegulatoryFormatCatalog.Scope.INSTITUTION?"statement.latestBranchCutoff":"report.cutoff",l)+": "+r.recordedCutoff());if(r.comparisonFrom()!=null){context.add(message("statement.export.comparison",l)+": "+r.comparisonFrom()+" / "+r.comparisonThrough());context.add(message(r.dimension()==com.sacco.mvp.accounting.statements.RegulatoryFormatCatalog.Scope.INSTITUTION?"statement.latestComparisonBranchCutoff":"statement.export.comparisonCutoff",l)+": "+r.comparisonCutoff());}context.add(message("statement.export.versions",l)+": "+r.policyVersion()+" / "+r.mappingVersion()+" / "+r.calculationVersion());context.add(message("statement.export.source",l)+": "+("DRAFT".equals(r.status())?message("statements.draftSource",l):r.dimension()==com.sacco.mvp.accounting.statements.RegulatoryFormatCatalog.Scope.INSTITUTION?r.institutionPeriodId()+" / "+r.institutionSourceChecksum():r.closeReviewId()+" / "+r.closeChecksum()));if(r.comparisonInstitutionPeriodId()!=null)context.add(message("statement.export.comparisonSource",l)+": "+r.comparisonInstitutionPeriodId()+" / "+r.comparisonInstitutionSourceChecksum());else if(r.comparisonCloseId()!=null)context.add(message("statement.export.comparisonSource",l)+": "+r.comparisonCloseId()+" / "+r.comparisonCloseChecksum());if(r.priorResultId()!=null)context.add(message("statement.export.prior",l)+": "+r.priorResultId());context.add(message("statement.export.generated",l)+": "+r.generatedAt());return List.copyOf(context);}
 public String disclosure(String text,Layout layout){if(text.equals("INCOMPLETE_NOT_AUTHORITATIVE"))return message("statement.coverage.incomplete",layout);if(text.equals("REVIEWED_COVERAGE"))return message("statement.coverage.reviewed",layout);if(text.equals("REVIEWED_DIFFERENCES_RETAINED"))return message("statement.export.reviewedCoverage",layout);if(text.equals("MEMO_DISCLOSURES_EXCLUDED_FROM_CALCULATIONS"))return message("statement.export.memo",layout);if(text.equals("RESTATEMENT"))return message("statement.export.restatement",layout);if(text.startsWith("REVIEWED_DIFFERENCE: "))return message("statement.export.reviewedDifference",layout)+": "+text.substring(21);if(text.startsWith("ACCOUNTING_BASIS: "))return message("statement.export.basis",layout)+": "+text.substring(18);if(text.equals("TZS; POSTED_ENTRIES; EFFECTIVE_DATE; RECORDED_CUTOFF; NOT_AN_AUDIT_OPINION"))return message("statement.export.postedBasis",layout);return text;}
 private List<String> headers(Layout l,boolean comparison){var h=new ArrayList<>(List.of(message("statement.export.line",l),message("statement.export.unit",l),message("statement.export.current",l),message("statement.export.status",l)));if(comparison){h.add(message("statement.export.comparison",l));h.add(message("statement.export.comparisonStatus",l));}return h;}
 private static String quote(String value){String leading=value.stripLeading();if(!leading.isEmpty()&&"=+-@".indexOf(leading.charAt(0))>=0)value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
 private String status(String status,Layout layout){return message("statement.export.status."+status,layout);}
 private byte[] csv(Result r,Layout l){StringBuilder out=new StringBuilder("\uFEFF");for(String line:context(r,l))out.append(quote(line)).append("\r\n");out.append(headers(l,r.definition().comparison()).stream().map(StatementTypedExporter::quote).reduce((a,b)->a+","+b).orElseThrow()).append("\r\n");for(var row:r.getVisibleRows()){out.append(quote(label(row,l))).append(',').append(quote(unit(row.unit(),l))).append(',').append(numeric(row.current(),row.unit())).append(',').append(quote(status(row.status(),l)));if(r.definition().comparison())out.append(',').append(numeric(row.comparison(),row.unit())).append(',').append(quote(status(row.comparisonStatus(),l)));out.append("\r\n");String note=l.language().equals("sw")?row.noteSw():row.noteEn();if(note!=null&&!note.isBlank())out.append(quote(note)).append("\r\n");}for(var disclosure:r.disclosures())out.append(quote(disclosure(disclosure,l))).append("\r\n");return out.toString().getBytes(StandardCharsets.UTF_8);}
 private String numeric(BigDecimal value,Unit unit){return value==null?quote("—"):"\""+value(value,unit)+"\"";}
 private byte[] xlsx(Result r,Layout l,Branding logo)throws IOException{
  try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var sheet=book.createSheet("Statement");int index=0;
   if(logo!=null){int picture=book.addPicture(logo.logo(),logo.mediaType().equals("image/png")?Workbook.PICTURE_TYPE_PNG:Workbook.PICTURE_TYPE_JPEG);var anchor=book.getCreationHelper().createClientAnchor();anchor.setCol1(6);anchor.setCol2(8);anchor.setRow1(0);anchor.setRow2(4);sheet.createDrawingPatriarch().createPicture(anchor,picture);}
   CellStyle money=book.createCellStyle();money.setDataFormat(book.createDataFormat().getFormat("#,##0.00;[Red]-#,##0.00"));CellStyle ratio=book.createCellStyle();ratio.setDataFormat(book.createDataFormat().getFormat("0.000000"));CellStyle head=book.createCellStyle();Font bold=book.createFont();bold.setBold(true);head.setFont(bold);head.setWrapText(true);
   for(String line:context(r,l))sheet.createRow(index++).createCell(0).setCellValue(line);
   int header=index;var cells=sheet.createRow(index++);var headings=headers(l,r.definition().comparison());for(int c=0;c<headings.size();c++){cells.createCell(c).setCellValue(headings.get(c));cells.getCell(c).setCellStyle(head);sheet.setColumnWidth(c,(c==0?60:24)*256);}
   for(var row:r.getVisibleRows()){var cellsRow=sheet.createRow(index++);cellsRow.createCell(0).setCellValue(label(row,l));cellsRow.createCell(1).setCellValue(unit(row.unit(),l));writeNumber(cellsRow.createCell(2),row.current(),row.unit(),money,ratio);cellsRow.createCell(3).setCellValue(status(row.status(),l));if(r.definition().comparison()){writeNumber(cellsRow.createCell(4),row.comparison(),row.unit(),money,ratio);cellsRow.createCell(5).setCellValue(status(row.comparisonStatus(),l));}String note=l.language().equals("sw")?row.noteSw():row.noteEn();if(note!=null&&!note.isBlank())sheet.createRow(index++).createCell(0).setCellValue(note);}
   for(var text:r.disclosures())sheet.createRow(index++).createCell(0).setCellValue(disclosure(text,l));sheet.createFreezePane(0,header+1);sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(header,header,-1,-1));sheet.getPrintSetup().setLandscape(l.landscape());sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);book.write(out);return out.toByteArray();
  }
 }
 private void writeNumber(Cell cell,BigDecimal amount,Unit unit,CellStyle money,CellStyle ratio){if(amount==null){cell.setCellValue("—");return;}String exact=value(amount,unit);if(amount.precision()>15)cell.setCellValue(exact);else{cell.setCellValue(amount.doubleValue());cell.setCellStyle(unit==Unit.RATIO?ratio:money);}}
 private byte[] pdf(Result r,Layout l,Branding logo)throws IOException{
  if(l.version()==1)return legacyPdf(r,l,logo);
  try(var document=new PDDocument();var out=new ByteArrayOutputStream();var in=new ByteArrayInputStream(font)){
   PDFont pdfFont=PDType0Font.load(document,in);boolean comparative=r.definition().comparison();
   String period=r.definition().kind()==com.sacco.mvp.accounting.statements.StatementDefinition.Kind.BALANCE_SHEET?message("financial.print.asAt",l)+": "+r.through():message("report.period",l)+": "+r.from()+" / "+r.through();
   var summary=List.of(message("report.scope",l)+": "+r.institution()+" / "+r.branch(),period+"  |  TZS  |  "+r.status(),message("statement.export.generated",l)+": "+r.generatedAt());
   String title=l.language().equals("sw")?r.definition().titleSw():r.definition().titleEn();
   var image=logo==null?null:PDImageXObject.createFromByteArray(document,logo.logo(),"institution-logo");
   try(var page=new AccountingPdfDocument(document,pdfFont,l.landscape()||l.version()==3&&r.definition().kind()==com.sacco.mvp.accounting.statements.StatementDefinition.Kind.CHANGES_IN_EQUITY,title,summary,image)){
    boolean matrix=l.version()==3&&!r.getEquityRows().isEmpty();if(matrix)equityMatrix(page,r,l);
    var headings=new ArrayList<>(List.of(message("statement.export.line",l),message("financial.print.notes",l),message("statement.export.current",l)));
    if(comparative)headings.add(message("statement.export.comparison",l));
    if(!matrix||comparative||r.getStatementRows().stream().anyMatch(row->row.kind()==RowKind.SUBTOTAL||row.kind()==RowKind.RATIO))page.columns(headings,comparative?new float[]{.46f,.06f,.24f,.24f}:new float[]{.64f,.08f,.28f},comparative?new boolean[]{false,false,true,true}:new boolean[]{false,false,true});
    var notes=new ArrayList<String>();
    for(var row:l.version()==3?r.getStatementRows():r.getVisibleRows()){
     String note=l.language().equals("sw")?row.noteSw():row.noteEn();boolean hasNote=note!=null&&!note.isBlank();
     if(row.kind()==RowKind.HEADING){page.section(label(row,l));if(hasNote)notes.add(note);continue;}
     // Required source evidence is appendix text, not a financial amount or an invented account code.
     if(row.kind()==RowKind.NOTE){notes.add(label(row,l)+(hasNote?": "+note:""));continue;}
     String noteNumber="";if(hasNote){notes.add(note);noteNumber=Integer.toString(notes.size());}
     var values=new ArrayList<>(List.of(label(row,l)+(row.unit()==Unit.RATIO?" ("+unit(row.unit(),l)+")":""),noteNumber,printed(row.current(),row.unit(),row.status(),l)));
     if(comparative)values.add(printed(row.comparison(),row.unit(),row.comparisonStatus(),l));
     if(l.version()!=3||r.getEquityRows().isEmpty()||comparative||row.kind()!=RowKind.ACCOUNT_GROUP)page.row(values,row.kind()==RowKind.SUBTOTAL || row.mandatory());
    }
    page.endTable();
    if(!notes.isEmpty()){page.section(message("financial.print.notes",l));for(int n=0;n<notes.size();n++)page.text((n+1)+". "+notes.get(n));}
    page.section(message("financial.print.reportContext",l));for(String line:context(r,l).subList(1,context(r,l).size()))page.text(line);
    if(l.version()==3)for(var row:r.getSupportingRows())page.text(label(row,l)+": "+printed(row.current(),row.unit(),row.status(),l)+(comparative?" / "+printed(row.comparison(),row.unit(),row.comparisonStatus(),l):""));
    for(var text:r.disclosures())page.text(disclosure(text,l));
   }
   document.save(out);return out.toByteArray();
  }
 }
 private void equityMatrix(AccountingPdfDocument page,Result result,Layout layout)throws IOException{
  var components=result.getEquityRows();
  // Bounded panels preserve readable columns when the institution has many equity components.
  for(int start=0;start<components.size();start+=4){
   var panel=components.subList(start,Math.min(start+4,components.size()));var headings=new ArrayList<String>();headings.add(message("statement.export.line",layout));panel.forEach(r->headings.add(label(r,layout)));headings.add(message("statements.equity.total",layout));
   float[] widths=new float[panel.size()+2];boolean[] numeric=new boolean[widths.length];widths[0]=.25f;for(int n=1;n<widths.length;n++){widths[n]=.75f/(panel.size()+1);numeric[n]=true;}
   page.columns(headings,widths,numeric);
   for(String measure:List.of("opening","movement","closing")){var values=new ArrayList<String>();values.add(message("statements.equity."+measure,layout));for(var component:panel){BigDecimal amount=switch(measure){case "opening"->component.opening();case "closing"->component.closing();default->component.current();};values.add(printed(amount,Unit.TZS,amount==null?"UNAVAILABLE":"KNOWN",layout));}BigDecimal total=result.reconciliations().get(switch(measure){case "opening"->"OPENING_EQUITY";case "closing"->"EQUITY";default->"EQUITY_MOVEMENT";});values.add(printed(total,Unit.TZS,total==null?"UNAVAILABLE":"KNOWN",layout));page.row(values,measure.equals("closing"));}
   page.endTable();
  }
 }
 private String printed(BigDecimal amount,Unit unit,String state,Layout layout){
  if(amount==null)return "TEXT".equals(state)?"":status(state,layout);
  var format=new java.text.DecimalFormat(unit==Unit.RATIO?"0.000000;(0.000000)":"#,##0.00;(#,##0.00)",java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH));format.setRoundingMode(RoundingMode.UNNECESSARY);return format.format(amount);
 }
 private byte[] legacyPdf(Result r,Layout l,Branding logo)throws IOException{
  try(var document=new PDDocument();var out=new ByteArrayOutputStream();var in=new ByteArrayInputStream(font)){
   PDFont pdfFont=PDType0Font.load(document,in);PDImageXObject image=logo==null?null:PDImageXObject.createFromByteArray(document,logo.logo(),"institution-logo");
   try(var page=new LegacyPdfWriter(document,pdfFont,l,context(r,l),headers(l,r.definition().comparison()),image)){
    for(var row:r.getVisibleRows()){List<String> values=new ArrayList<>(List.of(label(row,l),unit(row.unit(),l),value(row.current(),row.unit()),status(row.status(),l)));if(r.definition().comparison()){values.add(value(row.comparison(),row.unit()));values.add(status(row.comparisonStatus(),l));}page.row(values);String note=l.language().equals("sw")?row.noteSw():row.noteEn();if(note!=null&&!note.isBlank())page.text(note);}
    for(var text:r.disclosures())page.text(disclosure(text,l));
   }
   int n=0;for(var page:document.getPages())try(var stream=new PDPageContentStream(document,page,PDPageContentStream.AppendMode.APPEND,true)){stream.beginText();stream.setFont(pdfFont,8);stream.newLineAtOffset(35,20);stream.showText(++n+" / "+document.getNumberOfPages());stream.endText();}document.save(out);return out.toByteArray();
  }
 }
 private static final class LegacyPdfWriter implements AutoCloseable {
  private final PDDocument document;private final PDFont font;private final Layout layout;private final List<String> context,headers;private final PDImageXObject logo;private PDPageContentStream stream;private float y,width;
  LegacyPdfWriter(PDDocument d,PDFont f,Layout l,List<String> context,List<String> headers,PDImageXObject logo)throws IOException{document=d;font=f;layout=l;this.context=context;this.headers=headers;this.logo=logo;next();}
  private void next()throws IOException{if(stream!=null)stream.close();var size=layout.landscape()?new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()):PDRectangle.A4;var page=new PDPage(size);document.addPage(page);stream=new PDPageContentStream(document,page);y=size.getHeight()-35;width=size.getWidth()-70;if(logo!=null){float scale=Math.min(100f/logo.getWidth(),35f/logo.getHeight());stream.drawImage(logo,35,y-35,logo.getWidth()*scale,logo.getHeight()*scale);y-=45;}for(String line:context)writeText(line);draw(headers);}
  void text(String value)throws IOException{for(String line:wrap(value,width)){if(y<45)next();write(line,35,y);y-=11;}y-=3;}
  private void writeText(String value)throws IOException{for(String line:wrap(value,width)){write(line,35,y);y-=11;}y-=3;}
  void row(List<String> values)throws IOException{float height=height(values);if(y-height<40)next();draw(values);}
  private float cellWidth(int i){float[] weights=headers.size()==6?new float[]{0.30f,0.07f,0.20f,0.10f,0.20f,0.13f}:new float[]{0.40f,0.10f,0.30f,0.20f};return width*weights[i];}
  private float height(List<String> values)throws IOException{int lines=1;for(int i=0;i<values.size();i++)lines=Math.max(lines,wrap(values.get(i),cellWidth(i)-8).size());return lines*11+7;}
  private void draw(List<String> values)throws IOException{float height=height(values),x=35;for(int i=0;i<values.size();i++){float baseline=y;for(String line:wrap(values.get(i),cellWidth(i)-8)){write(line,x,baseline);baseline-=11;}x+=cellWidth(i);}y-=height;}
  private List<String> wrap(String value,float max)throws IOException{List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();for(int cp:value.codePoints().toArray()){String token=new String(Character.toChars(cp));String candidate=line+token;if(font.getStringWidth(candidate)/1000*7>max&&!line.isEmpty()){lines.add(line.toString());line.setLength(0);}line.append(token);}lines.add(line.toString());return lines;}
  private void write(String value,float x,float y)throws IOException{stream.beginText();stream.setFont(font,7);stream.newLineAtOffset(x,y);stream.showText(value);stream.endText();}
  @Override public void close()throws IOException{if(stream!=null)stream.close();}
 }
}
