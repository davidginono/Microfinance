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
import static com.sacco.mvp.accounting.reports.VoucherPdfRows.assertRow;

class VoucherPdfTest {
 @Test void journalWithoutPartyExportsTransactionsWithoutAnEmptyPartySection()throws Exception {
  var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
  var debit=new Account(UUID.randomUUID(),"111001","Cash","CASH");var credit=new Account(UUID.randomUUID(),"411001","Income","OTHER");
  var row=new Transaction(UUID.randomUUID(),1,null,null,"","TOTAL","Journal transaction",new BigDecimal("123.45"),debit,credit);
  var voucher=new Voucher(UUID.randomUUID(),"JV-NO-PARTY",Type.JOURNAL,LocalDate.of(2026,10,19),"","REF-JOURNAL","Adjustment","",null,row.amount(),1,UUID.randomUUID(),UUID.randomUUID(),"Accountant",OffsetDateTime.parse("2026-10-19T10:30:00+03:00"),null,null,"Example Microfinance","Main branch",List.of(row));
  try(var doc=Loader.loadPDF(new VoucherPdfService(messages,new ReportExportLimiter(1)).render(voucher,Locale.ENGLISH).bytes())){
   assertThat(new PDFTextStripper().getText(doc)).contains("JV-NO-PARTY","Journal transaction","123.45","111001","411001").doesNotContain(messages.getMessage("voucher.party",null,Locale.ENGLISH));
  }
 }
 @Test void paymentsRetainPostedSidesWhileReceiptsShowTransactionsOnceIncludingReversals()throws Exception {
  var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
  var cash=new Account(UUID.randomUUID(),"111001","QA Cash","CASH");var other=new Account(UUID.randomUUID(),"111002","QA Loan Principal","OTHER");
  var exporter=new VoucherPdfService(messages,new ReportExportLimiter(1));
  for(var type:List.of(Type.PAYMENT,Type.RECEIPT))for(boolean reversal:List.of(false,true)){
   boolean cashDebit=(type==Type.RECEIPT)!=reversal;
   var transaction=new Transaction(UUID.randomUUID(),1,null,null,"","TOTAL","Synthetic account allocation",new BigDecimal("1000.00"),cashDebit?cash:other,cashDebit?other:cash);
   var voucher=new Voucher(UUID.randomUUID(),type+"-ROWS",type,LocalDate.of(2026,10,16),"Synthetic client","REF-ROWS","Synthetic demonstration only.","Reference",cash.id(),transaction.amount(),1,UUID.randomUUID(),UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-16T10:30:00+03:00"),reversal?UUID.randomUUID():null,null,"Example Microfinance Limited","Main branch",List.of(transaction));
   try(var doc=Loader.loadPDF(exporter.render(voucher,Locale.ENGLISH).bytes())){
    if(type==Type.RECEIPT){String text=new PDFTextStripper().getText(doc);assertThat(text).contains("Synthetic account allocation","1,000.00").doesNotContain("111001","111002","QA Cash","QA Loan Principal","DEBIT","CREDIT","ACCOUNT / LEDGER");assertThat(text.split("Synthetic account allocation",-1)).hasSize(2);}
    else {assertRow(doc,"111001",1,cashDebit?"1,000.00":"",cashDebit?"":"1,000.00",type==Type.PAYMENT);
    assertRow(doc,"111002",2,cashDebit?"":"1,000.00",cashDebit?"1,000.00":"",type==Type.PAYMENT);}
   }
  }
 }
 @Test void formalVoucherSamplesAndExactAmountWords()throws Exception {
  assertThat(VoucherPdfService.amountInWords(new BigDecimal("1000.10"),Locale.ENGLISH)).isEqualTo("one thousand Tanzanian shillings and ten cents");
  assertThat(VoucherPdfService.amountInWords(new BigDecimal("0.01"),Locale.ENGLISH)).isEqualTo("zero Tanzanian shillings and one cent");
  assertThat(VoucherPdfService.amountInWords(new BigDecimal("500000.50"),Locale.forLanguageTag("sw"))).isEqualTo("Shilingi za Tanzania elfu mia tano na senti hamsini");
  assertThat(VoucherPdfService.amountInWords(new BigDecimal("9999999999999999.99"),Locale.ENGLISH)).contains("quadrillion","ninety-nine cents");
  var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
  var cash=new Account(UUID.randomUUID(),"111001","Branch bank account","BANK");var other=new Account(UUID.randomUUID(),"411001","Service income","OTHER");
  for(var type:Type.values()){
   var rows=new ArrayList<Transaction>();for(int i=1;i<=3;i++)rows.add(new Transaction(UUID.randomUUID(),i,null,null,"","TOTAL","Office services - transaction "+i,new BigDecimal("10000.25"),type==Type.PAYMENT?other:cash,type==Type.PAYMENT?cash:other));
   var v=new Voucher(UUID.randomUUID(),type+"-SAMPLE",type,LocalDate.of(2026,10,11),"Synthetic Example Company","REF-2026-1042","Office services and materials. Synthetic demonstration only.","INV-2026-114",type==Type.JOURNAL?null:cash.id(),new BigDecimal("30000.75"),3,UUID.randomUUID(),UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-11T11:30:00+03:00"),null,null,"Example Microfinance Limited","Dar es Salaam - Main branch",rows,UUID.randomUUID(),"CL-000045");
   var output=new VoucherPdfService(messages,new ReportExportLimiter(1)).render(v,Locale.ENGLISH);
   try(var doc=Loader.loadPDF(output.bytes())){
    assertThat(doc.getNumberOfPages()).isEqualTo(1);String text=new PDFTextStripper().getText(doc);
    assertThat(text).contains("AMOUNT IN WORDS","REMARKS / NARRATION","POSTING RECORD","30,000.75","thirty thousand").doesNotContain("APPROVALS","CHECKED BY","APPROVED BY","Details");
    if(type!=Type.JOURNAL){
     var numberColumn=new org.apache.pdfbox.text.PDFTextStripperByArea();
     numberColumn.addRegion("numbers",new java.awt.geom.Rectangle2D.Float(22,0,30,800));numberColumn.extractRegions(doc.getPage(0));
     var numbers=numberColumn.getTextForRegion("numbers").lines().map(String::strip).filter(value->value.matches("[0-9]+")).toList();
     assertThat(numbers).as("Only populated %s rows are numbered",type).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1,type==Type.RECEIPT?3:6).mapToObj(Integer::toString).toList());
    }
    Files.write(Path.of("target/"+type.name().toLowerCase()+"-format.pdf"),output.bytes());
    javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,120),"png",Path.of("target/"+type.name().toLowerCase()+"-format.png").toFile());
   }
  }
 }
 @Test void receiptPaymentAndJournalPdfsRetainMultiPageRowsAmountsAndBilingualLabels()throws Exception{
  var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
  var exporter=new VoucherPdfService(messages,new ReportExportLimiter(1));
  for(Type type:Type.values())for(Locale locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
   var debit=new Account(UUID.randomUUID(),"111001","Cash at branch","CASH");var credit=new Account(UUID.randomUUID(),"411001","Administration service income","OTHER");
   var rows=new ArrayList<Transaction>();for(int n=1;n<=50;n++)rows.add(new Transaction(UUID.randomUUID(),n,null,null,"","TOTAL","Synthetic transaction "+n+" — equipment and office services "+(n==50?"last-marker":""),new BigDecimal("10000.01"),debit,credit));
   var v=new Voucher(UUID.randomUUID(),"RV-2026-00000001",type,LocalDate.of(2026,10,8),"Synthetic customer — sample only","BANK-REF-1001","Multiple transactions posted together. Synthetic preview, not a live receipt.","INV-2026-012",type==Type.JOURNAL?null:debit.id(),new BigDecimal("500000.50"),50,UUID.randomUUID(),UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-08T10:30:00+03:00"),null,null,"Synthetic Microfinance Ltd","Dar es Salaam — Main branch",rows,UUID.randomUUID(),"CL-000045");
   var result=exporter.render(v,locale);
   try(var doc=Loader.loadPDF(result.bytes())){
    assertThat(doc.getNumberOfPages()).isGreaterThan(type==Type.RECEIPT?1:3);
    String text=new PDFTextStripper().getText(doc);assertThat(text).contains("CL-000045");assertThat(text).contains("last-marker","RV-2026-00000001",type==Type.JOURNAL?messages.getMessage("voucher.title."+type,null,locale):messages.getMessage("voucher.title."+type,null,locale).toUpperCase(locale));
    if(type==Type.RECEIPT)assertThat(text).doesNotContain("111001","411001",messages.getMessage("accounting.debit",null,locale).toUpperCase(locale),messages.getMessage("accounting.credit",null,locale).toUpperCase(locale));else assertThat(text).contains("111001","411001");
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
   assertThat(new PDFTextStripper().getText(doc)).contains("-last","POSTED BY");
   for(int n=0;n<doc.getNumberOfPages();n++)javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(n,100),"png",Path.of("target/voucher-long-"+n+".png").toFile());
  }
 }
}
