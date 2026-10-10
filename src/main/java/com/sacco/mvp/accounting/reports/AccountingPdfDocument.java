package com.sacco.mvp.accounting.reports;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.io.IOException;
import java.util.*;

/** Monochrome accounting stationery. Fixed columns, repeated headings and lossless row continuation. */
public final class AccountingPdfDocument implements AutoCloseable {
    private final PDDocument document;
    private final PDFont font;
    private final PDRectangle size;
    private final String title;
    private final List<String> context;
    private final PDImageXObject logo;
    private PDPageContentStream stream;
    private float y;
    private List<String> headers=List.of();
    private float[] weights=new float[0];
    private boolean[] numeric=new boolean[0];
    private boolean finished;
    public AccountingPdfDocument(PDDocument document,PDFont font,boolean landscape,String title,List<String> context)throws IOException {
        this(document,font,landscape,title,context,null);
    }
    public AccountingPdfDocument(PDDocument document,PDFont font,boolean landscape,String title,List<String> context,PDImageXObject logo)throws IOException {
        this.document=document;this.font=font;this.title=title;this.context=context;this.logo=logo;
        size=landscape?new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()):PDRectangle.A4;
        next();document.getDocumentInformation().setTitle(title);
    }
    public float width(){return size.getWidth()-64;}
    private void next()throws IOException {
        if(stream!=null)stream.close();var page=new PDPage(size);document.addPage(page);stream=new PDPageContentStream(document,page);y=size.getHeight()-36;
        float left=logo==null?32:115,headingWidth=size.getWidth()-32-left;
        if(logo!=null){float scale=Math.min(70f/logo.getWidth(),45f/logo.getHeight());stream.drawImage(logo,32,y-45,logo.getWidth()*scale,logo.getHeight()*scale);}
        for(String line:wrap(title,14,headingWidth)){draw(line,left+(headingWidth-font.getStringWidth(line)*14/1000)/2,y,14,true,false);y-=19;}
        y-=3;
        for(String value:context)for(String line:wrap(value,8,headingWidth)){draw(line,left,y,8,false,false);y-=12;}
        y-=12;if(!headers.isEmpty())header();
    }
    public void columns(List<String> labels,float[] weights,boolean[] numeric)throws IOException {
        this.headers=List.copyOf(labels);this.weights=weights.clone();this.numeric=numeric.clone();
        if(labels.size()!=weights.length || labels.size()!=numeric.length)throw new IllegalArgumentException("Column mismatch");
        header();
    }
    private void header()throws IOException {
        var lines=new ArrayList<List<String>>();int count=1;
        for(int i=0;i<headers.size();i++){var value=wrap(headers.get(i),8,width()*weights[i]-10);lines.add(value);count=Math.max(count,value.size());}
        if(y-count*12-10<55)nextWithoutHeader();
        rule(y+10);float x=32;
        for(int i=0;i<lines.size();i++){for(int n=0;n<lines.get(i).size();n++)draw(lines.get(i).get(n),numeric[i]?x+width()*weights[i]-5:x,y-n*12,8,true,numeric[i]);x+=width()*weights[i];}
        y-=count*12+4;rule(y);y-=12;
    }
    private void nextWithoutHeader()throws IOException {var saved=headers;headers=List.of();next();headers=saved;}
    public void row(List<String> values,boolean total)throws IOException {
        var lines=new ArrayList<List<String>>();var points=new ArrayList<Float>();int count=1;
        for(int i=0;i<values.size();i++){
            float max=width()*weights[i]-10,point=numeric[i]?8:9;
            if(numeric[i] && values.get(i).matches("[0-9.,()\\-]+"))point=Math.min(point,(max-1)*1000/font.getStringWidth(values.get(i)));
            points.add(point);var value=wrap(values.get(i),point,max);lines.add(value);count=Math.max(count,value.size());
        }
        int offset=0;
        if(y-Math.min(count*13+10,100)<55)next();
        while(offset<count){
            if(y<70)next();int take=Math.min(count-offset,Math.max(1,(int)((y-55)/13)));
            float x=32;
            for(int i=0;i<lines.size();i++){for(int n=0;n<take && offset+n<lines.get(i).size();n++)draw(lines.get(i).get(offset+n),numeric[i]?x+width()*weights[i]-5:x,y-n*13,points.get(i),total,numeric[i]);x+=width()*weights[i];}
            y-=take*13;offset+=take;
        }
        if(total){rule(y+2);y-=5;}y-=5;
    }
    public void text(String value)throws IOException {for(String line:wrap(value,9,width())){if(y<65)next();draw(line,32,y,9,false,false);y-=13;}y-=4;}
    public void section(String value)throws IOException {if(y<90)next();y-=10;for(String line:wrap(value,10,width())){if(y<65)next();draw(line,32,y,10,true,false);y-=14;}rule(y+3);y-=8;}
    /** End table before notes/provenance, so subsequent pages do not repeat an unrelated column header. */
    public void endTable(){headers=List.of();}
    private void rule(float top)throws IOException {stream.setStrokingColor(110/255f);stream.setLineWidth(.5f);stream.moveTo(32,top);stream.lineTo(size.getWidth()-32,top);stream.stroke();}
    private void draw(String value,float x,float top,float point,boolean bold,boolean right)throws IOException {
        if(right)x-=font.getStringWidth(value)*point/1000;
        stream.beginText();stream.setNonStrokingColor(0);stream.setStrokingColor(0);stream.setLineWidth(.22f);stream.setRenderingMode(bold?RenderingMode.FILL_STROKE:RenderingMode.FILL);
        stream.setFont(font,point);stream.newLineAtOffset(x,top);stream.showText(value);stream.endText();
    }
    private List<String> wrap(String value,float point,float max)throws IOException {
        var result=new ArrayList<String>();var line=new StringBuilder();
        for(int cp:value.codePoints().toArray()){
            if(cp=='\n'){result.add(line.toString());line.setLength(0);continue;}if(Character.isISOControl(cp))continue;
            String c=new String(Character.toChars(cp));try{font.encode(c);}catch(IllegalArgumentException unsupported){c="?";}
            if(!line.isEmpty() && font.getStringWidth(line+c)*point/1000>max){
                int space=line.lastIndexOf(" ");String rest=space>0?line.substring(space+1):"";
                result.add(space>0?line.substring(0,space):line.toString());line.setLength(0);line.append(rest);
            }line.append(c);
        }result.add(line.toString());return result;
    }
    @Override public void close()throws IOException {
        if(finished)return;finished=true;if(stream!=null)stream.close();
        for(int n=0;n<document.getNumberOfPages();n++)try(var footer=new PDPageContentStream(document,document.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
            stream=footer;draw((n+1)+" / "+document.getNumberOfPages(),size.getWidth()-32,24,8,false,true);
        }stream=null;
    }
}
