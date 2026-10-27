package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.*;
import com.sacco.mvp.grouplending.dto.GroupCycleStatementDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupCycleStatementPdf {
    private final LoanRecordingPdfService renderer;
    private final MessageSource messages;
    private String text(String key,Locale l){return messages.getMessage(key,null,l);}
    private StatementField field(String key,String value,Locale l){return new StatementField(text(key,l),value);}
    private String money(BigDecimal n,Locale l){if(n==null)return "—";var f=NumberFormat.getNumberInstance(l);f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f.format(n);}
    private String date(LocalDate d){return d==null?"—":com.sacco.mvp.service.ApplicationTimestamps.date(d);}
    public byte[] render(Statement s,Locale l){
        var c=s.context();var t=s.totals();var fields=new ArrayList<StatementField>(List.of(field("memberStatement.institution",c.institution(),l),field("voucher.print.branch",c.branch(),l),field("group.group",c.code()+" - "+c.name(),l),field("group.model",text("group.model."+c.model(),l),l),field("cycleStatement.context.state",text("group.cycleState."+c.state(),l),l),field("memberStatement.asOf",date(s.asOf()),l),field("cycleStatement.period",(s.from()==null?text("memberStatement.allDates",l):date(s.from()))+" - "+date(s.through()),l),field("cycleStatement.agreed",money(t.agreed(),l),l),field("cycleStatement.fundedOriginal",money(t.fundedPrincipal(),l),l)));
        if(t.funded()==0)fields.add(field("memberStatement.balance",text("sharedStatement.unfunded",l),l));else if(!t.known())fields.add(field("memberStatement.balance",text("cycleStatement.unknown",l),l));else for(var item:List.of(field("sharedStatement.principal",money(t.principal(),l),l),field("cycleStatement.balance.dueInterest",money(t.dueInterest(),l),l),field("cycleStatement.future",money(t.futureInterest(),l),l),field("sharedStatement.charges",money(t.charges(),l),l),field("cycleStatement.balance.arrears",money(t.arrears(),l),l),field("cycleStatement.outstanding",money(t.outstanding(),l),l),field("cycleStatement.nextDue",date(t.nextDue()),l)))fields.add(item);
        var loans=s.loans().stream().map(x->List.of(x.number(),x.borrowerNumber()+" - "+x.borrower(),text("recording.status."+x.status(),l),money(x.original(),l),x.funded()&&x.known()?money(x.principal(),l):"—",x.funded()&&x.known()?money(x.dueInterest(),l):"—",x.funded()&&x.known()?money(x.futureInterest(),l):"—",x.funded()&&x.known()?money(x.charges(),l):"—",x.funded()&&x.known()?money(x.arrears(),l):"—",x.funded()&&x.known()?money(x.outstanding(),l):"—",x.funded()&&x.known()?date(x.nextDue()):"—")).toList();
        var movements=s.movements().stream().map(x->List.of(date(x.date()),x.loanNumber(),x.reference(),text("cycleStatement.kind."+x.kind(),l),x.payer()+(x.reverses().isBlank()?"":"\n"+text("memberStatement.reverses",l)+": "+x.reverses()),money(x.cash(),l),money(x.principal(),l),money(x.interest(),l),money(x.charges(),l),money(x.excluded(),l),money(x.nonCash(),l))).toList();
        var contributions=s.contributions().stream().map(x->List.of(date(x.date()),x.receipt(),x.loan(),x.payer(),x.beneficiary(),text(x.reversal()?"memberStatement.kind.REVERSAL":"memberStatement.kind.PAYMENT",l),money(x.amount(),l),money(x.principal(),l),money(x.interest(),l),money(x.charges(),l))).toList();
        var sections=new ArrayList<StatementSection>(List.of(section("cycleStatement.loans",List.of("group.loan","group.client","group.status","group.principal","sharedStatement.principal","memberStatement.wholeDueInterest","cycleStatement.future","sharedStatement.charges","memberStatement.wholeArrears","cycleStatement.outstanding","cycleStatement.nextDue"),loans,List.of(65f,135f,95f,85f,85f,85f,85f,85f,85f,95f,90f),3,l),section("cycleStatement.activity",List.of("voucher.date","group.loan","memberStatement.reference","group.status","group.payer","cycleStatement.cash","recording.principal","recording.interest","charges.title","memberStatement.nonCash","cycleStatement.nonCash"),movements,List.of(100f,60f,95f,110f,130f,85f,85f,85f,85f,95f,95f),5,l),section("cycleStatement.contributions",List.of("voucher.date","memberStatement.reference","group.loan","group.payer","cycleStatement.beneficiary","group.status","voucher.amount","recording.principal","recording.interest","charges.title"),contributions,List.of(100f,95f,65f,130f,130f,85f,85f,85f,85f,85f),6,l)));
        if(!s.allocations().isEmpty()){
            var allocations=s.allocations().stream().map(x->List.of(x.number()+" - "+x.name(),money(x.principal(),l),x.known()?money(x.remainingPrincipal(),l):"—",x.known()?money(x.remainingInterest(),l):"—",x.known()?money(x.remainingCharges(),l):"—",x.known()?money(x.excludedInterest(),l):"—",x.known()?money(x.waivedInterest(),l):"—",x.known()?money(x.writtenOff(),l):"—")).toList();
            sections.add(1,new StatementSection(List.of(field("cycleStatement.allocations",text("cycleStatement.allocationNote",l),l)),List.of("group.client","group.principal","sharedStatement.principal","sharedStatement.interest","sharedStatement.charges","sharedStatement.excluded","memberStatement.waived","memberStatement.writtenOff").stream().map(k->text(k,l)).toList(),allocations,List.of(160f,100f,100f,100f,100f,100f,100f,100f),1));
        }
        if(s.coverage()!=null){
            var rows=s.coverage().rows().stream().map(x->List.of(x.loanNumber(),x.number()+" - "+x.name(),text("coverage.kind."+x.sourceKind(),l),text("coverage.state."+x.state(),l),date(x.callDate()),money(x.requested(),l),money(x.received(),l),money(x.unpaid(),l))).toList();
            sections.add(new StatementSection(List.of(field("coverage.title",text("coverage.statementNote",l),l)),List.of("group.loan","coverage.person","coverage.kind","group.status","coverage.called","coverage.requested","coverage.received","coverage.unpaid").stream().map(k->text(k,l)).toList(),rows,List.of(75f,160f,120f,120f,90f,90f,90f,90f),8));
        }
        return renderer.renderStatement(new StatementDocument(text("cycleStatement.title",l),fields,sections,text("memberStatement.empty",l),text("cycleStatement.note",l)));
    }
    private StatementSection section(String title,List<String> headers,List<List<String>> rows,List<Float> widths,int moneyStart,Locale l){return new StatementSection(List.of(field(title,"",l)),headers.stream().map(k->text(k,l)).toList(),rows,widths,moneyStart);}
}
