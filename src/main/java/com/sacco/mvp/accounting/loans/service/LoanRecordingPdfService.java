package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanRecordingPdfService {
    private final ReportExportLimiter limiter;
    public record Document(String title,String institution,String branch,String filename,List<String> lines) {}
    public byte[] render(Document source){return limiter.run(()->{
        try(var doc=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(doc,fontStream);PDPageContentStream stream=null;float y=0;
            try{
                for(var value:source.lines())for(var line:wrap(font,value,9,520)){
                    if(stream==null || y<60){if(stream!=null)stream.close();var page=new PDPage(PDRectangle.A4);doc.addPage(page);stream=new PDPageContentStream(doc,page);
                        stream.setNonStrokingColor(new Color(15,91,100));stream.addRect(0,745,595,97);stream.fill();
                        float head=816;for(var name:wrap(font,source.institution(),13,520)){draw(stream,font,36,head,13,Color.WHITE,name);head-=17;if(head<783)break;}
                        draw(stream,font,36,763,9,Color.WHITE,wrap(font,source.branch(),9,520).getFirst());y=722;
                        for(var title:wrap(font,source.title(),15,520)){draw(stream,font,36,y,15,new Color(15,91,100),title);y-=20;}y-=12;
                    }
                    draw(stream,font,36,y,9,new Color(31,41,55),line);y-=14;
                }
            }finally{if(stream!=null)stream.close();}
            for(int n=0;n<doc.getNumberOfPages();n++)try(var footer=new PDPageContentStream(doc,doc.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
                draw(footer,font,36,28,8,Color.GRAY,source.title()+" | "+(n+1)+" / "+doc.getNumberOfPages());
            }
            doc.getDocumentInformation().setTitle(source.title());doc.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Loan document generation failed",e);}
    });}
    private static List<String> wrap(PDType0Font font,String value,float size,float width)throws IOException{
        var rows=new ArrayList<String>();var line=new StringBuilder();
        for(int point:Objects.toString(value,"").codePoints().toArray()){
            if(point=='\n'){rows.add(line.toString());line.setLength(0);continue;}
            String ch=new String(Character.toChars(point));try{font.encode(ch);}catch(IllegalArgumentException e){ch="?";}
            if(line.length()>0 && font.getStringWidth(line+ch)*size/1000>width){rows.add(line.toString());line.setLength(0);}line.append(ch);
        }rows.add(line.toString());return rows;
    }
    private static void draw(PDPageContentStream stream,PDType0Font font,float x,float y,float size,Color color,String text)throws IOException{stream.beginText();stream.setNonStrokingColor(color);stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(text);stream.endText();}
}
