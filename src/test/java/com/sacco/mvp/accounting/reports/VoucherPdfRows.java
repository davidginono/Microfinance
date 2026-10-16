package com.sacco.mvp.accounting.reports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.*;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Checks the account and numeric cells at the same rendered PDF baseline. */
public final class VoucherPdfRows {
    private VoucherPdfRows() {}
    public static void assertRow(PDDocument document,String account,int number,String debit,String credit)throws IOException {
        var baselines=new ArrayList<Float>();
        var finder=new PDFTextStripper(){
            @Override protected void writeString(String text,List<TextPosition> positions){
                var ledger=positions.stream().filter(p->p.getXDirAdj()>=195 && p.getXDirAdj()<342).toList();
                String value=ledger.stream().map(TextPosition::getUnicode).collect(java.util.stream.Collectors.joining());
                if(value.startsWith(account))baselines.add(ledger.getFirst().getYDirAdj());
            }
        };
        finder.setStartPage(1);finder.setEndPage(1);finder.getText(document);
        assertThat(baselines).as("Rendered ledger row for %s",account).hasSize(1);
        var cells=new PDFTextStripperByArea();float top=baselines.getFirst()-10;
        cells.addRegion("number",new Rectangle2D.Float(22,top,30,13));
        cells.addRegion("debit",new Rectangle2D.Float(342,top,76,13));
        cells.addRegion("credit",new Rectangle2D.Float(419,top,76,13));
        cells.extractRegions(document.getPage(0));
        assertThat(cells.getTextForRegion("number").strip()).isEqualTo(Integer.toString(number));
        assertThat(cells.getTextForRegion("debit").strip()).isEqualTo(debit);
        assertThat(cells.getTextForRegion("credit").strip()).isEqualTo(credit);
    }
}
