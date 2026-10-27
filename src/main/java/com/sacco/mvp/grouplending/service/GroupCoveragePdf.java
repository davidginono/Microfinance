package com.sacco.mvp.grouplending.service;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.*;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.Detail;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupCoveragePdf {
    private final LoanRecordingPdfService renderer;
    private final MessageSource messages;
    private String text(String k,Locale l){return messages.getMessage(k,null,l);}
    private String money(BigDecimal n,Locale l){if(n==null)return "—";var f=NumberFormat.getNumberInstance(l);f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f.format(n);}
    public byte[] render(Detail d,Locale l){
        var c=d.header();var fields=new ArrayList<StatementField>();
        for(var pair:List.of(new String[]{"group.loan",c.loanNumber()},new String[]{"coverage.borrower",c.borrower()},new String[]{"coverage.person",c.number()+" - "+c.name()},new String[]{"coverage.kind",text("coverage.kind."+c.sourceKind(),l)},new String[]{"group.status",text("coverage.state."+c.state(),l)},new String[]{"coverage.requested",money(c.requested(),l)},new String[]{"coverage.received",money(c.received(),l)},new String[]{"coverage.unpaid",money(c.unpaid(),l)},new String[]{"coverage.expires",c.expiresOn().toString()},new String[]{"coverage.terms",c.terms()}))fields.add(new StatementField(text(pair[0],l),pair[1]));
        var events=d.events().rows().stream().map(x->List.of(Integer.toString(x.sequence()),text("coverage.state."+x.kind(),l),money(x.amount(),l),x.promiseOn()==null?"—":x.promiseOn().toString(),x.reason(),com.sacco.mvp.service.ApplicationTimestamps.date(x.at().toLocalDate()))).toList();
        var payments=d.payments().rows().stream().map(x->List.of(x.reference(),x.date().toString(),x.payer(),money(x.amount(),l),money(x.principal(),l),money(x.interest(),l),money(x.charges(),l),money(x.net(),l),x.reversedBy()!=null?text("coverage.receiptReversed",l)+x.reversedBy():x.correctedBy()!=null?text("coverage.linkCorrected",l)+x.correctedBy():"—")).toList();
        return renderer.renderStatement(new StatementDocument(text("coverage.case",l),fields,List.of(section("coverage.responses",List.of("coverage.sequence","group.status","voucher.amount","coverage.promiseDate","group.reason","voucher.date"),events,List.of(50f,110f,80f,90f,300f,100f),2,l),section("coverage.payments",List.of("memberStatement.reference","voucher.date","group.payer","voucher.amount","recording.principal","recording.interest","charges.title","coverage.net","coverage.correction"),payments,List.of(95f,85f,120f,80f,80f,80f,80f,80f,160f),3,l)),text("coverage.empty",l),text("coverage.noDebtChange",l)));
    }
    private StatementSection section(String title,List<String> columns,List<List<String>> rows,List<Float> widths,int money,Locale l){return new StatementSection(List.of(new StatementField(text(title,l),"")),columns.stream().map(k->text(k,l)).toList(),rows,widths,money);}
}
