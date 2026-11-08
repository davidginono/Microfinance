package com.sacco.mvp.accounting.reports;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.io.IOException;
import java.util.*;

/** Monochrome accounting stationery. Fixed columns, repeated headings and lossless row continuation. */
public final class AccountingPdfDocument implements AutoCloseable {
    public enum Style { ORIGINAL, STATEMENT, EQUITY, TRIAL, ACCOUNT, REFERENCE, REPAYMENT, AUDIT, EXPECTED }
    private final PDDocument document;
    private final PDFont font;
    private final PDRectangle size;
    private final String title;
    private final List<String> context;
    private final PDImageXObject logo;
    private final Style style;
    private final int firstPage;
    private final PDFont bodyFont,headingFont,titleFont,metadataFont;
    private final boolean sourceLayout,referenceRows;
    private List<String> groupLabels=List.of(),groupValues=List.of();
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
        this(document,font,landscape,title,context,logo,Style.ORIGINAL);
    }
    public AccountingPdfDocument(PDDocument document,PDFont font,boolean landscape,String title,List<String> context,PDImageXObject logo,Style style)throws IOException {
        this.document=document;this.font=font;this.title=title;this.context=context;this.logo=logo;
        firstPage=document.getNumberOfPages();
        this.style=style;
        sourceLayout=style==Style.REPAYMENT||style==Style.AUDIT||style==Style.EXPECTED;referenceRows=sourceLayout||style==Style.REFERENCE;
        bodyFont=style==Style.ORIGINAL?font:new PDType1Font(referenceRows?FontName.COURIER:style==Style.TRIAL?FontName.TIMES_ROMAN:style==Style.EQUITY?FontName.COURIER:FontName.HELVETICA);
        headingFont=style==Style.ORIGINAL?font:new PDType1Font(style==Style.REPAYMENT?FontName.HELVETICA_BOLD:style==Style.REFERENCE||style==Style.AUDIT||style==Style.EXPECTED?FontName.TIMES_BOLD:style==Style.TRIAL?FontName.TIMES_BOLD:style==Style.EQUITY?FontName.COURIER_BOLD:FontName.HELVETICA_BOLD);
        titleFont=style==Style.ORIGINAL?font:new PDType1Font(style==Style.REPAYMENT?FontName.TIMES_BOLD:style==Style.EXPECTED?FontName.HELVETICA:style==Style.TRIAL?FontName.TIMES_ROMAN:style==Style.EQUITY||style==Style.ACCOUNT||style==Style.REFERENCE||style==Style.AUDIT?FontName.HELVETICA_BOLD:FontName.HELVETICA);
        metadataFont=style==Style.REPAYMENT?new PDType1Font(FontName.HELVETICA):style==Style.AUDIT?new PDType1Font(FontName.TIMES_BOLD_ITALIC):bodyFont;
        var paper=switch(style){case AUDIT,EXPECTED->PDRectangle.LEGAL;case REPAYMENT->PDRectangle.LETTER;case REFERENCE->PDRectangle.A3;default->PDRectangle.A4;};
        size=landscape?new PDRectangle(paper.getHeight(),paper.getWidth()):paper;
        next();document.getDocumentInformation().setTitle(title);
    }
    public float width(){return size.getWidth()-64;}
    private void next()throws IOException {
        if(stream!=null)stream.close();var page=new PDPage(size);document.addPage(page);stream=new PDPageContentStream(document,page);y=size.getHeight()-(style==Style.AUDIT?12:style==Style.EXPECTED?22:style==Style.REPAYMENT?42.5f:36);
        float titleTop=y,left=logo==null?32:115,headingWidth=sourceLayout?width()*(style==Style.REPAYMENT?.8f:.5f):size.getWidth()-32-left;
        if(logo!=null){float scale=Math.min(70f/logo.getWidth(),45f/logo.getHeight());stream.drawImage(logo,32,y-45,logo.getWidth()*scale,logo.getHeight()*scale);}
        float titlePoint=style==Style.AUDIT?11:style==Style.REPAYMENT?12:14;
        for(String line:wrap(title,titlePoint,headingWidth)){
            float titleWidth=face(line,titleFont).getStringWidth(line)*titlePoint/1000,x=left+(headingWidth-titleWidth)/2;
            drawWith(line,x,y,titlePoint,titleFont,style==Style.ORIGINAL,false,style==Style.EQUITY);
            if(style==Style.TRIAL||style==Style.ACCOUNT||style==Style.REPAYMENT||style==Style.AUDIT)line(x,x+titleWidth,y-2);
            y-=titlePoint+5;
        }
        y-=3;
        if(sourceLayout && context.size()>2)draw(context.get(2),size.getWidth()-32,style==Style.AUDIT?size.getHeight()-26:style==Style.EXPECTED?size.getHeight()-26:titleTop+3,10,false,true);
        if(style==Style.REPAYMENT){
            float contextY=titleTop-12;
            for(int n:new int[]{1,0})for(String line:wrap(context.get(n),8,width()*.4f)){draw(line,size.getWidth()-32,contextY,8,false,true);contextY-=12;}
            for(int n=3;n<context.size();n++)for(String line:wrap(context.get(n),8,width()*.4f)){draw(line,size.getWidth()-32,contextY,8,false,true);contextY-=12;}
            y=Math.min(y,contextY)-6;
        }else {for(int n=0;n<context.size();n++)if(!sourceLayout||n!=2)for(String line:wrap(context.get(n),sourceLayout?10:8,sourceLayout?width():headingWidth)){draw(line,left,y,sourceLayout?10:8,false,false);y-=12;}y-=12;}
        if(sourceLayout&&!groupValues.isEmpty())drawSourceGroup();if(!headers.isEmpty())header();
    }
    /** Reference metadata stays above its table and repeats on continuation pages. */
    public void sourceGroup(List<String> labels,List<String> values)throws IOException {
        if(!sourceLayout||labels.size()!=values.size()||values.size()!=(style==Style.REPAYMENT?7:style==Style.AUDIT?3:1))throw new IllegalArgumentException("Group metadata mismatch");
        groupLabels=style==Style.REPAYMENT||style==Style.EXPECTED?labels.stream().map(v->v.toUpperCase(Locale.ROOT)).toList():List.copyOf(labels);groupValues=List.copyOf(values);
        if(y-sourceGroupHeight()<110){next();return;}drawSourceGroup();
    }
    private float sourceGroupHeight()throws IOException {
        if(style==Style.REPAYMENT){
            float[] identity={.22f,.48f,.30f},facts={.14f,.30f,.30f,.26f};int identityLines=1,labelLines=1,valueLines=1;
            for(int n=0;n<3;n++)identityLines=Math.max(identityLines,wrap(groupValues.get(n),10,Math.max(25,width()*identity[n]-headingFont.getStringWidth(groupLabels.get(n))*10/1000-9)).size());
            for(int n=0;n<4;n++){labelLines=Math.max(labelLines,wrap(groupLabels.get(n+3),10,width()*facts[n]-10).size());valueLines=Math.max(valueLines,wrap(groupValues.get(n+3),10,width()*facts[n]-10).size());}
            return identityLines*12+18+labelLines*12+valueLines*12+20;
        }
        if(style==Style.AUDIT)return 40+12*Math.max(wrap(groupLabels.get(1)+"  "+groupValues.get(1),10,width()*.48f).size(),wrap(groupLabels.get(2)+"  "+groupValues.get(2),10,width()*.48f).size());
        return wrap(groupLabels.getFirst()+"   "+groupValues.getFirst(),10,width()).size()*14+10;
    }
    private void drawSourceGroup()throws IOException {
        if(style==Style.REPAYMENT){
            float[] identity={.22f,.48f,.30f};float x=32;int lines=1;
            for(int n=0;n<3;n++){
                String label=groupLabels.get(n);float labelWidth=headingFont.getStringWidth(label)*10/1000;
                drawWith(label,x,y,10,headingFont,false,false,false);
                var parts=wrap(groupValues.get(n),10,Math.max(25,width()*identity[n]-labelWidth-9));lines=Math.max(lines,parts.size());
                for(int p=0;p<parts.size();p++)drawWith(parts.get(p),x+labelWidth+5,y-p*12,10,metadataFont,false,false,false);
                x+=width()*identity[n];
            }
            y-=lines*12+18;
            float[] facts={.14f,.30f,.30f,.26f};var labels=new ArrayList<List<String>>();var values=new ArrayList<List<String>>();int labelLines=1,valueLines=1;
            for(int n=0;n<4;n++){
                var l=wrap(groupLabels.get(n+3),10,width()*facts[n]-10);var v=wrap(groupValues.get(n+3),10,width()*facts[n]-10);
                labels.add(l);values.add(v);labelLines=Math.max(labelLines,l.size());valueLines=Math.max(valueLines,v.size());
            }
            x=32;
            for(int n=0;n<4;n++){
                float center=x+width()*facts[n]/2;
                for(int p=0;p<labels.get(n).size();p++){String text=labels.get(n).get(p);drawWith(text,center-headingFont.getStringWidth(text)*10/2000,y-p*12,10,headingFont,false,false,false);}
                for(int p=0;p<values.get(n).size();p++){String text=values.get(n).get(p);drawWith(text,center-face(text,metadataFont).getStringWidth(text)*10/2000,y-labelLines*12-5-p*12,10,metadataFont,false,false,false);}
                x+=width()*facts[n];
            }
            y-=labelLines*12+valueLines*12+20;
        }else if(style==Style.AUDIT){
            draw(groupLabels.get(0),32,y,10,true,false);drawWith(groupValues.get(0),155,y,11,metadataFont,false,false,false);y-=30;
            var product=wrap(groupLabels.get(1)+"  "+groupValues.get(1),10,width()*.48f);var account=wrap(groupLabels.get(2)+"  "+groupValues.get(2),10,width()*.48f);
            for(int n=0;n<product.size();n++)draw(product.get(n),32,y-n*12,10,true,false);
            for(int n=0;n<account.size();n++)draw(account.get(n),size.getWidth()-32,y-n*12,10,true,true);
            y-=Math.max(product.size(),account.size())*12+10;
        }else {
            for(String line:wrap(groupLabels.getFirst()+"   "+groupValues.getFirst(),10,width())){draw(line,32,y,10,true,false);y-=14;}y-=10;
        }
    }
    public void columns(List<String> labels,float[] weights,boolean[] numeric)throws IOException {
        this.headers=List.copyOf(labels);this.weights=weights.clone();this.numeric=numeric.clone();
        if(labels.size()!=weights.length || labels.size()!=numeric.length)throw new IllegalArgumentException("Column mismatch");
        header();
    }
    private void header()throws IOException {
        var lines=new ArrayList<List<String>>();int count=1;float point=sourceLayout?10:8;
        for(int i=0;i<headers.size();i++){var value=wrap(headers.get(i),point,width()*weights[i]-10);lines.add(value);count=Math.max(count,value.size());}
        if(y-count*12-10<55)nextWithoutHeader();
        if(style==Style.ORIGINAL)rule(y+10);
        if(style==Style.ACCOUNT||style==Style.REPAYMENT){stream.setNonStrokingColor(style==Style.REPAYMENT?.90f:.85f);stream.addRect(32,y-count*12-2,width(),count*12+12);stream.fill();}
        float x=32;
        for(int i=0;i<lines.size();i++){
            for(int n=0;n<lines.get(i).size();n++){
                String label=lines.get(i).get(n);float edge=numeric[i]?x+width()*weights[i]-5:x;
                draw(label,edge,y-n*12,point,true,numeric[i]);
                if(style==Style.TRIAL){float length=face(label,headingFont).getStringWidth(label)*8/1000;line(numeric[i]?edge-length:edge,numeric[i]?edge:edge+length,y-n*12-2);}
            }x+=width()*weights[i];
        }
        y-=count*12+4;if(style==Style.ORIGINAL)rule(y);y-=style==Style.ORIGINAL?12:4;
    }
    private void nextWithoutHeader()throws IOException {var saved=headers;headers=List.of();next();headers=saved;}
    public void row(List<String> values,boolean total)throws IOException {
        var lines=new ArrayList<List<String>>();var points=new ArrayList<Float>();int count=1;
        float leading=sourceLayout?12:referenceRows?10:13;
        for(int i=0;i<values.size();i++){
            float max=width()*weights[i]-10,point=sourceLayout?10:referenceRows?7.5f:numeric[i]?8:9;
            if(numeric[i] && values.get(i).matches("[0-9.,()\\-]+"))point=Math.min(point,(max-1)*1000/face(values.get(i),bodyFont).getStringWidth(values.get(i)));
            points.add(point);var value=wrap(values.get(i),point,max);lines.add(value);count=Math.max(count,value.size());
        }
        int offset=0;
        if(y-Math.min(count*leading+10,100)<55)next();
        while(offset<count){
            if(y<70)next();int take=Math.min(count-offset,Math.max(1,(int)((y-55)/leading)));
            float x=32;
            for(int i=0;i<lines.size();i++){for(int n=0;n<take && offset+n<lines.get(i).size();n++)draw(lines.get(i).get(offset+n),numeric[i]?x+width()*weights[i]-5:x,y-n*leading,points.get(i),total,numeric[i]);x+=width()*weights[i];}
            y-=take*leading;offset+=take;
        }
        if(total){if(style==Style.ORIGINAL)rule(y+2);else{float x=32;for(int i=0;i<values.size();i++){if(numeric[i])line(x+width()*weights[i]-5-face(values.get(i),headingFont).getStringWidth(values.get(i))*points.get(i)/1000,x+width()*weights[i]-5,y+leading-2);x+=width()*weights[i];}}y-=5;}
        if(style==Style.ORIGINAL)y-=5;
    }
    public void text(String value)throws IOException {for(String line:wrap(value,9,width())){if(y<65)next();draw(line,32,y,9,false,false);y-=13;}y-=4;}
    public void section(String value)throws IOException {if(y<90)next();y-=style==Style.ORIGINAL?10:16;for(String line:wrap(value,10,width())){if(y<65)next();draw(line,32,y,10,true,false);y-=14;}if(style==Style.ORIGINAL){rule(y+3);y-=8;}else y-=4;}
    /** End table before notes/provenance, so subsequent pages do not repeat an unrelated column header. */
    public void endTable(){headers=List.of();groupLabels=List.of();groupValues=List.of();}
    /** Keep provenance intact on its own page, leaving the financial face uncluttered. */
    public void appendix(String title)throws IOException {endTable();next();section(title);}
    private void rule(float top)throws IOException {stream.setStrokingColor(110/255f);stream.setLineWidth(.5f);stream.moveTo(32,top);stream.lineTo(size.getWidth()-32,top);stream.stroke();}
    private void line(float left,float right,float top)throws IOException {stream.setStrokingColor(0);stream.setLineWidth(.5f);stream.moveTo(left,top);stream.lineTo(right,top);stream.stroke();}
    private PDFont face(String value,PDFont preferred)throws IOException {try{preferred.encode(value);return preferred;}catch(IllegalArgumentException unavailable){return font;}}
    private void draw(String value,float x,float top,float point,boolean bold,boolean right)throws IOException {
        drawWith(value,x,top,point,bold?headingFont:bodyFont,bold&&style==Style.ORIGINAL,right,false);
    }
    private void drawWith(String value,float x,float top,float point,PDFont preferred,boolean bold,boolean right,boolean red)throws IOException {
        PDFont selected=face(value,preferred);if(right)x-=selected.getStringWidth(value)*point/1000;
        stream.beginText();if(red)stream.setNonStrokingColor(1f,0f,0f);else stream.setNonStrokingColor(0);stream.setStrokingColor(0);stream.setLineWidth(.22f);stream.setRenderingMode(bold?RenderingMode.FILL_STROKE:RenderingMode.FILL);
        stream.setFont(selected,point);stream.newLineAtOffset(x,top);stream.showText(value);stream.endText();
    }
    private List<String> wrap(String value,float point,float max)throws IOException {
        var result=new ArrayList<String>();var line=new StringBuilder();
        for(int cp:value.codePoints().toArray()){
            if(cp=='\n'){result.add(line.toString());line.setLength(0);continue;}if(Character.isISOControl(cp))continue;
            String c=new String(Character.toChars(cp));try{font.encode(c);}catch(IllegalArgumentException unsupported){c="?";}
            if(!line.isEmpty() && face(line+c,bodyFont).getStringWidth(line+c)*point/1000>max){
                int space=line.lastIndexOf(" ");String rest=space>0?line.substring(space+1):"";
                result.add(space>0?line.substring(0,space):line.toString());line.setLength(0);line.append(rest);
            }line.append(c);
        }result.add(line.toString());return result;
    }
    @Override public void close()throws IOException {
        if(finished)return;finished=true;if(stream!=null)stream.close();
        for(int n=firstPage;n<document.getNumberOfPages();n++)try(var footer=new PDPageContentStream(document,document.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
            stream=footer;String number=(n-firstPage+1)+" / "+(document.getNumberOfPages()-firstPage);
            if(sourceLayout){PDFont footerFont=style==Style.REPAYMENT?metadataFont:bodyFont;drawWith(number,(size.getWidth()-face(number,footerFont).getStringWidth(number)*10/1000)/2,style==Style.AUDIT?49:style==Style.EXPECTED?22:30,10,footerFont,false,false,false);}
            else draw(number,size.getWidth()-32,24,8,false,true);
        }stream=null;
    }
}
