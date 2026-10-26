package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.*;
import com.sacco.mvp.grouplending.dto.GroupMemberStatementDtos.Statement;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupMemberStatementPdf {
    private final LoanRecordingPdfService renderer;
    private final MessageSource messages;
    public byte[] render(Statement statement,Locale locale){
        var source=statement.source();var member=source.member();var fields=new ArrayList<StatementField>();
        fields.add(field("memberStatement.institution",source.institution(),locale));fields.add(field("voucher.print.branch",source.branch(),locale));fields.add(field("group.group",source.groupCode()+" - "+source.groupName(),locale));
        fields.add(field("group.client",member.number()+" - "+member.name(),locale));fields.add(field("group.loan",source.loanNumber(),locale));fields.add(field("memberStatement.asOf",date(statement.asOf()),locale));
        fields.add(field("group.status",label("recording.status."+source.status(),locale),locale));fields.add(field("group.sharedLiability",label("memberStatement.liability."+source.liability(),locale),locale));fields.add(field("memberStatement.cap",money(source.cap(),locale),locale));
        fields.add(field("memberStatement.originalPrincipal",money(member.principal(),locale),locale));fields.add(field("memberStatement.originalInterest",money(member.interest(),locale),locale));fields.add(field("memberStatement.totalFees",money(member.charges().add(source.deductions()),locale),locale));
        fields.add(field("group.deductions",money(source.deductions(),locale),locale));fields.add(field("group.shared.net",money(source.net(),locale),locale));
        if(source.funded()&&source.known()){
            fields.add(field("sharedStatement.principal",money(member.remainingPrincipal(),locale),locale));fields.add(field("memberStatement.remainingInterest",money(member.remainingInterest(),locale),locale));fields.add(field("sharedStatement.charges",money(member.remainingCharges(),locale),locale));fields.add(field("sharedStatement.excluded",money(member.excludedInterest(),locale),locale));fields.add(field("memberStatement.waived",money(member.waivedInterest(),locale),locale));fields.add(field("memberStatement.maximum",money(source.maximumLiability(),locale),locale));
        }else fields.add(field("memberStatement.balance",label(source.funded()?"memberStatement.unknown":"sharedStatement.unfunded",locale),locale));
        if(source.funded()&&source.duePrincipal()!=null){fields.add(field("memberStatement.wholeDuePrincipal",money(source.duePrincipal(),locale),locale));fields.add(field("memberStatement.wholeDueInterest",money(source.dueInterest(),locale),locale));fields.add(field("memberStatement.wholeCharges",money(source.loanCharges(),locale),locale));fields.add(field("memberStatement.wholeArrears",money(source.arrears(),locale),locale));}
        var history=statement.credits().stream().map(c->List.of(date(c.date()),c.number(),label("memberStatement.kind."+c.kind(),locale),c.payer()+(c.reverses().isBlank()?"":"\n"+label("memberStatement.reverses",locale)+": "+c.reverses())+(c.reversedBy().isBlank()?"":"\n"+label("memberStatement.reversedBy",locale)+": "+c.reversedBy()),money(c.principal(),locale),money(c.interest(),locale),money(c.charges(),locale),money(c.excludedInterest(),locale),money(c.waivedInterest(),locale))).toList();
        var schedule=statement.schedule().stream().map(d->List.of(Integer.toString(d.number()),date(d.date()),money(d.principal(),locale),money(d.interest(),locale),money(d.amount(),locale))).toList();
        var sections=List.of(new StatementSection(List.of(field("memberStatement.period",(statement.from()==null?label("memberStatement.allDates",locale):date(statement.from()))+" - "+date(statement.through()),locale)),List.of(label("voucher.date",locale),label("memberStatement.reference",locale),label("group.status",locale),label("group.payer",locale),label("recording.principal",locale)+" (TZS)",label("recording.interest",locale)+" (TZS)",label("charges.title",locale)+" (TZS)",label("memberStatement.nonCash",locale)+" (TZS)",label("memberStatement.waived",locale)),history,List.of(70f,85f,80f,130f,85f,85f,85f,105f,105f),4),
            new StatementSection(List.of(field("memberStatement.schedule",label("memberStatement.scheduleNote",locale),locale)),List.of("#",label("memberStatement.dueDate",locale),label("recording.principal",locale)+" (TZS)",label("recording.interest",locale)+" (TZS)",label("voucher.amount",locale)+" (TZS)"),schedule,List.of(35f,100f,140f,140f,140f),2));
        return renderer.renderStatement(new StatementDocument(label("memberStatement.title",locale),fields,sections,label("memberStatement.empty",locale),label("memberStatement.note",locale)));
    }
    private StatementField field(String key,String value,Locale locale){return new StatementField(label(key,locale),value);}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private String money(BigDecimal value,Locale locale){var f=NumberFormat.getNumberInstance(locale);f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f.format(value);}
    private static String date(LocalDate date){return date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));}
}
