package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import com.sacco.mvp.accounting.business.repository.BusinessTransferSourceRepository;
import com.sacco.mvp.accounting.business.repository.BusinessTransferSourceRepository.Source;
import com.sacco.mvp.accounting.reconciliation.InternalTransferReconciliationSource;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Projects posted owning business transfers; unidentified internal GL movements remain unresolved. */
@Service @RequiredArgsConstructor
public class BusinessInternalTransferSource implements InternalTransferReconciliationSource {
    private final BusinessTransferSourceRepository repository;
    private final BusinessSourceProofAuthorization authorization;
    private final ApplicationClock clock;
    private final ObjectMapper mapper;

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Coverage reviewedTransfers(AppUserPrincipal actor,LocalDate cutover,LocalDate asOf,OffsetDateTime cutoff){
        authorization.authorize(actor,actor==null?null:actor.getStationId(),false);
        return project(actor.getSaccoId(),actor.getStationId(),cutover,asOf,cutoff);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public boolean currentForInstitutionClose(AppUserPrincipal actor,String branch,LocalDate cutover,LocalDate asOf,
            OffsetDateTime cutoff,Coverage frozen){
        authorization.authorize(actor,branch,true);
        require(cutoff!=null && !cutoff.isAfter(clock.now()),"dates");
        return project(actor.getSaccoId(),branch,cutover,asOf,clock.now()).equals(frozen);
    }
    private Coverage project(String institution,String branch,LocalDate cutover,LocalDate asOf,OffsetDateTime cutoff){
        var isolation=TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        require(TransactionSynchronizationManager.isActualTransactionActive() && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                && (isolation==null || isolation==java.sql.Connection.TRANSACTION_READ_COMMITTED),"transaction");
        require(cutover!=null && asOf!=null && !asOf.isBefore(cutover) && !asOf.isAfter(clock.today())
                && cutoff!=null && !cutoff.isAfter(clock.now()),"dates");
        repository.lockPeriods(institution,asOf);
        require(repository.reviewedCutover(institution,branch,cutover,cutoff),"opening");
        var legs=new ArrayList<Leg>();var unknown=new ArrayList<UUID>();
        for(var source:repository.sources(institution,branch,cutover,asOf,cutoff)){
            var leg=leg(branch,source);if(leg.isPresent())legs.add(leg.get());else unknown.add(source.journal());
        }
        legs.sort(Comparator.comparing(leg->leg.journalId().toString()));unknown.sort(Comparator.comparing(UUID::toString));
        return new Coverage(legs,unknown,unknown.isEmpty());
    }
    private Optional<Leg> leg(String branch,Source s){
        if(s.document()==null || !Set.of("INTERNAL_TRANSFER_OUT","INTERNAL_TRANSFER_IN").contains(s.kind())
                || !"EXPENSE".equals(s.sourceType()) || !s.document().toString().equals(s.sourceReference())
                || !"TZS".equals(s.currency()) || s.reverses()!=null || s.maker()==null || s.checker()==null
                || s.maker().equals(s.checker()) || !s.maker().equals(s.journalMaker()) || !s.checker().equals(s.journalChecker())
                || !s.effectiveDate().equals(s.documentDate()) || s.lines().size()!=2
                || s.amount()==null || s.amount().signum()<=0 || !exact(s.amount())
                || s.command()==null || !sha(s.command()).equals(s.commandHash()))return Optional.empty();
        var command=mapper.readValue(s.command(),Command.class);
        if(command.kind()==null || !command.kind().name().equals(s.kind()) || !Objects.equals(command.effectiveDate(),s.documentDate())
                || command.amount()==null || command.amount().compareTo(s.amount())!=0 || !Objects.equals(command.moneyAccountKey(),s.moneyKey())
                || !Objects.equals(command.destinationBranch(),s.destination()) || !Objects.equals(command.relatedDocumentId(),s.related())
                || s.moneyKey()==null || !Set.of("CASH","BANK","MOBILE_MONEY").contains(s.moneyKey()))return Optional.empty();
        boolean out=command.kind()==Kind.INTERNAL_TRANSFER_OUT;
        if(out?s.related()!=null || s.destination()==null || s.destination().isBlank() || branch.equals(s.destination())
                :s.related()==null || s.document().equals(s.related()) || s.destination()!=null || !s.linkedOutgoing())return Optional.empty();
        var mappings=mapper.readTree(s.mappings());UUID money=uuid(mappings.path(s.moneyKey()).asText());
        UUID control=uuid(mappings.path(out?"INTERNAL_DUE_FROM":"INTERNAL_DUE_TO").asText());
        if(money==null || control==null || money.equals(control))return Optional.empty();
        var cash=s.lines().stream().filter(line->money.equals(line.account()) && s.moneyKey().equals(line.purpose())).toList();
        var internal=s.lines().stream().filter(line->control.equals(line.account())).toList();
        int sign=out?-1:1;if(cash.size()!=1 || internal.size()!=1 || !exact(cash.getFirst().amount()) || !exact(internal.getFirst().amount())
                || cash.getFirst().amount().signum()!=sign || cash.getFirst().amount().abs().compareTo(s.amount())!=0
                || cash.getFirst().amount().add(internal.getFirst().amount()).signum()!=0)return Optional.empty();
        String digest=sha(s.document()+"|"+s.commandHash()+"|"+s.maker()+"|"+s.checker()+"|"+s.postedAt().toInstant()
                +"|"+s.journal()+"|"+s.journalHash()+"|"+s.policy()+"|"+s.policyVersion());
        return Optional.of(new Leg(out?s.document():s.related(),s.document(),s.journal(),branch,out?Direction.OUT:Direction.IN,"TZS",
                s.amount().setScale(2),s.effectiveDate(),s.journalPostedAt().withOffsetSameInstant(ZoneOffset.UTC),s.maker(),s.checker(),digest,
                cash.getFirst().id(),money,cash.getFirst().amount().setScale(2),control,internal.getFirst().amount().setScale(2),s.policy(),s.policyVersion(),s.destination(),s.related()));
    }
    private static UUID uuid(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException e){return null;}}
    private static boolean exact(BigDecimal value){if(value==null || value.abs().compareTo(new BigDecimal("10000000000000000"))>=0)return false;
        try{value.setScale(2,RoundingMode.UNNECESSARY);return true;}catch(ArithmeticException e){return false;}}
    private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void require(boolean valid,String key){if(!valid)throw new IllegalArgumentException("business.source.error."+key);}
}
