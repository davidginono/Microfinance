package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import com.sacco.mvp.accounting.business.repository.BusinessAccountingRepository;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.repository.LoanJournalEntryRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Links an owned posted source to its actual operational voucher; it never creates a second GL entry. */
@Service @RequiredArgsConstructor
public class BusinessOperationalBridge {
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private final BusinessAccountingRepository sources;
    private final GeneralLedgerRepository books;
    private final LoanJournalEntryRepository operationalEntries;

    @Transactional(propagation=Propagation.MANDATORY)
    public void attach(AppUserPrincipal actor,UUID sourceId,UUID transactionId) {
        require(actor!=null && actor.getMemberId()!=null,"reviewedSource");
        var source=sources.document(actor.getSaccoId(),actor.getStationId(),sourceId,false)
            .orElseThrow(()->invalid("source"));
        require("POSTING".equals(source.state()) && actor.getMemberId().equals(source.checkerId())
            && source.journalId()!=null && source.command().loanId()!=null,"reviewedSource");
        var kind=source.command().kind();require(Set.of(Kind.LOAN_DISBURSEMENT,Kind.LOAN_REPAYMENT,Kind.LOAN_REPAYMENT_REVERSAL).contains(kind),"source");
        // JPA and JDBC share the owning transaction. Flush the actual subledger before reading immutable voucher rows.
        operationalEntries.flush();
        UUID voucher=transactionId;
        if(kind==Kind.LOAN_DISBURSEMENT) {
            require(transactionId==null,"source");var origins=sources.originVouchers(source.institutionId(),source.branchId(),source.command().loanId());
            require(origins.size()==1,"source");voucher=origins.getFirst();
        } else require(transactionId!=null,"source");
        var lines=books.operationalVoucher(source.institutionId(),source.branchId(),voucher);
        require(!lines.isEmpty() && lines.size()<=100,"source");
        var actual=new TreeMap<String,Components>();
        for(var line:lines) {
            require(source.command().loanId().equals(line.loanId()) && source.command().effectiveDate().equals(line.date()),"changedBalance");
            actual.merge(line.accountCode(),new Components(line.debit(),line.credit()),Components::add);
        }
        var expected=new TreeMap<String,Components>();
        if(kind==Kind.LOAN_DISBURSEMENT) {
            require(source.principal().compareTo(source.command().amount())==0 && source.interest().signum()==0 && source.fees().signum()==0,"changedBalance");
            expected.put("LOAN_PRINCIPAL",new Components(source.principal(),ZERO));
            expected.put("DISBURSEMENT_CLEARING",new Components(ZERO,source.principal()));
        } else {
            require(source.fees().signum()==0 && source.principal().add(source.interest()).compareTo(source.command().amount())==0,"changedBalance");
            boolean reversed=kind==Kind.LOAN_REPAYMENT_REVERSAL;
            expected.put("CASH_CLEARING",new Components(reversed?ZERO:source.command().amount(),reversed?source.command().amount():ZERO));
            if(source.principal().signum()>0)expected.put("LOAN_PRINCIPAL",new Components(reversed?source.principal():ZERO,reversed?ZERO:source.principal()));
            if(source.interest().signum()>0)expected.put("INTEREST_COLLECTIONS_CLEARING",new Components(reversed?source.interest():ZERO,reversed?ZERO:source.interest()));
        }
        require(actual.keySet().equals(expected.keySet()) && actual.entrySet().stream().allMatch(e->e.getValue().same(expected.get(e.getKey()))),"changedBalance");
        var journal=books.journal(source.institutionId(),source.branchId(),source.journalId(),false).orElseThrow(()->invalid("source"));
        require("POSTED".equals(journal.state()) && source.id().toString().equals(journal.sourceReference())
            && source.makerId().equals(journal.makerId()) && source.checkerId().equals(journal.checkerId())
            && source.command().effectiveDate().equals(journal.effectiveDate()),"changedBalance");
        var principal=books.sourceMappedBalance(journal.id(),"LOAN_PRINCIPAL");
        BigDecimal signedPrincipal=kind==Kind.LOAN_REPAYMENT?source.principal().negate():source.principal();
        require(principal.isPresent() && "CONTROL".equals(principal.get().kind()) && "LOAN_PRINCIPAL".equals(principal.get().purpose())
            && principal.get().signedBalance().compareTo(signedPrincipal)==0,"mapping");
        books.bridge(voucher,journal);
        require(books.bridgedJournal(source.institutionId(),source.branchId(),voucher).filter(journal.id()::equals).isPresent(),"retry");
    }
    private record Components(BigDecimal debit,BigDecimal credit) {
        Components add(Components other){return new Components(debit.add(other.debit),credit.add(other.credit));}
        boolean same(Components other){return debit.compareTo(other.debit)==0 && credit.compareTo(other.credit)==0;}
    }
    private static void require(boolean valid,String key){if(!valid)throw invalid(key);}
    private static IllegalArgumentException invalid(String key){return new IllegalArgumentException("finance.business.error."+key);}
}
