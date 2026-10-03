package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReconciliationService {
    private final ReconciliationRepository repo;
    private final AccountingPolicyService policies;
    private final AccessControlService access;
    private final MemberDirectoryService directory;
    private final UserClaimService claims;
    private final AuditService audit;
    private final ApplicationClock clock;
    private final Optional<BusinessReconciliationSource> businessControls;
    private final List<PeriodReopenListener> reopenListeners;
    private final Optional<CashFlowReconciliationSource> cashFlowSources;
    private static final Set<String> ROW_KINDS=Set.of("RECEIPT","DISBURSEMENT","TRANSFER","SETTLEMENT","CHARGE","REVERSAL");
    private static final JsonMapper JSON=JsonMapper.builder().enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).findAndAddModules().build();
    @Transactional(readOnly=true)
    public boolean hasInstitutionHistory(String institution) {return institution!=null&&repo.hasInstitutionHistory(institution);}
    @Transactional(readOnly=true)
    public boolean hasMemberHistory(UUID member) {return member!=null&&repo.hasMemberHistory(member);}

    @Transactional(readOnly=true)
    public List<Format> formats(AppUserPrincipal actor) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);return repo.formats(actor.getSaccoId());}
    @Transactional
    public UUID proposeFormat(AppUserPrincipal actor,String name,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);text(name,120);text(evidence,500);
        UUID id=repo.proposeFormat(actor.getSaccoId(),name,actor.getMemberId(),evidence,clock.now());event(actor,id,"FORMAT_PROPOSED");return id;
    }
    @Transactional
    public void approveFormat(AppUserPrincipal actor,UUID id,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_APPROVE);text(evidence,500);
        Format f=repo.format(actor.getSaccoId(),id).orElseThrow(ReconciliationService::outside);
        independent(actor,f.maker());require(f.checker()==null,"alreadyReviewed");repo.approveFormat(id,actor.getMemberId(),evidence,clock.now());event(actor,id,"FORMAT_APPROVED");
    }
    @Transactional(readOnly=true)
    public List<ImportedRow> preview(AppUserPrincipal actor,StatementCommand command) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);statementAccount(actor,command);return parse(command);
    }
    @Transactional
    public UUID importStatement(AppUserPrincipal actor,StatementCommand c) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);statementAccount(actor,c);var rows=parse(c);
        String checksum=sha(c.content());String payload=sha(c.account()+"|"+c.format()+"|"+c.from()+"|"+c.through()+"|"+c.opening().setScale(2)+"|"+c.closing().setScale(2)+"|"+c.filename()+"|"+c.evidence()+"|"+checksum);
        repo.requestLock(actor.getSaccoId(),actor.getStationId(),c.key());var prior=repo.byRequest(actor.getSaccoId(),actor.getStationId(),c.key());
        if(prior.isPresent()) {require(prior.get().get("payload_hash").equals(payload)&&prior.get().get("maker_id").equals(actor.getMemberId()),"changedRetry");return (UUID)prior.get().get("id");}
        requireOpenRange(actor,c.from(),c.through());repo.lock(actor.getSaccoId(),c.account());require(repo.duplicateFile(actor.getSaccoId(),actor.getStationId(),c,checksum).isEmpty(),"duplicateImport");
        UUID id=repo.insertStatement(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),c,payload,checksum,rows,clock.now());event(actor,id,"STATEMENT_IMPORTED");return id;
    }
    private void statementAccount(AppUserPrincipal actor,StatementCommand c) {
        require(c!=null&&c.account()!=null&&c.format()!=null,"statement");var account=repo.account(actor.getSaccoId(),c.account());
        require(Boolean.TRUE.equals(account.get("active"))&&Set.of("BANK","MOBILE_MONEY").contains(account.get("purpose")),"moneyAccount");
        var format=repo.format(actor.getSaccoId(),c.format()).orElseThrow(ReconciliationService::outside);require(format.checker()!=null,"formatApproval");
    }
    static List<ImportedRow> parse(StatementCommand c) {
        require(c!=null&&c.key()!=null&&c.from()!=null&&c.through()!=null&&!c.through().isBefore(c.from())&&c.through().isBefore(c.from().plusYears(1)),"dates");
        money(c.opening(),true);money(c.closing(),true);text(c.filename(),160);require(!c.filename().contains("/")&&!c.filename().contains("\\"),"filename");text(c.evidence(),500);
        require(c.content()!=null&&c.content().getBytes(StandardCharsets.UTF_8).length<=1_000_000,"importSize");
        String[] rows=c.content().strip().split("\\R");require(rows.length>=2&&rows.length<=2001&&rows[0].equals("date,reference,amount,kind"),"format");
        var result=new ArrayList<ImportedRow>();var seen=new HashSet<String>();BigDecimal total=c.opening();
        for(int i=1;i<rows.length;i++) {
            String[] cells=rows[i].split(",",-1);require(cells.length==4,"format");
            LocalDate date;try {date=LocalDate.parse(cells[0]);}catch(RuntimeException e){throw invalid("dates");}
            require(!date.isBefore(c.from())&&!date.isAfter(c.through()),"dates");text(cells[1],160);
            require(cells[2].matches("-?[0-9]{1,16}(\\.[0-9]{1,2})?"),"money");BigDecimal amount=new BigDecimal(cells[2]);money(amount,true);require(amount.signum()!=0,"money");
            require(ROW_KINDS.contains(cells[3]),"kind");String identity=date+"|"+cells[1]+"|"+amount.setScale(2);
            require(!"RECEIPT".equals(cells[3])||amount.signum()>0,"direction");require(!Set.of("DISBURSEMENT","CHARGE").contains(cells[3])||amount.signum()<0,"direction");
            result.add(new ImportedRow(i,date,cells[1],amount,cells[3],!seen.add(identity)));total=total.add(amount);
        }
        require(total.compareTo(c.closing())==0,"statementBalance");return List.copyOf(result);
    }
    @Transactional(readOnly=true)
    public Page<Statement> statements(AppUserPrincipal actor,int page) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);return page(repo.statements(actor.getSaccoId(),actor.getStationId(),offset(page)),page);}
    @Transactional(readOnly=true)
    public Statement statement(AppUserPrincipal actor,UUID id) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);return scopedStatement(actor,id);}
    @Transactional(readOnly=true)
    public Page<StatementRow> rows(AppUserPrincipal actor,UUID id,int page) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);scopedStatement(actor,id);return page(repo.rows(id,offset(page)),page);}
    @Transactional(readOnly=true)
    public Page<Candidate> candidates(AppUserPrincipal actor,UUID id,int page) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);var s=scopedStatement(actor,id);return page(repo.candidates(actor.getSaccoId(),actor.getStationId(),s.account(),s.from().minusDays(30),s.through().plusDays(30),offset(page)),page);}
    @Transactional
    public UUID proposeMatch(AppUserPrincipal actor,String kind,String evidence,List<Allocation> parts) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);text(evidence,500);require(Set.of("EXACT","SPLIT","BATCH","SETTLEMENT","CHARGE","REVERSAL").contains(kind),"kind");require(parts!=null&&!parts.isEmpty()&&parts.size()<=100,"allocations");
        for(var part:parts)require(part!=null&&part.statementLine()!=null&&part.journalLine()!=null,"allocations");
        var first=repo.line(actor.getSaccoId(),actor.getStationId(),parts.getFirst().statementLine()).orElseThrow(ReconciliationService::outside);UUID account=(UUID)first.get("account_id");
        repo.lock(actor.getSaccoId(),account);validateAllocations(actor,account,kind,parts);
        UUID id=repo.match(actor.getSaccoId(),actor.getStationId(),account,kind,actor.getMemberId(),evidence,null,parts,clock.now());event(actor,id,"MATCH_PROPOSED");return id;
    }
    private void validateAllocations(AppUserPrincipal actor,UUID account,String kind,List<Allocation> parts) {
        Map<UUID,BigDecimal> statements=new HashMap<>(),journals=new HashMap<>();Set<String> duplicates=new HashSet<>();
        require(parts!=null&&!parts.isEmpty()&&parts.size()<=100,"allocations");
        for(var part:parts)require(part!=null&&part.statementLine()!=null&&part.journalLine()!=null,"allocations");
        var statementRows=repo.matchingLines(actor.getSaccoId(),actor.getStationId(),parts.stream().map(Allocation::statementLine).distinct().toList(),true);
        var journalRows=repo.matchingLines(actor.getSaccoId(),actor.getStationId(),parts.stream().map(Allocation::journalLine).distinct().toList(),false);
        require(statementRows.size()==parts.stream().map(Allocation::statementLine).distinct().count()&&journalRows.size()==parts.stream().map(Allocation::journalLine).distinct().count(),"allocations");
        require(repo.openEvidenceDates(actor.getSaccoId(),statementRows.values().stream().map(r->((java.sql.Date)r.get("effective_date")).toLocalDate()).distinct().toList()),"openPeriod");
        for(var p:parts) {
            require(p!=null&&p.statementLine()!=null&&p.journalLine()!=null,"allocations");money(p.amount(),false);require(duplicates.add(p.statementLine()+"/"+p.journalLine()),"allocations");
            var s=statementRows.get(p.statementLine());var j=journalRows.get(p.journalLine());
            require(account.equals(s.get("account_id"))&&account.equals(j.get("account_id")),"account");
            BigDecimal statement=(BigDecimal)s.get("amount"),ledger=((BigDecimal)j.get("debit")).subtract((BigDecimal)j.get("credit"));
            require(statement.signum()==ledger.signum(),"direction");
            if(Boolean.TRUE.equals(s.get("duplicate")))require("BATCH".equals(kind),"duplicateReview");
            if(j.get("reverses_id")!=null||Boolean.TRUE.equals(j.get("original_reversed"))||"REVERSAL".equals(s.get("kind")))require("REVERSAL".equals(kind),"reversalReview");
            if("CHARGE".equals(s.get("kind")))require("CHARGE".equals(kind)||"SETTLEMENT".equals(kind),"feeReview");
            if("SETTLEMENT".equals(s.get("kind")))require("SETTLEMENT".equals(kind),"settlementReview");
            BigDecimal sum=statements.merge(p.statementLine(),p.amount(),BigDecimal::add);require(((BigDecimal)s.get("allocated")).add(sum).compareTo(statement.abs())<=0,"overmatch");
            sum=journals.merge(p.journalLine(),p.amount(),BigDecimal::add);require(((BigDecimal)j.get("allocated")).add(sum).compareTo(ledger.abs())<=0,"overmatch");
            if("EXACT".equals(kind))require(parts.size()==1&&p.amount().compareTo(statement.abs())==0&&p.amount().compareTo(ledger.abs())==0&&Objects.equals(s.get("reference"),j.get("source_reference")),"exactMatch");
        }
    }
    @Transactional
    public void reviewMatch(AppUserPrincipal actor,UUID id,boolean approved,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_APPROVE);text(evidence,500);var m=scopedMatch(actor,id);repo.lock(actor.getSaccoId(),repo.matchAccount(id));
        m=scopedMatch(actor,id);independent(actor,m.maker());require("DRAFT".equals(m.state()),"alreadyReviewed");
        if(approved&&m.reverses()==null)validateAllocations(actor,repo.matchAccount(id),m.kind(),repo.allocations(id));
        if(m.reverses()!=null) {var original=scopedMatch(actor,m.reverses());require("APPROVED".equals(original.state()),"match");independent(actor,original.maker());independent(actor,original.checker());if(approved)requireMatchPeriodsOpen(actor,original.id());}
        repo.decideMatch(id,actor.getMemberId(),approved,evidence,clock.now());event(actor,id,approved?"MATCH_APPROVED":"MATCH_REJECTED");
    }
    @Transactional
    public UUID reverseMatch(AppUserPrincipal actor,UUID id,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);text(evidence,500);var original=scopedMatch(actor,id);require(original.reverses()==null&&"APPROVED".equals(original.state()),"match");
        independent(actor,original.maker());independent(actor,original.checker());UUID account=repo.matchAccount(id);repo.lock(actor.getSaccoId(),account);requireMatchPeriodsOpen(actor,id);
        UUID reversed=repo.match(actor.getSaccoId(),actor.getStationId(),account,"REVERSAL",actor.getMemberId(),evidence,id,List.of(),clock.now());event(actor,reversed,"MATCH_REVERSAL_PROPOSED");return reversed;
    }
    @Transactional(readOnly=true)
    public Page<Match> matches(AppUserPrincipal actor,int page) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);return page(repo.matches(actor.getSaccoId(),actor.getStationId(),offset(page)),page);}
    @Transactional
    public UUID assignExceptionToStaff(AppUserPrincipal actor,UUID line,String kind,String staffNo,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);text(staffNo,40);
        UUID assigned=repo.staff(actor.getSaccoId(),actor.getStationId(),staffNo).orElseThrow(ReconciliationService::outside);
        return assignException(actor,line,kind,assigned,evidence);
    }
    @Transactional
    public UUID assignException(AppUserPrincipal actor,UUID line,String kind,UUID assigned,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);text(evidence,500);require(Set.of("UNMATCHED","DUPLICATE","PARTIAL","REVERSED","TIMING","CHANNEL_FEE").contains(kind),"kind");
        var source=repo.line(actor.getSaccoId(),actor.getStationId(),line).orElseThrow(ReconciliationService::outside);require(repo.openEvidencePeriod(actor.getSaccoId(),((java.sql.Date)source.get("effective_date")).toLocalDate()),"openPeriod");var assignee=directory.find(assigned).orElseThrow(ReconciliationService::outside);
        require(assignee.isStaffAccessActive()&&assignee.getStatus()==MemberStatus.ACTIVE&&Objects.equals(assignee.getSaccoId(),actor.getSaccoId())&&Objects.equals(assignee.getStationId(),actor.getStationId())&&claims.effectiveClaims(assignee.getId(),assignee.getActiveStaffRolesResolved(),assignee.isMemberAccess()).contains(UserClaim.ACCOUNTING_RECONCILIATION_APPROVE),"assignee");
        UUID id=repo.exception(actor.getSaccoId(),actor.getStationId(),line,kind,assigned,actor.getMemberId(),evidence,clock.now());event(actor,id,"EXCEPTION_ASSIGNED");return id;
    }
    @Transactional
    public void reviewException(AppUserPrincipal actor,UUID id,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_APPROVE);text(evidence,500);var ex=repo.exception(actor.getSaccoId(),actor.getStationId(),id).orElseThrow(ReconciliationService::outside);
        var source=repo.line(actor.getSaccoId(),actor.getStationId(),(UUID)ex.get("statement_line_id")).orElseThrow(ReconciliationService::outside);require(repo.openEvidencePeriod(actor.getSaccoId(),((java.sql.Date)source.get("effective_date")).toLocalDate()),"openPeriod");independent(actor,(UUID)ex.get("maker_id"));require(actor.getMemberId().equals(ex.get("assigned_to")),"assignee");repo.reviewException(id,actor.getMemberId(),evidence,clock.now());event(actor,id,"EXCEPTION_DIFFERENCE_REVIEWED");
    }
    @Transactional(readOnly=true)
    public Page<ExceptionRecord> exceptions(AppUserPrincipal actor,int page) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);return page(repo.exceptions(actor.getSaccoId(),actor.getStationId(),offset(page)),page);}
    @Transactional(readOnly=true)
    public Page<AccountBalance> balances(AppUserPrincipal actor,LocalDate asOf,int page) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);require(asOf!=null&&!asOf.isAfter(clock.today()),"dates");return page(repo.balances(actor.getSaccoId(),actor.getStationId(),asOf,offset(page)),page);}
    @Transactional
    public UUID certify(AppUserPrincipal actor,UUID account,LocalDate asOf,String kind,BigDecimal source,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_CREATE);text(evidence,500);money(source,true);require(asOf!=null&&!asOf.isAfter(clock.today()),"dates");require(repo.openEvidencePeriod(actor.getSaccoId(),asOf),"openPeriod");var a=repo.account(actor.getSaccoId(),account);
        Map<String,Set<String>> purposes=Map.of("PHYSICAL_CASH",Set.of("CASH"),"STATEMENT",Set.of("BANK","MOBILE_MONEY"),"CLEARING",Set.of("CLEARING","SUSPENSE","INTERNAL_TRANSFER"),"LOAN_CONTROL",Set.of("LOAN_PRINCIPAL"),"SUPPLIER",Set.of("PAYABLE"),"FUNDING",Set.of("FUNDING"),"OPENING",Set.of((String)a.get("purpose")));
        require(purposes.containsKey(kind)&&purposes.get(kind).contains(a.get("purpose")),"certificateKind");
        if("OPENING".equals(kind)) {
            require(!Set.of("PAYABLE","FUNDING","LOAN_PRINCIPAL").contains(a.get("purpose")),"certificateKind");var opening=repo.opening(actor.getSaccoId(),actor.getStationId()).orElseThrow(()->invalid("approvalRequired"));require(opening.through().equals(asOf)&&source.compareTo(repo.balance(actor.getSaccoId(),actor.getStationId(),account,asOf))==0,"staleEvidence");
        }
        if("LOAN_CONTROL".equals(kind)) {var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),asOf);require(account.equals(policy.accountMappings().get("LOAN_PRINCIPAL")),"loanMapping");}
        if(Set.of("LOAN_CONTROL","SUPPLIER","FUNDING").contains(kind)) {
            var control=provenance().control(actor,account,asOf,(String)a.get("purpose"),clock.now());
            require(source.compareTo(control.signedBalance())==0,"controlBalance");
        }
        if("STATEMENT".equals(kind))require(repo.statementBalanceExists(actor.getSaccoId(),actor.getStationId(),account,asOf,source),"statementEvidence");
        BigDecimal ledger=repo.balance(actor.getSaccoId(),actor.getStationId(),account,asOf);UUID id=repo.certificate(actor.getSaccoId(),actor.getStationId(),account,asOf,kind,source,ledger,actor.getMemberId(),evidence,clock.now());event(actor,id,"BALANCE_CERTIFICATE_PROPOSED");return id;
    }
    @Transactional
    public void reviewCertificate(AppUserPrincipal actor,UUID id,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_APPROVE);text(evidence,500);var c=repo.certificate(actor.getSaccoId(),actor.getStationId(),id).orElseThrow(ReconciliationService::outside);require(repo.openEvidencePeriod(actor.getSaccoId(),c.asOf()),"openPeriod");independent(actor,c.maker());require(c.checker()==null,"alreadyReviewed");
        require(c.ledgerBalance().compareTo(repo.balance(actor.getSaccoId(),actor.getStationId(),c.account(),c.asOf()))==0,"staleEvidence");
        if(Set.of("LOAN_CONTROL","SUPPLIER","FUNDING").contains(c.kind())) {
            String purpose=(String)repo.account(actor.getSaccoId(),c.account()).get("purpose");
            var control=provenance().control(actor,c.account(),c.asOf(),purpose,clock.now());require(control.signedBalance().compareTo(c.sourceBalance())==0,"staleEvidence");
        }
        repo.reviewCertificate(id,actor.getMemberId(),evidence,clock.now());event(actor,id,"BALANCE_DIFFERENCE_REVIEWED");
    }
    @Transactional(readOnly=true)
    public Page<Certificate> certificates(AppUserPrincipal actor,int page) {authorize(actor,UserClaim.ACCOUNTING_RECONCILIATION_VIEW);return page(repo.certificates(actor.getSaccoId(),actor.getStationId(),offset(page)),page);}
    @Transactional(readOnly=true)
    public Page<Period> periods(AppUserPrincipal actor,int page) {authorize(actor,UserClaim.ACCOUNTING_CLOSING_VIEW);return page(repo.periods(actor.getSaccoId(),offset(page)),page);}
    @Transactional(readOnly=true)
    public List<CloseCheck> closeChecks(AppUserPrincipal actor,UUID period) {
        authorize(actor,UserClaim.ACCOUNTING_CLOSING_VIEW);var p=scopedPeriod(actor,period,false);var checks=new ArrayList<>(repo.checks(actor.getSaccoId(),actor.getStationId(),p));
        long blockers=0;try{provenance().prepareControls(actor,p,clock.now());}catch(IllegalArgumentException unavailable){blockers=1;}
        checks.add(new CloseCheck("businessControlSources",blockers));return List.copyOf(checks);
    }
    @Transactional
    public UUID proposeClose(AppUserPrincipal actor,UUID period,String evidence,boolean reopen) {
        authorize(actor,reopen?UserClaim.ACCOUNTING_CLOSING_REOPEN:UserClaim.ACCOUNTING_CLOSING_CREATE);text(evidence,500);var p=scopedPeriod(actor,period,false);repo.lockPriorPeriods(actor.getSaccoId(),p.through());p=scopedPeriod(actor,period,true);
        require((reopen?"CLOSED":"OPEN").equals(p.state()),"periodState");require(reopen||!p.through().isAfter(clock.today()),"periodNotEnded");
        policies.requireApprovedLocalPolicy(actor.getSaccoId(),p.through());if(!reopen)checkReady(actor.getSaccoId(),actor.getStationId(),p);
        var cutoff=clock.now();var data=snapshotData(actor.getSaccoId(),actor.getStationId(),p);
        if(!reopen) {data.put("businessControlSources",provenance().prepareControls(actor,p,cutoff));data.put("cashFlowAllocations",provenance().prepareCash(actor,p,cutoff));data.put("automaticCashMovements",repo.automaticCashMovements(actor.getSaccoId(),actor.getStationId(),p));require(((List<?>)data.get("automaticCashMovements")).size()<=1000,"snapshotSize");}
        String snapshot=JSON.writeValueAsString(data);UUID id=repo.closeReview(actor.getSaccoId(),actor.getStationId(),p,reopen?"REOPEN":"CLOSE",snapshot,sha(snapshot),actor.getMemberId(),evidence,cutoff);event(actor,id,reopen?"REOPEN_PROPOSED":"CLOSE_PROPOSED");return id;
    }
    @Transactional
    public void approveClose(AppUserPrincipal actor,UUID id,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_CLOSING_APPROVE);text(evidence,500);var r=scopedClose(actor,id);var p=scopedPeriod(actor,r.period(),false);repo.lockPriorPeriods(actor.getSaccoId(),p.through());p=scopedPeriod(actor,r.period(),true);r=scopedClose(actor,id);independent(actor,r.maker());require(r.checker()==null,"alreadyReviewed");
        boolean reopen=r.state().equals("DRAFT_REOPEN");if(reopen)authorize(actor,UserClaim.ACCOUNTING_CLOSING_REOPEN);
        require((reopen?"CLOSED":"OPEN").equals(p.state()),"periodState");
        if(!reopen) {checkReady(actor.getSaccoId(),actor.getStationId(),p);require(r.checksum().equals(sha(revalidatedSnapshot(actor,actor.getStationId(),p,r,false))),"staleEvidence");}
        repo.decideClose(id,actor.getMemberId(),evidence,clock.now());
        if(reopen) {authorize(actor,UserClaim.ACCOUNTING_CLOSING_INSTITUTION);repo.setPeriod(p.id(),false,actor.getMemberId(),clock.now());for(var listener:reopenListeners)listener.periodReopened(actor,p.id(),p.from(),r.evidence()+" | "+evidence);}
        event(actor,id,reopen?"PERIOD_REOPENED":"BRANCH_CLOSE_APPROVED");
    }
    @Transactional
    public void completeInstitutionClose(AppUserPrincipal actor,UUID period,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_CLOSING_INSTITUTION);authorize(actor,UserClaim.ACCOUNTING_CLOSING_APPROVE);text(evidence,500);var p=scopedPeriod(actor,period,false);repo.lockPriorPeriods(actor.getSaccoId(),p.through());p=scopedPeriod(actor,period,true);require("OPEN".equals(p.state()),"periodState");
        var branches=repo.branches(actor.getSaccoId(),p.through());require(!branches.isEmpty()&&branches.size()<=1000,"branchCoverage");var reviews=repo.branchCloses(actor.getSaccoId(),period);require(reviews.size()<=1000,"branchCoverage");
        for(String branch:branches) {
            var review=reviews.stream().filter(r->r.branch().equals(branch)).findFirst().orElseThrow(()->invalid("branchCoverage"));
            var retained=repo.close(actor.getSaccoId(),branch,review.id()).orElseThrow(ReconciliationService::outside);
            checkReady(actor.getSaccoId(),branch,p);require(review.checksum().equals(sha(revalidatedSnapshot(actor,branch,p,retained,true))),"staleEvidence");
        }
        repo.setPeriod(period,true,actor.getMemberId(),clock.now());event(actor,period,"INSTITUTION_PERIOD_CLOSED");
    }
    @Transactional(readOnly=true)
    public Page<CloseReview> closes(AppUserPrincipal actor,int page) {authorize(actor,UserClaim.ACCOUNTING_CLOSING_VIEW);return page(repo.closes(actor.getSaccoId(),actor.getStationId(),offset(page)),page);}
    @Transactional(readOnly=true)
    public CloseReview close(AppUserPrincipal actor,UUID id) {authorize(actor,UserClaim.ACCOUNTING_CLOSING_VIEW);return scopedClose(actor,id);}
    /** Stored prior versions remain available. periodClosed also requires this to be the current branch closure. */
    @Transactional(readOnly=true)
    public FinalizedSnapshot finalizedSnapshot(AppUserPrincipal actor,UUID id) {
        authorize(actor,UserClaim.ACCOUNTING_CLOSING_VIEW);var r=scopedClose(actor,id);require("APPROVED_CLOSE".equals(r.state()),"approvalRequired");var p=scopedPeriod(actor,r.period(),false);
        boolean current="CLOSED".equals(p.state())&&repo.currentClose(actor.getSaccoId(),actor.getStationId(),r.id(),p.id());
        return new FinalizedSnapshot(r.id(),p.id(),r.version(),p.through(),r.recordedAt(),r.checksum(),r.snapshot(),r.checker(),current,repo.restated(p.id()),"REVIEWED_DIFFERENCES_RETAINED");
    }
    /** Called by report finalizers inside their short write transaction; reopening must wait for its completion. */
    @Transactional(propagation=Propagation.MANDATORY)
    public FinalizedSnapshot finalizedSnapshotForPublication(AppUserPrincipal actor,UUID id) {
        authorize(actor,UserClaim.ACCOUNTING_CLOSING_VIEW);var review=scopedClose(actor,id);repo.lockPeriodForPublication(actor.getSaccoId(),review.period());
        var result=finalizedSnapshot(actor,id);require(result.periodClosed(),"approvalRequired");
        var p=scopedPeriod(actor,review.period(),false);provenance().retainedControls(actor,actor.getStationId(),p,review,clock.now(),false);provenance().retainedCash(actor,actor.getStationId(),p,review,clock.now(),false);return result;
    }
    /** Trusted institution aggregate for finalizers; no foreign registries or invented common cutoff. */
    @Transactional(propagation=Propagation.MANDATORY)
    public InstitutionSnapshot finalizedInstitutionSnapshotForPublication(AppUserPrincipal actor,UUID period) {
        authorize(actor,UserClaim.FINANCIAL_REPORTS_VIEW);authorize(actor,UserClaim.FINANCIAL_REPORTS_INSTITUTION);
        repo.lockPeriodForPublication(actor.getSaccoId(),period);var p=scopedPeriod(actor,period,false);require("CLOSED".equals(p.state()),"approvalRequired");
        var branches=repo.branches(actor.getSaccoId(),p.through());require(!branches.isEmpty()&&branches.size()<=1000,"branchCoverage");
        var reviews=repo.currentInstitutionCloses(actor.getSaccoId(),period);require(reviews.size()<=1000&&reviews.size()==branches.size(),"branchCoverage");
        require(new HashSet<>(branches).equals(reviews.stream().map(CloseReview::branch).collect(java.util.stream.Collectors.toSet())),"branchCoverage");
        var proofs=new ArrayList<InstitutionSnapshotAssembler.BranchProof>();var now=clock.now();
        for(var review:reviews) {
            require(review.snapshot()!=null,"snapshotSize");require(review.checker()!=null&&!review.checker().equals(review.maker()),"independentReview");
            var control=provenance().retainedControls(actor,review.branch(),p,review,now,true);
            var cash=provenance().retainedCash(actor,review.branch(),p,review,now,true);
            proofs.add(new InstitutionSnapshotAssembler.BranchProof(review,control,cash));
        }
        return new InstitutionSnapshotAssembler().assemble(actor.getSaccoId(),p,proofs,repo.restated(period));
    }
    @Transactional(readOnly=true)
    public OpeningEvidence reviewedOpeningEvidence(AppUserPrincipal actor) {authorize(actor,UserClaim.ACCOUNTING_CLOSING_VIEW);return repo.opening(actor.getSaccoId(),actor.getStationId()).orElseThrow(()->invalid("approvalRequired"));}
    private Map<String,Object> snapshotData(String institution,String branch,Period p) {
        var data=repo.snapshot(institution,branch,p);for(String key:List.of("accounts","cashMovements","certificates","statementEvidence","retainedDifferences","sourcePolicyVersions","cancelledSources"))require(((List<?>)data.get(key)).size()<=1000,"snapshotSize");
        var policy=policies.requireApprovedLocalPolicy(institution,p.through());
        data.put("periodPolicy",data.get("accountingPolicy"));data.put("accountingPolicy",Map.of("id",policy.id().toString(),"policy_version",policy.version(),"authoritative_ledger",policy.authoritativeLedger().name(),"effective_from",policy.effectiveFrom().toString(),"opening_date",policy.openingDate().toString(),"decisions",policy.decisions()));
        return data;
    }
    private ReconciliationProvenance provenance() {return new ReconciliationProvenance(repo,businessControls,cashFlowSources);}
    private String revalidatedSnapshot(AppUserPrincipal actor,String branch,Period p,CloseReview review,boolean global) {
        var data=snapshotData(actor.getSaccoId(),branch,p);
        data.put("businessControlSources",provenance().retainedControls(actor,branch,p,review,clock.now(),global));
        data.put("cashFlowAllocations",provenance().retainedCash(actor,branch,p,review,clock.now(),global));
        var automatic=repo.automaticCashMovements(actor.getSaccoId(),branch,p);require(automatic.size()<=1000,"snapshotSize");data.put("automaticCashMovements",automatic);
        return JSON.writeValueAsString(data);
    }
    private void requireOpenRange(AppUserPrincipal actor,LocalDate from,LocalDate through) {
        var periods=repo.evidencePeriods(actor.getSaccoId(),from,through);require(!periods.isEmpty()&&periods.size()<370,"openPeriod");LocalDate next=from;
        for(var period:periods) {require("OPEN".equals(period.state())&&!period.from().isAfter(next),"openPeriod");if(!period.through().isBefore(next))next=period.through().plusDays(1);}
        require(next.isAfter(through),"openPeriod");
    }
    private void requireMatchPeriodsOpen(AppUserPrincipal actor,UUID match) {
        var ids=repo.allocations(match).stream().map(Allocation::statementLine).distinct().toList();var lines=repo.matchingLines(actor.getSaccoId(),actor.getStationId(),ids,true);require(lines.size()==ids.size(),"allocations");require(repo.openEvidenceDates(actor.getSaccoId(),lines.values().stream().map(r->((java.sql.Date)r.get("effective_date")).toLocalDate()).distinct().toList()),"openPeriod");
    }
    private void checkReady(String institution,String branch,Period p) {require(repo.checks(institution,branch,p).stream().allMatch(c->c.blockers()==0),"closeBlocked");}
    private Statement scopedStatement(AppUserPrincipal a,UUID id) {return repo.statement(a.getSaccoId(),a.getStationId(),id).orElseThrow(ReconciliationService::outside);}
    private Match scopedMatch(AppUserPrincipal a,UUID id) {return repo.match(a.getSaccoId(),a.getStationId(),id).orElseThrow(ReconciliationService::outside);}
    private Period scopedPeriod(AppUserPrincipal a,UUID id,boolean lock) {return repo.period(a.getSaccoId(),id,lock).orElseThrow(ReconciliationService::outside);}
    private CloseReview scopedClose(AppUserPrincipal a,UUID id) {return repo.close(a.getSaccoId(),a.getStationId(),id).orElseThrow(ReconciliationService::outside);}
    private void authorize(AppUserPrincipal actor,UserClaim claim) {
        if(actor==null||!actor.isStaffSession()||actor.isPlatformIdentity()||actor.getMemberId()==null||actor.getSaccoId()==null||actor.getSaccoId().isBlank()||actor.getStationId()==null||actor.getStationId().isBlank()||!access.has(actor,claim.name()))throw outside();
        var current=directory.find(actor.getMemberId()).orElseThrow(ReconciliationService::outside);
        if(!current.isStaffAccessActive()||current.getStatus()!=MemberStatus.ACTIVE||current.getActiveStaffRolesResolved().contains(Position.ADMIN)||!Objects.equals(current.getSaccoId(),actor.getSaccoId())||!Objects.equals(current.getStationId(),actor.getStationId())||!claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess()).contains(claim)||!repo.workspaceActive(actor.getSaccoId(),actor.getStationId()))throw outside();
    }
    private void event(AppUserPrincipal actor,UUID id,String action) {audit.logEvent("ACCOUNTING_RECONCILIATION",id,action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"RECONCILIATION",id.toString(),actor.getSaccoId(),actor.getStationId(),Map.of());}
    private static void independent(AppUserPrincipal actor,UUID maker) {require(maker!=null&&!maker.equals(actor.getMemberId()),"independentReview");}
    private static int offset(int page) {require(page>=0&&page<=10000,"page");return page*25;}
    private static <T> Page<T> page(List<T> rows,int p) {return new Page<>(rows.stream().limit(25).toList(),p,rows.size()>25);}
    static void money(BigDecimal value,boolean signed) {require(value!=null&&(signed||value.signum()>0)&&value.scale()<=2&&value.precision()-value.scale()<=16,"money");}
    static void text(String value,int max) {require(value!=null&&!value.isBlank()&&value.length()<=max&&value.chars().noneMatch(c->c<32||c=='<'||c=='>'),"text");}
    static String sha(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static AccessDeniedException outside() {return new AccessDeniedException("Reconciliation record or permission unavailable");}
    private static IllegalArgumentException invalid(String key) {return new IllegalArgumentException("reconciliation.error."+key);}
    private static void require(boolean condition,String key) {if(!condition)throw invalid(key);}
}
