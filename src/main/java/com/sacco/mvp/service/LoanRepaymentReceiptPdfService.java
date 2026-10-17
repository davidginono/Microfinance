package com.sacco.mvp.service;

import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument.Field;
import com.sacco.mvp.accounting.service.VoucherPdfService;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanRepaymentReceiptPdfService {
    private final LoanRepaymentLedgerService ledger;
    private final LoanApplicationRepository loans;
    private final MemberRepository members;
    private final RegisteredSaccoRepository institutions;
    private final SaccoStationRepository branches;
    private final SaccoLogoStorageService logos;
    private final LoanRecordingPdfService pdf;
    private final MessageSource messages;

    public byte[] render(UUID loanId,UUID receiptId,AppUserPrincipal actor,Locale locale){
        // The same tenant, branch and client ownership guard as the receipt screen runs first.
        var r=ledger.receipt(loanId,receiptId,actor);
        var loan=loans.findById(loanId).filter(l->Objects.equals(l.getSaccoId(),actor.getSaccoId())
            && (actor.isStaffSession()?Objects.equals(l.getStationId(),actor.getStationId()):Objects.equals(l.getApplicantMemberId(),actor.getMemberId())))
            .orElseThrow(()->new AccessDeniedException("Forbidden"));
        var client=members.findById(loan.getApplicantMemberId()).filter(m->Objects.equals(m.getSaccoId(),actor.getSaccoId()))
            .orElseThrow(()->new AccessDeniedException("Forbidden"));
        String institution=institutions.findById(actor.getSaccoId()).map(RegisteredSacco::getSaccoName).orElse("");
        String branch=branches.findBySaccoIdAndStationId(actor.getSaccoId(),loan.getStationId()).map(SaccoStation::getAddressLocation).filter(s->s!=null && !s.isBlank()).orElse(loan.getStationId());
        if("INTERNAL".equals(r.channel()))return pdf.render(new LoanRecordingPdfService.Document(label("recording.document."+r.kind(),locale),institution,branch,"loan-settlement-"+r.reference()+".pdf",
            List.of(label("loan.loanId",locale)+": "+r.loanId(),label("recording.client",locale)+": "+client.getFullName(),label("voucher.reference",locale)+": "+r.reference(),
                label("voucher.date",locale)+": "+r.paymentDate(),label("recording.principal",locale)+": TZS "+money(r.principal(),locale),label("recording.dueInterest",locale)+": TZS "+money(r.interest(),locale),
                label("recording.topUp.internal",locale),Objects.toString(r.reason(),""))));
        String status=label(r.reversedBy()!=null?"repayment.reversed":"repayment.kind."+r.kind(),locale);
        String date=r.paymentDate().format(DateTimeFormatter.ofPattern("dd / MM / uuuu"));
        String reference=label("loan.loanId",locale)+": "+r.loanId()+"\n"+r.channelReference();
        var rows=List.of(List.of("1",label("repayment.principal",locale),reference,money(r.principal(),locale)),
            List.of("2",label("repayment.interest",locale),reference,money(r.interest(),locale)));
        var posting=new ArrayList<Field>(List.of(field("loan.loanId",r.loanId(),locale),field("repayment.postedAt",
            r.postedAt().atZoneSameInstant(ZoneId.of("Africa/Dar_es_Salaam")).format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss z")),locale),field("common.status",status,locale)));
        if(r.reverses()!=null)posting.add(field("repayment.original",r.reverses().toString(),locale));
        if(r.reversedBy()!=null)posting.add(field("repayment.correction",r.reversedBy().toString(),locale));
        byte[] logo=logos.hasLogo(actor.getSaccoId())?logos.load(actor.getSaccoId()).content():null;
        return pdf.renderPosting(new VoucherPdfDocument.Source(label("voucher.title.RECEIPT",locale),institution,branch,r.reference(),status,
            List.of(field("voucher.print.number",r.reference(),locale),field("voucher.print.receiptDate",date,locale),field("voucher.print.branch",branch,locale)),
            label("voucher.print.payerDetails",locale),List.of(field("voucher.print.name",client.getFullName(),locale),field("voucher.print.clientNumber",client.getMemberNo(),locale),field("loan.loanId",r.loanId(),locale),field("voucher.print.phone","",locale),field("voucher.print.email","",locale)),
            label("voucher.print.receiptDetails",locale),List.of(field("voucher.print.reference",r.channelReference(),locale),field("repayment.paymentDate",date,locale)),
            List.of(label("voucher.print.no",locale),label("voucher.print.particulars",locale),label("voucher.print.reference",locale),label("voucher.amount",locale)+" (TZS)"),
            new float[]{.06f,.44f,.30f,.20f},rows,label("voucher.print.amountWords",locale),VoucherPdfService.amountInWords(r.amount(),locale),
            List.of(field("voucher.print.totalAmount",money(r.amount(),locale),locale)),label("voucher.print.narration",locale),label("repayment.receipt",locale)+"\n"+Objects.toString(r.reason(),""),
            label("voucher.print.postingRecord",locale),posting,label("voucher.page",locale),label("voucher.continued",locale),logo,label("voucher.print.method",locale),
            List.of(method("CASH",r.channel(),locale),method("BANK",r.channel(),locale),method("CHEQUE",r.channel(),locale),method("MOBILE_MONEY",r.channel(),locale))));
    }
    private Field field(String key,String value,Locale locale){return new Field(label(key,locale),Objects.toString(value,""));}
    private Field method(String code,String channel,Locale locale){return field("voucher.print.method."+code,Boolean.toString(code.equals(channel)),locale);}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private String money(BigDecimal value,Locale locale){var format=NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(value);}
}
