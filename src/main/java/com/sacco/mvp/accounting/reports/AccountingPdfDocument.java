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
    private final float[] sourceEdges;
    private List<String> groupLabels=List.of(),groupValues=List.of();
    private PDPageContentStream stream;
    private float y;
    private List<String> headers=List.of();
    private float[] weights=new float[0];
    private boolean[] numeric=new boolean[0];
    private String sourcePageNumber="Page {0} of {1}";
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
        sourceEdges=sourceEdges(style);
        bodyFont=style==Style.ORIGINAL?font:new PDType1Font(referenceRows&&style!=Style.REPAYMENT?FontName.COURIER:style==Style.TRIAL?FontName.TIMES_ROMAN:style==Style.EQUITY?FontName.COURIER:FontName.HELVETICA);
        headingFont=style==Style.ORIGINAL?font:new PDType1Font(style==Style.REPAYMENT?FontName.HELVETICA_BOLD:style==Style.REFERENCE||style==Style.AUDIT||style==Style.EXPECTED?FontName.TIMES_BOLD:style==Style.TRIAL?FontName.TIMES_BOLD:style==Style.EQUITY?FontName.COURIER_BOLD:FontName.HELVETICA_BOLD);
        titleFont=style==Style.ORIGINAL?font:new PDType1Font(style==Style.REPAYMENT?FontName.TIMES_BOLD:style==Style.EXPECTED?FontName.HELVETICA:style==Style.TRIAL?FontName.TIMES_ROMAN:style==Style.EQUITY||style==Style.ACCOUNT||style==Style.REFERENCE||style==Style.AUDIT?FontName.HELVETICA_BOLD:FontName.HELVETICA);
        metadataFont=style==Style.REPAYMENT?new PDType1Font(FontName.HELVETICA):style==Style.AUDIT?new PDType1Font(FontName.TIMES_BOLD_ITALIC):bodyFont;
        var paper=switch(style){case AUDIT,EXPECTED->PDRectangle.LEGAL;case REPAYMENT->PDRectangle.LETTER;case REFERENCE->PDRectangle.A3;default->PDRectangle.A4;};
        size=landscape?new PDRectangle(paper.getHeight(),paper.getWidth()):paper;
        next();document.getDocumentInformation().setTitle(title);
    }
    /** Column boundaries measured in points on the Legal and Letter reference pages. */
    public static float[] sourceEdges(Style style) {
        return switch(style){
            case AUDIT -> new float[]{0,23,85,226,267,334,442,538,636,707,783,856,895,940,1000};
            case EXPECTED -> new float[]{9,54,94,243,333,416,500,577,723,790};
            case REPAYMENT -> new float[]{9,76,153,220,292,378,472,544};
            default -> new float[0];
        };
    }
    public float width(){return sourceLayout?sourceEdges[sourceEdges.length-1]-sourceEdges[0]:size.getWidth()-(style==Style.EQUITY?40:64);}
    private float tableLeft(){return sourceLayout?sourceEdges[0]:style==Style.EQUITY?20:32;}
    private float columnWidth(int column){return sourceLayout?sourceEdges[column+1]-sourceEdges[column]:width()*weights[column];}
    private float rowRight(int column,float left){
        if(style==Style.EXPECTED && column==7)return 648;
        return left+columnWidth(column)-(sourceLayout?4:5);
    }
    private float rowWidth(int column){
        if(!sourceLayout)return columnWidth(column)-10;
        return rowRight(column,sourceEdges[column])-sourceEdges[column];
    }
    private void next()throws IOException {
        if(stream!=null)stream.close();var page=new PDPage(size);document.addPage(page);stream=new PDPageContentStream(document,page);
        if(sourceLayout){
            sourcePageHeading();
            if(!groupValues.isEmpty())drawSourceGroup();
            if(!headers.isEmpty())header();
            return;
        }
        if(style==Style.EQUITY){
            y=size.getHeight()-22;
            for(String part:wrap(title,14,width()*.546f,titleFont)){
                float titleWidth=face(part,titleFont).getStringWidth(part)*14/1000;
                drawWith(part,tableLeft()+width()*.273f-titleWidth/2,y,14,titleFont,false,false,true);y-=19;
            }
            for(String part:wrap(context.getFirst(),9,width(),headingFont)){draw(part,tableLeft(),y,9,true,false);y-=12;}
            y-=24;
            if(!headers.isEmpty())header();
            return;
        }
        y=size.getHeight()-36;
        float left=logo==null?32:115,headingWidth=size.getWidth()-32-left;
        if(logo!=null){float scale=Math.min(70f/logo.getWidth(),45f/logo.getHeight());stream.drawImage(logo,32,y-45,logo.getWidth()*scale,logo.getHeight()*scale);}
        float titlePoint=14;
        for(String line:wrap(title,titlePoint,headingWidth)){
            float titleWidth=face(line,titleFont).getStringWidth(line)*titlePoint/1000,x=left+(headingWidth-titleWidth)/2;
            drawWith(line,x,y,titlePoint,titleFont,style==Style.ORIGINAL,false,false);
            if(style==Style.TRIAL||style==Style.ACCOUNT)line(x,x+titleWidth,y-2);
            y-=titlePoint+5;
        }
        y-=3;
        for(String detail:context)for(String line:wrap(detail,8,headingWidth)){draw(line,left,y,8,false,false);y-=12;}
        y-=12;
        if(!headers.isEmpty())header();
    }
    private void sourcePageHeading()throws IOException {
        float height=size.getHeight();
        float titleCenter=style==Style.AUDIT?266:style==Style.EXPECTED?255:256;
        float point=style==Style.AUDIT?11:style==Style.EXPECTED?14:12;
        float baseline=height-(style==Style.AUDIT?10:style==Style.EXPECTED?22:42);
        for(String part:wrap(title,point,style==Style.REPAYMENT?198:460,titleFont)){
            float titleWidth=face(part,titleFont).getStringWidth(part)*point/1000;
            float left=titleCenter-titleWidth/2;
            drawWith(part,left,baseline,point,titleFont,false,false,false);
            if(style!=Style.EXPECTED)line(left,left+titleWidth,baseline-2);
            baseline-=point+2;
        }
        float stampX=style==Style.AUDIT?432:style==Style.EXPECTED?543:368;
        PDFont stampFont=style==Style.REPAYMENT?new PDType1Font(FontName.COURIER):bodyFont;
        float stampBaseline=height-(style==Style.REPAYMENT?39:26);
        float stampBottom=stampBaseline;
        for(String part:wrap(context.get(2),10,size.getWidth()-stampX-9,stampFont)){
            drawWith(part,stampX,stampBottom,10,stampFont,false,false,false);
            stampBottom-=12;
        }
        if(style==Style.AUDIT){
            y=sourceDetail(context.get(0),22,height-30,400,10);
            y=Math.min(y-12,height-58);
        }else if(style==Style.EXPECTED){
            float scope=sourceDetail(context.get(1),20,height-48,345,10);
            float period=sourceDetail(context.get(0),382,height-48,600,10);
            y=Math.min(Math.min(scope,period)-14,height-74);
        }else y=Math.min(stampBottom-18,height-85);
        for(int detail=3;detail<context.size();detail++)
            y=sourceDetail(context.get(detail),tableLeft()+9,y,size.getWidth()-tableLeft()-24,9);
        if(context.size()>3)y-=6;
    }
    private float sourceDetail(String value,float x,float baseline,float maxWidth,float point)throws IOException {
        for(String part:wrap(value,point,maxWidth)){
            draw(part,x,baseline,point,false,false);
            baseline-=point+2;
        }
        return baseline;
    }
    /** Reference metadata stays above its table and repeats on continuation pages. */
    public void sourceGroup(List<String> labels,List<String> values)throws IOException {
        if(!sourceLayout||labels.size()!=values.size()||values.size()!=(style==Style.EXPECTED?2:style==Style.AUDIT?5:7))throw new IllegalArgumentException("Group metadata mismatch");
        groupLabels=style==Style.REPAYMENT||style==Style.EXPECTED?labels.stream().map(v->v.toUpperCase(Locale.ROOT)).toList():List.copyOf(labels);groupValues=List.copyOf(values);
        // Repayment rows can carry several lines of receipt and correction references.
        float reserve=style==Style.REPAYMENT?205:120;
        if(y-sourceGroupHeight()<reserve){next();return;}drawSourceGroup();
    }
    private static final float[] REPAYMENT_IDENTITY={9,139,405,603};
    private static final float[] REPAYMENT_FACTS={9,76,202,362,544};

    private float sourceGroupHeight()throws IOException {
        if(style==Style.REPAYMENT){
            int identityLines=1,labelLines=1,valueLines=1;
            for(int n=0;n<3;n++)identityLines=Math.max(identityLines,repaymentIdentityValue(n).size());
            for(int n=0;n<4;n++){
                labelLines=Math.max(labelLines,repaymentFact(groupLabels.get(n+3),n,headingFont).size());
                valueLines=Math.max(valueLines,repaymentFact(groupValues.get(n+3),n,metadataFont).size());
            }
            return identityLines*12+20+labelLines*12+6+valueLines*12+10;
        }
        if(style==Style.AUDIT)return auditGroup(false);
        return expectedGroupHeight();
    }
    private List<String> repaymentIdentityValue(int column)throws IOException {
        float slotWidth=REPAYMENT_IDENTITY[column+1]-REPAYMENT_IDENTITY[column];
        String label=groupLabels.get(column);
        float labelWidth=face(label,headingFont).getStringWidth(label)*10/1000;
        return wrap(groupValues.get(column),10,slotWidth-labelWidth-11,metadataFont);
    }
    private List<String> repaymentFact(String value,int column,PDFont face)throws IOException {
        return wrap(value,10,REPAYMENT_FACTS[column+1]-REPAYMENT_FACTS[column]-8,face);
    }
    private List<String> auditField(int index,float maxWidth)throws IOException {
        return wrap(groupLabels.get(index)+"   "+groupValues.get(index),10,maxWidth,headingFont);
    }
    private float auditGroup(boolean drawFields)throws IOException {
        float baseline=y;
        float[][] edges={{22,270,998},{14,998},{27,378,998}};
        float[] gaps={20,24,10};
        int field=0;
        for(int row=0;row<edges.length;row++){
            int lines=1;
            for(int slot=0;slot<edges[row].length-1;slot++,field++){
                float x=edges[row][slot];
                if(field==0){
                    if(drawFields){
                        draw(groupLabels.getFirst(),x,baseline,10,true,false);
                        drawWith(groupValues.getFirst(),140,baseline,11,metadataFont,false,false,false);
                    }
                    continue;
                }
                var parts=auditField(field,edges[row][slot+1]-x-8);
                lines=Math.max(lines,parts.size());
                if(drawFields)for(int n=0;n<parts.size();n++)draw(parts.get(n),x,baseline-n*12,10,true,false);
            }
            baseline-=lines*12+gaps[row];
        }
        return y-baseline;
    }
    private void drawSourceGroup()throws IOException {
        if(style==Style.REPAYMENT){
            int lines=1;
            for(int n=0;n<3;n++){
                float x=REPAYMENT_IDENTITY[n];
                String label=groupLabels.get(n);float labelWidth=face(label,headingFont).getStringWidth(label)*10/1000;
                drawWith(label,x,y,10,headingFont,false,false,false);
                var parts=repaymentIdentityValue(n);lines=Math.max(lines,parts.size());
                for(int p=0;p<parts.size();p++)drawWith(parts.get(p),x+labelWidth+9,y-p*12,10,metadataFont,false,false,false);
            }
            y-=lines*12+20;
            var labels=new ArrayList<List<String>>();var values=new ArrayList<List<String>>();int labelLines=1,valueLines=1;
            for(int n=0;n<4;n++){
                var l=repaymentFact(groupLabels.get(n+3),n,headingFont);var v=repaymentFact(groupValues.get(n+3),n,metadataFont);
                labels.add(l);values.add(v);labelLines=Math.max(labelLines,l.size());valueLines=Math.max(valueLines,v.size());
            }
            float[] centers={37,148,283,453};
            for(int n=0;n<4;n++){
                for(int p=0;p<labels.get(n).size();p++)drawCentered(labels.get(n).get(p),centers[n],y-p*12,10,headingFont,REPAYMENT_FACTS[n]+2);
                for(int p=0;p<values.get(n).size();p++){
                    String value=values.get(n).get(p);
                    float top=y-labelLines*12-6-p*12;
                    if(n<2)drawWith(value,REPAYMENT_FACTS[n],top,10,metadataFont,false,false,false);
                    else drawCentered(value,centers[n],top,10,metadataFont,REPAYMENT_FACTS[n]+2);
                }
            }
            y-=labelLines*12+6+valueLines*12+10;
        }else if(style==Style.AUDIT){
            y-=auditGroup(true);
        }else {
            float[] positions={9,94,153,265,790};
            for(int n=0;n<4;n++){
                String value=n%2==0?groupLabels.get(n/2):groupValues.get(n/2);
                var parts=wrap(value,10,positions[n+1]-positions[n]-4,headingFont);
                for(int p=0;p<parts.size();p++)draw(parts.get(p),positions[n],y-p*14,10,true,false);
            }
            y-=expectedGroupHeight();
        }
    }
    private float expectedGroupHeight()throws IOException {
        int lines=Math.max(wrap(groupValues.get(0),10,55,headingFont).size(),wrap(groupValues.get(1),10,521,headingFont).size());
        lines=Math.max(lines,wrap(groupLabels.get(1),10,108,headingFont).size());
        return lines*14+13;
    }
    private void drawCentered(String value,float center,float baseline,float point,PDFont preferred,float minLeft)throws IOException {
        float halfWidth=face(value,preferred).getStringWidth(value)*point/2000;
        drawWith(value,Math.max(minLeft,center-halfWidth),baseline,point,preferred,false,false,false);
    }
    public void columns(List<String> labels,float[] weights,boolean[] numeric)throws IOException {
        this.headers=List.copyOf(labels);this.weights=weights.clone();this.numeric=numeric.clone();
        if(labels.size()!=weights.length || labels.size()!=numeric.length || sourceLayout && labels.size()!=sourceEdges.length-1)throw new IllegalArgumentException("Column mismatch");
        header();
    }
    private void header()throws IOException {
        var lines=new ArrayList<List<String>>();int count=1;float point=sourceLayout?10:style==Style.EQUITY?9:8;
        for(int i=0;i<headers.size();i++){
            float max=style==Style.AUDIT && i==0?columnWidth(i)-1:rowWidth(i);
            var value=sourceLayout?wrap(headers.get(i),point,max,headingFont):wrap(headers.get(i),point,columnWidth(i)-10);
            lines.add(value);count=Math.max(count,value.size());
        }
        if(y-count*12-10<55)nextWithoutHeader();
        if(style==Style.ORIGINAL)rule(y+10);
        if(style==Style.ACCOUNT||style==Style.REPAYMENT){
            stream.setNonStrokingColor(style==Style.REPAYMENT?234/255f:.85f);
            if(style==Style.REPAYMENT)stream.addRect(tableLeft(),y-count*12+2,width(),count*12+7);
            else stream.addRect(32,y-count*12-2,width(),count*12+12);
            stream.fill();
            if(style==Style.REPAYMENT){
                float highlight=sourceEdges[headers.size()-1];
                stream.setNonStrokingColor(1f,223/255f,191/255f);
                stream.addRect(highlight,y-count*12+2,sourceEdges[headers.size()]-highlight,count*12+7);
                stream.fill();
            }
        }
        float x=tableLeft();
        for(int i=0;i<lines.size();i++){
            for(int n=0;n<lines.get(i).size();n++){
                String label=lines.get(i).get(n);float edge=numeric[i]?rowRight(i,x):x;
                if(style==Style.EQUITY)drawCentered(label,x+columnWidth(i)/2,y-n*12,point,headingFont,x);
                else if(style==Style.EXPECTED && i==2)drawCentered(label,164,y-n*11,point,headingFont,x);
                else if(sourceLayout && numeric[i] && (style!=Style.REPAYMENT || i>=5))drawCentered(label,(x+edge)/2,y-n*11,point,headingFont,x);
                else draw(label,sourceLayout?x:edge,y-n*(sourceLayout?11:12),point,true,!sourceLayout && numeric[i]);
                if(style==Style.TRIAL){float length=face(label,headingFont).getStringWidth(label)*8/1000;line(numeric[i]?edge-length:edge,numeric[i]?edge:edge+length,y-n*12-2);}
            }x+=columnWidth(i);
        }
        if(sourceLayout)y-=count*11+(style==Style.REPAYMENT?12:4);
        else {y-=count*12+4;if(style==Style.ORIGINAL)rule(y);y-=style==Style.ORIGINAL?12:style==Style.EQUITY?28:4;}
    }
    private void nextWithoutHeader()throws IOException {var saved=headers;headers=List.of();next();headers=saved;}
    public void row(List<String> values,boolean total)throws IOException {
        if(style==Style.EQUITY && total)y-=10;
        var lines=new ArrayList<List<String>>();var points=new ArrayList<Float>();int count=1;
        float leading=sourceLayout?(style==Style.REPAYMENT?12:13.5f):referenceRows?10:style==Style.EQUITY?12:13;
        PDFont rowFont=total?headingFont:bodyFont;
        for(int i=0;i<values.size();i++){
            float max=rowWidth(i),point=sourceLayout?10:referenceRows?7.5f:style==Style.EQUITY?9:numeric[i]?8:9;
            if(total && i==0 && sourceLayout){
                int firstAmount=style==Style.AUDIT?4:style==Style.EXPECTED?5:2;
                max=sourceEdges[firstAmount]-sourceEdges[0]-4;
            }
            if((numeric[i] || style==Style.AUDIT && i==0) && values.get(i).matches("[0-9.,()\\-]+"))
                point=Math.min(point,(max-1)*1000/face(values.get(i),sourceLayout?rowFont:bodyFont).getStringWidth(values.get(i)));
            else if(!total && compactSourceColumn(i) && !values.get(i).isBlank() && !values.get(i).contains("\n")){
                float fit=(max-1)*1000/face(values.get(i),rowFont).getStringWidth(values.get(i));
                point=Math.max(8,Math.min(point,fit));
            }
            points.add(point);
            var value=sourceLayout?wrap(values.get(i),point,max,rowFont):wrap(values.get(i),point,max);
            lines.add(value);count=Math.max(count,value.size());
        }
        int offset=0;
        if(y-Math.min(count*leading+10,100)<55)next();
        while(offset<count){
            if(y<70)next();int take=Math.min(count-offset,Math.max(1,(int)((y-55)/leading)));
            float x=tableLeft();
            for(int i=0;i<lines.size();i++){
                float edge=numeric[i]?rowRight(i,x):x;
                for(int n=0;n<take && offset+n<lines.get(i).size();n++){
                    String part=lines.get(i).get(offset+n);
                    float baseline=y-n*leading;
                    draw(part,edge,baseline,points.get(i),total || style==Style.EQUITY && i==0,numeric[i]);
                    if(total && sourceLayout && numeric[i] && !part.isBlank()){
                        float textWidth=face(part,headingFont).getStringWidth(part)*points.get(i)/1000;
                        line(edge-textWidth,edge,baseline-2);
                    }
                }
                x+=columnWidth(i);
            }
            y-=take*leading;offset+=take;
        }
        if(total){
            if(style==Style.ORIGINAL)rule(y+2);
            else if(!sourceLayout){
                float x=tableLeft();
                for(int i=0;i<values.size();i++){
                    if(numeric[i] && !values.get(i).isBlank()){
                        float edge=rowRight(i,x);
                        line(edge-face(values.get(i),headingFont).getStringWidth(values.get(i))*points.get(i)/1000,edge,y+leading-2);
                    }
                    x+=columnWidth(i);
                }
            }
            y-=style==Style.REPAYMENT?35:style==Style.AUDIT?17:5;
        }
        if(style==Style.ORIGINAL)y-=5;
    }
    private boolean compactSourceColumn(int column){
        return style==Style.AUDIT && (column==1 || column==3 || column>=9)
            || style==Style.EXPECTED && column<=1;
    }
    public void text(String value)throws IOException {for(String line:wrap(value,9,width())){if(y<65)next();draw(line,tableLeft(),y,9,false,false);y-=13;}y-=4;}
    public void section(String value)throws IOException {if(y<90)next();y-=style==Style.ORIGINAL?10:16;for(String line:wrap(value,10,width())){if(y<65)next();draw(line,tableLeft(),y,10,true,false);y-=14;}if(style==Style.ORIGINAL){rule(y+3);y-=8;}else y-=4;}
    /** End table before notes/provenance, so subsequent pages do not repeat an unrelated column header. */
    public void endTable(){headers=List.of();groupLabels=List.of();groupValues=List.of();}
    public void sourcePageNumbers(String pattern){sourcePageNumber=pattern;}
    public void sourceTotalsGap(){if(style==Style.AUDIT)y-=17;}
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
        return wrap(value,point,max,bodyFont);
    }
    private List<String> wrap(String value,float point,float max,PDFont preferred)throws IOException {
        var result=new ArrayList<String>();var line=new StringBuilder();
        for(int cp:value.codePoints().toArray()){
            if(cp=='\n'){result.add(line.toString());line.setLength(0);continue;}if(Character.isISOControl(cp))continue;
            String c=new String(Character.toChars(cp));try{font.encode(c);}catch(IllegalArgumentException unsupported){c="?";}
            if(!line.isEmpty() && face(line+c,preferred).getStringWidth(line+c)*point/1000>max){
                int space=line.lastIndexOf(" ");String rest=space>0?line.substring(space+1):"";
                result.add(space>0?line.substring(0,space):line.toString());line.setLength(0);line.append(rest);
            }line.append(c);
        }result.add(line.toString());return result;
    }
    @Override public void close()throws IOException {
        if(finished)return;finished=true;if(stream!=null)stream.close();
        for(int n=firstPage;n<document.getNumberOfPages();n++)try(var footer=new PDPageContentStream(document,document.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
            stream=footer;String number=(n-firstPage+1)+" / "+(document.getNumberOfPages()-firstPage);
            if(sourceLayout){
                number=java.text.MessageFormat.format(sourcePageNumber,Integer.toString(n-firstPage+1),Integer.toString(document.getNumberOfPages()-firstPage));
                PDFont footerFont=style==Style.REPAYMENT?metadataFont:bodyFont;
                drawCentered(number,size.getWidth()/2,style==Style.AUDIT?49:style==Style.EXPECTED?22:30,10,footerFont,0);
            }
            else draw(number,size.getWidth()-32,24,8,false,true);
        }stream=null;
    }
}
