package com.sacco.mvp.accounting.ledger.service;

import com.sacco.mvp.accounting.ledger.dto.LedgerDtos.*;
import com.sacco.mvp.accounting.ledger.exception.LedgerException;
import com.sacco.mvp.accounting.ledger.repository.AccountingLedgerRepository;
import com.sacco.mvp.accounting.policy.dto.ApprovedAccountingPolicy;
import com.sacco.mvp.accounting.policy.model.AccountingEvent;
import com.sacco.mvp.accounting.policy.model.AccountRole;
import com.sacco.mvp.accounting.policy.service.AccountingPolicyService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountingLedgerService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<AccountCategory> CONTROL_CATEGORIES = Set.of(AccountCategory.LOAN_PRINCIPAL,
        AccountCategory.INTEREST_RECEIVABLE,AccountCategory.FEE_RECEIVABLE,AccountCategory.ALLOWANCE);
    private final AccountingLedgerRepository repository;
    private final AccountingPolicyService policies;
    private final AccessControlService access;
    private final ApplicationClock clock;

    @Transactional(readOnly=true)
    public Page<AccountView> accounts(AppUserPrincipal actor,int page) {
        staff(actor,UserClaim.ACCOUNTING_VIEW); page(page);
        return new PageImpl<>(repository.accounts(actor.getSaccoId(),page),PageRequest.of(page,25),repository.accountCount(actor.getSaccoId()));
    }
    @Transactional
    public UUID createAccount(AppUserPrincipal actor,AccountCommand command) {
        staff(actor,UserClaim.ACCOUNTING_ACCOUNTS_MANAGE);
        require(command!=null && command.kind()!=null && command.normalBalance()!=null && command.usage()!=null && command.category()!=null,"account");
        String code=text(command.code(),40,"account"); require(code.matches("[A-Z0-9][A-Z0-9_.-]{0,39}"),"account");
        require(!CONTROL_CATEGORIES.contains(command.category()) || command.usage()!=AccountUsage.POSTING,"control");
        require(validClassification(command.kind(),command.normalBalance(),command.category()),"classification");
        AccountCommand c=new AccountCommand(code,text(command.name(),160,"account"),command.kind(),command.normalBalance(),command.parentId(),command.usage(),command.category());
        UUID id=UUID.randomUUID(); repository.createAccount(id,actor.getSaccoId(),actor.getMemberId(),clock.now(),c);
        repository.event(actor.getSaccoId(),actor.getStationId(),null,"ACCOUNT_CREATED",actor.getMemberId(),code,clock.now(),false);
        return id;
    }
    @Transactional
    public UUID createAccountWithParentCode(AppUserPrincipal actor,AccountCommand command,String parentCode) {
        staff(actor,UserClaim.ACCOUNTING_ACCOUNTS_MANAGE);
        UUID parent=parentCode==null||parentCode.isBlank()?null:repository.account(actor.getSaccoId(),text(parentCode,40,"account")).orElseThrow(()->new LedgerException("account")).id();
        return createAccount(actor,new AccountCommand(command.code(),command.name(),command.kind(),command.normalBalance(),parent,command.usage(),command.category()));
    }
    @Transactional
    public void deactivateAccount(AppUserPrincipal actor,UUID id) {
        staff(actor,UserClaim.ACCOUNTING_ACCOUNTS_MANAGE);
        if(!repository.deactivateAccount(actor.getSaccoId(),id)) throw new AccessDeniedException("Forbidden");
        repository.event(actor.getSaccoId(),actor.getStationId(),null,"ACCOUNT_DEACTIVATED",actor.getMemberId(),id.toString(),clock.now(),false);
    }
    @Transactional
    public UUID openPeriod(AppUserPrincipal actor,LocalDate startsOn,LocalDate endsOn,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_PERIOD_MANAGE);
        String proof=text(evidence,500,"evidence"); policies.requireLocalPolicy(actor.getSaccoId(),clock.today());
        return createPeriod(actor,startsOn,endsOn,proof);
    }
    /** Narrow bootstrap for the exact approved initial opening, including a future first policy. */
    @Transactional
    public UUID openInitialPeriod(AppUserPrincipal actor,LocalDate startsOn,LocalDate endsOn,LocalDate openingCutoff,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_PERIOD_MANAGE);policies.requireOpeningPolicy(actor.getSaccoId(),openingCutoff);
        require(startsOn!=null && endsOn!=null && openingCutoff!=null && !openingCutoff.isBefore(startsOn) && !openingCutoff.isAfter(endsOn),"period");
        return createPeriod(actor,startsOn,endsOn,text(evidence,500,"evidence"));
    }
    private UUID createPeriod(AppUserPrincipal actor,LocalDate startsOn,LocalDate endsOn,String proof) {
        require(startsOn!=null && endsOn!=null && !endsOn.isBefore(startsOn) && !endsOn.isAfter(startsOn.plusDays(366)),"period");
        UUID id=UUID.randomUUID(); repository.createPeriod(id,actor.getSaccoId(),startsOn,endsOn,actor.getMemberId(),clock.now());
        repository.event(actor.getSaccoId(),actor.getStationId(),null,"PERIOD_OPENED",actor.getMemberId(),proof,clock.now(),false);
        return id;
    }
    @Transactional(readOnly=true)
    public Page<JournalSummary> journals(AppUserPrincipal actor,int page) {
        staff(actor,UserClaim.ACCOUNTING_VIEW); page(page);
        return new PageImpl<>(repository.journals(actor.getSaccoId(),actor.getStationId(),page),PageRequest.of(page,25),repository.journalCount(actor.getSaccoId(),actor.getStationId()));
    }
    @Transactional(readOnly=true)
    public JournalView view(AppUserPrincipal actor,UUID id) {
        staff(actor,UserClaim.ACCOUNTING_VIEW); return withLines(scoped(actor,id,false));
    }
    @Transactional
    public UUID draft(AppUserPrincipal actor,JournalCommand command) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_DRAFT);
        require(command!=null,"request");
        return create(actor,command,AccountingEvent.MANUAL_JOURNAL,"MANUAL",command.requestKey()==null?null:command.requestKey().toString(),actor.getMemberId(),null,null,null);
    }
    @Transactional
    public void approve(AppUserPrincipal actor,UUID id,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_APPROVE); JournalView journal=scoped(actor,id,true);
        require(!journal.sourceKind().equals("OPENING"),"openingReview");
        approveJournal(actor,journal,text(evidence,500,"evidence"));
    }
    private void approveJournal(AppUserPrincipal actor,JournalView journal,String evidence) {
        if(journal.state().equals("APPROVED") && actor.getMemberId().equals(journal.approvedBy())) return;
        require(journal.state().equals("DRAFT"),"state"); require(!actor.getMemberId().equals(journal.makerId()),"independent");
        validatePinned(journal); List<LineView> lines=repository.lines(actor.getSaccoId(),journal.id()); validateTotals(lines);
        repository.approve(journal.id(),actor.getMemberId(),clock.now());
        repository.event(actor.getSaccoId(),actor.getStationId(),journal.id(),"JOURNAL_APPROVED",actor.getMemberId(),evidence,clock.now(),false);
    }
    @Transactional
    public void post(AppUserPrincipal actor,UUID id) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_POST); JournalView journal=scoped(actor,id,true);
        if(journal.state().equals("POSTED")||journal.state().equals("REVERSED")) return;
        require(journal.state().equals("APPROVED"),"state"); validatePinned(journal);
        if(!journal.sourceKind().equals("OPENING")) requireAfterCutover(actor,journal.effectiveDate());
        require(repository.openPeriod(actor.getSaccoId(),journal.effectiveDate()).filter(journal.periodId()::equals).isPresent(),"period");
        for(LineView line:repository.lines(actor.getSaccoId(),id)) {
            AccountView account=repository.account(actor.getSaccoId(),line.accountCode()).orElseThrow(()->new LedgerException("account"));
            require(account.active() && !account.usage().equals("HEADING"),"account");
        }
        repository.post(id,actor.getMemberId(),clock.now());
        if(journal.reversesJournalId()!=null) repository.markReversed(journal.reversesJournalId());
        repository.postedOpening(id);
        repository.event(actor.getSaccoId(),actor.getStationId(),id,"JOURNAL_POSTED",actor.getMemberId(),journal.evidenceReference(),clock.now(),true);
    }
    @Transactional
    public UUID requestReversal(AppUserPrincipal actor,UUID originalId,UUID requestKey,LocalDate date,String reason,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_REVERSE); JournalView original=scoped(actor,originalId,true);
        Optional<UUID> existing=repository.reversal(originalId);
        if(existing.isPresent()) {
            JournalView previous=scoped(actor,existing.get(),false);
            require(Objects.equals(previous.requestKey(),requestKey) && Objects.equals(previous.effectiveDate(),date)
                && Objects.equals(previous.reversalReason(),reason==null?null:reason.trim()) && Objects.equals(previous.evidenceReference(),evidence==null?null:evidence.trim()),"retry");
            return previous.id();
        }
        require(original.state().equals("POSTED"),"state");
        require(!actor.getMemberId().equals(original.makerId()) && !actor.getMemberId().equals(original.postedBy()),"independent");
        require(original.sourceKind().equals("MANUAL") || original.sourceKind().equals("OPENING"),"sourceReversal");
        require(!repository.cutoverOpening(originalId),"cutover");
        String why=text(reason,500,"evidence");
        List<LineCommand> lines=repository.lines(actor.getSaccoId(),originalId).stream().map(l->new LineCommand(l.accountCode(),l.credit(),l.debit(),l.memo())).toList();
        return create(actor,new JournalCommand(requestKey,date,original.description(),evidence,lines),AccountingEvent.REVERSAL,"REVERSAL",originalId.toString(),actor.getMemberId(),originalId,why,null);
    }

    /** Must participate in the originating financial command's transaction. No standalone source HTTP route. */
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID postSource(AppUserPrincipal actor,SourceJournalCommand command) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_POST); require(command!=null,"request");
        AccountingEvent event; try { event=AccountingEvent.valueOf(command.eventType()); } catch(RuntimeException ex) { throw new LedgerException("event"); }
        require(!Set.of(AccountingEvent.MANUAL_JOURNAL,AccountingEvent.OPENING_BALANCE,AccountingEvent.CLEARING_BRIDGE,AccountingEvent.REVERSAL).contains(event),"event");
        require(command.makerId()!=null && command.checkerId()!=null && !command.makerId().equals(command.checkerId()),"independent");
        requireAfterCutover(actor,command.effectiveDate());
        String kind=text(command.sourceKind(),40,"request"); require(kind.matches("[A-Z_]{1,40}") && !Set.of("MANUAL","OPENING","REVERSAL").contains(kind),"event");
        UUID id=create(actor,new JournalCommand(command.requestKey(),command.effectiveDate(),command.description(),command.evidenceReference(),command.lines()),event,kind,command.sourceReference(),command.makerId(),null,null,command.checkerId());
        JournalView j=scoped(actor,id,true);
        if(j.state().equals("POSTED") || j.state().equals("REVERSED")) {require(command.checkerId().equals(j.approvedBy()),"retry");return id;}
        require(j.state().equals("DRAFT"),"state");
        repository.approve(id,command.checkerId(),clock.now()); repository.post(id,actor.getMemberId(),clock.now());
        repository.event(actor.getSaccoId(),actor.getStationId(),id,"SOURCE_POSTED",actor.getMemberId(),command.evidenceReference(),clock.now(),true);
        return id;
    }
    /** The source workflow must reverse its own subledger in this same transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID reverseSource(AppUserPrincipal actor,UUID originalId,SourceJournalCommand command) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_REVERSE);require(command!=null && originalId!=null,"request");
        require(command.eventType().equals(AccountingEvent.REVERSAL.name()) && command.makerId()!=null && command.checkerId()!=null
            && !command.makerId().equals(command.checkerId()),"independent");
        requireAfterCutover(actor,command.effectiveDate());
        JournalView original=scoped(actor,originalId,true);
        require(!Set.of("MANUAL","OPENING","REVERSAL").contains(original.sourceKind()) && original.reversesJournalId()==null,"sourceReversal");
        require(!command.makerId().equals(original.makerId()) && !command.makerId().equals(original.postedBy()),"independent");
        List<LineCommand> opposite=repository.lines(actor.getSaccoId(),originalId).stream().map(l->new LineCommand(l.accountCode(),l.credit(),l.debit(),l.memo())).toList();
        require(command.lines()==null || command.lines().isEmpty(),"sourceReversal");
        Optional<UUID> existing=repository.reversal(originalId);
        require(original.state().equals("POSTED") || existing.isPresent(),"state");
        String why=text(command.description(),500,"evidence");
        UUID id=create(actor,new JournalCommand(command.requestKey(),command.effectiveDate(),original.description(),command.evidenceReference(),opposite),AccountingEvent.REVERSAL,
            text(command.sourceKind(),40,"source"),command.sourceReference(),command.makerId(),originalId,why,command.checkerId());
        JournalView j=scoped(actor,id,true);
        if(j.state().equals("POSTED")) return id;
        require(existing.isEmpty() || existing.get().equals(id),"retry");
        repository.approve(id,command.checkerId(),clock.now()); repository.post(id,actor.getMemberId(),clock.now());repository.markReversed(originalId);
        repository.event(actor.getSaccoId(),actor.getStationId(),id,"SOURCE_REVERSED",actor.getMemberId(),command.evidenceReference(),clock.now(),true);
        return id;
    }
    /** Source correction lookup; no cross-component repository access or arbitrary read endpoint. */
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID sourceJournalId(AppUserPrincipal actor,String sourceKind,String sourceReference) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_REVERSE);
        String kind=text(sourceKind,40,"source");require(kind.matches("[A-Z_]{1,40}") && !Set.of("MANUAL","OPENING","REVERSAL").contains(kind),"sourceReversal");
        JournalView source=repository.source(actor.getSaccoId(),actor.getStationId(),kind,text(sourceReference,120,"source")).orElseThrow(()->new LedgerException("source"));
        require(source.state().equals("POSTED") || source.state().equals("REVERSED"),"state");return source.id();
    }
    /** Resolve and lock actual tenant account metadata inside the source monetary transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public AccountView sourceAccount(AppUserPrincipal actor,String code) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_POST);
        AccountView account=repository.account(actor.getSaccoId(),text(code,40,"account")).orElseThrow(()->new LedgerException("account"));
        require(account.active() && !account.usage().equals("HEADING"),"account");return account;
    }

    @Transactional
    public UUID previewOpening(AppUserPrincipal actor,OpeningCommand command) {
        staff(actor,UserClaim.ACCOUNTING_OPENING_MANAGE); require(command!=null && command.requestKey()!=null,"request");
        repository.requestLock(actor.getSaccoId(),actor.getStationId(),"REQUEST",command.requestKey().toString());
        JournalCommand j=new JournalCommand(command.requestKey(),command.cutoff(),"Opening balances",command.sourceEvidence(),command.lines());
        UUID journal=create(actor,j,AccountingEvent.OPENING_BALANCE,"OPENING",command.requestKey().toString(),actor.getMemberId(),null,null,null);
        Optional<OpeningView> previous=repository.openingRequest(actor.getSaccoId(),actor.getStationId(),command.requestKey());
        if(previous.isPresent()) return previous.get().id();
        JournalView jv=scoped(actor,journal,false);
        UUID id=UUID.randomUUID(); repository.opening(id,actor.getSaccoId(),actor.getStationId(),command,journal,jv.payloadHash(),actor.getMemberId(),clock.now(),jv.policyId(),jv.policyVersion(),jv.policyHash());
        return id;
    }
    /** Explicit certificate for a genuinely zero opening; never invent a balancing journal. */
    @Transactional
    public UUID previewZeroOpening(AppUserPrincipal actor,UUID requestKey,LocalDate cutoff,String sourceEvidence) {
        staff(actor,UserClaim.ACCOUNTING_OPENING_MANAGE);require(requestKey!=null && cutoff!=null && !cutoff.isAfter(clock.today()),"request");
        String evidence=text(sourceEvidence,500,"evidence");var policy=policies.requireOpeningPolicy(actor.getSaccoId(),cutoff);
        require(policy.postingRules().get(AccountingEvent.OPENING_BALANCE)!=null && policy.postingRules().get(AccountingEvent.OPENING_BALANCE).enabled(),"event");
        String hash=fingerprint("ZERO_CERTIFICATE",cutoff,evidence,actor.getMemberId(),policy.id(),policy.version(),policy.contentHash());
        repository.requestLock(actor.getSaccoId(),actor.getStationId(),"REQUEST",requestKey.toString());
        Optional<OpeningView> existing=repository.openingRequest(actor.getSaccoId(),actor.getStationId(),requestKey);
        if(existing.isPresent()){require(existing.get().payloadHash().equals(hash),"retry");return existing.get().id();}
        require(repository.request(actor.getSaccoId(),actor.getStationId(),requestKey).isEmpty(),"retry");
        UUID id=UUID.randomUUID();repository.opening(id,actor.getSaccoId(),actor.getStationId(),new OpeningCommand(requestKey,cutoff,evidence,List.of()),null,hash,actor.getMemberId(),clock.now(),policy.id(),policy.version(),policy.contentHash());
        repository.event(actor.getSaccoId(),actor.getStationId(),null,"ZERO_OPENING_PREVIEW",actor.getMemberId(),evidence,clock.now(),false);return id;
    }
    @Transactional(readOnly=true)
    public Page<OpeningView> openings(AppUserPrincipal actor,int page) {
        staff(actor,UserClaim.ACCOUNTING_VIEW); page(page);
        return new PageImpl<>(repository.openings(actor.getSaccoId(),actor.getStationId(),page),PageRequest.of(page,25),repository.openingCount(actor.getSaccoId(),actor.getStationId()));
    }
    @Transactional
    public void approveOpening(AppUserPrincipal actor,UUID batchId,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_OPENING_APPROVE);
        OpeningView opening=repository.opening(actor.getSaccoId(),actor.getStationId(),batchId,true).orElseThrow(()->new AccessDeniedException("Forbidden"));
        require(!actor.getMemberId().equals(opening.makerId()),"independent"); String proof=text(evidence,500,"evidence");
        if(opening.state().equals("APPROVED") && actor.getMemberId().equals(opening.reviewedBy())) return;
        require(opening.state().equals("PREVIEW"),"state");
        if(opening.kind().equals("ZERO_CERTIFICATE")) {
            policies.requireOpeningPolicy(actor.getSaccoId(),opening.policyId(),opening.policyVersion(),opening.cutoff());
            require(repository.unknownLoans(actor.getSaccoId(),actor.getStationId())==0 && repository.subledgerPrincipal(actor.getSaccoId(),actor.getStationId(),opening.cutoff()).signum()==0,"reconciliation");
            repository.event(actor.getSaccoId(),actor.getStationId(),null,"ZERO_OPENING_APPROVED",actor.getMemberId(),proof,clock.now(),false);
        } else approveJournal(actor,scoped(actor,opening.journalId(),true),proof);
        repository.reviewOpening(batchId,actor.getMemberId(),proof,clock.now());
    }
    @Transactional
    public void approveCutover(AppUserPrincipal actor,UUID batchId,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_CUTOVER_APPROVE);
        repository.requestLock(actor.getSaccoId(),actor.getStationId(),"CUTOVER","BOUNDARY");
        OpeningView b=repository.opening(actor.getSaccoId(),actor.getStationId(),batchId,true).orElseThrow(()->new AccessDeniedException("Forbidden"));
        require((b.kind().equals("JOURNAL")?b.state().equals("POSTED"):b.state().equals("APPROVED")) && !actor.getMemberId().equals(b.makerId()),"openingReview");
        require(repository.cutoff(actor.getSaccoId(),actor.getStationId()).isEmpty(),"cutover");
        require(repository.unknownLoans(actor.getSaccoId(),actor.getStationId())==0,"coverage");
        policies.requireOpeningPolicy(actor.getSaccoId(),b.policyId(),b.policyVersion(),b.cutoff());
        BigDecimal principal=b.journalId()==null?ZERO:repository.openingPrincipal(actor.getSaccoId(),b.journalId());
        require(principal.compareTo(repository.subledgerPrincipal(actor.getSaccoId(),actor.getStationId(),b.cutoff()))==0,"reconciliation");
        repository.cutover(actor.getSaccoId(),actor.getStationId(),b,actor.getMemberId(),text(evidence,500,"evidence"),clock.now());
        repository.event(actor.getSaccoId(),actor.getStationId(),b.journalId(),"CUTOVER_REVIEWED",actor.getMemberId(),evidence,clock.now(),false);
    }
    @Transactional(readOnly=true)
    public Coverage coverage(AppUserPrincipal actor) {
        staff(actor,UserClaim.ACCOUNTING_VIEW); LocalDate cutoff=repository.cutoff(actor.getSaccoId(),actor.getStationId()).orElse(null);
        long unknown=repository.unknownLoans(actor.getSaccoId(),actor.getStationId()); long bridge=repository.unbridged(actor.getSaccoId(),actor.getStationId(),cutoff);
        return new Coverage(cutoff,cutoff!=null,unknown,bridge,cutoff==null || unknown>0 || bridge>0?"INCOMPLETE":"REVIEWED");
    }
    /** Bridges immutable vouchers only after the reviewed boundary; explicit mapping is checked against policy. */
    @Transactional
    public UUID bridge(AppUserPrincipal actor,UUID voucher,UUID requestKey,Map<String,String> mapping,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_DRAFT); require(voucher!=null && mapping!=null && mapping.size()<=20,"request");
        LocalDate cutoff=repository.cutoff(actor.getSaccoId(),actor.getStationId()).orElseThrow(()->new LedgerException("cutover"));
        LocalDate date=repository.operationalDate(actor.getSaccoId(),actor.getStationId(),voucher).orElseThrow(()->new LedgerException("source"));
        require(date.isAfter(cutoff),"cutover"); List<LineCommand> source=repository.operationalVoucher(actor.getSaccoId(),actor.getStationId(),voucher);
        require(source.size()>=2 && source.size()<=200,"source");
        List<LineCommand> lines=source.stream().map(l->{String target=mapping.get(l.accountCode());require(target!=null,"mapping");return new LineCommand(target,l.debit(),l.credit(),"");}).toList();
        return create(actor,new JournalCommand(requestKey,date,"Operational loan voucher",evidence,lines),AccountingEvent.CLEARING_BRIDGE,"OPERATIONAL_CLEARING",voucher.toString(),actor.getMemberId(),null,null,null);
    }
    @Transactional
    public UUID bridgeText(AppUserPrincipal actor,UUID voucher,UUID requestKey,String mappingText,String evidence) {
        staff(actor,UserClaim.ACCOUNTING_JOURNAL_DRAFT); require(mappingText!=null && mappingText.length()<=2000,"mapping");
        Map<String,String> mapping=new LinkedHashMap<>(); String[] rows=mappingText.strip().split("\\R");require(rows.length<=20,"mapping");
        for(String row:rows) {String[] fields=row.split("=",-1);require(fields.length==2 && mapping.putIfAbsent(text(fields[0],40,"mapping"),text(fields[1],40,"mapping"))==null,"mapping");}
        return bridge(actor,voucher,requestKey,mapping,evidence);
    }

    private UUID create(AppUserPrincipal actor,JournalCommand command,AccountingEvent event,String sourceKind,String sourceReference,UUID maker,UUID reversal,String reason,UUID sourceChecker) {
        require(command.requestKey()!=null && command.effectiveDate()!=null && !command.effectiveDate().isAfter(clock.today()),"date");
        String reference=text(sourceReference,120,"request"); String description=text(command.description(),500,"request"); String evidence=text(command.evidenceReference(),500,"evidence");
        require(command.lines()!=null && command.lines().size()>=2 && command.lines().size()<=200,"lines");
        List<LineCommand> normalized=command.lines().stream().map(l->{require(l!=null,"lines");return new LineCommand(text(l.accountCode(),40,"account"),amount(l.debit()),amount(l.credit()),optionalText(l.memo(),240));}).toList();
        String fingerprint=fingerprint(command.effectiveDate(),event,sourceKind,reference,description,evidence,maker,sourceChecker,reversal,reason,normalized);
        repository.requestLock(actor.getSaccoId(),actor.getStationId(),"REQUEST",command.requestKey().toString());
        repository.requestLock(actor.getSaccoId(),actor.getStationId(),sourceKind,reference);
        Optional<JournalView> same=repository.request(actor.getSaccoId(),actor.getStationId(),command.requestKey());
        if(same.isEmpty()) same=repository.source(actor.getSaccoId(),actor.getStationId(),sourceKind,reference);
        if(same.isPresent()) {require(same.get().requestKey().equals(command.requestKey()) && same.get().payloadHash().equals(fingerprint),"retry");return same.get().id();}
        ApprovedAccountingPolicy policy=event==AccountingEvent.OPENING_BALANCE?policies.requireOpeningPolicy(actor.getSaccoId(),command.effectiveDate()):policies.requireLocalPolicy(actor.getSaccoId(),command.effectiveDate());
        var rule=policy.postingRules().get(event);require(rule!=null && rule.enabled(),"event");
        for(var mapping:rule.accountCodes().entrySet()) {
            AccountView mapped=repository.account(actor.getSaccoId(),mapping.getValue()).orElseThrow(()->new LedgerException("mapping"));
            require(mapped.active() && validRole(mapping.getKey(),mapped),"mapping");
        }
        List<LineView> lines=new ArrayList<>(); int lineNumber=1;
        for(LineCommand l:normalized) {
            AccountView account=repository.account(actor.getSaccoId(),l.accountCode()).orElseThrow(()->new LedgerException("account"));
            require(account.active() && !account.usage().equals("HEADING"),"account");
            require(!sourceKind.equals("MANUAL") || !account.usage().equals("CONTROL"),"control");
            if(event!=AccountingEvent.MANUAL_JOURNAL && event!=AccountingEvent.OPENING_BALANCE && event!=AccountingEvent.REVERSAL)
                require(rule.accountCodes().containsValue(account.code()),"mapping");
            require((l.debit().signum()>0 && l.credit().signum()==0)||(l.credit().signum()>0&&l.debit().signum()==0),"amount");
            lines.add(new LineView(lineNumber++,account.id(),account.code(),account.name(),l.debit(),l.credit(),l.memo()));
        }
        validateTotals(lines);
        UUID period=repository.openPeriod(actor.getSaccoId(),command.effectiveDate()).orElseThrow(()->new LedgerException("period"));
        UUID id=UUID.randomUUID(); OffsetDateTime now=clock.now();
        JournalView j=new JournalView(id,actor.getSaccoId(),actor.getStationId(),period,command.effectiveDate(),now,"TZS",policy.id(),policy.version(),policy.contentHash(),event.name(),sourceKind,reference,command.requestKey(),fingerprint,description,evidence,"DRAFT",maker,null,null,null,null,reversal,reason,lines);
        repository.insertJournal(j); repository.insertLines(actor.getSaccoId(),id,lines);
        repository.event(actor.getSaccoId(),actor.getStationId(),id,"JOURNAL_DRAFTED",actor.getMemberId(),evidence,now,false); return id;
    }
    private void validatePinned(JournalView j) {
        var policy=j.sourceKind().equals("OPENING")?policies.requireOpeningPolicy(j.institution(),j.policyId(),j.policyVersion(),j.effectiveDate()):policies.requireApprovedLocal(j.institution(),j.policyId(),j.policyVersion(),j.effectiveDate());
        require(policy.contentHash().equals(j.policyHash()),"policy");var rule=policy.postingRules().get(AccountingEvent.valueOf(j.eventType()));require(rule!=null && rule.enabled(),"event");
    }
    private void requireAfterCutover(AppUserPrincipal actor,LocalDate date) {
        LocalDate cutoff=repository.cutoff(actor.getSaccoId(),actor.getStationId()).orElseThrow(()->new LedgerException("cutover"));
        require(date!=null && date.isAfter(cutoff) && cutoff.equals(policies.approvedOpeningDate(actor.getSaccoId(),date)),"cutover");
    }
    private JournalView scoped(AppUserPrincipal actor,UUID id,boolean lock) {
        return repository.journal(actor.getSaccoId(),actor.getStationId(),id,lock).orElseThrow(()->new AccessDeniedException("Forbidden"));
    }
    private JournalView withLines(JournalView j) {
        return new JournalView(j.id(),j.institution(),j.branch(),j.periodId(),j.effectiveDate(),j.recordedAt(),j.currency(),j.policyId(),j.policyVersion(),j.policyHash(),j.eventType(),j.sourceKind(),j.sourceReference(),j.requestKey(),j.payloadHash(),j.description(),j.evidenceReference(),j.state(),j.makerId(),j.approvedBy(),j.approvedAt(),j.postedBy(),j.postedAt(),j.reversesJournalId(),j.reversalReason(),repository.lines(j.institution(),j.id()));
    }
    public List<LineCommand> parseLines(String text) {
        require(text!=null && text.length()<=64000,"lines"); String[] rows=text.strip().split("\\R"); require(rows.length>=2 && rows.length<=200,"lines");
        List<LineCommand> lines=new ArrayList<>();
        for(String row:rows) {
            String[] fields=row.split(",",4); require(fields.length>=3,"lines");
            try {lines.add(new LineCommand(fields[0].trim(),new BigDecimal(fields[1].trim()),new BigDecimal(fields[2].trim()),fields.length==4?fields[3].trim():""));}
            catch(NumberFormatException ex) {throw new LedgerException("amount");}
        }
        return List.copyOf(lines);
    }
    private void staff(AppUserPrincipal actor,UserClaim claim) {
        if(actor==null || actor.isPlatformIdentity() || !actor.isStaffSession() || !access.has(actor,claim) || actor.getSaccoId()==null || actor.getSaccoId().isBlank() || actor.getStationId()==null || actor.getStationId().isBlank()) throw new AccessDeniedException("Forbidden");
    }
    private static boolean validClassification(AccountKind kind,NormalBalance normal,AccountCategory category) {
        if(category==AccountCategory.OTHER) return true;
        AccountKind expected=switch(category) {
            case PAYABLE,FUNDING,TAX,CUSTOMER_ADVANCE,EXPENSE_ACCRUAL -> AccountKind.LIABILITY;
            case CAPITAL,RETAINED_EARNINGS -> AccountKind.EQUITY;
            case INCOME -> AccountKind.INCOME;
            case EXPENSE -> AccountKind.EXPENSE;
            case CLEARING,SUSPENSE,INTERNAL_TRANSFER -> kind==AccountKind.LIABILITY?AccountKind.LIABILITY:AccountKind.ASSET;
            default -> AccountKind.ASSET;
        };
        NormalBalance expectedNormal=Set.of(AccountCategory.ALLOWANCE,AccountCategory.ACCUMULATED_DEPRECIATION).contains(category)?NormalBalance.CREDIT:
            Set.of(AccountKind.ASSET,AccountKind.EXPENSE).contains(expected)?NormalBalance.DEBIT:NormalBalance.CREDIT;
        return kind==expected && normal==expectedNormal;
    }
    private static boolean validRole(AccountRole role,AccountView account) {
        if(account.usage().equals("HEADING")) return false;
        AccountCategory category=AccountCategory.valueOf(account.category());
        return switch(role) {
            case CASH_BANK -> Set.of(AccountCategory.CASH,AccountCategory.BANK,AccountCategory.MOBILE_MONEY,AccountCategory.CLEARING).contains(category);
            case LOAN_PRINCIPAL -> category==AccountCategory.LOAN_PRINCIPAL && account.usage().equals("CONTROL");
            case INTEREST_RECEIVABLE -> category==AccountCategory.INTEREST_RECEIVABLE && account.usage().equals("CONTROL");
            case FEE_RECEIVABLE -> category==AccountCategory.FEE_RECEIVABLE && account.usage().equals("CONTROL");
            case ALLOWANCE -> category==AccountCategory.ALLOWANCE && account.usage().equals("CONTROL");
            case INTEREST_INCOME,FEE_INCOME,RECOVERY_INCOME -> category==AccountCategory.INCOME;
            case EXPENSE,IMPAIRMENT_EXPENSE,FUNDING_INTEREST,DEPRECIATION -> category==AccountCategory.EXPENSE;
            case CUSTOMER_ADVANCE -> category==AccountCategory.CUSTOMER_ADVANCE;
            case SUSPENSE -> category==AccountCategory.SUSPENSE;
            case PAYABLE -> category==AccountCategory.PAYABLE;
            case FUNDING_PRINCIPAL -> category==AccountCategory.FUNDING;
            case CAPITAL -> category==AccountCategory.CAPITAL;
            case ASSET -> category==AccountCategory.FIXED_ASSET;
            case ACCUMULATED_DEPRECIATION -> category==AccountCategory.ACCUMULATED_DEPRECIATION;
            case TAX_PAYABLE -> category==AccountCategory.TAX;
            case INTERNAL_TRANSFER -> category==AccountCategory.INTERNAL_TRANSFER;
            case RETAINED_EARNINGS -> category==AccountCategory.RETAINED_EARNINGS;
            case PREPAYMENT -> category==AccountCategory.PREPAYMENT;
            case EXPENSE_ACCRUAL -> category==AccountCategory.EXPENSE_ACCRUAL;
        };
    }
    private static void page(int page) {require(page>=0 && page<=100000,"page");}
    private static String text(String value,int max,String code) {require(value!=null && !value.isBlank() && value.trim().length()<=max && value.chars().noneMatch(c->c<32 && c!='\n' && c!='\r' && c!='\t'),code);return value.trim();}
    private static String optionalText(String value,int max) {return value==null||value.isBlank()?"":text(value,max,"request");}
    private static BigDecimal amount(BigDecimal amount) {
        require(amount!=null && amount.signum()>=0 && amount.precision()-(long)amount.scale()<=16,"amount");
        if(amount.signum()==0) return ZERO;
        require(amount.scale()<=2 || amount.stripTrailingZeros().scale()<=2,"precision");
        try{return amount.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException ex){throw new LedgerException("precision");}
    }
    private static void validateTotals(List<LineView> lines) {require(lines.size()>=2 && lines.size()<=200,"lines");BigDecimal debit=ZERO,credit=ZERO;for(LineView l:lines){debit=debit.add(l.debit());credit=credit.add(l.credit());}require(debit.signum()>0 && debit.compareTo(credit)==0,"balance");}
    private static String fingerprint(Object... parts) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(Object part:parts) {
                if(part instanceof List<?> list) {for(Object item:list){LineCommand l=(LineCommand)item;for(Object v:List.of(l.accountCode(),l.debit().toPlainString(),l.credit().toPlainString(),l.memo())){byte[] data=v.toString().getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());digest.update(data);}}}
                else {byte[] data=String.valueOf(part).getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());digest.update(data);}
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static void require(boolean condition,String code) {if(!condition) throw new LedgerException(code);}
}
