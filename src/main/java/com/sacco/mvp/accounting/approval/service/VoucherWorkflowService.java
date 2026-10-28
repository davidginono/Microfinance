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
        require(category!=null && (category.isEmpty() || Set.of("AGE_0_7","AGE_8_14","AGE_15_PLUS","OTHERS","INCOMPLETE","NOT_VALIDATED","VALIDATED","ERROR","NOT_POSTED","FAILED","POSTED","RECENT_CREATED","RECENT_UPDATED","READY","ACCOUNT_ERRORS","MISSING_DETAILS","UNBALANCED").contains(category) || Arrays.stream(Approval.values()).anyMatch(v->v.name().equals(category))),"input");
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
    public Details details(AppUserPrincipal a,UUID id) {
        var w=find(a,id,false);viewAccess(a,w.type());var actions=new LinkedHashSet<String>();
        boolean owner=w.owner().equals(a.getMemberId()), editable=!Set.of("POSTING","POSTED").contains(w.posting());
        if(editable) {
            if(owner && w.approval()!=Approval.INITIATED && a.getClaims().contains("VOUCHER_WORKSPACE_UPDATE"))actions.add("EDIT");
            if(owner && w.approval()!=Approval.INITIATED && a.getClaims().contains("VOUCHER_WORKSPACE_VALIDATE"))actions.add("VALIDATE");

            if(owner && w.approval()==Approval.INITIATED && a.getClaims().contains("VOUCHER_APPROVAL_WITHDRAW"))actions.add("WITHDRAW");
            if(owner && w.approval()==Approval.REQUIRED && a.getClaims().contains("VOUCHER_APPROVAL_HOLD"))actions.add("HOLD");
            if(!owner && assigned(a,w) && !repository.alreadyApproved(w,a.getMemberId())) {if(a.getClaims().contains("VOUCHER_APPROVAL_REJECT"))actions.add("REJECT");actions.add("APPROVE");}
            if(w.modelVersion()<2 && w.forceAllowed() && "VALIDATED".equals(w.validation()) && !w.approval().approved() && a.getClaims().contains("VOUCHER_APPROVAL_FORCE"))actions.add("FORCE");
            if(owner && w.approval().approved() && "VALIDATED".equals(w.validation()) && a.getClaims().contains("VOUCHER_WORKSPACE_POST"))actions.add("POST");
            if(owner && w.approval()!=Approval.INITIATED && !w.approval().approved() && a.getClaims().contains("VOUCHER_APPROVAL_INITIATE"))actions.add("SEND");
            if(owner && w.scope().equals("BATCH") && w.itemCount()<50 && w.approval()!=Approval.INITIATED && a.getClaims().contains("VOUCHER_WORKSPACE_UPDATE"))actions.add("ADD");
        }
        return new Details(w,repository.items(id),repository.history(id),Set.copyOf(actions),repository.responses(w));
    }
    @Transactional
    public UUID capture(AppUserPrincipal a,Type type,Form source) {return capture(a,type,source,true);}
    @Transactional
    public UUID capture(AppUserPrincipal a,Type type,Form source,boolean send) {
        authorize(a,"VOUCHER_WORKSPACE_CREATE");viewAccess(a,type);vouchers.authorize(a,type,"CREATE");
        repository.policyLock(a,false);var form=gate.canonical(source,source.getRequestKey());repository.requestLock(a,source.getRequestKey());var existing=repository.request(a,source.getRequestKey());
        if(existing.isPresent()) {var w=existing.get();require(w.owner().equals(a.getMemberId()) && w.type()==type && w.scope().equals("SINGLE"),"retry");var items=repository.items(w.id());require(items.size()==1 && gate.formHash(type,items.getFirst().form()).equals(gate.formHash(type,form)),"retry");return w.id();}
        if(send)vouchers.preview(a,type,form);
        var w=repository.create(a,source.getRequestKey(),type,"SINGLE",form.getReference().isBlank()?type.name():form.getReference(),clock.now());
        repository.save(w,a,form.getRequestKey(),1,form,true);changed(w,1);event(a,w,"CREATED","");if(send)assessAndRoute(a,find(a,w.id(),true));return w.id();
    }
    @Transactional
    public UUID save(AppUserPrincipal a,UUID id,UUID item,int expectedVersion,int itemVersion,Form source) {
        authorize(a,"VOUCHER_WORKSPACE_UPDATE");var w=editable(a,id,expectedVersion);vouchers.authorize(a,w.type(),"CREATE");
        var prior=repository.item(id,item).orElseThrow(()->new Invalid("unavailable"));
        require(prior.version()==itemVersion,"stale");
        var form=gate.canonical(source,item);
        form.setActivityId(prior.form().getActivityId());
        repository.save(w,a,item,prior.line(),form,false);repository.invalidate(id);changed(w,w.itemCount());event(a,w,"CORRECTED","");return item;
    }
    private void changed(Workflow w,int count) {
        var approval=w.approval().approved()?Approval.RESUBMIT_FOR_APPROVAL:w.approval();
        repository.update(w,approval,"NOT_VALIDATED","NOT_POSTED",BigDecimal.ZERO,BigDecimal.ZERO,count,0,0,List.of(),false,null,clock.now());
    }
    @Transactional(timeout=60)
    public void validate(AppUserPrincipal a,UUID id,int version) {
        authorize(a,"VOUCHER_WORKSPACE_VALIDATE");repository.policyLock(a,false);var w=editable(a,id,version);assess(a,w);
    }
    private void assess(AppUserPrincipal a,Workflow w) {
        var id=w.id();var items=repository.items(id);require(!items.isEmpty() && items.size()<=50,"empty");
        BigDecimal total=BigDecimal.ZERO;boolean valid=true;
        for(var item:items) {
            var issues=basicIssues(item.form(),w.type());Preview plan=null;
            if(issues.isEmpty())try {plan=preview(a,w.type(),item.form());}
            catch(com.sacco.mvp.accounting.dto.VoucherDtos.Invalid e){issues.add(new Issue(e.field(),e.getMessage()));}
            catch(IllegalArgumentException e){issues.add(new Issue("",safeError(e)));}
            if(plan!=null)total=total.add(plan.total());
            valid&=issues.isEmpty();repository.issues(id,item.id(),issues,plan==null?null:gate.planHash(plan));
        }
        require(total.precision()<=18,"amount");
        repository.update(w,Approval.REQUIRED,valid?"VALIDATED":"ERROR","NOT_POSTED",total,total,items.size(),0,0,List.of(),false,null,clock.now());
        event(a,w,valid?"VALIDATED":"VALIDATION_FAILED","");
    }
    private void assessAndRoute(AppUserPrincipal a,Workflow w) {
        gate.authorize(a,"VOUCHER_WORKSPACE_POST");
        assess(a,w);w=find(a,w.id(),true);if(!w.validation().equals("VALIDATED"))return;
        var config=repository.configuration(a,w.type());require(config.modelVersion()==2 && config.enabled(w.scope()),"configuration");
        var rule=match(config,w.scope(),w.total());
        checkCapacity(a,w.type(),rule.stages(),a.getMemberId(),new HashMap<>());
        repository.beginRound(w.id());repository.route(w.id(),rule);
        repository.update(w,Approval.INITIATED,"VALIDATED","NOT_POSTED",w.total(),w.total(),w.itemCount(),0,config.version(),rule.stages(),false,null,clock.now());
        event(a,w,"INITIATE","");
    }
    @Transactional
    public UUID batch(AppUserPrincipal a,Type type,UUID key) {
        authorize(a,"VOUCHER_WORKSPACE_CREATE");viewAccess(a,type);vouchers.authorize(a,type,"CREATE");repository.policyLock(a,false);
        var c=repository.configuration(a,type);require(c.modelVersion()==2 && c.batches(),"configuration");require(key!=null,"input");
        repository.requestLock(a,key);var prior=repository.request(a,key);
        if(prior.isPresent()){require(prior.get().scope().equals("BATCH") && prior.get().type()==type && prior.get().owner().equals(a.getMemberId()),"retry");return prior.get().id();}
        var w=repository.create(a,key,type,"BATCH","",clock.now());
        var f=new Form();f.setRequestKey(UUID.randomUUID());f.setEffectiveDate(clock.today());
        repository.save(w,a,f.getRequestKey(),1,gate.canonical(f,f.getRequestKey()),true);changed(w,1);event(a,w,"CREATED","");return w.id();
    }
    @Transactional
    public UUID add(AppUserPrincipal a,UUID id,int version) {
        authorize(a,"VOUCHER_WORKSPACE_UPDATE");var w=editable(a,id,version);vouchers.authorize(a,w.type(),"CREATE");
        require(w.scope().equals("BATCH") && w.itemCount()<50,"limit");
        var f=new Form();f.setRequestKey(UUID.randomUUID());f.setEffectiveDate(clock.today());
        repository.save(w,a,f.getRequestKey(),repository.nextLine(id),gate.canonical(f,f.getRequestKey()),true);
        repository.invalidate(id);changed(w,w.itemCount()+1);event(a,w,"CORRECTED","");return f.getRequestKey();
    }
    @Transactional
    public void remove(AppUserPrincipal a,UUID id,UUID item,int version,int itemVersion) {
        authorize(a,"VOUCHER_WORKSPACE_UPDATE");var w=editable(a,id,version);vouchers.authorize(a,w.type(),"CREATE");
        require(w.scope().equals("BATCH") && w.itemCount()>1,"transition");
        var prior=repository.item(id,item).orElseThrow(()->new Invalid("unavailable"));require(prior.version()==itemVersion,"stale");
        require(repository.removeItem(id,item)==1,"stale");repository.invalidate(id);changed(w,w.itemCount()-1);event(a,w,"REMOVED",prior.form().getReference());
    }
    private Preview preview(AppUserPrincipal a,Type type,Form f) {
        return vouchers.approvalPreview(a,type,f);
    }
    public List<Issue> basicIssues(Form f,Type type) {
        var result=new ArrayList<Issue>();
        if(f.getEffectiveDate()==null || f.getEffectiveDate().isAfter(clock.today()))result.add(new Issue("effectiveDate","voucher.error.date"));
        if(type!=Type.JOURNAL && f.getParty().isBlank())result.add(new Issue("party","approval.error.requiredField"));
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
        require(comments!=null && comments.length()<=1000 && Set.of("SEND","INITIATE","RESUBMIT","WITHDRAW","HOLD","APPROVE","REJECT","FORCE").contains(action),"input");
        String claim=switch(action){case "SEND","RESUBMIT"->"VOUCHER_APPROVAL_INITIATE";case "FORCE"->"VOUCHER_APPROVAL_FORCE";default->"VOUCHER_APPROVAL_"+action;};authorize(a,action.equals("APPROVE")?"VOUCHER_WORKSPACE_VIEW":claim);
        repository.policyLock(a,false);var w=find(a,id,true);viewAccess(a,w.type());require(w.version()==version && !Set.of("POSTING","POSTED").contains(w.posting()),"stale");
        if(Set.of("SEND","WITHDRAW","HOLD","INITIATE","RESUBMIT").contains(action))owner(a,w);
        if(action.equals("SEND")){require(w.approval()!=Approval.INITIATED && !w.approval().approved(),"transition");assessAndRoute(a,w);return;}
        if(Set.of("REJECT","FORCE","HOLD").contains(action))require(!comments.isBlank(),"comments");
        Approval status=w.approval();int stage=w.stage(),revision=w.configVersion();List<Stage> stages=w.stages();boolean force=w.forceAllowed();
        if(action.equals("INITIATE") || action.equals("RESUBMIT")) {
            require(action.equals("INITIATE")?status==Approval.REQUIRED:Set.of(Approval.REJECTED,Approval.WITHDRAWN,Approval.HELD_FROM_APPROVAL,Approval.RESUBMIT_FOR_APPROVAL).contains(status),"transition");
            require(w.validation().equals("VALIDATED"),"validate");
            var config=repository.configuration(a,w.type());require(config.enabled(w.scope()),"configuration");
            if(config.modelVersion()==2){assessAndRoute(a,w);return;}
            throw new Invalid("configuration");

        } else if(action.equals("WITHDRAW")) {require(status==Approval.INITIATED,"transition");status=Approval.WITHDRAWN;}
        else if(action.equals("HOLD")) {require(status==Approval.REQUIRED,"transition");status=Approval.HELD_FROM_APPROVAL;}
        else if(action.equals("FORCE")) {
            var c=repository.configuration(a,w.type());require(w.modelVersion()<2 && w.forceAllowed() && c.forceAllowed() && w.validation().equals("VALIDATED"),"force");
            require(!status.approved(),"transition");checkPlans(a,w);status=Approval.MANUALLY_APPROVED;stage=stages.size();
        } else {
            require(assigned(a,w) && !w.owner().equals(a.getMemberId()),"assignment");authorize(a,w.stages().get(w.stage()).claim());
            require(!repository.alreadyApproved(w,a.getMemberId()),"independent");
            if(action.equals("REJECT"))status=Approval.REJECTED;
            else {checkPlans(a,w);long votes=repository.responses(w).getOrDefault(stage,0L)+1;if(votes>=stages.get(stage).required())stage++;status=stage==stages.size()?Approval.WORKFLOW_APPROVED:Approval.INITIATED;}
        }
        repository.update(w,status,w.validation(),w.posting(),w.total(),w.assessmentAmount(),w.itemCount(),stage,revision,stages,force,null,clock.now());if(action.equals("APPROVE")){repository.decisionStage(id,w.round(),a.getMemberId(),w.stage(),comments.strip());audit.logEvent("ACCOUNTING",id,"VOUCHER_WORKFLOW_APPROVE",a.getMemberId(),AuditEventStatus.SUCCESS,"Voucher approval","VOUCHER_WORKFLOW",w.number(),a.getSaccoId(),a.getStationId(),Map.of("step",w.stage()+1));}else event(a,w,action,comments.strip());
    }
    private void checkPlans(AppUserPrincipal a,Workflow w) {
        var items=repository.items(w.id());require(!items.isEmpty() && items.size()==w.itemCount(),"changed");
        for(var item:items)require(item.planHash()!=null && item.planHash().equals(gate.planHash(preview(a,w.type(),item.form()))),"changed");
    }
    public static Rule match(Configuration c,String scope,BigDecimal amount) {
        require(c.modelVersion()==2 && amount!=null && amount.signum()>0,"configuration");
        var matches=c.rules().stream().filter(r->scope.equals(r.scope()) && amount.compareTo(r.minimum())>=0 && (r.maximum()==null || amount.compareTo(r.maximum())<0)).toList();
        require(matches.size()==1,"noRule");return matches.getFirst();
    }
    private boolean assigned(AppUserPrincipal a,Workflow w) {
        if(w.approval()!=Approval.INITIATED || w.stage()>=w.stages().size())return false;
        var step=w.stages().get(w.stage());if(!a.getClaims().contains(step.claim()))return false;
        if(step.role()==null)return step.memberId()==null || a.getMemberId().equals(step.memberId());
        var member=members.find(a.getMemberId()).orElse(null);
        return member!=null && member.isStaffAccessActive() && member.getStatus()==com.sacco.mvp.domain.MemberStatus.ACTIVE
            && Objects.equals(member.getSaccoId(),a.getSaccoId()) && Objects.equals(member.getStationId(),a.getStationId()) && member.getActiveStaffRolesResolved().contains(step.role());
    }
    @Transactional(readOnly=true)
    public boolean batchAvailable(AppUserPrincipal a,Type type){vouchers.authorize(a,type,"VIEW");if(!a.getClaims().containsAll(Set.of("VOUCHER_WORKSPACE_CREATE","VOUCHER_WORKSPACE_VIEW")))return false;var c=repository.configuration(a,type);return c.modelVersion()==2 && c.batches();}
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
    private Configuration configurationDefinition(AppUserPrincipal a,RuleForm f) {
        require(f.getType()!=null && f.getRules()!=null && f.getRules().size()<=20,"input");
        require(repository.configuration(a,f.getType()).version()==f.getVersion(),"stale");var rules=new ArrayList<Rule>();
        var candidates=new HashMap<com.sacco.mvp.domain.Position,Set<UUID>>();
        for(var row:f.getRules()) {
            require(row!=null && Set.of("SINGLE","BATCH").contains(row.getScope()),"configuration");
            require(row.getMinimum()!=null && row.getMinimum().signum()>=0 && row.getMinimum().scale()<=2 && row.getMinimum().precision()-row.getMinimum().scale()<=16,"configuration");
            require(row.getMaximum()==null || (row.getMaximum().compareTo(row.getMinimum())>0 && row.getMaximum().scale()<=2 && row.getMaximum().precision()-row.getMaximum().scale()<=16),"configuration");
            require(row.getStages()!=null && !row.getStages().isEmpty() && row.getStages().size()<=10,"steps");
            var stages=new ArrayList<Stage>();
            for(var step:row.getStages()){
                require(step!=null && step.getRole()!=null && step.getRole().isStaffRole() && step.getApprovals()>=1 && step.getApprovals()<=10,"steps");
                stages.add(new Stage("VOUCHER_APPROVAL_APPROVE",null,step.getRole(),step.getApprovals()));
            }
            require(stages.stream().mapToInt(Stage::required).sum()<=50,"steps");checkCapacity(a,f.getType(),stages,null,candidates);
            rules.add(new Rule("Rule "+(rules.size()+1),row.getMinimum(),row.getMaximum(),false,List.copyOf(stages),row.getScope(),row.isAutoPost()));
        }
        for(String scope:List.of("SINGLE","BATCH")){
            var scoped=rules.stream().filter(r->r.scope().equals(scope)).sorted(Comparator.comparing(Rule::minimum)).toList();
            for(int n=1;n<scoped.size();n++)require(scoped.get(n-1).maximum()!=null && scoped.get(n-1).maximum().compareTo(scoped.get(n).minimum())<=0,"overlap");
        }
        return new Configuration(f.getType(),f.getVersion()+1,rules.stream().anyMatch(r->r.scope().equals("SINGLE")),rules.stream().anyMatch(r->r.scope().equals("BATCH")),false,List.copyOf(rules),2);
    }
    private Set<UUID> eligible(AppUserPrincipal a,Type type,com.sacco.mvp.domain.Position role) {
        var ids=repository.roleCandidates(a,role);require(ids.size()<=500,"staffLimit");
        var candidates=members.findAll(ids);var subjects=new LinkedHashMap<UUID,UserClaimService.ClaimSubject>();
        for(var m:candidates)if(m.isStaffAccessActive() && m.getStatus()==com.sacco.mvp.domain.MemberStatus.ACTIVE && Objects.equals(m.getSaccoId(),a.getSaccoId()) && Objects.equals(m.getStationId(),a.getStationId()) && m.getActiveStaffRolesResolved().contains(role))subjects.put(m.getId(),new UserClaimService.ClaimSubject(m.getActiveStaffRolesResolved(),m.isMemberAccess()));
        var permissions=claims.effectiveClaims(subjects);
        return subjects.keySet().stream().filter(id->permissions.getOrDefault(id,Set.of()).containsAll(Set.of(UserClaim.VOUCHER_WORKSPACE_VIEW,UserClaim.VOUCHER_APPROVAL_APPROVE,type==Type.JOURNAL?UserClaim.ACCOUNTING_JOURNALS_VIEW:UserClaim.ACCOUNTING_BUSINESS_VIEW))).collect(java.util.stream.Collectors.toSet());
    }
    private void checkCapacity(AppUserPrincipal a,Type type,List<Stage> stages,UUID exclude,Map<com.sacco.mvp.domain.Position,Set<UUID>> cache) {
        var slots=new ArrayList<Set<UUID>>();
        for(var step:stages){var people=new HashSet<>(cache.computeIfAbsent(step.role(),r->eligible(a,type,r)));people.remove(exclude);for(int i=0;i<step.required();i++)slots.add(people);}
        var assigned=new HashMap<UUID,Integer>();for(int slot=0;slot<slots.size();slot++)require(assignSlot(slot,slots,assigned,new HashSet<>()),"staff");
    }
    private boolean assignSlot(int slot,List<Set<UUID>> slots,Map<UUID,Integer> assigned,Set<UUID> visited) {
        for(var person:slots.get(slot))if(visited.add(person)){var previous=assigned.get(person);if(previous==null || assignSlot(previous,slots,assigned,visited)){assigned.put(person,slot);return true;}}return false;
    }
    private Workflow editable(AppUserPrincipal a,UUID id,int version) {var w=find(a,id,true);viewAccess(a,w.type());owner(a,w);require(w.version()==version && w.approval()!=Approval.INITIATED && !Set.of("POSTING","POSTED").contains(w.posting()),"stale");return w;}
    private Workflow find(AppUserPrincipal a,UUID id,boolean lock) {authorize(a,"VOUCHER_WORKSPACE_VIEW");return repository.find(a,id,lock).orElseThrow(()->new AccessDeniedException("Voucher workspace unavailable"));}
    private static void owner(AppUserPrincipal a,Workflow w) {if(!a.getMemberId().equals(w.owner()))throw new AccessDeniedException("Voucher owner required");}
    private void event(AppUserPrincipal a,Workflow w,String action,String comments) {repository.history(a,w.id(),action,comments,clock.now());audit.logEvent("ACCOUNTING",w.id(),"VOUCHER_WORKFLOW_"+action,a.getMemberId(),AuditEventStatus.SUCCESS,"Voucher workflow","VOUCHER_WORKFLOW",w.number(),a.getSaccoId(),a.getStationId(),Map.of("action",action));}
    public static String safeError(RuntimeException e) {String s=e.getMessage();return s!=null && s.length()<=160 && s.matches("(?:approval|voucher|accounting)\\.error\\.[A-Za-z0-9]+")?s:"approval.error.validation";}
}
