package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.grouplending.dto.GroupSharedAgreementDtos.Export;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;

@Component @RequiredArgsConstructor
public class GroupSharedAgreementPdf {
    private final MessageSource messages;
    public byte[] render(Export data,Locale locale){
        var p=data.packet();var r=p.row();var d=r.document();var t=p.terms();var n=NumberFormat.getNumberInstance(locale);n.setMinimumFractionDigits(2);n.setMaximumFractionDigits(2);
        try(var pdf=new PDDocument();var font=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var original=Loader.loadPDF(data.original());var out=new ByteArrayOutputStream()){
            try(var page=new AccountingPdfDocument(pdf,PDType0Font.load(pdf,font),false,label("sharedAgreement.title",locale),List.of(d.institutionName(),d.branch()))){
                page.columns(List.of(label("orgDocument.field",locale),label("orgDocument.value",locale)),new float[]{.34f,.66f},new boolean[]{false,false});
                field(page,"group.name",r.group(),locale);field(page,"group.loan",r.number(),locale);field(page,"group.client",r.clientNumber()+" - "+r.clientName(),locale);field(page,"group.principal",n.format(t.principal()),locale);
                field(page,"group.annualRate",n.format(t.annualRate().movePointRight(2))+"%",locale);field(page,"group.contractualInterest",n.format(t.contractualInterest()),locale);
                field(page,"group.instalment",n.format(t.instalment())+" - "+label("group.frequency."+t.frequency(),locale),locale);
                field(page,"group.tenure",t.tenureValue()+" "+label("group.unit."+t.tenureUnit(),locale),locale);field(page,"group.chargeTotal",n.format(t.chargeTotal()),locale);
                field(page,"group.deductions",n.format(t.deductions()),locale);field(page,"group.totalPayable",n.format(t.totalPayable()),locale);
                field(page,"group.sharedLiability",label("group.sharedLiability."+r.obligation().liability(),locale),locale);field(page,"group.sharedCap",n.format(r.obligation().cap()),locale);
                field(page,"orgDocument.reference",d.reference(),locale);field(page,"orgDocument.address",d.institutionAddress(),locale);
                field(page,"orgDocument.contact",d.contactPhone()+" / "+d.contactEmail(),locale);field(page,"orgDocument.complaints",d.complaintPhone()+" / "+d.complaintEmail()+" / "+d.complaintAddress(),locale);page.endTable();
                GroupDisclosurePdf.render(page,t.groupDisclosure(),messages,locale);page.section(label("group.sharedObligation",locale));page.text(label("group.sharedDisclosure."+r.obligation().liability(),locale));page.text(label("sharedAgreement.repaymentRule",locale));
                page.columns(List.of(label("orgDocument.field",locale),label("orgDocument.value",locale)),new float[]{.34f,.66f},new boolean[]{false,false});
                field(page,"group.principal",n.format(r.obligation().allocation().principal()),locale);field(page,"group.contractualInterest",n.format(r.obligation().allocation().interest()),locale);
                field(page,"group.chargeTotal",n.format(r.obligation().allocation().charges()),locale);field(page,"group.deductions",n.format(r.obligation().allocation().deductions()),locale);field(page,"group.shared.net",n.format(r.obligation().allocation().net()),locale);
                field(page,"voucher.moneyAccount",r.destination().provider()+" / "+r.destination().accountHolderName()+" / "+r.destination().accountIdentifier(),locale);page.endTable();
                page.section(label("group.participants",locale));page.columns(List.of(label("group.client",locale),label("group.principal",locale)),new float[]{.7f,.3f},new boolean[]{false,false});
                for(var person:data.parties())page.row(List.of(person.number()+" - "+person.name(),n.format(person.principal())),false);page.endTable();
                page.section(label("orgAgreement.schedule",locale));page.columns(List.of(label("orgAgreement.payment",locale),label("orgAgreement.dueDate",locale),label("group.principal",locale),label("orgDocument.interest",locale)),new float[]{.1f,.3f,.3f,.3f},new boolean[]{false,false,false,false});
                for(var due:data.schedule())page.row(List.of(Integer.toString(due.number()),com.sacco.mvp.service.ApplicationTimestamps.date(due.date()),n.format(due.principal()),n.format(due.interest())),false);page.endTable();
                page.section(label("orgDocument.evidence",locale));page.text(label("orgDocument.packetHash",locale)+": "+r.hash());page.text(label("orgDocument.fileHash",locale)+": "+d.sha256());
                if(r.confirmation()!=null)page.text(label("sharedAgreement.signed",locale)+": "+r.clientNumber()+" - "+r.clientName()+" / "+com.sacco.mvp.service.ApplicationTimestamps.format(r.confirmation().at())+" / "+label(r.confirmation().otpRequired()?"orgDocument.signatureOtp":"orgDocument.signature",locale));
                if(r.confirmation()!=null&&r.confirmation().preferredLanguage()!=null)page.text(label("groupDisclosure.preferredLanguage",locale)+": "+label("orgDocument.language."+r.confirmation().preferredLanguage(),locale));
                page.text(label("orgDocument.appended",locale));
            }
            new PDFMergerUtility().appendDocument(pdf,original);pdf.getDocumentInformation().setTitle(label("sharedAgreement.title",locale));pdf.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Shared agreement PDF could not be prepared",e);}
    }
    private void field(AccountingPdfDocument page,String key,String value,Locale locale)throws IOException{page.row(List.of(label(key,locale),Objects.toString(value,"")),false);}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
}
