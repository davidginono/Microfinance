package com.sacco.mvp.accounting.payroll;

import com.sacco.mvp.accounting.payroll.service.PayrollFeedbackWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

public class PayrollFeedbackWorkbookTest {
    public static byte[] workbook(List<String[]> rows){return workbook(rows,b->{});}
    public static byte[] workbook(List<String[]> rows,Consumer<XSSFWorkbook> change){try(var b=new XSSFWorkbook();var out=new ByteArrayOutputStream()){var s=b.createSheet("Results");var head=s.createRow(0);for(int c=0;c<7;c++)head.createCell(c).setCellValue(PayrollFeedbackWorkbook.HEADERS.get(c));for(int n=0;n<rows.size();n++){var r=s.createRow(n+1);for(int c=0;c<rows.get(n).length;c++)r.createCell(c).setCellValue(rows.get(n)[c]);}change.accept(b);b.write(out);return out.toByteArray();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    static String[] row(String employee,String amount,String status){return new String[]{"PAY-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT),employee,UUID.randomUUID().toString(),status,amount,status.equals("SUCCESS")?"2026-10-08":"","EMPLOYER-001"};}
    @Test void preservesLeadingZerosAndExactDecimalsAndFailedRows(){var inputs=new PayrollFeedbackWorkbook().read(workbook(Arrays.asList(row("00007","100.01","SUCCESS"),row("00008","0","FAILED"))));assertThat(inputs).hasSize(2);assertThat(inputs.getFirst().employee()).isEqualTo("00007");assertThat(inputs.getFirst().amount()).isEqualByComparingTo("100.01");assertThat(inputs.getLast().date()).isNull();}
    @Test void rejectsFormulasNumericIdentifiersExtraSheetsAndWrongHeaders(){for(var change:List.<Consumer<XSSFWorkbook>>of(b->b.getSheetAt(0).getRow(1).getCell(4).setCellFormula("100+1"),b->b.getSheetAt(0).getRow(1).getCell(1).setCellValue(7),b->b.createSheet("Extra"),b->b.getSheetAt(0).getRow(0).getCell(1).setCellValue("Employee"))){assertThatThrownBy(()->new PayrollFeedbackWorkbook().read(workbook(Collections.singletonList(row("0007","100","SUCCESS")),change))).isInstanceOf(IllegalArgumentException.class);}}
    @Test void rejectsDuplicatesInvalidAmountsMissingSuccessDatesAndOversizedFiles(){var a=row("0007","1","SUCCESS");var b=a.clone();b[2]=UUID.randomUUID().toString();assertThatThrownBy(()->new PayrollFeedbackWorkbook().read(workbook(Arrays.asList(a,b)))).hasMessage("payroll.feedback.error.row");for(var value:List.of("1.001","-1","0","1,000","=1+1")){assertThatThrownBy(()->new PayrollFeedbackWorkbook().read(workbook(Collections.singletonList(row("007",value,"SUCCESS"))))).isInstanceOf(IllegalArgumentException.class);}assertThatThrownBy(()->new PayrollFeedbackWorkbook().read(new byte[PayrollFeedbackWorkbook.MAX_BYTES+1])).hasMessage("payroll.feedback.error.file");assertThatThrownBy(()->new PayrollFeedbackWorkbook().read(new byte[]{1,2,3})).hasMessage("payroll.feedback.error.file");}
    @Test void rejectsMacroEnabledWorkbooksAndBoundedDecompression()throws Exception {
        assertThatThrownBy(()->new PayrollFeedbackWorkbook().read(workbook(Collections.singletonList(row("0007","1","SUCCESS")),b->b.setWorkbookType(org.apache.poi.xssf.usermodel.XSSFWorkbookType.XLSM)))).hasMessage("payroll.feedback.error.file");
        var out=new ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(out)){zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));zip.write(new byte[5*1024*1024]);zip.closeEntry();}
        assertThat(out.size()).isLessThan(PayrollFeedbackWorkbook.MAX_BYTES);assertThatThrownBy(()->new PayrollFeedbackWorkbook().read(out.toByteArray())).hasMessage("payroll.feedback.error.file");
    }
}
