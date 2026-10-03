package com.sacco.mvp.accounting.reports;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/** A reviewed classification of actual money lines, never a journal or a loan mutation. */
public final class CashFlowAllocation {
    private CashFlowAllocation() { }
    public enum Activity { OPERATING, INVESTING, FINANCING, INTERNAL_TRANSFER }
    public record Split(UUID moneyLineId, UUID counterpartAccountId, Activity activity, BigDecimal signedAmount) { }
    public record SourceLine(UUID id, UUID accountId, String code, String type, String purpose, BigDecimal signedAmount) {
        public boolean money() { return Set.of("CASH","BANK","MOBILE_MONEY").contains(purpose); }
    }
    public record Source(UUID journalId, UUID policyId, int policyVersion, String sourceReference,
            List<SourceLine> lines) { public Source { lines=List.copyOf(lines); } }
    public record Version(UUID id, UUID journalId, int version, UUID maker, OffsetDateTime madeAt,
            String evidence, String noncashEvidence, String sourceChecksum, String definitionChecksum,
            Source source, List<Split> splits, UUID checker, OffsetDateTime reviewedAt, String reviewEvidence) {
        public Version { splits=List.copyOf(splits); }
        public boolean approved() { return checker!=null; }
    }
    public record Coverage(List<Version> versions, List<UUID> missingJournalIds) {
        public Coverage { versions=List.copyOf(versions);missingJournalIds=List.copyOf(missingJournalIds); }
        public boolean complete() { return missingJournalIds.isEmpty(); }
    }

    /** Money-line sums must be exact; counterpart residuals are explicitly retained as noncash evidence. */
    public static void validate(Source source,List<Split> splits,String noncashEvidence) {
        require(source!=null && source.lines().size()>=2 && source.lines().size()<=200,"source");
        require(source.lines().stream().anyMatch(SourceLine::money),"moneyLine");
        require(splits!=null && !splits.isEmpty() && splits.size()<=400,"size");
        var lines=new HashMap<UUID,SourceLine>();source.lines().forEach(l->require(lines.put(l.id(),l)==null,"source"));
        var accountLines=new HashMap<UUID,List<SourceLine>>();source.lines().forEach(l->accountLines.computeIfAbsent(l.accountId(),k->new ArrayList<>()).add(l));
        var sums=new HashMap<UUID,BigDecimal>();var positiveAssigned=new HashMap<UUID,BigDecimal>();var negativeAssigned=new HashMap<UUID,BigDecimal>();var internalByAccount=new HashMap<UUID,BigDecimal>();
        BigDecimal transfers=BigDecimal.ZERO;var unique=new HashSet<String>();
        for(Split s:splits) {
            require(s!=null && s.moneyLineId()!=null && s.counterpartAccountId()!=null && s.activity()!=null,"source");
            BigDecimal amount=s.signedAmount();require(amount!=null && amount.signum()!=0 && amount.precision()-amount.scale()<=16,"amount");
            try { amount.setScale(2,java.math.RoundingMode.UNNECESSARY); } catch(ArithmeticException e) { throw new IllegalArgumentException("financial.cash.error.amount"); }
            SourceLine money=lines.get(s.moneyLineId());require(money!=null && money.money() && money.signedAmount().signum()==amount.signum(),"moneyLine");
            var counterparts=accountLines.get(s.counterpartAccountId());require(counterparts!=null && !money.accountId().equals(s.counterpartAccountId()),"counterpart");
            boolean targetMoney=counterparts.stream().allMatch(SourceLine::money);
            require(targetMoney==(s.activity()==Activity.INTERNAL_TRANSFER),"transfer");
            require(counterparts.stream().anyMatch(l->l.signedAmount().signum()==-amount.signum()),"counterpart");
            require(unique.add(s.moneyLineId()+"/"+s.counterpartAccountId()+"/"+s.activity()),"duplicate");
            sums.merge(s.moneyLineId(),amount,BigDecimal::add);
            (amount.signum()<0?positiveAssigned:negativeAssigned).merge(s.counterpartAccountId(),amount.abs(),BigDecimal::add);
            if(targetMoney){transfers=transfers.add(amount);internalByAccount.merge(money.accountId(),amount,BigDecimal::add);}
        }
        for(SourceLine l:source.lines())if(l.money())require(sums.getOrDefault(l.id(),BigDecimal.ZERO).compareTo(l.signedAmount())==0,"lineTotal");
        require(transfers.signum()==0,"transfer");
        // Validate all gross capacities before checking paired transfers, independent of UUID iteration order.
        for(var e:accountLines.entrySet()) {
            var positive=e.getValue().stream().map(SourceLine::signedAmount).filter(a->a.signum()>0).reduce(BigDecimal.ZERO,BigDecimal::add);
            var negative=e.getValue().stream().map(SourceLine::signedAmount).filter(a->a.signum()<0).map(BigDecimal::abs).reduce(BigDecimal.ZERO,BigDecimal::add);
            require(positiveAssigned.getOrDefault(e.getKey(),BigDecimal.ZERO).compareTo(positive)<=0 && negativeAssigned.getOrDefault(e.getKey(),BigDecimal.ZERO).compareTo(negative)<=0,"counterpartTotal");
        }
        boolean residual=false;
        for(var e:accountLines.entrySet()) {
            BigDecimal positive=e.getValue().stream().map(SourceLine::signedAmount).filter(a->a.signum()>0).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal negative=e.getValue().stream().map(SourceLine::signedAmount).filter(a->a.signum()<0).map(BigDecimal::abs).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal assignedPositive=positiveAssigned.getOrDefault(e.getKey(),BigDecimal.ZERO),assignedNegative=negativeAssigned.getOrDefault(e.getKey(),BigDecimal.ZERO);
            if(e.getValue().stream().allMatch(SourceLine::money))require(internalByAccount.getOrDefault(e.getKey(),BigDecimal.ZERO).compareTo(assignedPositive.subtract(assignedNegative))==0,"transfer");
            else residual|=assignedPositive.compareTo(positive)!=0 || assignedNegative.compareTo(negative)!=0;
        }
        require(!residual || noncashEvidence!=null && !noncashEvidence.isBlank(),"noncashEvidence");
    }
    static void require(boolean allowed,String key){if(!allowed)throw new IllegalArgumentException("financial.cash.error."+key);}
}
