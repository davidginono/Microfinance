package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceipt;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanReceiptPdfService {
    private final LoanRecordingPdfService renderer;
    private final MessageSource messages;
    public byte[] render(LoanReceipt receipt,Locale locale,byte[] logo){
        var v=receipt.header();var rows=new ArrayList<List<String>>();
        for(var r:receipt.rows())rows.add(List.of(Integer.toString(r.lineNo()),r.description()+"\n"+label("loan.loanId",locale)+": "+r.loanNumber()+"\n"+label("recording.principal",locale)+": "+money(r.principal(),locale)+"; "+label("recording.interest",locale)+": "+money(r.interest(),locale)+"\n"+label("recording.balanceAfter",locale)+": "+money(r.principalBalance(),locale),
            r.reference()+"\n"+r.moneyAccount()+"\n"+r.principalAccount()+(r.interest().signum()>0?"\n"+r.interestAccount():""),money(r.amount(),locale)));
        var principal=receipt.rows().stream().map(r->r.principal()).reduce(BigDecimal.ZERO,BigDecimal::add);
        var interest=receipt.rows().stream().map(r->r.interest()).reduce(BigDecimal.ZERO,BigDecimal::add);
        var date=v.effectiveDate().format(DateTimeFormatter.ofPattern("dd / MM / uuuu"));
        return renderer.renderPosting(new Source(label("voucher.title.RECEIPT",locale),v.institutionName(),v.branchName(),v.number(),label("voucher.status."+v.status(),locale),
            List.of(field("voucher.print.number",v.number(),locale),field("voucher.print.receiptDate",date,locale),field("voucher.print.branch",v.branchName(),locale)),
            label("voucher.print.payerDetails",locale),List.of(field("voucher.print.name",v.party(),locale),field("voucher.print.clientNumber",receipt.clientNumber(),locale)),
            label("voucher.print.receiptDetails",locale),List.of(field("voucher.print.moneyAccount",receipt.rows().getFirst().moneyAccount(),locale),field("voucher.print.reference",v.reference(),locale),field("voucher.date",date,locale)),
            List.of(label("voucher.print.no",locale),label("voucher.print.particulars",locale),label("voucher.print.ledger",locale),label("voucher.amount",locale)+" (TZS)"),new float[]{.06f,.44f,.30f,.20f},rows,
            label("voucher.print.amountWords",locale),VoucherPdfService.amountInWords(v.total(),locale),List.of(field("recording.principal",money(principal,locale),locale),field("recording.interest",money(interest,locale),locale),field("voucher.print.totalAmount",money(v.total(),locale),locale)),
            label("voucher.print.narration",locale),v.description()+"\n"+label("voucher.evidence",locale)+": "+v.evidence(),label("voucher.print.postingRecord",locale),
            List.of(field("voucher.postedBy",v.postedByName(),locale),field("voucher.postedAt",v.postedAt().atZoneSameInstant(java.time.ZoneId.of("Africa/Dar_es_Salaam")).format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss z")),locale),field("voucher.status",label("voucher.status."+v.status(),locale),locale)),
            label("voucher.page",locale),label("voucher.continued",locale),logo,label("voucher.print.method",locale),List.of()));
    }
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private Field field(String key,String value,Locale locale){return new Field(label(key,locale),value);}
    private String money(BigDecimal value,Locale locale){var f=NumberFormat.getNumberInstance(locale);f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f.format(value);}
}
