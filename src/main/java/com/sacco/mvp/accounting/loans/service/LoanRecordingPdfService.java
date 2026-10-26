package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.service.ReportExportLimiter;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument;
import com.sacco.mvp.accounting.reports.LoanAmortizationPdfDocument;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanRecordingPdfService {
    private final ReportExportLimiter limiter;
    private static final float STATEMENT_MARGIN=36;
    private static final float STATEMENT_WIDTH=PDRectangle.A4.getHeight()-2*STATEMENT_MARGIN;
    public record Document(String title,String institution,String branch,String filename,List<String> lines) {}
    public record StatementField(String label,String value) {}
    public record StatementSection(List<StatementField> fields,List<String> columns,List<List<String>> rows,List<Float> columnWidths,int monetaryColumnStart) {
        public StatementSection(List<StatementField> fields,List<String> columns,List<List<String>> rows){this(fields,columns,rows,List.of(),6);}
        public StatementSection(List<StatementField> fields,List<String> columns,List<List<String>> rows,List<Float> columnWidths){this(fields,columns,rows,columnWidths,6);}
    }
    public record StatementDocument(String title,List<StatementField> client,List<StatementSection> sections,String empty,String note) {}
    public byte[] renderAmortization(LoanAmortizationPdfDocument.Source source){return limiter.run(()->{
        try(var doc=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(doc,fontStream);
            try(var layout=new LoanAmortizationPdfDocument(doc,font,source)){layout.write();}
            doc.getDocumentInformation().setTitle(source.title());doc.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Loan amortization document generation failed",e);}
    });}
    public byte[] renderPosting(VoucherPdfDocument.Source source){return limiter.run(()->{
        try(var doc=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(doc,fontStream);
            try(var layout=new VoucherPdfDocument(doc,font,source)){layout.write();}
            doc.getDocumentInformation().setTitle(source.title());doc.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Loan posting document generation failed",e);}
    });}
    public byte[] renderStatement(StatementDocument source){return limiter.run(()->{
        try(var doc=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            PDFont fallback=PDType0Font.load(doc,fontStream);PDFont font=new PDType1Font(FontName.COURIER);
            PDFont titleFont=readable(new PDType1Font(FontName.TIMES_BOLD),fallback,source.title());

            var sections=source.sections().isEmpty()?List.of(new StatementSection(List.of(),List.of(),List.of())):source.sections();
            for(var section:sections){
                float[] widths={70,80,55,76,58,173,70,70,70,70};
                if(section.columnWidths()!=null&&!section.columnWidths().isEmpty()){
                    if(section.columnWidths().size()!=section.columns().size()||section.columnWidths().stream().anyMatch(w->w==null||!Float.isFinite(w)||w<=0))throw new IllegalArgumentException("Invalid statement columns");
                    widths=new float[section.columnWidths().size()];for(int c=0;c<widths.length;c++)widths[c]=section.columnWidths().get(c);
                }
                float totalWidth=0;for(float width:widths)totalWidth+=width;for(int c=0;c<widths.length;c++)widths[c]*=STATEMENT_WIDTH/totalWidth;
                PDPageContentStream stream=null;float y=0;
                try{
                    var rows=section.rows().isEmpty()?List.of(List.of(source.empty())):section.rows();
                    for(var row:rows){
                        var cells=new ArrayList<List<String>>();var faces=new ArrayList<PDFont>();var points=new ArrayList<Float>();int lineCount=1;
                        for(int c=0;c<row.size();c++){PDFont face=readable(font,fallback,row.get(c));float point=9;if(c>=section.monetaryColumnStart()&&!row.get(c).isBlank())point=Math.min(point,(widths[c]-8)*1000/face.getStringWidth(row.get(c)));faces.add(face);points.add(point);var lines=wrap(face,row.get(c),point,row.size()==1?STATEMENT_WIDTH-8:widths[c]-8);cells.add(lines);lineCount=Math.max(lineCount,lines.size());}
                        float height=lineCount*11+5;
                        if(stream==null || y-height<45){
                            if(stream!=null)stream.close();
                            var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));doc.addPage(page);stream=new PDPageContentStream(doc,page);
                            float titleWidth=titleFont.getStringWidth(source.title())*12/1000;
                            float pageWidth=page.getMediaBox().getWidth();
                            draw(stream,titleFont,(pageWidth-titleWidth)/2,570,12,Color.BLACK,source.title());
                            stream.setStrokingColor(Color.BLACK);stream.moveTo((pageWidth-titleWidth)/2,567);stream.lineTo((pageWidth+titleWidth)/2,567);stream.stroke();
                            y=548;
                            y=statementFields(stream,fallback,source.client(),y);
                            y=statementFields(stream,fallback,section.fields(),y)-6;
                            if(!section.columns().isEmpty()){
                                stream.setNonStrokingColor(new Color(222,222,222));stream.addRect(STATEMENT_MARGIN,y-32,STATEMENT_WIDTH,32);stream.fill();float x=STATEMENT_MARGIN;
                                for(int c=0;c<section.columns().size();c++){PDFont face=readable(new PDType1Font(FontName.TIMES_BOLD),fallback,section.columns().get(c));float lineY=y-10;for(var line:wrap(face,section.columns().get(c),8,widths[c]-8)){draw(stream,face,x+4,lineY,8,Color.BLACK,line);lineY-=10;}x+=widths[c];}y-=32;
                            }
                        }
                        float x=STATEMENT_MARGIN;
                        for(int c=0;c<cells.size();c++){float lineY=y-12;PDFont face=faces.get(c);float point=points.get(c);for(var line:cells.get(c)){float textX=x+4;if(row.size()>1 && c>=section.monetaryColumnStart())textX=x+widths[c]-4-face.getStringWidth(line)*point/1000;draw(stream,face,textX,lineY,point,Color.BLACK,line);lineY-=11;}x+=row.size()==1?STATEMENT_WIDTH:widths[c];}
                        y-=height;
                    }
                }finally{if(stream!=null)stream.close();}
            }
            for(int n=0;n<doc.getNumberOfPages();n++)try(var footer=new PDPageContentStream(doc,doc.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
                float y=27;for(var line:wrap(font,source.note(),6,STATEMENT_WIDTH-70)){draw(footer,font,STATEMENT_MARGIN,y,6,Color.DARK_GRAY,line);y-=8;}
                String pageNumber=(n+1)+" / "+doc.getNumberOfPages();
                draw(footer,font,STATEMENT_MARGIN+STATEMENT_WIDTH-font.getStringWidth(pageNumber)*7/1000,27,7,Color.DARK_GRAY,pageNumber);
            }
            doc.getDocumentInformation().setTitle(source.title());doc.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Client statement generation failed",e);}
    });}
    private static float statementFields(PDPageContentStream stream,PDFont font,List<StatementField> fields,float y)throws IOException{
        PDFont labelFont=new PDType1Font(FontName.TIMES_BOLD),valueFont=new PDType1Font(FontName.TIMES_BOLD_ITALIC);
        for(int i=0;i<fields.size();i+=3){int height=1;
            for(int c=i;c<Math.min(i+3,fields.size());c++){
                float columnWidth=STATEMENT_WIDTH/3,x=STATEMENT_MARGIN+(c-i)*columnWidth,lineY=y;
                PDFont labelFace=readable(labelFont,font,fields.get(c).label()),valueFace=readable(valueFont,font,fields.get(c).value());
                var labels=wrap(labelFace,fields.get(c).label().toUpperCase(Locale.ROOT),8,columnWidth*.56f);
                var values=wrap(valueFace,fields.get(c).value(),9,columnWidth*.40f);height=Math.max(height,Math.max(labels.size(),values.size()));
                for(var line:labels){draw(stream,labelFace,x,lineY,8,Color.BLACK,line);lineY-=11;}
                lineY=y;for(var line:values){draw(stream,valueFace,x+columnWidth*.58f,lineY,9,Color.BLACK,line);lineY-=11;}
            }y-=height*11+5;
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
    private static PDFont readable(PDFont preferred,PDFont fallback,String value)throws IOException{try{preferred.encode(value);return preferred;}catch(IllegalArgumentException unsupported){return fallback;}}
    private static List<String> wrap(PDFont font,String value,float size,float width)throws IOException{
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
    private static void draw(PDPageContentStream stream,PDFont font,float x,float y,float size,Color color,String text)throws IOException{stream.beginText();stream.setNonStrokingColor(color);stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(text);stream.endText();}
}
