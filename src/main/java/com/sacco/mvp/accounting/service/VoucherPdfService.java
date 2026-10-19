package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.Voucher;
import com.sacco.mvp.service.ReportExportLimiter;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument.Field;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument.Source;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class VoucherPdfService {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    public record Output(byte[] bytes,String filename) {}
    public Output render(Voucher voucher,Locale locale){return render(voucher,locale,null);}
    public Output render(Voucher voucher,Locale locale,byte[] logo){return limiter.run(()->{
        try(var document=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(document,fontStream);
            if(voucher.type()==com.sacco.mvp.accounting.dto.VoucherDtos.Type.JOURNAL){
                try(var layout=new Layout(document,font,voucher,locale)){layout.write();}
            }else try(var layout=new VoucherPdfDocument(document,font,stationery(voucher,locale,logo))){layout.write();}
            document.getDocumentInformation().setTitle(label("voucher.title."+voucher.type(),locale)+" "+voucher.number());
            document.save(out);return new Output(out.toByteArray(),voucher.number()+".pdf");
        }catch(IOException failure){throw new IllegalStateException("Voucher PDF generation failed",failure);}
    });}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    public static String amountInWords(BigDecimal amount,Locale locale){
        boolean sw="sw".equals(locale.getLanguage());var value=amount.abs().setScale(2,java.math.RoundingMode.UNNECESSARY);
        // Convert the integral part separately: cents never pass through floating point.
        var whole=value.toBigInteger();int cents=value.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
        String words=numberWords(whole,sw);
        return (amount.signum()<0?(sw?"Hasi ":"Minus "):"")+(sw?"Shilingi za Tanzania "+words+" na senti "+underThousand(cents,true):words+" Tanzanian "+(whole.equals(java.math.BigInteger.ONE)?"shilling":"shillings")+" and "+underThousand(cents,false)+(cents==1?" cent":" cents"));
    }
    private Source stationery(Voucher v,Locale locale,byte[] logo){
        boolean receipt=v.type()==com.sacco.mvp.accounting.dto.VoucherDtos.Type.RECEIPT;
        var rows=new ArrayList<List<String>>();
        for(var t:v.transactions()){
            String description=t.description();if(!t.transactionName().isBlank())description+="\n"+t.transactionName();
            if(!"TOTAL".equals(t.component()))description+="\n"+label("library.component."+t.component(),locale);
            if(receipt){rows.add(List.of(Integer.toString(t.lineNo()),description,money(t.amount(),locale)));continue;}
            var first=t.debit().id().equals(v.moneyAccountId())?t.debit():t.credit();
            var second=first==t.debit()?t.credit():t.debit();
            for(var account:List.of(first,second)){
                boolean debit=account==t.debit();String amount=money(t.amount(),locale);
                var row=List.of(Integer.toString(rows.size()+1),description,account.code()+" - "+account.name(),debit?amount:"",debit?"":amount,amount);
                rows.add(receipt?row:row.subList(0,5));
            }
        }
        var moneyAccount=v.transactions().stream().flatMap(t->java.util.stream.Stream.of(t.debit(),t.credit()))
            .filter(a->a.id().equals(v.moneyAccountId())).findFirst();
        String account=moneyAccount.map(a->a.code()+" - "+a.name()).orElse("");
        String channel=moneyAccount.map(a->a.purpose()).orElse("");
        var totals=new ArrayList<Field>();
        if(!receipt){totals.add(field("voucher.print.totalDebit",money(v.total(),locale),locale));totals.add(field("voucher.print.totalCredit",money(v.total(),locale),locale));}
        totals.add(field("voucher.print.totalAmount",money(v.total(),locale),locale));
        var posting=new ArrayList<Field>(List.of(field("voucher.postedBy",v.postedByName(),locale),
            field("voucher.postedAt",v.postedAt().atZoneSameInstant(java.time.ZoneId.of("Africa/Dar_es_Salaam")).format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss z")),locale),
            field("voucher.status",label("voucher.status."+v.status(),locale),locale)));
        if(v.reversesId()!=null)posting.add(field("voucher.reversal",v.reference(),locale));
        if(v.reversedBy()!=null)posting.add(field("voucher.reversal",v.reversedBy().toString(),locale));
        var header=new ArrayList<Field>(List.of(field("voucher.print.number",v.number(),locale),field(receipt?"voucher.print.receiptDate":"voucher.print.paymentDate",v.effectiveDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),locale),field("voucher.print.branch",v.branchName(),locale)));
        if(v.clientNumber()!=null)header.add(field("recording.statement.clientNumber",v.clientNumber(),locale));
        return new Source(label("voucher.title."+v.type(),locale),v.institutionName(),v.branchName(),v.number(),label("voucher.status."+v.status(),locale),
            header,
            label(receipt?"voucher.print.payerDetails":"voucher.print.payeeDetails",locale),
            List.of(field("voucher.print.name",v.party(),locale),field("voucher.print.company","",locale),field("voucher.print.address","",locale),field("voucher.print.phone","",locale),field("voucher.print.email","",locale)),
            label(receipt?"voucher.print.receiptDetails":"voucher.print.paymentHeading",locale),
            receipt?List.of(field("voucher.print.reference",v.reference(),locale),field("voucher.date",v.effectiveDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),locale)):List.of(field("voucher.print.moneyAccount",account,locale),field("voucher.print.reference",v.reference(),locale),field("voucher.date",v.effectiveDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),locale)),
            receipt?List.of(label("voucher.print.no",locale),label("voucher.print.particulars",locale),label("voucher.amount",locale)+" (TZS)"):List.of(label("voucher.print.no",locale),label("voucher.print.particulars",locale),label("voucher.print.ledger",locale),label("accounting.debit",locale)+" (TZS)",label("accounting.credit",locale)+" (TZS)"),
            receipt?new float[]{.06f,.74f,.20f}:new float[]{.055f,.26f,.325f,.18f,.18f},rows,
            label("voucher.print.amountWords",locale),amountInWords(v.total(),locale),totals,label("voucher.print.narration",locale),v.description()+(v.evidence().isBlank()?"":"\n"+label("voucher.evidence",locale)+": "+v.evidence()),
            label("voucher.print.postingRecord",locale),posting,label("voucher.page",locale),label("voucher.continued",locale),logo,
            label("voucher.print.method",locale),List.of(method("CASH",channel,locale),method("BANK",channel,locale),method("CHEQUE",channel,locale),method("MOBILE_MONEY",channel,locale)));
    }
    private Field method(String code,String channel,Locale locale){return new Field(label("voucher.print.method."+code,locale),Boolean.toString(code.equals(channel)));}
    private Field field(String key,String value,Locale locale){return new Field(label(key,locale),value);}
    private String money(BigDecimal value,Locale locale){var format=NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(value);}
    private static String numberWords(java.math.BigInteger value,boolean sw){
        if(value.signum()==0)return sw?"sifuri":"zero";
        String[] scales=sw?new String[]{"","elfu","milioni","bilioni","trilioni","kwadrilioni","kwintilioni"}:new String[]{"","thousand","million","billion","trillion","quadrillion","quintillion"};
        var parts=new ArrayList<String>();int group=0;
        while(value.signum()>0){var split=value.divideAndRemainder(java.math.BigInteger.valueOf(1000));int n=split[1].intValueExact();
            if(n!=0)parts.add(sw&&group>0?scales[group]+" "+underThousand(n,true):underThousand(n,sw)+(group==0?"":" "+scales[group]));value=split[0];group++;}
        Collections.reverse(parts);return String.join(sw?" na ":" ",parts);
    }
    private static String underThousand(int n,boolean sw){
        String[] small=sw?new String[]{"sifuri","moja","mbili","tatu","nne","tano","sita","saba","nane","tisa","kumi","kumi na moja","kumi na mbili","kumi na tatu","kumi na nne","kumi na tano","kumi na sita","kumi na saba","kumi na nane","kumi na tisa"}:new String[]{"zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
        String[] tens=sw?new String[]{"","","ishirini","thelathini","arobaini","hamsini","sitini","sabini","themanini","tisini"}:new String[]{"","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"};
        if(n<20)return small[n];if(n<100)return tens[n/10]+(n%10==0?"":(sw?" na ":"-")+small[n%10]);
        return (sw?"mia "+small[n/100]:small[n/100]+" hundred")+(n%100==0?"":(sw?" na ":" and ")+underThousand(n%100,sw));
    }
    private final class Layout implements AutoCloseable {
        private final PDDocument document;private final PDType0Font font;private final Voucher v;private final Locale locale;
        private PDPageContentStream stream;private float y;private int page;
        private final Color ink=Color.BLACK,muted=new Color(70,70,70),border=new Color(130,130,130);
        Layout(PDDocument document,PDType0Font font,Voucher voucher,Locale locale){this.document=document;this.font=font;this.v=voucher;this.locale=locale;}
        void write()throws IOException{
            page();
            pair(label("voucher.date",locale),v.effectiveDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),label("voucher.reference",locale),v.reference());
            if(!v.party().isBlank()){
                section(label(v.type().name().equals("RECEIPT")?"voucher.print.payer":v.type().name().equals("PAYMENT")?"voucher.print.payee":"voucher.party",locale));
                pair(label("voucher.print.name",locale),v.party(),label(v.moneyAccountId()==null?"voucher.type":"voucher.print.paymentDetails",locale),moneyAccount());
            }
            if(v.clientNumber()!=null)paragraph(label("recording.statement.clientNumber",locale)+": "+v.clientNumber(),9,ink);
            if(!v.evidence().isBlank())paragraph(label("voucher.evidence",locale)+": "+v.evidence(),9,muted);
            y-=10;tableHeader();
            for(var t:v.transactions()){
                String text=t.description();if(!t.transactionName().isBlank())text+="\n"+t.transactionName();
                if(!"TOTAL".equals(t.component()))text+="\n"+label("library.component."+t.component(),locale);
                String accounts=label("accounting.debit",locale)+": "+t.debit().code()+" - "+t.debit().name()+"\n"+label("accounting.credit",locale)+": "+t.credit().code()+" - "+t.credit().name();
                var lines=wrap(text,9,v.type().name().equals("RECEIPT")?239:176);var refs=wrap(accounts,8,v.type().name().equals("RECEIPT")?147:115);int n=0;int count=Math.max(lines.size(),refs.size());
                while(n<count){
                    if(y<95){page();tableHeader();}
                    int take=Math.min(count-n,Math.max(1,(int)((y-82)/13)));
                    float height=take*13+14;
                    draw(44,y-14,9,ink,Integer.toString(t.lineNo()));
                    for(int j=0;j<take;j++){if(n+j<lines.size())draw(66,y-14-j*13,9,ink,lines.get(n+j));if(n+j<refs.size())draw(v.type().name().equals("RECEIPT")?315:253,y-14-j*13,8,ink,refs.get(n+j));}
                    if(n==0){amount(553,y-14,t.amount());if(v.type()!=com.sacco.mvp.accounting.dto.VoucherDtos.Type.RECEIPT)amount(463,y-14,t.amount());}
                    y-=height;rule(y);n+=take;
                }
            }
            // Keep short vouchers on formal stationery without creating fictitious transaction rows.
            for(int blank=v.transactions().size();blank<10 && y>370;blank++){y-=18;rule(y);}
            if(y<245)page();y-=18;
            section(label("voucher.print.amountWords",locale));paragraph(amountInWords(v.total(),locale),10,ink);
            if(v.type()!=com.sacco.mvp.accounting.dto.VoucherDtos.Type.RECEIPT){total("voucher.print.totalDebit");total("voucher.print.totalCredit");}
            total("voucher.total");y-=10;
            section(label("voucher.print.narration",locale));paragraph(v.description().isBlank()?"-":v.description(),9,ink);
            section(label("voucher.print.postingRecord",locale));
            paragraph(label("voucher.postedBy",locale)+": "+v.postedByName(),9,ink);
            paragraph(label("voucher.postedAt",locale)+": "+v.postedAt().atZoneSameInstant(java.time.ZoneId.of("Africa/Dar_es_Salaam")).format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss z")),9,muted);
            if(v.reversesId()!=null)paragraph(label("voucher.reversal",locale)+": "+v.reference(),9,muted);
            if(v.reversedBy()!=null)paragraph(label("voucher.reversedHelp",locale)+" "+v.reversedBy(),9,muted);
            close();
            for(int n=0;n<document.getNumberOfPages();n++)try(var footer=new PDPageContentStream(document,document.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
                stream=footer;draw(36,30,8,muted,v.number()+" | "+label("voucher.status."+v.status(),locale));
                right(559,30,8,muted,label("voucher.page",locale)+" "+(n+1)+" / "+document.getNumberOfPages());
            }stream=null;
        }
        private void page()throws IOException{
            close();var p=new PDPage(PDRectangle.A4);document.addPage(p);stream=new PDPageContentStream(document,p);page++;
            y=803;var institution=wrap(v.institutionName(),13,246);var title=wrap(label("voucher.title."+v.type(),locale),17,250);
            for(int n=0;n<Math.max(institution.size(),title.size());n++){if(n<institution.size())draw(36,y,13,ink,institution.get(n));if(n<title.size())draw(309,y,17,ink,title.get(n));y-=20;}
            paragraph(v.branchName(),9,muted);
            draw(36,y,10,ink,label("voucher.print.number",locale)+": "+v.number());right(559,y,9,ink,label("voucher.status."+v.status(),locale));y-=20;rule(y);y-=18;
            if(page>1){draw(36,y,9,muted,label("voucher.continued",locale));y-=22;}
        }
        private void pair(String leftLabel,String left,String rightLabel,String right)throws IOException{
            var l=wrap(left,10,248);var r=wrap(right,10,248);int height=Math.max(l.size(),r.size());
            if(y-height*14-35<65)page();draw(36,y,8,muted,leftLabel);draw(310,y,8,muted,rightLabel);y-=16;
            for(int n=0;n<height;n++){if(n<l.size())draw(36,y,10,ink,l.get(n));if(n<r.size())draw(310,y,10,ink,r.get(n));y-=14;}y-=13;
        }
        private void section(String title)throws IOException{if(y<100)page();draw(36,y,10,ink,title.toUpperCase(locale));y-=8;rule(y);y-=18;}
        private void total(String key)throws IOException{
            var lines=wrap(label(key,locale)+" (TZS)",9,95);if(y-lines.size()*13<70)page();
            for(int n=0;n<lines.size();n++)draw(310,y-n*13,9,ink,lines.get(n));
            String value=money(v.total());right(553,y,Math.min(11,140*1000/font.getStringWidth(value)),ink,value);
            y-=lines.size()*13;rule(y+3);y-=13;
        }
        private String moneyAccount(){if(v.moneyAccountId()==null)return label("voucher.title.JOURNAL",locale);return v.transactions().stream().flatMap(t->java.util.stream.Stream.of(t.debit(),t.credit())).filter(a->a.id().equals(v.moneyAccountId())).findFirst().map(a->a.code()+" - "+a.name()).orElse("-");}
        private void tableHeader()throws IOException{
            fill(36,y-38,523,38,new Color(235,235,235));draw(44,y-20,8,ink,"#");draw(66,y-20,8,ink,label("voucher.print.particulars",locale));
            draw(v.type().name().equals("RECEIPT")?315:253,y-20,8,ink,label("voucher.print.ledger",locale));
            if(v.type()!=com.sacco.mvp.accounting.dto.VoucherDtos.Type.RECEIPT){right(463,y-16,8,ink,label("accounting.debit",locale));right(553,y-16,8,ink,label("accounting.credit",locale));right(463,y-30,8,ink,"TZS");right(553,y-30,8,ink,"TZS");}
            else{right(553,y-16,8,ink,label("voucher.amount",locale));right(553,y-30,8,ink,"TZS");}y-=38;
        }
        private void paragraph(String text,float size,Color color)throws IOException{for(var line:wrap(text,size,523)){if(y<70)page();draw(36,y,size,color,line);y-=14;}y-=5;}
        private String money(BigDecimal value){var format=NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(value);}
        private void amount(float x,float top,BigDecimal value)throws IOException{String text=money(value);right(x,top,Math.min(8,80*1000/font.getStringWidth(text)),ink,text);}
        private List<String> wrap(String value,float size,float width)throws IOException{
            var result=new ArrayList<String>();var line=new StringBuilder();
            for(int cp:value.codePoints().toArray()){
                if(cp=='\n'){result.add(line.toString());line.setLength(0);continue;}
                String c=new String(Character.toChars(cp));try{font.encode(c);}catch(IllegalArgumentException unsupported){c="?";}
                if(line.length()>0 && font.getStringWidth(line+c)*size/1000>width){
                    int space=line.lastIndexOf(" ");String rest=space>0?line.substring(space+1):"";
                    result.add(space>0?line.substring(0,space):line.toString());line.setLength(0);line.append(rest);
                }line.append(c);
            }result.add(line.toString());return result;
        }
        private void draw(float x,float top,float size,Color color,String value)throws IOException{stream.beginText();stream.setNonStrokingColor(color);stream.setFont(font,size);stream.newLineAtOffset(x,top);stream.showText(value);stream.endText();}
        private void right(float x,float top,float size,Color color,String value)throws IOException{draw(x-font.getStringWidth(value)*size/1000,top,size,color,value);}
        private void fill(float x,float bottom,float width,float height,Color color)throws IOException{stream.setNonStrokingColor(color);stream.addRect(x,bottom,width,height);stream.fill();}
        private void rule(float top)throws IOException{stream.setStrokingColor(border);stream.setLineWidth(.5f);stream.moveTo(36,top);stream.lineTo(559,top);stream.stroke();}
        public void close()throws IOException{if(stream!=null){stream.close();stream=null;}}
    }
}
