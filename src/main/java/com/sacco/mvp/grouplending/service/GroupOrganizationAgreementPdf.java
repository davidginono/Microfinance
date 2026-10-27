package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.grouplending.dto.GroupOrganizationAgreementDtos.ExportData;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Pure bounded rendering; never called inside an accounting or signing transaction. */
@Component @RequiredArgsConstructor
public class GroupOrganizationAgreementPdf {
    private final MessageSource messages;
    public byte[] render(ExportData data,Locale locale){return render(data,locale,true);}
    public byte[] render(ExportData data,Locale locale,boolean includeSignatures){
        var s=data.row().snapshot();var d=s.document();if(d==null)throw new IllegalArgumentException("group.error.documentRequired");var f=s.financial();
        var number=NumberFormat.getNumberInstance(locale);number.setMinimumFractionDigits(2);number.setMaximumFractionDigits(2);
        try(var pdf=new PDDocument();var font=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var original=Loader.loadPDF(data.terms());var out=new ByteArrayOutputStream()){
            try(var page=new AccountingPdfDocument(pdf,PDType0Font.load(pdf,font),false,label("orgAgreement.title",locale),List.of(d.institutionName(),label("voucher.branch",locale)+": "+d.branch()))){
                page.text(label("orgAgreement.representation",locale));page.columns(List.of(label("orgDocument.field",locale),label("orgDocument.value",locale)),new float[]{.34f,.66f},new boolean[]{false,false});
                field(page,"group.name",s.organization().name(),locale);field(page,"group.loan",s.loan().number(),locale);field(page,"group.product",s.loan().product(),locale);
                field(page,"group.status",label("loan.status."+data.row().loanStatus(),locale),locale);
                field(page,"group.purpose",s.loan().purpose(),locale);field(page,"group.policyReference",s.loan().policyReference(),locale);field(page,"group.agreement",s.loan().agreementReference(),locale);
                field(page,"group.principal",number.format(s.loan().principal()),locale);field(page,"recording.basis.ANNUAL",f.interestRate().movePointRight(2).stripTrailingZeros().toPlainString()+"%",locale);
                field(page,"orgAgreement.interestMethod",label("orgAgreement.method."+f.interestMethod(),locale),locale);
                field(page,"group.instalment",number.format(f.periodicRepaymentAmount())+" - "+label("group.frequency."+f.repaymentFrequency(),locale),locale);
                field(page,"group.maximumInstalment",number.format(f.maximumInstallmentAmount()),locale);field(page,"group.tenure",f.tenureValue()+" "+label("group.unit."+f.tenureUnit(),locale),locale);
                field(page,"group.contractualInterest",number.format(f.interestAmount()),locale);field(page,"orgAgreement.recordedChargeTotal",number.format(f.recordedChargeTotal()),locale);
                field(page,"orgAgreement.recordedChargeDeductions",number.format(f.recordedChargeDeductions()),locale);field(page,"orgAgreement.recordedNetCashEstimate",number.format(f.recordedNetCashEstimate()),locale);field(page,"orgAgreement.recordedTotalPayable",number.format(f.recordedTotalPayable()),locale);
                field(page,"orgAgreement.payout",s.payout().provider()+" / "+s.payout().accountHolderName()+" / "+s.payout().accountIdentifier(),locale);
                field(page,"orgDocument.reference",d.reference(),locale);field(page,"orgDocument.language",label("orgDocument.language."+d.language(),locale),locale);
                field(page,"orgDocument.address",d.institutionAddress(),locale);field(page,"orgDocument.contact",contacts(d.contactPhone(),d.contactEmail()),locale);field(page,"orgDocument.complaints",contacts(d.complaintPhone(),d.complaintEmail())+" "+d.complaintAddress(),locale);
                page.endTable();GroupDisclosurePdf.render(page,f.groupDisclosure(),messages,locale);page.section(label("group.costs",locale));
                for(var charge:f.recordedCharges().terms())page.text(label("charges.kind."+charge.kind(),locale)+" - TZS "+number.format(charge.amount())+" - "+label("charges.timing."+charge.timing(),locale)+" - "+Objects.toString(charge.provider(),""));
                if(f.recordedCharges().terms().isEmpty())page.text(label("group.noCharges",locale));page.section(label("prepayment.title",locale));page.text(label("prepayment.mandatory",locale));var terms=f.prepaymentTerms();if(terms!=null){page.text(label("prepayment.schedule."+terms.get("schedule"),locale));page.text(label("prepayment.partial."+terms.get("partialInterest"),locale));page.text(label("prepayment.basis."+terms.get("dayBasis"),locale));}
                page.section(label("orgAgreement.schedule",locale));page.text(label("orgAgreement.projection",locale));if("DAYS".equals(f.tenureUnit()))page.text(label("orgAgreement.dayDates",locale));
                page.columns(List.of(label("orgAgreement.payment",locale),label("orgAgreement.dueDate",locale),label("group.principal",locale),label("orgDocument.interest",locale),label("group.chargeAmount",locale)),new float[]{.10f,.19f,.23f,.24f,.24f},new boolean[]{false,false,true,true,true});
                for(var row:GroupOrganizationAgreementService.schedule(s))page.row(List.of(Integer.toString(row.number()),row.date()==null?"-":row.date().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),number.format(row.principal()),number.format(row.interest()),number.format(row.amount())),false);
                page.endTable();page.section(label("orgDocument.signatures",locale));page.text(label("orgAgreement.signatures",locale)+": "+data.row().signedCount()+" / "+s.authority().requiredSignatures());
                if(includeSignatures&&data.signatures().isEmpty())page.text(label("group.notSigned",locale));
                if(!includeSignatures){page.text(label("orgDocument.physical",locale));for(int i=0;i<s.authority().requiredSignatures();i++)page.text(label("orgDocument.paperLine",locale));}
                if(includeSignatures)for(var signature:data.signatures())page.text((signature.lender()?label("orgFunding.lender",locale)+": ":"")+signature.name()+" ("+signature.number()+") - "+signature.at().format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm xxx"))+" - "+label(signature.otpRequired()?"orgDocument.signatureOtp":"orgDocument.signature",locale)+(signature.preferredLanguage()==null?"":" / "+label("groupDisclosure.preferredLanguage",locale)+": "+label("orgDocument.language."+signature.preferredLanguage(),locale)));
                page.section(label("orgDocument.evidence",locale));page.text(label("orgDocument.packetHash",locale)+": "+data.row().hash());page.text(label("orgDocument.fileHash",locale)+": "+d.sha256());page.text(label("orgDocument.appended",locale));
            }
            new PDFMergerUtility().appendDocument(pdf,original);pdf.getDocumentInformation().setTitle(label("orgAgreement.title",locale));pdf.save(out);return out.toByteArray();
        }catch(IOException error){throw new IllegalStateException("Organization agreement PDF could not be prepared",error);}
    }
    private static String contacts(String phone,String email){return phone+(phone.isEmpty()||email.isEmpty()?"":" / ")+email;}
    private void field(AccountingPdfDocument page,String key,String value,Locale locale)throws IOException{page.row(List.of(label(key,locale),Objects.toString(value,"-")),false);}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
}
