package com.sacco.mvp.reporting;

import com.sacco.mvp.reporting.OperationalReportDefinition.Column;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Screen and exports consume the same typed result, including full-result totals and coverage. */
@Service
@RequiredArgsConstructor
public class OperationalReportExportService {
    private final MessageSource messages;
    public enum Format { CSV, XLSX, PDF }
    public byte[] export(OperationalReportService.Result result, Format format) {
        if (result.page() != 0 || result.truncated() || result.rows().size() != result.rowsInScope())
            throw new IllegalArgumentException("report.error.largeExport");
        try {
            return switch (format) { case CSV -> csv(result); case XLSX -> xlsx(result); case PDF -> pdf(result); };
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("report.error.export");
        }
    }
    public String heading(Column column, String language) {
        String canonical = message(column.field().getKey(), language) + (column.field().getUnit().isEmpty() ? "" : " (TZS)");
        return column.label().isBlank() ? canonical : column.label() + " [" + canonical + "]";
    }
    public String message(String key, String language) { return messages.getMessage(key, null, Locale.forLanguageTag(language)); }
    private List<String> context(OperationalReportService.Result r) {
        String lang = r.definition().language();
        return List.of(r.definition().title(), message("report.dataset." + r.definition().dataset(), lang),
            message("report.period",lang) + ": " + r.from() + " / " + r.through(),
            message("report.cutoff",lang) + ": " + r.recordedCutoff(),
            message("report.coverage." + r.definition().dataset(),lang),
            message("report.untracked",lang) + ": " + r.untrackedLoans(),
            message("report.rows",lang) + ": " + r.rowsInScope());
    }
    private byte[] csv(OperationalReportService.Result r) {
        StringBuilder text = new StringBuilder("\uFEFF");
        for (String line : context(r)) text.append(quote(line)).append("\r\n");
        List<Column> columns = r.getVisibleColumns();
        text.append(columns.stream().map(c -> quote(heading(c,r.definition().language()))).reduce((a,b) -> a+","+b).orElseThrow()).append("\r\n");
        for (Map<String,Object> row : r.rows()) {
            text.append(columns.stream().map(c -> csvValue(row.get(c.field().name()))).reduce((a,b) -> a+","+b).orElseThrow()).append("\r\n");
        }
        appendCsvTotals(text,r);
        text.append(quote(r.definition().footer())).append("\r\n");
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
    private void appendCsvTotals(StringBuilder text, OperationalReportService.Result r) {
        text.append(quote(message("report.total",r.definition().language()))).append("\r\n");
        for (Column column:r.getVisibleColumns()) if (r.totals().containsKey(column.field().name()))
            text.append(quote(heading(column,r.definition().language()))).append(',').append(csvValue(r.totals().get(column.field().name()))).append("\r\n");
    }
    /** Neutralize formula prefixes even after leading whitespace; numeric typed values never become formulas. */
    static String quote(String text) {
        if (!text.isEmpty() && "=+-@".indexOf(text.stripLeading().isEmpty() ? ' ' : text.stripLeading().charAt(0))>=0) text="'"+text;
        return "\"" + text.replace("\"","\"\"") + "\"";
    }
    private static String csvValue(Object value){return value instanceof BigDecimal?"\""+string(value)+"\"":quote(string(value));}
    static String string(Object value) {
        return value == null ? "—" : value instanceof BigDecimal decimal ? decimal.setScale(2).toPlainString() : value.toString();
    }
    private byte[] xlsx(OperationalReportService.Result r) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet=workbook.createSheet("Report"); int index=0;
            CellStyle money=workbook.createCellStyle(); money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00;[Red]-#,##0.00"));
            CellStyle date=workbook.createCellStyle(); date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle head=workbook.createCellStyle(); Font bold=workbook.createFont(); bold.setBold(true); head.setFont(bold); head.setWrapText(true);
            for (String line:context(r)) sheet.createRow(index++).createCell(0).setCellValue(line);
            Row headers=sheet.createRow(index++); List<Column> columns=r.getVisibleColumns();
            for(int c=0;c<columns.size();c++) { Cell cell=headers.createCell(c); cell.setCellValue(heading(columns.get(c),r.definition().language())); cell.setCellStyle(head); sheet.setColumnWidth(c,Math.min(60*256,columns.get(c).width()*36)); }
            for (Map<String,Object> row:r.rows()) {
                Row cells=sheet.createRow(index++);
                for(int c=0;c<columns.size();c++) {
                    Cell cell=cells.createCell(c); Object value=row.get(columns.get(c).field().name());
                    writeCell(cell,value,money,date);
                }
            }
            sheet.createRow(index++).createCell(0).setCellValue(message("report.total",r.definition().language()));
            for(Column column:columns) if(r.totals().containsKey(column.field().name())) {
                Row total=sheet.createRow(index++); total.createCell(0).setCellValue(heading(column,r.definition().language()));
                writeCell(total.createCell(1),r.totals().get(column.field().name()),money,date);
            }
            sheet.createRow(index).createCell(0).setCellValue(r.definition().footer());
            sheet.createFreezePane(0,headers.getRowNum()+1); sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(headers.getRowNum(),headers.getRowNum(),-1,-1));
            sheet.getPrintSetup().setLandscape(r.definition().landscape()); sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)0);
            workbook.write(out); return out.toByteArray();
        }
    }
    private static void writeCell(Cell cell,Object value,CellStyle money,CellStyle date) {
        if(value instanceof BigDecimal decimal) {
            // Excel has 15 significant numeric digits. Keep high amounts exact as decimal text.
            if(decimal.precision()<=15) { cell.setCellValue(decimal.doubleValue()); cell.setCellStyle(money); }
            else cell.setCellValue(decimal.setScale(2).toPlainString());
        } else if(value instanceof LocalDate day) { cell.setCellValue(day); cell.setCellStyle(date); }
        else cell.setCellValue(string(value));
    }
    private byte[] pdf(OperationalReportService.Result r) throws IOException {
        try (PDDocument document=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            List<Column> columns=r.getVisibleColumns();
            // Wide reports use column bands, repeating loan/reference context where available.
            for(int first=0;first<columns.size();first+=5) {
                List<Column> band=new ArrayList<>();
                if(first>0 && columns.getFirst().field().getType()==OperationalReportDefinition.Type.TEXT) band.add(columns.getFirst());
                band.addAll(columns.subList(first,Math.min(columns.size(),first+5)));
                PdfPage page=new PdfPage(document,r,band,this);
                for(Map<String,Object> row:r.rows()) page.row(band.stream().map(c->string(row.get(c.field().name()))).toList());
                page.close();
            }
            PdfPage totals=new PdfPage(document,r,List.of(),this);
            totals.text(message("report.total",r.definition().language()));
            for(Column column:columns) if(r.totals().containsKey(column.field().name())) totals.text(heading(column,r.definition().language())+": "+string(r.totals().get(column.field().name())));
            totals.text(r.definition().footer()); totals.close();
            int pageNo=0;
            for(PDPage page:document.getPages()) try(PDPageContentStream stream=new PDPageContentStream(document,page,PDPageContentStream.AppendMode.APPEND,true)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),8); stream.newLineAtOffset(35,20); stream.showText(++pageNo+" / "+document.getNumberOfPages()); stream.endText();
            }
            document.save(out); return out.toByteArray();
        }
    }
    private static final class PdfPage implements AutoCloseable {
        private final PDDocument doc; private final OperationalReportService.Result result; private final List<Column> columns; private final OperationalReportExportService exporter;
        private final PDType1Font font=new PDType1Font(Standard14Fonts.FontName.HELVETICA); private PDPageContentStream stream; private float y,width;
        PdfPage(PDDocument doc,OperationalReportService.Result result,List<Column> columns,OperationalReportExportService exporter) throws IOException {
            this.doc=doc; this.result=result; this.columns=columns; this.exporter=exporter; next();
        }
        private void next() throws IOException {
            if(stream!=null)stream.close(); PDRectangle size=result.definition().landscape()?new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()):PDRectangle.A4;
            PDPage page=new PDPage(size); doc.addPage(page); stream=new PDPageContentStream(doc,page); y=size.getHeight()-35; width=size.getWidth()-70;
            for(String line:exporter.context(result))text(line);
            if(!columns.isEmpty())draw(columns.stream().map(c->exporter.heading(c,result.definition().language())).toList());
        }
        private void text(String value) throws IOException {
            for(String line:wrap(value,width)) { if(y<45)next(); write(line,35,y); y-=11; } y-=4;
        }
        void row(List<String> values) throws IOException {
            float height=height(values); if(y-height<40)next(); draw(values);
        }
        private float height(List<String> values) throws IOException {
            float total=columns.stream().mapToInt(Column::width).sum(); int max=1;
            for(int i=0;i<values.size();i++)max=Math.max(max,wrap(values.get(i),width*columns.get(i).width()/total-8).size());
            return max*11+7;
        }
        private void draw(List<String> values) throws IOException {
            float total=columns.stream().mapToInt(Column::width).sum(),x=35; float height=height(values);
            for(int i=0;i<values.size();i++) {float cellWidth=width*columns.get(i).width()/total; float baseline=y;
                for(String line:wrap(values.get(i),cellWidth-8)){write(line,x,baseline);baseline-=11;} x+=cellWidth;
            }
            y-=height;
        }
        private List<String> wrap(String text,float max) throws IOException {
            List<String> lines=new ArrayList<>(); StringBuilder line=new StringBuilder();
            for(int i=0;i<text.length();i++) {String candidate=line.toString()+text.charAt(i);
                if(font.getStringWidth(candidate)/1000*7>max && !line.isEmpty()){lines.add(line.toString());line.setLength(0);} line.append(text.charAt(i));
            }
            lines.add(line.toString()); return lines;
        }
        private void write(String text,float x,float y) throws IOException { stream.beginText(); stream.setFont(font,7);stream.newLineAtOffset(x,y);stream.showText(text);stream.endText(); }
        @Override public void close() throws IOException { stream.close(); }
    }
}
