package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.reports.VoucherPdfDocument.Field;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import java.awt.Color;
import java.io.IOException;
import java.util.*;

/** Amortization stationery with recorded payments kept separate from contractual instalments. */
public final class LoanAmortizationPdfDocument implements AutoCloseable {
    public record Table(String title,List<String> columns,float[] widths,boolean[] numeric,List<List<String>> rows,String empty) {}
    public record Source(String title,String number,List<Field> identity,List<Field> terms,
                         Table payments,Table schedule,String note,String pageLabel) {}
    private static final float LEFT=28,WIDTH=539;
    private static final Color BLUE=new Color(53,102,126),TEAL=new Color(29,65,80),OLIVE=new Color(104,117,34),
        PALE_BLUE=new Color(224,237,242),PALE_GREEN=new Color(242,245,216),BORDER=new Color(153,165,166);
    private final PDDocument document;
    private final PDType0Font font;
    private final Source source;
    private PDPageContentStream stream;
    private float y;
    public LoanAmortizationPdfDocument(PDDocument document,PDType0Font font,Source source){this.document=document;this.font=font;this.source=source;}
    public void write()throws IOException {
        page();summary();y-=18;table(source.payments());y-=18;
        if(!source.note().isBlank()){
            var lines=wrap(source.note(),8,WIDTH-10);ensure(lines.size()*11+16+headerHeight(source.schedule())+48);
            for(String line:lines){text(line,LEFT+5,y-10,8,Color.DARK_GRAY,false);y-=11;}y-=8;
        }
        table(source.schedule());close();
        for(int n=0;n<document.getNumberOfPages();n++)try(var footer=new PDPageContentStream(document,document.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
            stream=footer;text(source.number(),LEFT,24,7,Color.DARK_GRAY,false);
            String value=source.pageLabel()+" "+(n+1)+" / "+document.getNumberOfPages();
            text(value,LEFT+WIDTH-font.getStringWidth(value)*7/1000,24,7,Color.DARK_GRAY,false);
        }stream=null;
    }
    private void page()throws IOException {
        close();var page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);y=805;
        for(String line:wrap(source.title().toUpperCase(Locale.ROOT),15,WIDTH)){text(line,LEFT,y,15,BLUE,true);y-=20;}
        stream.setStrokingColor(BORDER);stream.setLineWidth(.5f);stream.moveTo(LEFT,y+7);stream.lineTo(LEFT+WIDTH,y+7);stream.stroke();y-=9;
    }
    private void ensure(float height)throws IOException {if(y-height<50)page();}
    private void summary()throws IOException {
        for(int n=0;n<Math.max(source.identity().size(),source.terms().size());n++){
            Field a=n<source.identity().size()?source.identity().get(n):null,b=n<source.terms().size()?source.terms().get(n):null;
            float height=Math.max(fieldHeight(a),fieldHeight(b));ensure(height);
            if(a!=null)field(a,LEFT,y,259,height,BLUE,PALE_BLUE,false);
            if(b!=null)field(b,LEFT+279,y,260,height,OLIVE,n>=source.terms().size()-3?PALE_BLUE:PALE_GREEN,n>=source.terms().size()-3);
            y-=height;
        }
    }
    private float fieldHeight(Field field)throws IOException {
        return field==null?0:Math.max(22,Math.max(wrap(field.label().toUpperCase(Locale.ROOT),7.5f,102).size(),wrap(field.value(),8,135).size())*11+9);
    }
    private void field(Field field,float x,float top,float width,float height,Color labelColor,Color valueColor,boolean bold)throws IOException {
        box(x,top,112,height,labelColor);box(x+112,top,width-112,height,valueColor);
        lines(wrap(field.label().toUpperCase(Locale.ROOT),7.5f,102),x+5,top-13,7.5f,Color.WHITE,true);
        lines(wrap(field.value(),8,width-122),x+117,top-13,8,Color.BLACK,bold);
    }
    private void table(Table table)throws IOException {
        float header=headerHeight(table);ensure(header+48);heading(table,header);
        if(table.rows().isEmpty()){
            var values=wrap(table.empty(),8,WIDTH-12);float height=values.size()*11+10;ensure(height);box(LEFT,y,WIDTH,height,Color.WHITE);lines(values,LEFT+6,y-13,8,Color.DARK_GRAY,false);y-=height;return;
        }
        for(int index=0;index<table.rows().size();index++){
            var values=table.rows().get(index);var cells=new ArrayList<List<String>>();var sizes=new ArrayList<Float>();int count=1;
            for(int c=0;c<values.size();c++){
                float size=8,width=WIDTH*table.widths()[c]-10;
                if(table.numeric()[c] && values.get(c).matches("[-0-9.,]+"))size=Math.min(size,width*1000/font.getStringWidth(values.get(c)));
                var wrapped=wrap(values.get(c),size,width);cells.add(wrapped);sizes.add(size);count=Math.max(count,wrapped.size());
            }
            float height=Math.max(21,count*11+9);
            if(y-height<50){page();heading(table,header);}
            float x=LEFT;
            for(int c=0;c<cells.size();c++){
                float width=WIDTH*table.widths()[c];box(x,y,width,height,index%2==0?Color.WHITE:PALE_GREEN);
                for(int line=0;line<cells.get(c).size();line++){
                    String value=cells.get(c).get(line);float start=x+5;
                    if(table.numeric()[c])start=x+width-5-font.getStringWidth(value)*sizes.get(c)/1000;
                    text(value,start,y-13-line*11,sizes.get(c),Color.BLACK,false);
                }x+=width;
            }y-=height;
        }
    }
    private float headerHeight(Table table)throws IOException {
        int count=1;for(int n=0;n<table.columns().size();n++)count=Math.max(count,wrap(table.columns().get(n).toUpperCase(Locale.ROOT),7.5f,WIDTH*table.widths()[n]-10).size());
        return Math.max(28,count*10+10);
    }
    private void heading(Table table,float height)throws IOException {
        box(LEFT,y,WIDTH,22,TEAL);text(table.title().toUpperCase(Locale.ROOT),LEFT+6,y-15,9,Color.WHITE,true);y-=22;
        float x=LEFT;
        for(int n=0;n<table.columns().size();n++){
            float width=WIDTH*table.widths()[n];box(x,y,width,height,n<2?BLUE:OLIVE);
            lines(wrap(table.columns().get(n).toUpperCase(Locale.ROOT),7.5f,width-10),x+5,y-12,7.5f,Color.WHITE,true);x+=width;
        }y-=height;
    }
    private void box(float x,float top,float width,float height,Color fill)throws IOException {
        stream.setNonStrokingColor(fill);stream.addRect(x,top-height,width,height);stream.fill();
        stream.setStrokingColor(BORDER);stream.setLineWidth(.4f);stream.addRect(x,top-height,width,height);stream.stroke();
    }
    private void lines(List<String> lines,float x,float top,float size,Color color,boolean bold)throws IOException {
        for(String line:lines){text(line,x,top,size,color,bold);top-=11;}
    }
    private List<String> wrap(String value,float size,float width)throws IOException {
        var result=new ArrayList<String>();var line=new StringBuilder();
        for(int point:Objects.toString(value,"").codePoints().toArray()){
            if(point=='\n'){result.add(line.toString());line.setLength(0);continue;}if(Character.isISOControl(point))continue;
            String ch=new String(Character.toChars(point));try{font.encode(ch);}catch(IllegalArgumentException ignored){ch="?";}
            if(!line.isEmpty() && font.getStringWidth(line+ch)*size/1000>width){
                int space=line.lastIndexOf(" ");String rest=space>0?line.substring(space+1):"";result.add(space>0?line.substring(0,space):line.toString());line.setLength(0);line.append(rest);
            }line.append(ch);
        }result.add(line.toString());return result;
    }
    private void text(String value,float x,float top,float size,Color color,boolean bold)throws IOException {
        stream.beginText();stream.setNonStrokingColor(color);stream.setStrokingColor(color);stream.setLineWidth(.16f);
        stream.setRenderingMode(bold?RenderingMode.FILL_STROKE:RenderingMode.FILL);stream.setFont(font,size);stream.newLineAtOffset(x,top);stream.showText(value);stream.endText();
    }
    @Override public void close()throws IOException {if(stream!=null){stream.close();stream=null;}}
}
