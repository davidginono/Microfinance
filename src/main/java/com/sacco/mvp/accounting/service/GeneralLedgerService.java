package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.policy.PostingEvent;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.UserClaimService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GeneralLedgerService {
    private final GeneralLedgerRepository books;
    private final AccountingPolicyService policies;
    private final AccessControlService access;
    private final AuditService audit;
    private final ApplicationClock clock;
    private final UserClaimService userClaims;
    private final MemberDirectoryService directory;
    private final SaccoRegistryService institutions;
    private final AccountingReleaseGateService releaseGate;
    private static final Set<String> TYPES=Set.of("ASSET","LIABILITY","EQUITY","INCOME","EXPENSE");
    private static final Set<String> KINDS=Set.of("HEADING","POSTING","CONTROL");
    private static final Set<String> PURPOSES=Set.of("CASH","BANK","MOBILE_MONEY","CLEARING","SUSPENSE","LOAN_PRINCIPAL",
        "INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE","PAYABLE","FUNDING","CAPITAL","INCOME","EXPENSE","FIXED_ASSET","PREPAYMENT","TAX","INTERNAL_TRANSFER","OTHER");

    @Transactional(readOnly=true)
    public boolean hasInstitutionHistory(String institution) {return institution!=null && books.hasInstitutionHistory(institution);}
    @Transactional(readOnly=true)
    public boolean hasMemberHistory(UUID member) {return member!=null && books.hasMemberHistory(member);}
    @Transactional(readOnly=true)
    public Page<Account> accounts(AppUserPrincipal actor,int page) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");
        int n=page(page); List<Account> rows=books.accounts(actor.getSaccoId(),n*25,26);
        return new Page<>(rows.stream().limit(25).toList(),n,rows.size()>25);
    }
    @Transactional
    public UUID createAccount(AppUserPrincipal actor,AccountCommand c) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_CREATE");
        require(c!=null && c.code()!=null && c.code().matches("[A-Z0-9][A-Z0-9_.-]{0,39}"),"accountCode");
        text(c.name(),160,"name");
        require(TYPES.contains(c.type()) && Set.of("DEBIT","CREDIT").contains(c.normalBalance()) && KINDS.contains(c.kind()) && PURPOSES.contains(c.purpose()),"accountClassification");
        require(!Set.of("LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE").contains(c.purpose()) || "CONTROL".equals(c.kind()),"loanControl");
        books.lockAccounts(actor.getSaccoId());
        if(c.parentId()!=null) {
            Account parent=books.account(actor.getSaccoId(),c.parentId()).orElseThrow(()->invalid("parent"));
            require(parent.active() && "HEADING".equals(parent.kind()) && parent.type().equals(c.type()),"parent");
            int depth=0;
            while(parent.parentId()!=null) {
                require(++depth<20,"hierarchyDepth");
                parent=books.account(actor.getSaccoId(),parent.parentId()).orElseThrow(()->invalid("parent"));
            }
        }
        UUID id=UUID.randomUUID(); books.createAccount(actor.getSaccoId(),id,c,actor.getMemberId(),clock.now());
        event(actor,id,"ACCOUNT_CREATED",Map.of("accountCode",c.code()));return id;
    }
    @Transactional
    public void deactivateAccount(AppUserPrincipal actor,UUID id) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");
        books.lockAccounts(actor.getSaccoId());
        require(books.account(actor.getSaccoId(),id).isPresent(),"account");
        books.deactivate(actor.getSaccoId(),id);event(actor,id,"ACCOUNT_DEACTIVATED",Map.of());
    }
    @Transactional
    public UUID createPeriod(AppUserPrincipal actor,LocalDate start,LocalDate end) {
        requireActor(actor,"ACCOUNTING_PERIODS_CREATE");
        require(start!=null && end!=null && !end.isBefore(start) && end.isBefore(start.plusYears(2)),"period");
        var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),start);
        require(!start.isBefore(policy.openingDate()),"openingDate");
        books.lockAccounts(actor.getSaccoId());require(!books.hasClosedLaterPeriod(actor.getSaccoId(),start),"earlierPeriodNeedsReopen");require(!books.periodOverlap(actor.getSaccoId(),start,end),"overlappingPeriod");
        UUID id=books.createPeriod(actor.getSaccoId(),start,end,policy.id(),actor.getMemberId(),clock.now());
        event(actor,id,"PERIOD_CREATED",Map.of("start",start,"end",end));return id;
    }
    @Transactional(readOnly=true)
    public Page<Journal> journals(AppUserPrincipal actor,int page) {
        requireActor(actor,"ACCOUNTING_JOURNALS_VIEW");int n=page(page);
        List<Journal> rows=books.journals(actor.getSaccoId(),actor.getStationId(),n*25,26);
        return new Page<>(rows.stream().limit(25).toList(),n,rows.size()>25);
    }
    @Transactional(readOnly=true)
    public Journal journal(AppUserPrincipal actor,UUID id) {
        requireActor(actor,"ACCOUNTING_JOURNALS_VIEW");return withLines(scoped(actor,id,false));
    }
    @Transactional(readOnly=true)
    public List<DisplayLine> displayLines(AppUserPrincipal actor,UUID id) {
        requireActor(actor,"ACCOUNTING_JOURNALS_VIEW");scoped(actor,id,false);return books.displayLines(id);
    }
    @Transactional(readOnly=true)
    public List<Line> previewImport(AppUserPrincipal actor,String content,boolean opening) {
        requireActor(actor,opening?"ACCOUNTING_OPENINGS_CREATE":"ACCOUNTING_JOURNALS_CREATE");
        require(content!=null && content.length()<=100000,"importSize");
        String[] rows=content.strip().split("\\R");require(rows.length>=2 && rows.length<=500,"lines");
        List<Line> lines=new ArrayList<>();
        for(String row:rows) {
            String[] cells=row.split(",",-1);require(cells.length==3,"importFormat");
            Account a=books.accountCode(actor.getSaccoId(),cells[0].trim()).orElseThrow(()->invalid("account"));
            require(a.active() && !"HEADING".equals(a.kind()) && (opening || !"CONTROL".equals(a.kind())),"postingAccount");
            require(cells[1].trim().matches("[0-9]{1,16}(\\.[0-9]{1,2})?") && cells[2].trim().matches("[0-9]{1,16}(\\.[0-9]{1,2})?"),"moneyPrecision");
            lines.add(new Line(a.id(),new BigDecimal(cells[1].trim()),new BigDecimal(cells[2].trim())));
        }
        validateCommand(new JournalCommand(UUID.randomUUID(),"PREVIEW",clock.today(),"PREVIEW",null,lines));return List.copyOf(lines);
    }
    @Transactional
    public Journal draftManual(AppUserPrincipal actor,JournalCommand c) {
        requireActor(actor,"ACCOUNTING_JOURNALS_CREATE");return draft(actor,c,"MANUAL",null);
    }
    @Transactional
    public Journal importOpening(AppUserPrincipal actor,JournalCommand c) {
        requireActor(actor,"ACCOUNTING_OPENINGS_CREATE");return draft(actor,c,"OPENING",null);
    }
    /** Called inside the business event transaction; request callers cannot select a source type. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Journal draftSourceEvent(AppUserPrincipal actor,PostingEvent event,JournalCommand c) {
        requireActor(actor,"ACCOUNTING_JOURNALS_CREATE");
        require(event!=null && !Set.of(PostingEvent.MANUAL_JOURNAL,PostingEvent.OPENING_BALANCE,PostingEvent.REVERSAL,PostingEvent.OPERATIONAL_BRIDGE).contains(event),"sourceType");
        return draft(actor,c,event.name(),null);
    }
    @Transactional
    public Journal approve(AppUserPrincipal actor,UUID id,String evidence) {
        Journal j=scoped(actor,id,true);requireGenericSource(j);return approveInternal(actor,j,evidence);
    }
    private Journal approveInternal(AppUserPrincipal actor,Journal j,String evidence) {
        UUID id=j.id();
        requireActor(actor,"OPENING".equals(j.sourceType())?"ACCOUNTING_OPENINGS_APPROVE":"ACCOUNTING_JOURNALS_APPROVE");
        text(evidence,500,"approvalEvidence");
        require(!j.makerId().equals(actor.getMemberId()),"independentChecker");
        if(j.reversesId()!=null) independentOriginal(actor,scoped(actor,j.reversesId(),true));
        if("APPROVED".equals(j.state()) || "POSTED".equals(j.state())) {
            require(actor.getMemberId().equals(j.checkerId()),"checker");return withLines(j);
        }
        require("DRAFT".equals(j.state()),"state");books.approve(id,actor.getMemberId(),evidence,clock.now());
        event(actor,id,"JOURNAL_APPROVED",Map.of("evidenceReference",evidence));return withLines(scoped(actor,id,false));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Journal post(AppUserPrincipal actor,UUID id,boolean openingReconciled) {
        Journal j=scoped(actor,id,true);requireGenericSource(j);return postInternal(actor,j,openingReconciled);
    }
    private Journal postInternal(AppUserPrincipal actor,Journal j,boolean openingReconciled) {
        UUID id=j.id();
        requireActor(actor,"OPENING".equals(j.sourceType())?"ACCOUNTING_OPENINGS_APPROVE":"ACCOUNTING_JOURNALS_APPROVE");
        require(actor.getMemberId().equals(j.checkerId()),"checker");
        if("POSTED".equals(j.state())) return withLines(j);
        require("APPROVED".equals(j.state()),"approvalRequired");
        var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),j.effectiveDate());
        require(policy.id().equals(j.policyId()),"policyChanged");policies.requireAllowedPosting(policy,postingEvent(j.sourceType()));
        lockSourcePostingPeriod(actor,j.effectiveDate());
        if(!"OPENING".equals(j.sourceType()))releaseGate.requireLiveRelease(actor,policy.id(),policy.version());
        if("OPENING".equals(j.sourceType())) {
            require(openingReconciled && j.effectiveDate().equals(policy.openingDate()),"reviewedOpeningRequired");
            require(!books.reviewedOpening(actor.getSaccoId(),actor.getStationId()),"duplicateOpening");
            books.openingCoverage(j,actor.getMemberId(),true,clock.now());
        } else {
            require(books.reviewedOpening(actor.getSaccoId(),actor.getStationId()),"reviewedOpeningRequired");
            require(j.effectiveDate().isAfter(policy.openingDate()),"openingBoundary");
            if("OPERATIONAL_BRIDGE".equals(j.sourceType())) require(!books.coveredAtCutover(actor.getSaccoId(),actor.getStationId(),j.effectiveDate()),"alreadyInOpening");
        }
        for(Line l:books.lines(id)) require(books.account(actor.getSaccoId(),l.accountId()).map(Account::active).orElse(false),"inactiveAccount");
        if(j.reversesId()!=null) independentOriginal(actor,scoped(actor,j.reversesId(),true));
        books.outbox(j,clock.now());books.post(id,clock.now());
        event(actor,id,"JOURNAL_POSTED",Map.of("sourceType",j.sourceType(),"sourceReference",j.sourceReference(),"policyVersion",j.policyVersion()));
        return withLines(scoped(actor,id,false));
    }
    /** Lock prior and target periods before the release leaf, in the same order as closing/reopening. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockSourcePostingPeriod(AppUserPrincipal actor,LocalDate date) {
        requireActor(actor,"ACCOUNTING_JOURNALS_APPROVE");
        require(date!=null && !date.isAfter(clock.today()),"effectiveDate");
        var history=books.lockSourcePeriodHistory(actor.getSaccoId(),date);
        require(history.size()<=1000,"period");
        var current=history.stream().filter(p->!date.isBefore(p.startsOn()) && !date.isAfter(p.endsOn())).toList();
        require(current.size()==1 && "OPEN".equals(current.getFirst().state()),"openPeriodRequired");
    }
    /** Source component must update its source/subledger in this same transaction. Never exposed as a generic HTTP command. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Journal approveAndPostSourceEvent(AppUserPrincipal actor,UUID journalId,PostingEvent event,String sourceReference,String evidence) {
        requireActor(actor,"ACCOUNTING_JOURNALS_APPROVE");
        require(event!=null && !Set.of(PostingEvent.MANUAL_JOURNAL,PostingEvent.OPENING_BALANCE,PostingEvent.REVERSAL,PostingEvent.OPERATIONAL_BRIDGE).contains(event),"sourceType");
        Journal j=scoped(actor,journalId,true);
        require(event.name().equals(j.sourceType()) && Objects.equals(sourceReference,j.sourceReference()),"sourceType");
        return postInternal(actor,approveInternal(actor,j,evidence),false);
    }
    /** Owning source must retain its correction and subledger changes in this same transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Journal draftSourceReversal(AppUserPrincipal actor,UUID originalId,JournalCommand command) {
        requireActor(actor,"ACCOUNTING_JOURNALS_REVERSE");
        validateCommand(command);text(command.reason(),500,"reason");
        Journal original=withLines(scoped(actor,originalId,true));requireBusinessOriginal(original);
        independentOriginal(actor,original);
        require(!command.effectiveDate().isBefore(original.effectiveDate()),"effectiveDate");
        require(exactLines(command.lines()).equals(exactLines(original.lines().stream().map(l->new Line(l.accountId(),l.credit(),l.debit())).toList())),"original");
        return draft(actor,command,"SOURCE_REVERSAL",originalId);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Journal approveAndPostSourceReversal(AppUserPrincipal actor,UUID journalId,String sourceReference,String evidence) {
        requireActor(actor,"ACCOUNTING_JOURNALS_REVERSE");requireActor(actor,"ACCOUNTING_JOURNALS_APPROVE");
        Journal j=scoped(actor,journalId,true);
        require("SOURCE_REVERSAL".equals(j.sourceType()) && j.reversesId()!=null && Objects.equals(sourceReference,j.sourceReference()),"sourceType");
        Journal original=withLines(scoped(actor,j.reversesId(),true));requireBusinessOriginal(original);independentOriginal(actor,original);
        require(exactLines(books.lines(j.id())).equals(exactLines(original.lines().stream().map(l->new Line(l.accountId(),l.credit(),l.debit())).toList())),"original");
        text(evidence,500,"approvalEvidence");
        if(Set.of("APPROVED","POSTED").contains(j.state()))require(Objects.equals(evidence,books.sourceApprovalEvidence(j.id())),"changedRetry");
        var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),j.effectiveDate());
        require(policy.id().equals(j.policyId()),"policyChanged");policies.requireAllowedPosting(policy,PostingEvent.REVERSAL);
        return postInternal(actor,approveInternal(actor,j,evidence),false);
    }
    /** Rejection/cancellation of the owning source must commit or roll back with this immutable overlay. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Journal cancelSourceEvent(AppUserPrincipal actor,UUID journalId,PostingEvent sourceEvent,String sourceReference,String evidence) {
        requireActor(actor,"ACCOUNTING_JOURNALS_APPROVE");text(evidence,500,"approvalEvidence");
        Journal j=scoped(actor,journalId,true);
        require(isBusinessSource(j.sourceType()) && sourceEvent!=null && postingEvent(j.sourceType())==sourceEvent && Objects.equals(sourceReference,j.sourceReference()),"sourceType");
        if("SOURCE_REVERSAL".equals(j.sourceType()))requireActor(actor,"ACCOUNTING_JOURNALS_REVERSE");
        require(!j.makerId().equals(actor.getMemberId()),"independentChecker");
        var prior=books.cancellation(j.id());
        if(prior.isPresent()) {
            require(prior.get().checker().equals(actor.getMemberId()) && prior.get().sourceEvent().equals(sourceEvent.name())
                && prior.get().sourceReference().equals(sourceReference) && prior.get().evidence().equals(evidence),"changedRetry");
            return withLines(scoped(actor,j.id(),false));
        }
        require(Set.of("DRAFT","APPROVED").contains(j.state()),"state");
        openPeriod(actor,j.effectiveDate());
        require(books.historicallyApprovedPolicy(j),"policyChanged");
        books.cancel(j,actor.getMemberId(),sourceEvent.name(),evidence,clock.now());
        event(actor,j.id(),"SOURCE_JOURNAL_CANCELLED",Map.of("sourceType",j.sourceType(),"sourceReference",sourceReference,"sourceEvent",sourceEvent.name(),"evidenceReference",evidence,"previousState",j.state(),"payloadChecksum",j.payloadHash()));
        return withLines(scoped(actor,j.id(),false));
    }
    @Transactional(readOnly=true)
    public Optional<SourceCancellation> sourceCancellation(AppUserPrincipal actor,UUID journalId) {
        requireActor(actor,"ACCOUNTING_JOURNALS_VIEW");scoped(actor,journalId,false);return books.cancellation(journalId);
    }
    private static boolean isBusinessSource(String source) {
        return Set.of("DISBURSEMENT","REPAYMENT","INTEREST_ACCRUAL","FEE","REFUND","ADVANCE","SETTLEMENT","TOP_UP","EXPENSE","FUNDING","CAPITAL","PROVISION","WRITE_OFF","RECOVERY","SOURCE_REVERSAL").contains(source);
    }
    private static void requireBusinessOriginal(Journal j) {
        require("POSTED".equals(j.state()) && isBusinessSource(j.sourceType()) && !"SOURCE_REVERSAL".equals(j.sourceType()) && j.reversesId()==null,"original");
    }
    private static List<String> exactLines(List<Line> lines) {
        return lines.stream().map(l->l.accountId()+":"+l.debit().setScale(2).toPlainString()+":"+l.credit().setScale(2).toPlainString()).sorted().toList();
    }
    private void requireGenericSource(Journal j) {require(Set.of("MANUAL","OPENING","REVERSAL","OPERATIONAL_BRIDGE").contains(j.sourceType()),"sourceType");}
    @Transactional
    public Journal reverse(AppUserPrincipal actor,UUID originalId,UUID key,LocalDate date,String reason,String evidence) {
        requireActor(actor,"ACCOUNTING_JOURNALS_REVERSE");text(reason,500,"reason");
        Journal original=withLines(scoped(actor,originalId,true));
        require("POSTED".equals(original.state()) && "MANUAL".equals(original.sourceType()) && original.reversesId()==null,"original");
        independentOriginal(actor,original);
        List<Line> lines=original.lines().stream().map(l->new Line(l.accountId(),l.credit(),l.debit())).toList();
        return draft(actor,new JournalCommand(key,originalId.toString(),date,evidence,reason,lines),"REVERSAL",originalId);
    }
    @Transactional
    public Journal bridgeOperationalVoucher(AppUserPrincipal actor,UUID voucher,UUID key,String evidence) {
        requireActor(actor,"ACCOUNTING_JOURNALS_CREATE");
        var owner=books.operationalOwner(actor.getSaccoId(),actor.getStationId(),voucher);
        if(owner.isPresent()) {
            Journal existing=scoped(actor,owner.get(),false);
            require("OPERATIONAL_BRIDGE".equals(existing.sourceType()),"sourceAlreadyOwned");
            require(existing.requestKey().equals(key) && existing.makerId().equals(actor.getMemberId()) && Objects.equals(existing.evidenceReference(),evidence),"changedRetry");
            return withLines(existing);
        }
        List<GeneralLedgerRepository.OperationalLine> source=books.operationalVoucher(actor.getSaccoId(),actor.getStationId(),voucher);
        require(source.size()>=2 && source.size()<=100,"voucher");LocalDate date=source.getFirst().date();
        require(source.stream().allMatch(l->date.equals(l.date()) && source.getFirst().loanId().equals(l.loanId())),"voucherScope");
        require(!books.coveredAtCutover(actor.getSaccoId(),actor.getStationId(),date),"alreadyInOpening");
        var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),date);
        List<Line> lines=source.stream().map(l->{
            UUID account=policy.accountMappings().get(l.accountCode());require(account!=null,"unmappedAccount");
            return new Line(account,l.debit(),l.credit());
        }).toList();
        Journal j=draft(actor,new JournalCommand(key,voucher.toString(),date,evidence,"Verified operational voucher bridge",lines),"OPERATIONAL_BRIDGE",null);
        books.bridge(voucher,j);
        return j;
    }
    @Transactional(readOnly=true)
    public Coverage coverage(AppUserPrincipal actor) {
        requireActor(actor,"ACCOUNTING_JOURNALS_VIEW");boolean opening=books.reviewedOpening(actor.getSaccoId(),actor.getStationId());
        long bridge=books.unbridged(actor.getSaccoId(),actor.getStationId());long legacy=books.uncoveredLegacy(actor.getSaccoId(),actor.getStationId());
        return new Coverage(opening,bridge,legacy,opening && bridge==0 && legacy==0?"REVIEWED_COVERAGE":"INCOMPLETE_NOT_AUTHORITATIVE");
    }
    private Journal draft(AppUserPrincipal actor,JournalCommand c,String source,UUID reverses) {
        validateCommand(c);var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),c.effectiveDate());
        policies.requireAllowedPosting(policy,postingEvent(source));
        require(!c.effectiveDate().isAfter(clock.today()) && !c.effectiveDate().isBefore(policy.openingDate()),"effectiveDate");
        if("OPENING".equals(source)) require(c.effectiveDate().equals(policy.openingDate()),"openingDate");
        Period period=openPeriod(actor,c.effectiveDate());
        for(Line line:c.lines()) {
            Account a=books.account(actor.getSaccoId(),line.accountId()).orElseThrow(()->invalid("account"));
            require(a.active() && !"HEADING".equals(a.kind()),"postingAccount");
            require(!"MANUAL".equals(source) || !"CONTROL".equals(a.kind()),"manualControlBypass");
        }
        if(!Set.of("MANUAL","OPENING","REVERSAL").contains(source)) require(c.lines().stream().allMatch(l->policy.accountMappings().containsValue(l.accountId())),"unmappedAccount");
        String hash=payloadHash(source,c,reverses,policy.id());books.lockRequest(actor.getSaccoId(),actor.getStationId(),c.requestKey());
        Optional<Journal> prior=books.byRequest(actor.getSaccoId(),actor.getStationId(),c.requestKey());
        if(prior.isPresent()) {require(actor.getMemberId().equals(prior.get().makerId()) && hash.equals(prior.get().payloadHash()),"changedRetry");return withLines(prior.get());}
        Journal j=new Journal(UUID.randomUUID(),actor.getSaccoId(),actor.getStationId(),policy.id(),policy.version(),period.id(),source,c.sourceReference(),c.requestKey(),hash,"DRAFT",c.evidenceReference(),c.reason(),c.effectiveDate(),actor.getMemberId(),null,clock.now(),null,reverses,List.copyOf(c.lines()),false);
        books.createJournal(j);event(actor,j.id(),"JOURNAL_DRAFTED",Map.of("sourceType",source,"sourceReference",c.sourceReference()));return j;
    }
    private PostingEvent postingEvent(String source) {
        return switch(source) { case "MANUAL"->PostingEvent.MANUAL_JOURNAL;case "OPENING"->PostingEvent.OPENING_BALANCE;case "SOURCE_REVERSAL"->PostingEvent.REVERSAL;default->PostingEvent.valueOf(source);};
    }
    private Period openPeriod(AppUserPrincipal actor,LocalDate date) {
        List<Period> rows=books.lockOpenPeriod(actor.getSaccoId(),date);
        require(rows.size()==1 && "OPEN".equals(rows.getFirst().state()),"openPeriodRequired");return rows.getFirst();
    }
    private Journal scoped(AppUserPrincipal actor,UUID id,boolean lock) {
        requireScope(actor);return books.journal(actor.getSaccoId(),actor.getStationId(),id,lock).orElseThrow(()->new AccessDeniedException("Accounting record is outside your workspace"));
    }
    private Journal withLines(Journal j) {
        return new Journal(j.id(),j.institutionId(),j.branchId(),j.policyId(),j.policyVersion(),j.periodId(),j.sourceType(),j.sourceReference(),j.requestKey(),j.payloadHash(),j.state(),j.evidenceReference(),j.reason(),j.effectiveDate(),j.makerId(),j.checkerId(),j.recordedAt(),j.postedAt(),j.reversesId(),books.lines(j.id()),j.reversed());
    }
    private void independentOriginal(AppUserPrincipal actor,Journal original) {
        require(!actor.getMemberId().equals(original.makerId()) && !actor.getMemberId().equals(original.checkerId()),"independentReversal");
    }
    private void requireActor(AppUserPrincipal actor,String claim) {
        requireScope(actor);if(!access.has(actor,claim))throw new AccessDeniedException("Accounting permission required");
        var current=directory.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Accounting access unavailable"));
        if(!current.isStaffAccessActive() || current.getStatus()!=MemberStatus.ACTIVE || current.getActiveStaffRolesResolved().contains(Position.ADMIN) || !Objects.equals(current.getSaccoId(),actor.getSaccoId()) || !Objects.equals(current.getStationId(),actor.getStationId())
            || institutions.findActiveSacco(actor.getSaccoId()).isEmpty()
            || institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive)
                .filter(station->station.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty()
            || !userClaims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess()).contains(UserClaim.valueOf(claim)))
            throw new AccessDeniedException("Accounting permission unavailable");
    }
    private void requireScope(AppUserPrincipal actor) {
        if(actor==null || !actor.isStaffSession() || actor.isPlatformIdentity() || actor.getMemberId()==null || actor.getSaccoId()==null || actor.getSaccoId().isBlank() || actor.getStationId()==null || actor.getStationId().isBlank()) throw new AccessDeniedException("Institution and branch staff workspace required");
    }
    private void event(AppUserPrincipal actor,UUID id,String action,Map<String,Object> details) {
        audit.logEvent("ACCOUNTING",id,action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"JOURNAL",id.toString(),actor.getSaccoId(),actor.getStationId(),details);
    }
    static void validateCommand(JournalCommand c) {
        require(c!=null && c.requestKey()!=null && c.effectiveDate()!=null,"command");
        text(c.sourceReference(),160,"sourceReference");text(c.evidenceReference(),500,"evidence");
        require(c.reason()==null || c.reason().length()<=500,"reason");
        require(c.lines()!=null && c.lines().size()>=2 && c.lines().size()<=500,"lines");
        BigDecimal balance=BigDecimal.ZERO;
        for(Line l:c.lines()) {
            require(l!=null && l.accountId()!=null,"line");money(l.debit());money(l.credit());
            require((l.debit().signum()>0 && l.credit().signum()==0) || (l.credit().signum()>0 && l.debit().signum()==0),"oneSide");
            balance=balance.add(l.debit()).subtract(l.credit());
        }
        require(balance.signum()==0,"balanced");
    }
    static String payloadHash(String source,JournalCommand c,UUID reverses,UUID policy) {
        try {
            StringBuilder b=new StringBuilder();
            for(Object v:List.of(source,c.sourceReference(),c.effectiveDate(),c.evidenceReference(),c.reason()==null?"":c.reason(),reverses==null?"":reverses,policy)) {
                String s=v.toString();b.append(s.length()).append(':').append(s);
            }
            c.lines().stream().map(l->l.accountId()+":"+l.debit().setScale(2).toPlainString()+":"+l.credit().setScale(2).toPlainString()).sorted().forEach(s->b.append(s.length()).append(':').append(s));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.toString().getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private static void money(BigDecimal amount) {require(amount!=null && amount.signum()>=0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"moneyPrecision");}
    private static int page(int page) {require(page>=0 && page<=10000,"page");return page;}
    private static void text(String value,int max,String key) {require(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->c<32),key);}
    private static void require(boolean condition,String key) {if(!condition)throw invalid(key);}
    private static IllegalArgumentException invalid(String key) {return new IllegalArgumentException("accounting.error."+key);}
}
