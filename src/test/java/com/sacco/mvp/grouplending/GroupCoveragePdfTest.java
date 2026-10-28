package com.sacco.mvp.grouplending;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page;
import com.sacco.mvp.grouplending.service.GroupCoveragePdf;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class GroupCoveragePdfTest {
 @Test void bilingualCasePdfIncludesLastHistoryAndCorrectionWithoutPrivateVerification()throws Exception{
  var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");var pdf=new GroupCoveragePdf(new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);UUID id=UUID.randomUUID();var day=LocalDate.of(2026,10,27);var time=day.atStartOfDay().atOffset(ZoneOffset.UTC);var money=new BigDecimal("123.45");
  var c=new Case(id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"PERSONAL",UUID.randomUUID(),"CLIENT-ONE","Named person","Organization borrower","LN-CASE",UUID.randomUUID(),day,day.plusDays(30),day.minusDays(15),money,money,money,money,money,BigDecimal.ZERO,"PROMISED",27,"Exact retained terms","BOTH","HASH",1,time);
  var events=new ArrayList<Event>();for(int n=1;n<=27;n++)events.add(new Event(UUID.randomUUID(),n,"PROMISED",money,day.plusDays(1),"Retained promise "+n,time));UUID reversal=UUID.randomUUID(),correction=UUID.randomUUID();var p=new Payment(id,UUID.randomUUID(),UUID.randomUUID(),"RV-EXACT",day,"Actual payer",money,new BigDecimal("100.00"),new BigDecimal("20.00"),new BigDecimal("3.45"),BigDecimal.ZERO,reversal,correction,"PRIVATE PAYER VERIFICATION",time,null,"PRIVATE CORRECTION REASON",time);
  var detail=new Detail(c,new Page<>(events,0,false),new Page<>(List.of(p),0,false),false,null,null,day);
  for(var locale:List.of(Locale.ENGLISH,Locale.of("sw")))try(var document=Loader.loadPDF(pdf.render(detail,locale))){String text=new PDFTextStripper().getText(document);assertThat(text).contains("LN-CASE","CLIENT-ONE","RV-EXACT","Retained promise 27","123.45","TZS","27/10/2026","28/10/2026","26/11/2026").doesNotContain("PRIVATE PAYER VERIFICATION","PRIVATE CORRECTION REASON","2026-10-27");assertThat(text.replaceAll("\\s+", "")).contains(reversal.toString(),correction.toString());assertThat(text.replaceAll("\\s+", " ")).contains(messages.getMessage("coverage.liabilityNote",null,locale));assertThat(document.getNumberOfPages()).isGreaterThan(1);String name=locale.getLanguage();java.nio.file.Files.write(java.nio.file.Path.of("target/coverage-case-"+name+".pdf"),pdf.render(detail,locale));javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0,90),"png",java.nio.file.Path.of("target/coverage-case-"+name+".png").toFile());javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(document.getNumberOfPages()-1,90),"png",java.nio.file.Path.of("target/coverage-case-"+name+"-last.png").toFile());}
 }
}
