package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.Evidence;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

/** Signed disclosure plus the exact retained approved source; no legal approval is generated here. */
@Component @RequiredArgsConstructor
public class GroupPersonalGuaranteePdf {
    private final MessageSource messages;
    public byte[] render(Evidence e,Locale locale){var p=e.packet();var o=p.offer();var s=p.source();var d=p.document();
        try(var pdf=new PDDocument();var fonts=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var terms=Loader.loadPDF(e.terms());var out=new ByteArrayOutputStream()){
            try(var page=new AccountingPdfDocument(pdf,PDType0Font.load(pdf,fonts),false,text("personalGuarantee.proof",locale),List.of(s.institution()))){
                page.columns(List.of(text("orgDocument.field",locale),text("orgDocument.value",locale)),new float[]{.34f,.66f},new boolean[]{false,false});
                field(page,"personalGuarantee.code",o.code(),locale);field(page,"memberStatement.institution",s.institution()+" / "+d.getInstitutionAddress(),locale);field(page,"personalGuarantee.guarantor",o.number()+" - "+o.name(),locale);field(page,"personalGuarantee.borrower",s.borrower(),locale);field(page,"group.loan",s.number(),locale);
                field(page,"group.status",text("recording.status."+s.status(),locale),locale);field(page,"personalGuarantee.principal",money(s.principal(),locale)+" TZS",locale);field(page,"personalGuarantee.interest",money(s.interest(),locale)+" TZS",locale);field(page,"personalGuarantee.charges",money(s.charges(),locale)+" TZS",locale);field(page,"personalGuarantee.cap",money(o.cap(),locale)+" TZS",locale);field(page,"recording.rate",s.ratePercent().stripTrailingZeros().toPlainString()+"% / "+text("recording.basis."+s.basis(),locale),locale);
                field(page,"personalGuarantee.fundedDate",s.fundedDate()==null?text("sharedStatement.unfunded",locale):com.sacco.mvp.service.ApplicationTimestamps.date(s.fundedDate()),locale);field(page,"orgDocument.signature",o.signature(),locale);field(page,"personalGuarantee.date",com.sacco.mvp.service.ApplicationTimestamps.display(o.decidedAt()),locale);
                field(page,"orgDocument.language",text("orgDocument.language."+o.language(),locale),locale);field(page,"orgDocument.reference",o.reference(),locale);field(page,"orgDocument.contact",d.getContactPhone()+" "+d.getContactEmail(),locale);field(page,"orgDocument.complaints",d.getComplaintPhone()+" "+d.getComplaintEmail()+" "+d.getComplaintAddress(),locale);page.endTable();page.text(text("personalGuarantee.acceptance",locale));page.text(text("personalGuarantee.capNote",locale));page.text(text("orgDocument.packetHash",locale)+": "+o.hash());page.text(text("orgDocument.fileHash",locale)+": "+o.sha256());page.text(text("orgDocument.appended",locale));
            }
            new PDFMergerUtility().appendDocument(pdf,terms);pdf.getDocumentInformation().setTitle(text("personalGuarantee.proof",locale));pdf.save(out);return out.toByteArray();
        }catch(IOException error){throw new IllegalStateException("Personal guarantee evidence could not be prepared",error);}
    }
    private String text(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private void field(AccountingPdfDocument page,String key,String value,Locale locale)throws IOException{page.row(List.of(text(key,locale),Objects.toString(value,"—")),false);}
    private static String money(BigDecimal n,Locale locale){var format=NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(n);}
}
