package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.InternalTransferReconciliationSource.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Independently compares owning-source transfer proof with the scoped posted GL. */
final class ReconciliationTransferProjection {
    record Line(UUID id,UUID account,String purpose,BigDecimal signedAmount,boolean internal) { }
    record Journal(UUID id,String sourceType,String sourceReference,UUID policy,int policyVersion,
            LocalDate effectiveDate,OffsetDateTime recordedAt,OffsetDateTime postedAt,String currency,
            UUID maker,UUID checker,UUID reverses,List<Line> lines) {
        Journal {lines=List.copyOf(lines);}
    }
    static Coverage validate(String branch,LocalDate cutover,LocalDate through,OffsetDateTime cutoff,
            List<Journal> journals,Coverage proof) {
        require(branch!=null&&!branch.isBlank()&&cutover!=null&&through!=null&&!through.isBefore(cutover)&&cutoff!=null);
        require(journals!=null&&journals.size()<=1000&&proof!=null&&proof.legs().size()+proof.unknownJournalIds().size()<=1000);
        var known=new HashMap<UUID,Journal>();int lineCount=0;
        for(var journal:journals){require(journal!=null&&journal.id()!=null&&known.put(journal.id(),journal)==null);
            require(journal.effectiveDate()!=null&&journal.effectiveDate().isAfter(cutover)&&!journal.effectiveDate().isAfter(through)
                    &&journal.recordedAt()!=null&&!journal.recordedAt().isAfter(cutoff)&&journal.postedAt()!=null&&!journal.postedAt().isAfter(cutoff));
            lineCount=Math.addExact(lineCount,journal.lines().size());require(lineCount<=10000&&journal.lines().stream().anyMatch(Line::internal));}
        var covered=new HashSet<UUID>();var normalized=new ArrayList<Leg>();
        for(var leg:proof.legs()){
            require(leg!=null&&leg.journalId()!=null&&covered.add(leg.journalId()));var journal=known.get(leg.journalId());require(journal!=null);
            require(branch.equals(leg.branch())&&leg.documentId()!=null&&leg.transferId()!=null&&leg.direction()!=null
                    &&"EXPENSE".equals(journal.sourceType())&&leg.documentId().toString().equals(journal.sourceReference())&&journal.reverses()==null
                    &&"TZS".equals(journal.currency())&&journal.currency().equals(leg.currency())
                    &&leg.policyId()!=null&&leg.policyId().equals(journal.policy())&&leg.policyVersion()==journal.policyVersion()&&leg.policyVersion()>0
                    &&leg.effectiveDate()!=null&&leg.effectiveDate().equals(journal.effectiveDate())
                    &&leg.postedAt()!=null&&leg.postedAt().isEqual(journal.postedAt())
                    &&leg.maker()!=null&&leg.maker().equals(journal.maker())&&leg.checker()!=null&&leg.checker().equals(journal.checker())&&!leg.maker().equals(leg.checker())
                    &&leg.sourceDigest()!=null&&leg.sourceDigest().matches("[0-9a-f]{64}"));
            exactPositive(leg.amount());require(journal.lines().size()==2);
            var money=journal.lines().stream().filter(line->Set.of("CASH","BANK","MOBILE_MONEY").contains(line.purpose())).toList();
            var internal=journal.lines().stream().filter(Line::internal).toList();require(money.size()==1&&internal.size()==1&&!money.getFirst().id().equals(internal.getFirst().id()));
            var cash=money.getFirst();var control=internal.getFirst();int direction=leg.direction()==Direction.OUT?-1:1;
            require(cash.id().equals(leg.moneyLineId())&&cash.account().equals(leg.moneyAccountId())&&control.account().equals(leg.counterpartAccountId())
                    &&exact(cash.signedAmount())&&exact(control.signedAmount())&&exact(leg.signedMoneyAmount())&&exact(leg.signedCounterpartAmount())
                    &&cash.signedAmount().compareTo(leg.signedMoneyAmount())==0&&control.signedAmount().compareTo(leg.signedCounterpartAmount())==0
                    &&cash.signedAmount().signum()==direction&&cash.signedAmount().abs().compareTo(leg.amount())==0
                    &&cash.signedAmount().add(control.signedAmount()).signum()==0);
            if(direction<0)require(leg.transferId().equals(leg.documentId())&&leg.relatedDocumentId()==null&&leg.destinationBranch()!=null&&!leg.destinationBranch().isBlank()&&!branch.equals(leg.destinationBranch()));
            else require(leg.relatedDocumentId()!=null&&leg.transferId().equals(leg.relatedDocumentId())&&leg.destinationBranch()==null&&!leg.documentId().equals(leg.relatedDocumentId()));
            normalized.add(new Leg(leg.transferId(),leg.documentId(),leg.journalId(),branch,leg.direction(),"TZS",leg.amount().setScale(2),leg.effectiveDate(),utc(leg.postedAt()),leg.maker(),leg.checker(),leg.sourceDigest(),leg.moneyLineId(),leg.moneyAccountId(),leg.signedMoneyAmount().setScale(2),leg.counterpartAccountId(),leg.signedCounterpartAmount().setScale(2),leg.policyId(),leg.policyVersion(),leg.destinationBranch(),leg.relatedDocumentId()));
        }
        var unknown=new ArrayList<UUID>();for(var id:proof.unknownJournalIds()){require(id!=null&&known.containsKey(id)&&covered.add(id));unknown.add(id);}
        require(covered.equals(known.keySet())&&proof.complete()==unknown.isEmpty());
        normalized.sort(Comparator.comparing(leg->leg.journalId().toString()));unknown.sort(Comparator.comparing(UUID::toString));
        return new Coverage(normalized,unknown,unknown.isEmpty());
    }
    private static OffsetDateTime utc(OffsetDateTime value){return value.withOffsetSameInstant(ZoneOffset.UTC);}
    private static void exactPositive(BigDecimal value){require(exact(value)&&value.signum()>0);}
    private static boolean exact(BigDecimal value){if(value==null||value.abs().compareTo(new BigDecimal("10000000000000000"))>=0)return false;try{value.setScale(2,java.math.RoundingMode.UNNECESSARY);return true;}catch(ArithmeticException ex){return false;}}
    private static void require(boolean valid){if(!valid)throw new IllegalArgumentException("reconciliation.error.transferSource");}
}
