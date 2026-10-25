package com.sacco.mvp.accounting.reports;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.awt.Color;
import java.io.IOException;
import java.util.*;

/** Receipt/payment stationery shared by ordinary vouchers and loan postings. */
public final class VoucherPdfDocument implements AutoCloseable {
    public record Field(String label,String value) {}
    public record Source(String title,String institution,String branch,String number,String status,
        List<Field> header,String partyTitle,List<Field> party,String detailsTitle,List<Field> details,
        List<String> columns,float[] widths,List<List<String>> rows,String wordsLabel,String words,
        List<Field> totals,String narrationLabel,String narration,String postingLabel,List<Field> posting,
        String pageLabel,String continued,byte[] logo,String methodLabel,List<Field> methods) {}
    private static final float LEFT=22,RIGHT=573,WIDTH=551;
    private final PDDocument document;
    private final PDType0Font font;
    private final Source source;
    private final PDImageXObject logo;
    private PDPageContentStream stream;
    private float y;

    public VoucherPdfDocument(PDDocument document,PDType0Font font,Source source)throws IOException {
        this.document=document;this.font=font;this.source=source;
        logo=source.logo()==null?null:PDImageXObject.createFromByteArray(document,source.logo(),"Institution logo");
    }
    public void write()throws IOException {
        page(false);
        float top=y;
        int split=Math.min(3,source.party().size());
        float partyEnd=fields(source.party().subList(0,split),LEFT,top,275,80);
        if(split<source.party().size())partyEnd=fields(source.party().subList(split,source.party().size()),LEFT,partyEnd-18,275,80);
        paymentMethods(top);
        float detailsEnd=fields(source.details(),325,top-48,248,108);
        y=Math.min(top-130,Math.min(partyEnd,detailsEnd)-18);
        tableHeader();
        for(var row:source.rows())row(row);
        // Preserve stationery spacing without numbering empty writing slots.
        float required=tailHeight();
        for(int n=source.rows().size();n<10 && y-20>=required;n++){
            y-=20;
        }
        if(y<210)page(false);
        y-=24;
        float summary=y;
        text(source.wordsLabel().toUpperCase(Locale.ROOT),LEFT,summary,10,true);
        float wordsEnd=paragraph(source.words(),LEFT,summary-19,340,9);
        float totalsEnd=summary;
        for(int n=0;n<source.totals().size();n++){
            var total=source.totals().get(n);boolean last=n==source.totals().size()-1;
            totalsEnd=field(total,384,totalsEnd,189,101,last);totalsEnd-=7;
        }
        y=Math.min(wordsEnd,totalsEnd)-14;
        section(source.narrationLabel());
        y=paragraph(narration(),LEFT,y-18,WIDTH,9)-22;
        posting();
        close();
        for(int n=0;n<document.getNumberOfPages();n++)try(var footer=new PDPageContentStream(document,document.getPage(n),PDPageContentStream.AppendMode.APPEND,true,true)){
            stream=footer;
            text(source.number()+" | "+source.status(),LEFT,24,7,false);
            right(source.pageLabel()+" "+(n+1)+" / "+document.getNumberOfPages(),RIGHT,24,7,false);
        }
        stream=null;
    }
    private String narration()throws IOException {
        String value=source.narration();
        if(wrap(source.institution(),12,155).size()>2)value+="\n"+source.institution();
        if(wrap(source.branch(),9,155).size()>2 || wrap(source.branch(),9,82).size()>2)value+="\n"+source.branch();
        return value;
    }
    private float tailHeight()throws IOException {
        int postingLines=2;
        for(var item:source.posting())postingLines=Math.max(postingLines,wrap(item.label().toUpperCase(Locale.ROOT),9,126).size()+wrap(item.value(),8,126).size());
        float totals=0;
        for(var item:source.totals())totals+=wrap(item.label().toUpperCase(Locale.ROOT),8,93).size()*12+7;
        return 24+Math.max(19+wrap(source.words(),9,340).size()*13,totals)+14+18+wrap(narration(),9,WIDTH).size()*13+22+24+10+postingLines*13+45;
    }
    private void posting()throws IOException {
        int lines=1;
        for(var item:source.posting())lines=Math.max(lines,wrap(item.label().toUpperCase(Locale.ROOT),9,126).size()+wrap(item.value(),8,126).size());
        float height=24+10+lines*13;
        if(y-height<45)page(false);
        section(source.postingLabel());y-=24;
        // Actual posting provenance replaces the sample's unrecorded checker/approval evidence.
        float postingTop=y,postingEnd=y;
        for(int n=0;n<source.posting().size();n++){
            float x=LEFT+(n%4)*138;
            if(n>0 && n%4==0){postingTop=postingEnd-15;if(postingTop<110){page(false);postingTop=y;}}
            var item=source.posting().get(n);
            float end=paragraph(item.label().toUpperCase(Locale.ROOT),x,postingTop,126,9,true);
            end=paragraph(item.value(),x,end-10,126,8);
            postingEnd=Math.min(postingEnd,end);
        }
    }
    private void paymentMethods(float top)throws IOException {
        if(source.methods().isEmpty())return;
        paragraph(source.methodLabel().toUpperCase(Locale.ROOT),325,top,106,8);
        for(int n=0;n<source.methods().size();n++){
            float x=438+(n%2)*67,baseline=top-(n/2)*19;
            stream.setStrokingColor(Color.BLACK);stream.setLineWidth(.6f);stream.addRect(x,baseline-2,10,10);stream.stroke();
            if("true".equals(source.methods().get(n).value())){
                stream.moveTo(x+2,baseline+3);stream.lineTo(x+4,baseline);stream.lineTo(x+8,baseline+6);stream.stroke();
            }
            paragraph(source.methods().get(n).label(),x+14,baseline,54,7);
        }
    }
    private void page(boolean table)throws IOException {
        close();var page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);
        if(logo!=null){float scale=Math.min(194f/logo.getWidth(),90f/logo.getHeight());
            float w=logo.getWidth()*scale,h=logo.getHeight()*scale;
            stream.drawImage(logo,LEFT+(194-w)/2,724+(90-h)/2,w,h);
        }
        float identity=paragraph(abbreviate(source.institution(),12,155),230,815,155,12,true);
        float identityEnd=paragraph(abbreviate(source.branch(),9,155),230,identity-5,155,9);
        float title=paragraph(source.title().toUpperCase(Locale.ROOT),397,815,176,17,true);
        var header=new ArrayList<Field>();
        for(var item:source.header())header.add(new Field(item.label(),abbreviate(item.value(),9,82)));
        float headerEnd=fields(header,397,title-10,176,82);
        y=Math.min(690,Math.min(identityEnd,headerEnd)-18);
        if(document.getNumberOfPages()==1){
            paragraph(source.partyTitle().toUpperCase(Locale.ROOT),LEFT,y,285,11,true);
            paragraph(source.detailsTitle().toUpperCase(Locale.ROOT),325,y,248,11,true);y-=29;
        }else{paragraph(source.continued(),LEFT,y,WIDTH,9);y-=22;}
        if(table)tableHeader();
    }
    private String abbreviate(String value,float size,float width)throws IOException {
        var lines=wrap(value,size,width);
        if(lines.size()<=2)return value;
        return lines.getFirst()+"\n"+lines.get(1)+" …";
    }
    private float fields(List<Field> values,float x,float top,float width,float labelWidth)throws IOException {
        for(var value:values){top=field(value,x,top,width,labelWidth,false)-8;}
        return top;
    }
    private float field(Field value,float x,float top,float width,float labelWidth,boolean bold)throws IOException {
        var labels=wrap(value.label().toUpperCase(Locale.ROOT),8,labelWidth-8);
        String raw=Objects.toString(value.value(),"");float size=9,available=width-labelWidth-12;
        if(raw.matches("[0-9.,()\\-]+"))size=Math.min(size,available*1000/font.getStringWidth(raw));
        var values=wrap(raw,size,available);
        int count=Math.max(labels.size(),values.size());
        if(top-count*12<60){page(false);top=y;}
        for(int n=0;n<count;n++){
            if(n<labels.size())text(labels.get(n),x,top-n*12,8,bold);
            if(n==0)text(":",x+labelWidth,top,9,false);
            if(n<values.size())text(values.get(n),x+labelWidth+10,top-n*12,size,bold);
        }
        return top-count*12;
    }
    private void tableHeader()throws IOException {
        var cells=new ArrayList<List<String>>();int count=1;
        for(int n=0;n<source.columns().size();n++){
            var lines=wrap(source.columns().get(n).toUpperCase(Locale.ROOT),8,WIDTH*source.widths()[n]-12);
            cells.add(lines);count=Math.max(count,lines.size());
        }
        float height=Math.max(35,count*11+12);
        stream.setNonStrokingColor(new Color(233,234,235));stream.addRect(LEFT,y-height,WIDTH,height);stream.fill();
        float x=LEFT;
        for(int n=0;n<cells.size();n++){
            for(int j=0;j<cells.get(n).size();j++)text(cells.get(n).get(j),x+6,y-14-j*11,8,false);
            x+=WIDTH*source.widths()[n];
        }y-=height;
    }
    private void row(List<String> values)throws IOException {
        var cells=new ArrayList<List<String>>();var sizes=new ArrayList<Float>();int count=1;
        for(int n=0;n<values.size();n++){
            float width=WIDTH*source.widths()[n]-12,size=n<2?9:8;
            String value=Objects.toString(values.get(n),"");
            if(n>1 && value.matches("[0-9.,()\\-]+"))size=Math.min(size,width*1000/font.getStringWidth(value));
            var lines=wrap(value,size,width);cells.add(lines);sizes.add(size);count=Math.max(count,lines.size());
        }
        int offset=0;
        if(y-Math.min(count*12+8,100)<60)page(true);
        while(offset<count){
            if(y<82)page(true);
            int take=Math.min(count-offset,Math.max(1,(int)((y-60)/12)));float x=LEFT;
            for(int n=0;n<cells.size();n++){
                for(int j=0;j<take && offset+j<cells.get(n).size();j++){
                    String value=cells.get(n).get(offset+j);float baseline=y-14-j*12;
                    if(n>1 && values.get(n).matches("[0-9.,()\\-]+"))right(value,x+WIDTH*source.widths()[n]-6,baseline,sizes.get(n),false);
                    else text(value,x+6,baseline,sizes.get(n),false);
                }x+=WIDTH*source.widths()[n];
            }y-=Math.max(20,take*12+8);offset+=take;
        }
    }
    private void section(String title)throws IOException {
        if(y<95)page(false);
        paragraph(title.toUpperCase(Locale.ROOT),LEFT,y,WIDTH,10,true);
    }
    private float paragraph(String value,float x,float top,float width,float size)throws IOException {return paragraph(value,x,top,width,size,false);}
    private float paragraph(String value,float x,float top,float width,float size,boolean bold)throws IOException {
        for(var line:wrap(value,size,width)){
            if(top<45){page(false);top=y;}
            text(line,x,top,size,bold);top-=size+4;
        }return top;
    }
    private List<String> wrap(String value,float size,float width)throws IOException {
        var lines=new ArrayList<String>();var line=new StringBuilder();
        for(int cp:Objects.toString(value,"").codePoints().toArray()){
            if(cp=='\n'){lines.add(line.toString());line.setLength(0);continue;}
            if(Character.isISOControl(cp))continue;
            String ch=new String(Character.toChars(cp));try{font.encode(ch);}catch(IllegalArgumentException ignored){ch="?";}
            if(!line.isEmpty() && font.getStringWidth(line+ch)*size/1000>width){
                int space=line.lastIndexOf(" ");String rest=space>0?line.substring(space+1):"";
                lines.add(space>0?line.substring(0,space):line.toString());line.setLength(0);line.append(rest);
            }line.append(ch);
        }lines.add(line.toString());return lines;
    }
    private void text(String value,float x,float top,float size,boolean bold)throws IOException {
        stream.beginText();stream.setNonStrokingColor(Color.BLACK);stream.setStrokingColor(Color.BLACK);
        stream.setLineWidth(.22f);stream.setRenderingMode(bold?RenderingMode.FILL_STROKE:RenderingMode.FILL);
        stream.setFont(font,size);stream.newLineAtOffset(x,top);stream.showText(value);stream.endText();
    }
    private void right(String value,float x,float top,float size,boolean bold)throws IOException {text(value,x-font.getStringWidth(value)*size/1000,top,size,bold);}
    @Override public void close()throws IOException {if(stream!=null){stream.close();stream=null;}}
}
