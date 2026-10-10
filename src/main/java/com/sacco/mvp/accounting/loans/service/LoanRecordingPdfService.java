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
    public record StatementField(String label,String value) {}
    public record StatementSection(List<StatementField> fields,List<String> columns,List<List<String>> rows) {}
    public record StatementDocument(String title,List<StatementField> client,List<StatementSection> sections,String empty,String note) {}
    public byte[] renderStatement(StatementDocument source){return limiter.run(()->{
        try(var doc=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(doc,fontStream);
            var sections=source.sections().isEmpty()?List.of(new StatementSection(List.of(),List.of(),List.of())):source.sections();
            for(var section:sections){
                PDPageContentStream stream=null;float y=0;
                try{
                    var rows=section.rows().isEmpty()?List.of(List.of(source.empty())):section.rows();
                    for(var row:rows){
                        float[] widths={68,78,70,72,68,196,60,60,58,62};
                        var cells=new ArrayList<List<String>>();int lineCount=1;
                        for(int c=0;c<row.size();c++){var lines=wrap(font,row.get(c),7,row.size()==1?792:widths[c]-8);cells.add(lines);lineCount=Math.max(lineCount,lines.size());}
                        float height=lineCount*10+8;
                        if(stream==null || y-height<45){
                            if(stream!=null)stream.close();
                            var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));doc.addPage(page);stream=new PDPageContentStream(doc,page);
                            float titleWidth=font.getStringWidth(source.title())*12/1000;
                            draw(stream,font,(842-titleWidth)/2,570,12,Color.BLACK,source.title());
                            stream.setStrokingColor(Color.BLACK);stream.moveTo((842-titleWidth)/2,567);stream.lineTo((842+titleWidth)/2,567);stream.stroke();
                            y=548;
                            y=statementFields(stream,font,source.client(),y);
                            y=statementFields(stream,font,section.fields(),y)-6;
                            if(!section.columns().isEmpty()){
                                stream.setNonStrokingColor(new Color(222,222,222));stream.addRect(24,y-32,792,32);stream.fill();float x=24;
                                for(int c=0;c<section.columns().size();c++){float lineY=y-10;for(var line:wrap(font,section.columns().get(c),7,widths[c]-8)){draw(stream,font,x+4,lineY,7,Color.BLACK,line);lineY-=9;}x+=widths[c];}y-=32;
                            }
                        }
                        float x=24;
                        for(int c=0;c<cells.size();c++){float lineY=y-12;for(var line:cells.get(c)){float textX=x+4;if(row.size()>1 && c>=6)textX=x+widths[c]-4-font.getStringWidth(line)*7/1000;draw(stream,font,textX,lineY,7,Color.BLACK,line);lineY-=10;}x+=row.size()==1?792:widths[c];}
                        y-=height;
                    }
                }finally{if(stream!=null)stream.close();}
            }
            for(int n=0;n<doc.getNumberOfPages();n++)try(var footer=new PDPageContentStream(doc,doc.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
                float y=27;for(var line:wrap(font,source.note(),6,740)){draw(footer,font,24,y,6,Color.DARK_GRAY,line);y-=8;}
                draw(footer,font,785,27,7,Color.DARK_GRAY,(n+1)+" / "+doc.getNumberOfPages());
            }
            doc.getDocumentInformation().setTitle(source.title());doc.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Client statement generation failed",e);}
    });}
    private static float statementFields(PDPageContentStream stream,PDType0Font font,List<StatementField> fields,float y)throws IOException{
        for(int i=0;i<fields.size();i+=4){int height=2;
            for(int c=i;c<Math.min(i+4,fields.size());c++){
                float x=24+(c-i)*198,lineY=y;
                var labels=wrap(font,fields.get(c).label(),7,190);var values=wrap(font,fields.get(c).value(),8,190);height=Math.max(height,labels.size()+values.size());
                for(var line:labels){draw(stream,font,x,lineY,7,Color.BLACK,line);lineY-=10;}
                for(var line:values){draw(stream,font,x,lineY,8,Color.BLACK,line);lineY-=10;}
            }y-=height*10+5;
        }return y;
    }
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
            if(line.length()>0 && font.getStringWidth(line+ch)*size/1000>width){
                int space=line.lastIndexOf(" ");
                if(space>0){rows.add(line.substring(0,space));String remainder=line.substring(space+1);line.setLength(0);line.append(remainder);}
                else{rows.add(line.toString());line.setLength(0);}
            }line.append(ch);
        }rows.add(line.toString());return rows;
    }
    private static void draw(PDPageContentStream stream,PDType0Font font,float x,float y,float size,Color color,String text)throws IOException{stream.beginText();stream.setNonStrokingColor(color);stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(text);stream.endText();}
}
