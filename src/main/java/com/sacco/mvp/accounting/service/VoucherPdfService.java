package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.Voucher;
import com.sacco.mvp.service.ReportExportLimiter;
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
import java.util.*;

@Service @RequiredArgsConstructor
public class VoucherPdfService {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    public record Output(byte[] bytes,String filename) {}
    public Output render(Voucher voucher,Locale locale){return limiter.run(()->{
        try(var document=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(document,fontStream);
            try(var layout=new Layout(document,font,voucher,locale)){layout.write();}
            document.getDocumentInformation().setTitle(label("voucher.title."+voucher.type(),locale)+" "+voucher.number());
            document.save(out);return new Output(out.toByteArray(),voucher.number()+".pdf");
        }catch(IOException failure){throw new IllegalStateException("Voucher PDF generation failed",failure);}
    });}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private final class Layout implements AutoCloseable {
        private final PDDocument document;private final PDType0Font font;private final Voucher v;private final Locale locale;
        private PDPageContentStream stream;private float y;private int page;
        private final Color ink=new Color(31,41,55),muted=new Color(82,96,109),teal=new Color(15,91,100),border=new Color(215,221,227);
        Layout(PDDocument document,PDType0Font font,Voucher voucher,Locale locale){this.document=document;this.font=font;this.v=voucher;this.locale=locale;}
        void write()throws IOException{
            page();
            pair(label("voucher.date",locale),v.effectiveDate().toString(),label("voucher.reference",locale),v.reference());
            pair(label(v.type().name().equals("RECEIPT")?"voucher.receivedFrom":v.type().name().equals("PAYMENT")?"voucher.paidTo":"voucher.party",locale),v.party(),label("voucher.status",locale),label("voucher.status."+v.status(),locale));
            if(!v.description().isBlank())paragraph(label("voucher.description",locale)+": "+v.description(),9,muted);
            if(!v.evidence().isBlank())paragraph(label("voucher.evidence",locale)+": "+v.evidence(),9,muted);
            y-=10;tableHeader();
            for(var t:v.transactions()){
                String text=t.description();if(!t.transactionName().isBlank())text+="\n"+t.transactionName();
                text+="\n"+label("accounting.debit",locale)+": "+t.debit().code()+" - "+t.debit().name();
                text+="\n"+label("accounting.credit",locale)+": "+t.credit().code()+" - "+t.credit().name();
                if(!"TOTAL".equals(t.component()))text+="\n"+label("library.component."+t.component(),locale);
                var lines=wrap(text,9,328);int n=0;
                while(n<lines.size()){
                    if(y<95){page();tableHeader();}
                    int take=Math.min(lines.size()-n,Math.max(1,(int)((y-82)/13)));
                    float height=take*13+14;fill(36,y-height,523,height,t.lineNo()%2==0?new Color(247,249,250):Color.WHITE);
                    draw(44,y-14,9,ink,Integer.toString(t.lineNo()));
                    for(int j=0;j<take;j++)draw(76,y-14-j*13,9,ink,lines.get(n+j));
                    if(n==0)right(548,y-14,10,ink,money(t.amount()));
                    y-=height;rule(y);n+=take;
                }
            }
            if(y<160)page();y-=14;fill(340,y-38,219,38,new Color(233,243,244));
            draw(352,y-15,10,teal,label("voucher.total",locale)+" (TZS)");right(548,y-30,15,teal,money(v.total()));y-=60;
            paragraph(label("voucher.postedBy",locale)+": "+v.postedByName(),9,ink);
            paragraph(label("voucher.postedAt",locale)+": "+v.postedAt().toLocalDateTime().toString().replace('T',' '),9,muted);
            if(v.reversesId()!=null)paragraph(label("voucher.reversal",locale)+": "+v.reference(),9,muted);
            close();
            for(int n=0;n<document.getNumberOfPages();n++)try(var footer=new PDPageContentStream(document,document.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
                stream=footer;draw(36,30,8,muted,v.number()+" | "+label("voucher.posted",locale));
                right(559,30,8,muted,label("voucher.page",locale)+" "+(n+1)+" / "+document.getNumberOfPages());
            }stream=null;
        }
        private void page()throws IOException{
            close();var p=new PDPage(PDRectangle.A4);document.addPage(p);stream=new PDPageContentStream(document,p);page++;
            fill(0,763,595,79,teal);
            var institution=wrap(v.institutionName(),14,520);int line=0;
            for(String name:institution){if(line==2)break;draw(36,814-line*18,14,Color.WHITE,name);line++;}
            draw(36,775,9,Color.WHITE,wrap(v.branchName(),9,520).getFirst());y=738;
            draw(36,y,18,teal,label("voucher.title."+v.type(),locale));y-=25;
            draw(36,y,11,ink,v.number());right(559,y,10,teal,label("voucher.status."+v.status(),locale));y-=24;rule(y);y-=18;
            if(page>1){draw(36,y,9,muted,label("voucher.continued",locale));y-=22;}
        }
        private void pair(String leftLabel,String left,String rightLabel,String right)throws IOException{
            var l=wrap(left,10,248);var r=wrap(right,10,248);int height=Math.max(l.size(),r.size());
            if(y-height*14-35<65)page();draw(36,y,8,muted,leftLabel);draw(310,y,8,muted,rightLabel);y-=16;
            for(int n=0;n<height;n++){if(n<l.size())draw(36,y,10,ink,l.get(n));if(n<r.size())draw(310,y,10,ink,r.get(n));y-=14;}y-=13;
        }
        private void tableHeader()throws IOException{fill(36,y-27,523,27,teal);draw(44,y-17,9,Color.WHITE,"#");draw(76,y-17,9,Color.WHITE,label("voucher.transactions",locale));right(548,y-17,9,Color.WHITE,label("voucher.amount",locale)+" (TZS)");y-=27;}
        private void paragraph(String text,float size,Color color)throws IOException{for(var line:wrap(text,size,523)){if(y<70)page();draw(36,y,size,color,line);y-=14;}y-=5;}
        private String money(BigDecimal value){var format=NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(value);}
        private List<String> wrap(String value,float size,float width)throws IOException{
            var result=new ArrayList<String>();var line=new StringBuilder();
            for(int cp:value.codePoints().toArray()){
                if(cp=='\n'){result.add(line.toString());line.setLength(0);continue;}
                String c=new String(Character.toChars(cp));try{font.encode(c);}catch(IllegalArgumentException unsupported){c="?";}
                if(line.length()>0 && font.getStringWidth(line+c)*size/1000>width){result.add(line.toString());line.setLength(0);}line.append(c);
            }result.add(line.toString());return result;
        }
        private void draw(float x,float top,float size,Color color,String value)throws IOException{stream.beginText();stream.setNonStrokingColor(color);stream.setFont(font,size);stream.newLineAtOffset(x,top);stream.showText(value);stream.endText();}
        private void right(float x,float top,float size,Color color,String value)throws IOException{draw(x-font.getStringWidth(value)*size/1000,top,size,color,value);}
        private void fill(float x,float bottom,float width,float height,Color color)throws IOException{stream.setNonStrokingColor(color);stream.addRect(x,bottom,width,height);stream.fill();}
        private void rule(float top)throws IOException{stream.setStrokingColor(border);stream.setLineWidth(.5f);stream.moveTo(36,top);stream.lineTo(559,top);stream.stroke();}
        public void close()throws IOException{if(stream!=null){stream.close();stream=null;}}
    }
}
