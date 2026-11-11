package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanGroupApprovalService {
    private static final Set<String> CLOSED=Set.of("DISBURSED","PAR","DEFAULTED","WRITTEN_OFF","PAID","DISBURSEMENT_REVERSED","WITHDRAWN");
    private final LoanRecordingRepository records;
    private final GeneralLedgerService access;
    private final MemberDirectoryService members;
    private final AuditService audit;
    private final ApplicationClock clock;
    private final ObjectMapper json;
    private final LoanRecordingService recording;
    private final SaccoConfigurationService products;
    @Transactional public UUID customProduct(AppUserPrincipal a){
        access.requireActor(a,"APPROVAL_FLOW_CONFIGURE");access.requireActor(a,"LOAN_PRODUCTS_CREATE");
        UUID product=products.customRecordingProduct(a.getSaccoId());event(a,product,"CUSTOM_RECORDING_SETUP",Map.of("product",product));return product;
    }
    @Transactional(readOnly=true)
    public GroupLoan loan(AppUserPrincipal a,UUID id){access.requireActor(a,"LOAN_RECORDING_VIEW");return records.groupLoan(a,id,false);}
    @Transactional(readOnly=true)
    public List<Choice> products(AppUserPrincipal a,String search,int page){access.requireActor(a,"APPROVAL_FLOW_VIEW");require(search!=null&&search.length()<=100&&page>=0&&page<=10000,"product");return records.filterProducts(a,search,page);}

    @Transactional(readOnly=true)
    public GroupRuleForm rule(AppUserPrincipal a,UUID product) {
        access.requireActor(a,"APPROVAL_FLOW_VIEW");
        var f=new GroupRuleForm();f.setProductId(product);
        if(product!=null) {
            f.setProductName(records.filterProduct(a,product).orElseThrow(()->invalid("product")).label());
            records.groupRule(a,product).ifPresent(r->{f.setExpectedRevision(r.revision());for(var s:steps(r.steps())){f.getGroups().add(s.group());f.getApprovals().add(s.approvals());}});
        }
        return f;
    }
    @Transactional
    public void saveRule(AppUserPrincipal a,GroupRuleForm f) {
        access.requireActor(a,"APPROVAL_FLOW_CONFIGURE");
        require(f.getProductId()!=null,"product");records.filterProduct(a,f.getProductId()).orElseThrow(()->invalid("product"));
        require(f.getGroups()!=null&&f.getApprovals()!=null&&f.getGroups().size()==f.getApprovals().size()&&f.getGroups().size()<=10,"steps");
        var plan=new ArrayList<GroupStep>();
        for(int i=0;i<f.getGroups().size();i++) {
            var group=f.getGroups().get(i);var count=f.getApprovals().get(i);
            if(group==null){require(count==null||count==1,"steps");continue;}
            require(group!=Position.ADMIN&&Position.staffAssignableRoles().contains(group)&&count!=null&&count>=1&&count<=10,"steps");
            plan.add(new GroupStep(group,count));
        }
        require(!plan.isEmpty()&&plan.stream().mapToInt(GroupStep::approvals).sum()<=50,"steps");
        records.lockGroupRule(a,f.getProductId());
        var previous=records.groupRule(a,f.getProductId());
        require(previous.map(GroupRule::revision).orElse(0)==f.getExpectedRevision(),"changed");
        if(previous.isPresent()&&steps(previous.get().steps()).equals(plan))return;
        var rule=new GroupRule(UUID.randomUUID(),f.getProductId(),f.getExpectedRevision()+1,json.writeValueAsString(plan));
        records.groupRule(a,rule,clock.now());event(a,rule.id(),"LOAN_GROUP_RULE_SAVED",Map.of("product",f.getProductId(),"revision",rule.revision()));
    }
    @Transactional(readOnly=true)
    public GroupReview review(AppUserPrincipal a,UUID loan) {
        access.requireActor(a,"LOAN_RECORDING_VIEW");var l=records.groupLoan(a,loan,false);
        var r=records.groupRequest(a,loan).orElse(null);
        if(r==null)return new GroupReview(null,List.of(),List.of(),0,false,false);
        var plan=steps(records.groupSteps(a,r.rule()));var decisions=records.groupDecisions(r);int step=next(plan,decisions);
        boolean eligible="SUBMITTED".equals(l.status())&&r.termsHash().equals(l.termsHash())&&step<plan.size()
            &&independent(a,l,r,decisions)&&currentGroups(a).contains(plan.get(step).group());
        return new GroupReview(r,plan,decisions,step,eligible&&a.getClaims().contains("LOAN_RECORDING_APPROVE"),eligible&&a.getClaims().contains("LOAN_RECORDING_REJECT"));
    }
    @Transactional
    public void submit(AppUserPrincipal a,UUID loan,UUID key) {
        access.requireActor(a,"LOAN_RECORDING_CREATE");require(key!=null,"request");
        var l=records.groupLoan(a,loan,true);var previous=records.groupRequest(a,loan);
        if(previous.isPresent()&&previous.get().id().equals(key)) {
            require(previous.get().maker().equals(a.getMemberId())&&previous.get().termsHash().equals(l.termsHash()),"retry");return;
        }
        require(!CLOSED.contains(l.status())&&!"READY_FOR_DISBURSEMENT".equals(l.status()),"state");
        require(previous.isEmpty()||!"SUBMITTED".equals(l.status()),"state");
        recording.validateStaffReview(a,loan);
        var rule=records.groupRule(a,l.product()).orElseThrow(()->invalid("rule"));
        var plan=steps(rule.steps());
        var candidates=records.groupCandidates(a,l,json.writeValueAsString(plan.stream().map(GroupStep::group).distinct().toList()));
        var slots=new ArrayList<Position>();for(var step:plan)for(int i=0;i<step.approvals();i++)slots.add(step.group());
        var assigned=new HashMap<UUID,Integer>();
        for(int i=0;i<slots.size();i++)require(assign(i,slots,candidates,assigned,new HashSet<>()),"staff");
        var r=new GroupRequest(key,loan,rule.id(),a.getMemberId(),previous.map(GroupRequest::round).orElse(0)+1,l.termsHash(),l.status());
        records.groupRequest(a,r,clock.now());records.groupStatus(a,loan,"SUBMITTED",clock.now());
        event(a,loan,"LOAN_GROUP_REVIEW_STARTED",Map.of("request",key,"rule",rule.id(),"previousStatus",l.status()));
    }
    @Transactional
    public void decide(AppUserPrincipal a,UUID loan,UUID request,UUID key,String decision,String reason,String evidence) {
        require(Set.of("APPROVE","REJECT").contains(decision),"decision");access.requireActor(a,"LOAN_RECORDING_"+decision);
        access.requireActor(a,"LOAN_RECORDING_VIEW");text(reason);text(evidence);require(key!=null,"request");
        var l=records.groupLoan(a,loan,true);var r=records.groupRequest(a,loan).orElseThrow(()->invalid("state"));
        require(r.id().equals(request),"changed");var decisions=records.groupDecisions(r);
        var retry=decisions.stream().filter(d->d.id().equals(key)).findFirst();
        if(retry.isPresent()) {var d=retry.get();require(d.actor().equals(a.getMemberId())&&d.decision().equals(decision)&&d.reason().equals(reason.strip())&&d.evidence().equals(evidence.strip()),"retry");return;}
        require("SUBMITTED".equals(l.status())&&r.termsHash().equals(l.termsHash()),"changed");
        var plan=steps(records.groupSteps(a,r.rule()));int step=next(plan,decisions);
        require(step<plan.size()&&independent(a,l,r,decisions),"independent");
        if(!currentGroups(a).contains(plan.get(step).group()))throw new AccessDeniedException("Current approval group required");
        var d=new GroupDecision(key,step,a.getMemberId(),a.getFullName(),decision,reason.strip(),evidence.strip(),clock.now());
        records.groupDecision(r,d);var all=new ArrayList<>(decisions);all.add(d);
        if(decision.equals("REJECT"))records.groupStatus(a,loan,"REJECTED",clock.now());
        else if(next(plan,all)==plan.size())records.groupStatus(a,loan,"READY_FOR_DISBURSEMENT",clock.now());
        event(a,loan,"LOAN_GROUP_"+decision,Map.of("request",r.id(),"step",step,"group",plan.get(step).group().name(),"reason",reason.strip(),"evidence",evidence.strip()));
    }
    private Set<Position> currentGroups(AppUserPrincipal a) {
        return members.find(a.getMemberId()).filter(Member::isStaffAccessActive).map(Member::getActiveStaffRolesResolved).orElse(Set.of());
    }
    private static boolean independent(AppUserPrincipal a,GroupLoan l,GroupRequest r,List<GroupDecision> decisions) {
        return !l.relatedActor()&&!a.getMemberId().equals(l.client())&&!a.getMemberId().equals(l.recorder())&&!a.getMemberId().equals(r.maker())
            &&decisions.stream().noneMatch(d->d.actor().equals(a.getMemberId()));
    }
    private static int next(List<GroupStep> plan,List<GroupDecision> decisions) {
        if(decisions.stream().anyMatch(d->d.decision().equals("REJECT")))return plan.size();
        for(int i=0;i<plan.size();i++){final int step=i;if(decisions.stream().filter(d->d.step()==step&&d.decision().equals("APPROVE")).count()<plan.get(i).approvals())return i;}
        return plan.size();
    }
    private List<GroupStep> steps(String data){return Arrays.asList(json.readValue(data,GroupStep[].class));}
    private static boolean assign(int slot,List<Position> slots,Map<Position,List<UUID>> candidates,Map<UUID,Integer> assigned,Set<UUID> visited) {
        for(var person:candidates.getOrDefault(slots.get(slot),List.of()))if(visited.add(person)){
            var previous=assigned.get(person);
            if(previous==null||assign(previous,slots,candidates,assigned,visited)){assigned.put(person,slot);return true;}
        }
        return false;
    }
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,Object> details){audit.logEvent("LOAN",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"LOAN",id.toString(),a.getSaccoId(),a.getStationId(),details);}
    private static void text(String value){require(value!=null&&!value.isBlank()&&value.length()<=500&&value.codePoints().noneMatch(Character::isISOControl),"evidence");}
    private static void require(boolean valid,String key){if(!valid)throw invalid(key);}
    private static IllegalArgumentException invalid(String key){return new IllegalArgumentException("loanGroups.error."+key);}
}
