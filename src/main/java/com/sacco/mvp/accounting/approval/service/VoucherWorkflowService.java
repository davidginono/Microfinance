package com.sacco.mvp.accounting.approval.service;

import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.*;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Invalid;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Page;
import com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository;
import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.sacco.mvp.accounting.approval.service.VoucherApprovalGate.require;

@Service @RequiredArgsConstructor
public class VoucherWorkflowService {
    private final VoucherWorkflowRepository repository;
    private final VoucherApprovalGate gate;
    private final VoucherService vouchers;
    private final AuditService audit;
    private final ApplicationClock clock;
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private static final Set<String> REVIEW_CLAIMS=Set.of("VOUCHER_APPROVAL_APPROVE","VOUCHER_APPROVAL_ESCALATED_APPROVE");

    public void authorize(AppUserPrincipal a,String claim) {gate.authorize(a,claim);}
    private void viewAccess(AppUserPrincipal a,Type type) {authorize(a,"VOUCHER_WORKSPACE_VIEW");vouchers.authorize(a,type,"VIEW");}
    @Transactional(readOnly=true)
    public Page list(AppUserPrincipal a,Type type,String search,String category,String sort,int page,int size) {
        viewAccess(a,type);require(search!=null && search.length()<=100 && page>=0 && page<=10000 && Set.of(10,30,50).contains(size),"input");
        require(category!=null && (category.isEmpty() || Set.of("OTHERS","INCOMPLETE","NOT_VALIDATED","VALIDATED","ERROR","NOT_POSTED","FAILED","POSTED","RECENT_CREATED","RECENT_UPDATED","READY","ACCOUNT_ERRORS","MISSING_DETAILS","UNBALANCED").contains(category) || Arrays.stream(Approval.values()).anyMatch(s->s.name().equals(category))),"input");
        var rows=repository.list(a,type,search,category,sort,page,size);return new Page(rows.stream().limit(size).toList(),page,size,rows.size()>size,repository.counts(a,type));
    }
    @Transactional(readOnly=true)
    public Page register(AppUserPrincipal a,Type type,String search,String category,String sort,int days,int page,int size) {
        authorize(a,"VOUCHER_WORKSPACE_VIEW");require(search!=null && search.length()<=100 && page>=0 && page<=10000 && Set.of(10,30,50).contains(size) && Set.of(0,7,30,90).contains(days),"input");
        var types=Arrays.stream(Type.values()).filter(t->type==null||type==t).filter(t->a.getClaims().contains(t==Type.JOURNAL?"ACCOUNTING_JOURNALS_VIEW":"ACCOUNTING_BUSINESS_VIEW")).toList();
        if(types.isEmpty())throw new AccessDeniedException("Voucher type unavailable");for(var t:types)vouchers.authorize(a,t,"VIEW");
        require(category!=null && (category.isEmpty() || Set.of("OTHERS","INCOMPLETE","NOT_VALIDATED","VALIDATED","ERROR","NOT_POSTED","FAILED","POSTED","RECENT_CREATED","RECENT_UPDATED","READY","ACCOUNT_ERRORS","MISSING_DETAILS","UNBALANCED").contains(category) || Arrays.stream(Approval.values()).anyMatch(v->v.name().equals(category))),"input");
        var rows=repository.register(a,types,search,category,sort,days,page,size,clock.now());var counts=repository.registerCounts(a,types,clock.now());counts.putAll(repository.warningCounts(a,types));return new Page(rows.stream().limit(size).toList(),page,size,rows.size()>size,counts);
    }
    @Transactional(readOnly=true)
    public Map<UUID,String> transactionDates(AppUserPrincipal a,List<Workflow> rows) {authorize(a,"VOUCHER_WORKSPACE_VIEW");require(rows.size()<=50,"limit");return repository.transactionDates(a,rows);}
    @Transactional(readOnly=true)
    public Map<UUID,Set<String>> reviewActions(AppUserPrincipal a,List<Workflow> rows) {
        authorize(a,"VOUCHER_WORKSPACE_VIEW");require(rows.size()<=50,"limit");var approved=repository.approvedWorkflows(a,rows);var out=new HashMap<UUID,Set<String>>();
        for(var w:rows){var available=new HashSet<String>();if(!w.owner().equals(a.getMemberId()) && assigned(a,w) && !approved.contains(w.id())){available.add("APPROVE");if(a.getClaims().contains("VOUCHER_APPROVAL_REJECT"))available.add("REJECT");}out.put(w.id(),available);}return out;
    }
    @Transactional(readOnly=true)
    public List<Map<String,String>> reviewers(AppUserPrincipal a,String claim,String search,int page) {
        authorize(a,"VOUCHER_RULE_VIEW");authorize(a,"VOUCHER_RULE_CONFIGURE");require(REVIEW_CLAIMS.contains(claim) && search!=null && search.length()<=100 && page>=0 && page<=10000,"input");
        var candidates=members.findAll(repository.reviewerCandidates(a,search,page));var subjects=new LinkedHashMap<UUID,UserClaimService.ClaimSubject>();
        for(var member:candidates)if(member.isStaffAccessActive())subjects.put(member.getId(),new UserClaimService.ClaimSubject(member.getActiveStaffRolesResolved(),member.isMemberAccess()));
        var permissions=claims.effectiveClaims(subjects);return candidates.stream().filter(m->permissions.getOrDefault(m.getId(),Set.of()).contains(UserClaim.valueOf(claim))).limit(25).map(m->Map.of("id",m.getId().toString(),"name",m.getFullName())).toList();
    }
    @Transactional(readOnly=true)
    public Details details(AppUserPrincipal a,UUID id) {
        var w=find(a,id,false);viewAccess(a,w.type());var actions=new LinkedHashSet<String>();
        boolean owner=w.owner().equals(a.getMemberId()), editable=!Set.of("POSTING","POSTED").contains(w.posting());
        if(editable) {
            if(owner && w.approval()!=Approval.INITIATED && a.getClaims().contains("VOUCHER_WORKSPACE_UPDATE"))actions.add("EDIT");
            if(owner && w.approval()!=Approval.INITIATED && a.getClaims().contains("VOUCHER_WORKSPACE_VALIDATE"))actions.add("VALIDATE");
            if(owner && "VALIDATED".equals(w.validation()) && !w.approval().approved() && w.approval()!=Approval.INITIATED && a.getClaims().contains("VOUCHER_APPROVAL_INITIATE"))actions.add(w.approval()==Approval.REQUIRED?"INITIATE":"RESUBMIT");
            if(owner && w.approval()==Approval.INITIATED && a.getClaims().contains("VOUCHER_APPROVAL_WITHDRAW"))actions.add("WITHDRAW");
            if(owner && w.approval()==Approval.REQUIRED && a.getClaims().contains("VOUCHER_APPROVAL_HOLD"))actions.add("HOLD");
            if(!owner && assigned(a,w) && !repository.alreadyApproved(w,a.getMemberId())) {if(a.getClaims().contains("VOUCHER_APPROVAL_REJECT"))actions.add("REJECT");actions.add("APPROVE");}
            if(w.forceAllowed() && "VALIDATED".equals(w.validation()) && !w.approval().approved() && a.getClaims().contains("VOUCHER_APPROVAL_FORCE"))actions.add("FORCE");
            if(w.approval().approved() && "VALIDATED".equals(w.validation()) && a.getClaims().contains("VOUCHER_WORKSPACE_POST"))actions.add("POST");
        }
        return new Details(w,repository.items(id),repository.history(id),Set.copyOf(actions));
    }
    @Transactional
    public UUID capture(AppUserPrincipal a,Type type,Form source) {
        authorize(a,"VOUCHER_WORKSPACE_CREATE");viewAccess(a,type);vouchers.authorize(a,type,"CREATE");
        var form=gate.canonical(source,source.getRequestKey());repository.requestLock(a,source.getRequestKey());var existing=repository.request(a,source.getRequestKey());
        if(existing.isPresent()) {var w=existing.get();require(w.owner().equals(a.getMemberId()) && w.type()==type && w.scope().equals("SINGLE"),"retry");var items=repository.items(w.id());require(items.size()==1 && gate.formHash(type,items.getFirst().form()).equals(gate.formHash(type,form)),"retry");return w.id();}
        var w=repository.create(a,source.getRequestKey(),type,"SINGLE",form.getReference().isBlank()?type.name():form.getReference(),clock.now());
        repository.save(w,a,form.getRequestKey(),1,form,true);changed(w,1);event(a,w,"CREATED","");return w.id();
    }
    @Transactional
    public UUID save(AppUserPrincipal a,UUID id,UUID item,int expectedVersion,int itemVersion,Form source) {
        authorize(a,"VOUCHER_WORKSPACE_UPDATE");var w=editable(a,id,expectedVersion);vouchers.authorize(a,w.type(),"CREATE");
        var prior=repository.item(id,item).orElseThrow(()->new Invalid("unavailable"));
        require(prior.version()==itemVersion,"stale");
        var form=gate.canonical(source,item);
        repository.save(w,a,item,prior.line(),form,false);repository.invalidate(id);changed(w,w.itemCount());event(a,w,"CORRECTED","");return item;
    }
    private void changed(Workflow w,int count) {
        var approval=w.approval().approved()?Approval.RESUBMIT_FOR_APPROVAL:w.approval();
        repository.update(w,approval,"NOT_VALIDATED","NOT_POSTED",BigDecimal.ZERO,BigDecimal.ZERO,count,0,0,List.of(),false,null,clock.now());
    }
    @Transactional(timeout=60)
    public void validate(AppUserPrincipal a,UUID id,int version) {
        authorize(a,"VOUCHER_WORKSPACE_VALIDATE");repository.policyLock(a,false);var w=editable(a,id,version);var items=repository.items(id);require(!items.isEmpty(),"empty");
        BigDecimal total=BigDecimal.ZERO,maximum=BigDecimal.ZERO;boolean valid=true;
        for(var item:items) {
            var issues=basicIssues(item.form(),w.type());Preview preview=null;
            if(issues.isEmpty())try {preview=preview(a,w.type(),item.form());}
            catch(com.sacco.mvp.accounting.dto.VoucherDtos.Invalid e){issues.add(new Issue(e.field(),e.getMessage()));}
            catch(IllegalArgumentException e){issues.add(new Issue("",safeError(e)));}
            if(preview!=null){total=total.add(preview.total());maximum=maximum.max(preview.total());}
            valid&=issues.isEmpty();repository.issues(id,item.id(),issues,preview==null?null:gate.planHash(preview));
        }
        require(total.precision()<=18,"amount");
        var config=repository.configuration(a,w.type());
        Approval status=!config.enabled(w.scope())?Approval.NOT_REQUIRED:w.approval().approved()?Approval.RESUBMIT_FOR_APPROVAL:w.approval();
        repository.update(w,status,valid?"VALIDATED":"ERROR","NOT_POSTED",total,w.type()==Type.JOURNAL?maximum:total,items.size(),0,config.version(),List.of(),config.forceAllowed(),null,clock.now());
        event(a,w,valid?"VALIDATED":"VALIDATION_FAILED","");
    }
    private Preview preview(AppUserPrincipal a,Type type,Form f) {
        return vouchers.approvalPreview(a,type,f);
    }
    public List<Issue> basicIssues(Form f,Type type) {
        var result=new ArrayList<Issue>();
        if(f.getEffectiveDate()==null || f.getEffectiveDate().isAfter(clock.today()))result.add(new Issue("effectiveDate","voucher.error.date"));
        if(f.getParty().isBlank())result.add(new Issue("party","approval.error.requiredField"));
        if(f.getReference().isBlank())result.add(new Issue("reference","approval.error.requiredField"));
        if(type!=Type.JOURNAL && f.getMoneyAccountId()==null)result.add(new Issue("moneyAccountId","voucher.error.moneyAccount"));
        for(int n=0;n<f.getRows().size();n++) {var r=f.getRows().get(n);String p="rows["+n+"].";
            if(r.getDescription().isBlank())result.add(new Issue(p+"description","approval.error.requiredField"));
            if(r.getAmount()==null || r.getAmount().signum()<=0)result.add(new Issue(p+"amount","voucher.error.amount"));
            if(r.getTransactionId()==null) {
                if(type!=Type.RECEIPT && r.getDebitAccountId()==null)result.add(new Issue(p+"debitAccountId","voucher.error.account"));
                if(type!=Type.PAYMENT && r.getCreditAccountId()==null)result.add(new Issue(p+"creditAccountId","voucher.error.account"));
            }
        }return result;
    }
    @Transactional(timeout=60)
    public void action(AppUserPrincipal a,UUID id,int version,String action,String comments) {
        require(comments!=null && comments.length()<=1000 && Set.of("INITIATE","RESUBMIT","WITHDRAW","HOLD","APPROVE","REJECT","FORCE").contains(action),"input");
        String claim=switch(action){case "RESUBMIT"->"VOUCHER_APPROVAL_INITIATE";case "FORCE"->"VOUCHER_APPROVAL_FORCE";default->"VOUCHER_APPROVAL_"+action;};authorize(a,action.equals("APPROVE")?"VOUCHER_WORKSPACE_VIEW":claim);
        repository.policyLock(a,false);var w=find(a,id,true);viewAccess(a,w.type());require(w.version()==version && !Set.of("POSTING","POSTED").contains(w.posting()),"stale");
        if(Set.of("WITHDRAW","HOLD","INITIATE","RESUBMIT").contains(action))owner(a,w);
        if(Set.of("REJECT","FORCE","HOLD").contains(action))require(!comments.isBlank(),"comments");
        Approval status=w.approval();int stage=w.stage(),revision=w.configVersion();List<Stage> stages=w.stages();boolean force=w.forceAllowed();
        if(action.equals("INITIATE") || action.equals("RESUBMIT")) {
            require(action.equals("INITIATE")?status==Approval.REQUIRED:Set.of(Approval.REJECTED,Approval.WITHDRAWN,Approval.HELD_FROM_APPROVAL,Approval.RESUBMIT_FOR_APPROVAL).contains(status),"transition");
            require(w.validation().equals("VALIDATED"),"validate");
            var config=repository.configuration(a,w.type());require(config.enabled(w.scope()),"configuration");require(w.configVersion()==config.version(),"configurationChanged");
            checkPlans(a,w);
            var rule=match(config,w.assessmentAmount());stages=rule.stages();force=config.forceAllowed();revision=config.version();stage=0;
            status=rule.automatic()?Approval.WORKFLOW_APPROVED:Approval.INITIATED;repository.beginRound(id);
        } else if(action.equals("WITHDRAW")) {require(status==Approval.INITIATED,"transition");status=Approval.WITHDRAWN;}
        else if(action.equals("HOLD")) {require(status==Approval.REQUIRED,"transition");status=Approval.HELD_FROM_APPROVAL;}
        else if(action.equals("FORCE")) {
            var c=repository.configuration(a,w.type());require(w.forceAllowed() && c.forceAllowed() && w.configVersion()==c.version() && w.validation().equals("VALIDATED"),"force");
            require(!status.approved(),"transition");checkPlans(a,w);status=Approval.MANUALLY_APPROVED;stage=stages.size();
        } else {
            require(assigned(a,w) && !w.owner().equals(a.getMemberId()),"assignment");authorize(a,w.stages().get(w.stage()).claim());
            require(!repository.alreadyApproved(w,a.getMemberId()),"independent");
            require(w.configVersion()==repository.configuration(a,w.type()).version(),"configurationChanged");
            if(action.equals("REJECT"))status=Approval.REJECTED;
            else {checkPlans(a,w);stage++;status=stage==stages.size()?Approval.WORKFLOW_APPROVED:Approval.INITIATED;}
        }
        repository.update(w,status,w.validation(),w.posting(),w.total(),w.assessmentAmount(),w.itemCount(),stage,revision,stages,force,null,clock.now());event(a,w,action,comments.strip());
    }
    private void checkPlans(AppUserPrincipal a,Workflow w) {
        var items=repository.items(w.id());require(!items.isEmpty() && items.size()==w.itemCount(),"changed");
        for(var item:items)require(item.planHash()!=null && item.planHash().equals(gate.planHash(preview(a,w.type(),item.form()))),"changed");
    }
    public static Rule match(Configuration c,BigDecimal amount) {
        var matches=c.rules().stream().filter(r->amount.compareTo(r.minimum())>=0 && (r.maximum()==null || amount.compareTo(r.maximum())<0)).toList();require(matches.size()==1,"noRule");return matches.getFirst();
    }
    private boolean assigned(AppUserPrincipal a,Workflow w) {return w.approval()==Approval.INITIATED && w.stage()<w.stages().size() && a.getClaims().contains(w.stages().get(w.stage()).claim()) && (w.stages().get(w.stage()).memberId()==null || a.getMemberId().equals(w.stages().get(w.stage()).memberId()));}
    @Transactional(readOnly=true)
    public Configuration configuration(AppUserPrincipal a,Type type) {authorize(a,"VOUCHER_RULE_VIEW");return repository.configuration(a,type);}
    @Transactional(readOnly=true)
    public Map<UUID,String> ownerNames(AppUserPrincipal a,List<Workflow> workflows) {
        authorize(a,"VOUCHER_WORKSPACE_VIEW");require(workflows.size()<=50,"limit");var names=new HashMap<UUID,String>();
        members.findAll(workflows.stream().map(Workflow::owner).distinct().toList()).stream()
            .filter(m->Objects.equals(m.getSaccoId(),a.getSaccoId()) && Objects.equals(m.getStationId(),a.getStationId()))
            .forEach(m->names.put(m.getId(),m.getFullName()));return names;
    }
    @Transactional(readOnly=true)
    public List<Account> accounts(AppUserPrincipal a,UUID id,String search,int page) {
        var d=details(a,id);require(d.actions().contains("EDIT"),"transition");return vouchers.accounts(a,d.workflow().type(),search,page);
    }
    @Transactional(readOnly=true)
    public List<Client> clients(AppUserPrincipal a,UUID id,String search,int page){var d=details(a,id);require(d.actions().contains("EDIT"),"transition");return vouchers.clients(a,d.workflow().type(),search,page);}
    @Transactional(readOnly=true)
    public Client selectedClient(AppUserPrincipal a,UUID id,Form f){var d=details(a,id);return vouchers.reviewClient(a,d.workflow().type(),f.getClientId()).orElse(null);}
    @Transactional(readOnly=true)
    public Map<String,Account> selectedAccounts(AppUserPrincipal a,UUID id,Form f) {
        var d=details(a,id);return vouchers.reviewAccounts(a,d.workflow().type(),f);
    }
    @Transactional
    public void configure(AppUserPrincipal a,RuleForm f) {
        authorize(a,"VOUCHER_RULE_VIEW");authorize(a,"VOUCHER_RULE_CONFIGURE");repository.policyLock(a,true);
        var definition=configurationDefinition(a,f);
        repository.configure(a,definition,clock.now());
        audit.logEvent("ACCOUNTING",UUID.randomUUID(),"VOUCHER_APPROVAL_CONFIGURED",a.getMemberId(),AuditEventStatus.SUCCESS,"Voucher approval configuration","VOUCHER_RULE",f.getType().name(),a.getSaccoId(),a.getStationId(),Map.of("version",f.getVersion()+1));
    }
    @Transactional(readOnly=true)
    public void validateConfiguration(AppUserPrincipal a,RuleForm f) {
        authorize(a,"VOUCHER_RULE_VIEW");authorize(a,"VOUCHER_RULE_CONFIGURE");configurationDefinition(a,f);
    }
    private Configuration configurationDefinition(AppUserPrincipal a,RuleForm f) {
        require(f.getType()!=null && f.getRules()!=null && f.getRules().size()<=20,"input");
        require(repository.configuration(a,f.getType()).version()==f.getVersion(),"stale");var rules=new ArrayList<Rule>();
        for(var row:f.getRules()) {
            if(!f.isIndividuals() && !f.isBatches() && (row.getName()==null || row.getName().isBlank()))continue;
            require(row.getName()!=null && !row.getName().isBlank() && row.getName().length()<=120 && row.getMinimum()!=null && row.getMinimum().signum()>=0 && row.getMinimum().scale()<=2 && row.getMinimum().precision()-row.getMinimum().scale()<=16,"configuration");
            require(row.getMaximum()==null || (row.getMaximum().compareTo(row.getMinimum())>0 && row.getMaximum().scale()<=2 && row.getMaximum().precision()-row.getMaximum().scale()<=16),"configuration");
            var stages=new ArrayList<Stage>();if(!row.isAutomatic()){stages.add(stage(a,row.getFirstClaim(),row.getFirstMember()));if(row.getSecondClaim()!=null && !row.getSecondClaim().isBlank())stages.add(stage(a,row.getSecondClaim(),row.getSecondMember()));}
            rules.add(new Rule(row.getName().strip(),row.getMinimum(),row.getMaximum(),row.isAutomatic(),List.copyOf(stages)));
        }
        rules.sort(Comparator.comparing(Rule::minimum));for(int n=1;n<rules.size();n++)require(rules.get(n-1).maximum()!=null && rules.get(n-1).maximum().compareTo(rules.get(n).minimum())<=0,"overlap");
        require(!(f.isIndividuals() || f.isBatches()) || !rules.isEmpty(),"configuration");
        return new Configuration(f.getType(),f.getVersion()+1,f.isIndividuals(),f.isBatches(),f.isForceAllowed(),List.copyOf(rules));
    }
    private Stage stage(AppUserPrincipal a,String claim,UUID member) {
        require(REVIEW_CLAIMS.contains(claim),"configuration");if(member!=null) {
            var m=members.find(member).orElseThrow(()->new Invalid("assignment"));
            require(m.isStaffAccessActive() && m.getStatus()==com.sacco.mvp.domain.MemberStatus.ACTIVE && Objects.equals(m.getSaccoId(),a.getSaccoId()) && Objects.equals(m.getStationId(),a.getStationId()) && claims.effectiveClaims(m.getId(),m.getActiveStaffRolesResolved(),m.isMemberAccess()).contains(UserClaim.valueOf(claim)),"assignment");
        }return new Stage(claim,member);
    }
    private Workflow editable(AppUserPrincipal a,UUID id,int version) {var w=find(a,id,true);viewAccess(a,w.type());owner(a,w);require(w.version()==version && w.approval()!=Approval.INITIATED && !Set.of("POSTING","POSTED").contains(w.posting()),"stale");return w;}
    private Workflow find(AppUserPrincipal a,UUID id,boolean lock) {authorize(a,"VOUCHER_WORKSPACE_VIEW");return repository.find(a,id,lock).orElseThrow(()->new AccessDeniedException("Voucher workspace unavailable"));}
    private static void owner(AppUserPrincipal a,Workflow w) {if(!a.getMemberId().equals(w.owner()))throw new AccessDeniedException("Voucher owner required");}
    private void event(AppUserPrincipal a,Workflow w,String action,String comments) {repository.history(a,w.id(),action,comments,clock.now());audit.logEvent("ACCOUNTING",w.id(),"VOUCHER_WORKFLOW_"+action,a.getMemberId(),AuditEventStatus.SUCCESS,"Voucher workflow","VOUCHER_WORKFLOW",w.number(),a.getSaccoId(),a.getStationId(),Map.of("action",action));}
    public static String safeError(RuntimeException e) {String s=e.getMessage();return s!=null && s.length()<=160 && s.matches("(?:approval|voucher|accounting)\\.error\\.[A-Za-z0-9]+")?s:"approval.error.validation";}
}
