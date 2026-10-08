package com.sacco.mvp.accounting.service;
import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class VoucherPdfTest {
 @Test void receiptPaymentAndJournalPdfsRetainMultiPageRowsAmountsAndBilingualLabels()throws Exception{
  var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
  var exporter=new VoucherPdfService(messages,new ReportExportLimiter(1));
  for(Type type:Type.values())for(Locale locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
   var debit=new Account(UUID.randomUUID(),"111001","Cash at branch","CASH");var credit=new Account(UUID.randomUUID(),"411001","Administration service income","OTHER");
   var rows=new ArrayList<Transaction>();for(int n=1;n<=50;n++)rows.add(new Transaction(UUID.randomUUID(),n,null,null,"","TOTAL","Synthetic transaction "+n+" — equipment and office services "+(n==50?"last-marker":""),new BigDecimal("10000.01"),debit,credit));
   var v=new Voucher(UUID.randomUUID(),"RV-2026-00000001",type,LocalDate.of(2026,10,8),"Synthetic customer — sample only","BANK-REF-1001","Multiple transactions posted together. Synthetic preview, not a live receipt.","INV-2026-012",type==Type.JOURNAL?null:debit.id(),new BigDecimal("500000.50"),50,UUID.randomUUID(),UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-08T10:30:00+03:00"),null,null,"Synthetic Microfinance Ltd","Dar es Salaam — Main branch",rows);
   var result=exporter.render(v,locale);
   try(var doc=Loader.loadPDF(result.bytes())){
    assertThat(doc.getNumberOfPages()).isGreaterThan(3);
    String text=new PDFTextStripper().getText(doc);assertThat(text).contains("last-marker","RV-2026-00000001","111001","411001",messages.getMessage("voucher.title."+type,null,locale));
    assertThat(text.replaceAll("\\s+","")).contains("500,000.50");
    if(type==Type.RECEIPT && locale.equals(Locale.ENGLISH)){
     Files.write(Path.of("target/voucher-preview.pdf"),result.bytes());
     javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,120),"png",Path.of("target/voucher-preview.png").toFile());
     javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(doc.getNumberOfPages()-1,120),"png",Path.of("target/voucher-preview-last.png").toFile());
    }
   }
  }
 }
 @Test void longDescriptionsNeverLoseTheFinalTransaction()throws Exception{
  var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
  var a=new Account(UUID.randomUUID(),"111001","A".repeat(160),"CASH");var b=new Account(UUID.randomUUID(),"211001","B".repeat(160),"OTHER");
  var t=new Transaction(UUID.randomUUID(),1,null,null,"","TOTAL","X".repeat(490)+"-last",new BigDecimal("9999999999999999.99"),a,b);
  var v=new Voucher(UUID.randomUUID(),"PV-2026-99999999",Type.PAYMENT,LocalDate.of(2026,10,8),"P".repeat(160),"R".repeat(160),"D".repeat(500),"E".repeat(500),a.id(),t.amount(),1,UUID.randomUUID(),UUID.randomUUID(),"Test staff",OffsetDateTime.now(),null,null,"I".repeat(160),"B".repeat(500),List.of(t));
  try(var doc=Loader.loadPDF(new VoucherPdfService(messages,new ReportExportLimiter(1)).render(v,Locale.ENGLISH).bytes())){
   assertThat(new PDFTextStripper().getText(doc)).contains("-last","Posted by");
  }
 }
}
