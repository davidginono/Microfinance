package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.dto.AccountOnboardingForm;
import com.sacco.mvp.accounting.dto.AccountVersionDtos.*;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GeneralLedgerService {
    private final GeneralLedgerRepository books;
    private final AccessControlService access;
    private final AuditService audit;
    private final ApplicationClock clock;
    private final UserClaimService userClaims;
    private final MemberDirectoryService directory;
    private final SaccoRegistryService institutions;
    private static final Set<String> TYPES=Set.of("ASSET","LIABILITY","EQUITY","INCOME","EXPENSE");
    private static final Set<String> KINDS=Set.of("HEADING","POSTING","CONTROL");
    private static final Set<String> PURPOSES=Set.of("CASH","BANK","MOBILE_MONEY","CLEARING","SUSPENSE","LOAN_PRINCIPAL",
        "INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE","PAYABLE","FUNDING","CAPITAL","INCOME","EXPENSE","FIXED_ASSET","PREPAYMENT","TAX","INTERNAL_TRANSFER","OTHER");
    private static final List<String> ROOT_TYPES=List.of("ASSET","LIABILITY","EQUITY","EXPENSE","INCOME");
    private static final List<String> ROOT_NAMES=List.of("Assets","Liabilities","Equity","Expenses","Income");
    private static final List<String> ROOT_NAMES_SW=List.of("Mali","Madeni","Mtaji","Gharama","Mapato");

    public static boolean mainGroup(Account account) {
        return account.parentId()==null && "HEADING".equals(account.kind()) && account.code().matches("[1-5]00000");
    }
    @Transactional(readOnly=true)
    public Page<AccountRow> chart(AppUserPrincipal actor,AccountFilter filter,int page) {
        return chart(actor,filter,page,25);
    }
    @Transactional(readOnly=true)
    public Page<AccountRow> chart(AppUserPrincipal actor,AccountFilter filter,int page,int size) {
        return chart(actor,filter,page,size,"code","asc");
    }
    @Transactional(readOnly=true)
    public Page<AccountRow> chart(AppUserPrincipal actor,AccountFilter filter,int page,int size,String sort,String direction) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");int n=page(page);validateFilter(filter);
        require(java.util.Set.of(10,25,30,50).contains(size),"filter");
        require(java.util.Set.of("code","name","parent","type","kind","normalBalance","status").contains(sort) && java.util.Set.of("asc","desc").contains(direction),"filter");
        if(filter.parentId()!=null) require(books.account(actor.getSaccoId(),filter.parentId()).map(a->"HEADING".equals(a.kind())).orElse(false),"parent");
        var rows="code".equals(sort) && "asc".equals(direction)
            ? books.chart(actor.getSaccoId(),filter,n*size,size+1)
            : books.chart(actor.getSaccoId(),filter,n*size,size+1,sort,direction);
        return new Page<>(rows.stream().limit(size).toList(),n,rows.size()>size);
    }
    @Transactional(readOnly=true)
    public List<Account> chartPath(AppUserPrincipal actor,UUID id) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");
        var path=books.chartPath(actor.getSaccoId(),id);
        require(!path.isEmpty() && path.size()<=16 && path.getFirst().parentId()==null &&
            path.stream().allMatch(a->"HEADING".equals(a.kind())) && path.stream().map(Account::id).distinct().count()==path.size(),"parent");
        return path;
    }
    @Transactional(readOnly=true)
    public Page<Account> chartParents(AppUserPrincipal actor,boolean groups,String search,int page) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");int n=page(page);
        require(search!=null && search.length()<=100,"search");
        var rows=books.chartParents(actor.getSaccoId(),groups,search,n*25,26);
        return new Page<>(rows.stream().limit(25).toList(),n,rows.size()>25);
    }
    @Transactional(readOnly=true)
    public Account chartParent(AppUserPrincipal actor,UUID id,boolean groups) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");return onboardingParent(actor,id,groups);
    }
    @Transactional(readOnly=true)
    public String suggestChartCode(AppUserPrincipal actor,UUID parentId,boolean groups) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");var parent=onboardingParent(actor,parentId,groups);
        int step=groups?(mainGroup(parent)?10000:1000):1;
        int base=Integer.parseInt(parent.code());
        return books.availableChartCode(actor.getSaccoId(),base+step,base+(groups?9*step:999),step).orElse("");
    }
    /** Internal provisioning helper; not exposed by accountant onboarding routes. */
    @Transactional
    public void initializeChart(AppUserPrincipal actor) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_CREATE");books.lockAccounts(actor.getSaccoId());
        for(int index=0;index<ROOT_TYPES.size();index++) {
            String code=(index+1)+"00000",type=ROOT_TYPES.get(index);
            var existing=books.accountCode(actor.getSaccoId(),code);
            if(existing.isPresent()) {
                var a=existing.get();
                require(mainGroup(a) && a.active() && a.type().equals(type) && a.normalBalance().equals(normalBalance(type)),"mainGroupConflict");
            } else {
                persistAccount(actor,new AccountCommand(code,ROOT_NAMES.get(index),type,normalBalance(type),"HEADING","OTHER",null,ROOT_NAMES_SW.get(index),null));
            }
        }
    }
    @Transactional
    public UUID onboardAccount(AppUserPrincipal actor,AccountOnboardingForm form,boolean group) {
        if(trim(form.getReason()).isEmpty())form.setReason("Account creation");
        return books.version(actor.getSaccoId(),onboardVersion(actor,form,group,true)).orElseThrow().accountId();
    }
    @Transactional
    public UUID onboardVersion(AppUserPrincipal actor,AccountOnboardingForm form,boolean group,boolean complete) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_CREATE");books.lockAccounts(actor.getSaccoId());
        var parent=onboardingParent(actor,form.getParentId(),group);
        String code=trim(form.getCode());
        boolean validCode=group
            ? (mainGroup(parent)?code.matches(parent.code().substring(0,1)+"[1-9]0000"):code.matches(parent.code().substring(0,2)+"[1-9]000"))
            : code.matches(parent.code().substring(0,3)+"[0-9]{3}") && !code.endsWith("000");
        require(validCode,"chartCode");
        String kind=group?"HEADING":form.getKind(),purpose=group?"OTHER":form.getPurpose();
        require(group || Set.of("POSTING","CONTROL").contains(kind),"accountClassification");
        require(group || Set.of("DEBIT","CREDIT").contains(form.getNormalBalance()) && PURPOSES.contains(purpose),"accountClassification");
        require(group || !Set.of("LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE").contains(purpose) || "CONTROL".equals(kind),"loanControl");
        var command=new AccountCommand(code,trim(form.getName()),parent.type(),group?normalBalance(parent.type()):form.getNormalBalance(),kind,purpose,parent.id(),trim(form.getNameSw()),trim(form.getDescription()));
        return createAccountVersion(actor,command,form.getRequestKey(),trim(form.getReason()),complete);
    }
    private Account onboardingParent(AppUserPrincipal actor,UUID id,boolean groups) {
        require(id!=null,"parent");
        var parent=books.account(actor.getSaccoId(),id).orElseThrow(()->invalid("parent"));
        require(parent.active() && "HEADING".equals(parent.kind()),"parent");
        require(groups?parent.code().matches("[1-5][0-9]0000"):parent.code().matches("[1-5][1-9][1-9]000"),"chartParent");
        // Verify the actual hierarchy, not just the readable code prefix.
        var node=parent;
        while(!mainGroup(node)) {
            require(node.parentId()!=null,"chartParent");
            var ancestor=books.account(actor.getSaccoId(),node.parentId()).orElseThrow(()->invalid("parent"));
            require(ancestor.active() && "HEADING".equals(ancestor.kind()) && ancestor.type().equals(parent.type()) &&
                (node.code().matches("[1-5][1-9]0000")?mainGroup(ancestor) && node.code().startsWith(ancestor.code().substring(0,1)):
                    ancestor.code().matches("[1-5][1-9]0000") && node.code().startsWith(ancestor.code().substring(0,2))),"chartParent");
            node=ancestor;
        }
        require(ROOT_TYPES.get(Integer.parseInt(node.code().substring(0,1))-1).equals(parent.type()),"chartParent");
        return parent;
    }
    private static String normalBalance(String type) {return Set.of("ASSET","EXPENSE").contains(type)?"DEBIT":"CREDIT";}
    private static String trim(String value) {return value==null?"":value.strip();}
    private static void validateFilter(AccountFilter filter) {
        require(filter!=null && filter.search()!=null && filter.search().length()<=100,"search");
        require(filter.type()!=null && (filter.type().isEmpty() || TYPES.contains(filter.type())) && filter.kind()!=null &&
            (filter.kind().isEmpty() || KINDS.contains(filter.kind())) && Set.of("","ACTIVE","INACTIVE").contains(filter.state()),"accountClassification");
    }

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
        require(c!=null && !("HEADING".equals(c.kind()) && c.parentId()==null),"parent");
        books.lockAccounts(actor.getSaccoId());
        UUID version=createAccountVersion(actor,c,UUID.randomUUID(),"Account creation",true);
        return books.version(actor.getSaccoId(),version).orElseThrow().accountId();
    }
    private UUID persistAccount(AppUserPrincipal actor,AccountCommand c) {
        validateAccount(actor,c);
        UUID id=UUID.randomUUID(); books.createAccount(actor.getSaccoId(),id,c,actor.getMemberId(),clock.now());
        event(actor,id,"ACCOUNT_PROVISIONED",Map.of("accountCode",c.code()));return id;
    }
    private void validateAccount(AppUserPrincipal actor,AccountCommand c) {
        require(c!=null && c.code()!=null && c.code().matches("[A-Z0-9][A-Z0-9_.-]{0,39}"),"accountCode");
        text(c.name(),160,"name");
        require(c.nameSw()==null || c.nameSw().length()<=160,"nameSw");
        require(c.description()==null || c.description().length()<=500,"description");
        require(TYPES.contains(c.type()) && Set.of("DEBIT","CREDIT").contains(c.normalBalance()) && KINDS.contains(c.kind()) && PURPOSES.contains(c.purpose()),"accountClassification");
        require(!Set.of("LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE").contains(c.purpose()) || "CONTROL".equals(c.kind()),"loanControl");
        if(c.parentId()!=null) {
            Account parent=books.account(actor.getSaccoId(),c.parentId()).orElseThrow(()->invalid("parent"));
            require(parent.active() && "HEADING".equals(parent.kind()) && parent.type().equals(c.type()),"parent");
            int depth=0;
            while(parent.parentId()!=null) {
                require(++depth<20,"hierarchyDepth");
                parent=books.account(actor.getSaccoId(),parent.parentId()).orElseThrow(()->invalid("parent"));
            }
        }
    }
    @Transactional
    public void deactivateAccount(AppUserPrincipal actor,UUID id) {
        var v=accountCurrent(actor,id);var form=metadataForm(v.proposed());form.setReason("Account deactivation");
        proposeAccountVersion(actor,id,v.id(),"DEACTIVATE",UUID.randomUUID(),form,true);
    }
    @Transactional
    public void reactivateAccount(AppUserPrincipal actor,UUID id) {
        var v=accountCurrent(actor,id);var form=metadataForm(v.proposed());form.setReason("Account reactivation");
        proposeAccountVersion(actor,id,v.id(),"REACTIVATE",UUID.randomUUID(),form,true);
    }
    @Transactional(readOnly=true)
    public Version accountCurrent(AppUserPrincipal actor,UUID account) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");return books.currentVersion(actor.getSaccoId(),account).orElseThrow(()->invalid("account"));
    }
    public static EditForm metadataForm(Account a) {
        var f=new EditForm();f.setName(a.name());f.setNameSw(a.nameSw());f.setDescription(a.description());return f;
    }
    @Transactional(readOnly=true)
    public Policy accountApprovalPolicy(AppUserPrincipal actor) {
        requireActor(actor,"ACCOUNTING_ACCOUNT_RULES_VIEW");return books.accountPolicy(actor.getSaccoId());
    }
    @Transactional
    public void configureAccountApproval(AppUserPrincipal actor,PolicyForm form) {
        requireActor(actor,"ACCOUNTING_ACCOUNT_RULES_VIEW");requireActor(actor,"ACCOUNTING_ACCOUNT_RULES_CONFIGURE");
        books.lockAccounts(actor.getSaccoId());var old=books.accountPolicy(actor.getSaccoId());require(form.getVersion()==old.version(),"versionStale");
        text(form.getReason(),500,"versionReason");
        var policy=new Policy(old.version()+1,form.isCreateAutomatic(),form.isUpdateAutomatic(),form.isDeactivateAutomatic(),form.isReactivateAutomatic());
        books.saveAccountPolicy(actor.getSaccoId(),policy,form.getReason().strip(),actor.getMemberId(),clock.now());
        audit.log("ACCOUNT_APPROVAL_POLICY",null,"ACCOUNT_POLICY_CHANGED",actor.getMemberId(),policyAudit(actor,old,form.getReason()),policyAudit(actor,policy,form.getReason()));
    }
    private Map<String,Object> policyAudit(AppUserPrincipal a,Policy p,String reason) {
        return Map.of("saccoId",a.getSaccoId(),"stationId",a.getStationId(),"policy",p,"reason",reason,"auditReferenceType","ACCOUNT_POLICY","auditReferenceValue",a.getSaccoId());
    }
    private UUID createAccountVersion(AppUserPrincipal actor,AccountCommand c,UUID key,String reason,boolean complete) {
        require(key!=null,"versionInput");
        String hash=requestHash(List.of(c,reason,complete));
        var prior=books.versionRequest(actor.getSaccoId(),key);
        if(prior.isPresent()) {require(prior.get().creator().equals(actor.getMemberId()) && Objects.equals(prior.get().branch(),actor.getStationId()) && prior.get().requestHash().equals(hash),"versionRetry");return prior.get().id();}
        require(books.accountCode(actor.getSaccoId(),c.code()).isEmpty(),"duplicate");
        UUID account=UUID.randomUUID();var a=new Account(account,c.code(),c.name(),c.type(),c.normalBalance(),c.kind(),c.purpose(),c.parentId(),true,c.nameSw(),c.description());
        var v=newDraft(actor,account,null,"CREATE",null,a,key,hash);
        books.createVersion(actor.getSaccoId(),v);return saveAccountVersion(actor,v.id(),versionForm(a,reason,0),complete);
    }
    @Transactional
    public UUID proposeAccountVersion(AppUserPrincipal actor,UUID account,UUID base,String operation,UUID key,EditForm form,boolean complete) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");books.lockAccounts(actor.getSaccoId());
        require(Set.of("UPDATE","DEACTIVATE","REACTIVATE").contains(operation) && key!=null,"versionInput");
        String hash=requestHash(Arrays.asList(account,base,operation,form.getName(),form.getNameSw(),form.getDescription(),form.getReason(),complete));
        var prior=books.versionRequest(actor.getSaccoId(),key);
        if(prior.isPresent()) {require(prior.get().creator().equals(actor.getMemberId()) && Objects.equals(prior.get().branch(),actor.getStationId()) && prior.get().requestHash().equals(hash),"versionRetry");return prior.get().id();}
        var current=books.currentVersion(actor.getSaccoId(),account).orElseThrow(()->invalid("account"));
        require(current.id().equals(base),"versionStale");require(!books.openVersion(actor.getSaccoId(),account),"versionOpen");
        var a=current.proposed();var proposed=new Account(a.id(),a.code(),a.name(),a.type(),a.normalBalance(),a.kind(),a.purpose(),a.parentId(),operation.equals("REACTIVATE") || !operation.equals("DEACTIVATE") && a.active(),a.nameSw(),a.description());
        var v=newDraft(actor,account,base,operation,a,proposed,key,hash);books.createVersion(actor.getSaccoId(),v);
        return saveAccountVersion(actor,v.id(),versionForm(operation.equals("UPDATE")?new Account(a.id(),a.code(),form.getName(),a.type(),a.normalBalance(),a.kind(),a.purpose(),a.parentId(),a.active(),form.getNameSw(),form.getDescription()):proposed,form.getReason(),0),complete);
    }
    private Version newDraft(AppUserPrincipal actor,UUID account,UUID base,String op,Account before,Account proposed,UUID key,String hash) {
        return new Version(UUID.randomUUID(),account,books.nextVersion(actor.getSaccoId(),account),base,op,"DRAFT",before,proposed,actor.getMemberId(),"",actor.getStationId(),"",0,0,"DRAFT",null,null,"",clock.now(),null,null,key,hash);
    }
    @Transactional
    public UUID reviseAccountVersion(AppUserPrincipal actor,UUID id,UUID key,EditForm form,boolean complete) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");books.lockAccounts(actor.getSaccoId());var old=findVersion(actor,id);
        require(owner(actor,old) && Set.of("REJECTED","WITHDRAWN").contains(old.state()),"versionTransition");
        requireActor(actor,old.operation().equals("CREATE")?"ACCOUNTING_ACCOUNTS_CREATE":"ACCOUNTING_ACCOUNTS_UPDATE");
        String hash=requestHash(Arrays.asList(id,form.getName(),form.getNameSw(),form.getDescription(),form.getReason(),complete));
        var prior=books.versionRequest(actor.getSaccoId(),key);if(prior.isPresent()){require(owner(actor,prior.get()) && prior.get().requestHash().equals(hash),"versionRetry");return prior.get().id();}
        require(key!=null && !books.openVersion(actor.getSaccoId(),old.accountId()),"versionOpen");
        var current=books.currentVersion(actor.getSaccoId(),old.accountId());var before=current.map(Version::proposed).orElse(null);
        require(old.operation().equals("CREATE")?current.isEmpty():current.isPresent(),"versionStale");
        var source=old.proposed();var a=before==null?source:before;
        var proposed=new Account(a.id(),a.code(),source.name(),a.type(),a.normalBalance(),a.kind(),a.purpose(),a.parentId(),old.operation().equals("REACTIVATE") || !old.operation().equals("DEACTIVATE") && a.active(),source.nameSw(),source.description());
        var v=newDraft(actor,old.accountId(),current.map(Version::id).orElse(null),old.operation(),before,proposed,key,hash);books.createVersion(actor.getSaccoId(),v);
        form.setRevision(0);return saveAccountVersion(actor,v.id(),form,complete);
    }
    private static EditForm versionForm(Account a,String reason,int revision) {var f=metadataForm(a);f.setReason(reason);f.setRevision(revision);return f;}
    @Transactional
    public UUID saveAccountVersion(AppUserPrincipal actor,UUID id,EditForm form,boolean complete) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");books.lockAccounts(actor.getSaccoId());var v=findVersion(actor,id);requireActor(actor,v.operation().equals("CREATE")?"ACCOUNTING_ACCOUNTS_CREATE":"ACCOUNTING_ACCOUNTS_UPDATE");
        require(owner(actor,v) && v.state().equals("DRAFT"),"versionTransition");require(v.revision()==form.getRevision(),"versionStale");
        require(form.getReason()!=null && form.getReason().length()<=500 && form.getName()!=null && form.getName().length()<=160 && (form.getNameSw()==null || form.getNameSw().length()<=160) && (form.getDescription()==null || form.getDescription().length()<=500),"versionInput");
        var a=v.proposed();boolean metadata=Set.of("CREATE","UPDATE").contains(v.operation());
        var proposed=new Account(a.id(),a.code(),metadata?trim(form.getName()):a.name(),a.type(),a.normalBalance(),a.kind(),a.purpose(),a.parentId(),a.active(),metadata?trim(form.getNameSw()):a.nameSw(),metadata?trim(form.getDescription()):a.description());
        books.saveVersionDraft(actor.getSaccoId(),v,proposed,trim(form.getReason()));v=books.version(actor.getSaccoId(),id).orElseThrow();
        versionEvent(actor,v,"DRAFT_SAVED",v.reason());
        if(complete) {
            text(v.reason(),500,"versionReason");validateVersion(actor,v);
            var p=books.accountPolicy(actor.getSaccoId());boolean automatic=p.automatic(v.operation());
            books.decideVersion(actor.getSaccoId(),v,automatic?"APPROVED":"PENDING",p.version(),automatic?"AUTOMATIC":"INDEPENDENT",null,"",clock.now());
            v=books.version(actor.getSaccoId(),id).orElseThrow();
            if(automatic)books.applyAccountVersion(actor.getSaccoId(),v);
            versionEvent(actor,v,automatic?"AUTOMATIC_APPROVAL":"SUBMITTED",v.reason());
        }
        return id;
    }
    private void validateVersion(AppUserPrincipal actor,Version v) {
        var a=v.proposed();validateAccount(actor,new AccountCommand(a.code(),a.name(),a.type(),a.normalBalance(),a.kind(),a.purpose(),a.parentId(),a.nameSw(),a.description()));
        var current=books.currentVersion(actor.getSaccoId(),v.accountId());
        if(v.operation().equals("CREATE")) {
            require(current.isEmpty() && books.accountCode(actor.getSaccoId(),a.code()).isEmpty(),"duplicate");
            if(a.code().matches("[1-5][0-9]{5}"))onboardingParent(actor,a.parentId(),a.kind().equals("HEADING"));
        } else {
            require(current.isPresent() && current.get().id().equals(v.baseId()),"versionStale");
            if(v.operation().equals("DEACTIVATE")) {require(!mainGroup(a),"mainGroupProtected");require(current.get().proposed().active(),"versionTransition");require(!books.activeChildren(actor.getSaccoId(),a.id()),"activeChildren");}
            if(v.operation().equals("REACTIVATE"))require(!current.get().proposed().active(),"versionTransition");
            if(v.operation().equals("UPDATE"))require(!trim(a.name()).equals(trim(v.before().name())) || !trim(a.nameSw()).equals(trim(v.before().nameSw())) || !trim(a.description()).equals(trim(v.before().description())),"versionUnchanged");
        }
    }
    @Transactional
    public void accountVersionAction(AppUserPrincipal actor,UUID id,int revision,String action,String reason) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");books.lockAccounts(actor.getSaccoId());var v=findVersion(actor,id);
        require(Set.of("APPROVE","REJECT","WITHDRAW").contains(action),"versionInput");
        String target=switch(action){case "APPROVE"->"APPROVED";case "REJECT"->"REJECTED";default->"WITHDRAWN";};
        if(action.equals("WITHDRAW"))requireActor(actor,v.operation().equals("CREATE")?"ACCOUNTING_ACCOUNTS_CREATE":"ACCOUNTING_ACCOUNTS_UPDATE");
        else {requireActor(actor,"ACCOUNTING_ACCOUNT_APPROVAL_VIEW");requireActor(actor,"ACCOUNTING_ACCOUNT_APPROVAL_"+action);require(!v.creator().equals(actor.getMemberId()),"versionSelfApproval");}
        if(v.state().equals(target) && v.revision()==revision+1 && (action.equals("WITHDRAW")?owner(actor,v):Objects.equals(v.reviewer(),actor.getMemberId())) && Objects.equals(v.decisionReason(),trim(reason)))return;
        require(v.revision()==revision,"versionStale");
        require(action.equals("WITHDRAW")?owner(actor,v) && Set.of("DRAFT","PENDING").contains(v.state()):v.state().equals("PENDING"),"versionTransition");
        text(reason,500,"versionReason");
        if(action.equals("APPROVE")){require(v.policyVersion()==books.accountPolicy(actor.getSaccoId()).version(),"versionPolicyChanged");validateVersion(actor,v);}
        books.decideVersion(actor.getSaccoId(),v,target,v.policyVersion(),v.mode(),action.equals("WITHDRAW")?null:actor.getMemberId(),reason.strip(),clock.now());
        v=books.version(actor.getSaccoId(),id).orElseThrow();if(action.equals("APPROVE"))books.applyAccountVersion(actor.getSaccoId(),v);
        versionEvent(actor,v,action,reason.strip());
    }
    @Transactional(readOnly=true)
    public Page<Version> accountVersionRegister(AppUserPrincipal actor,String state,String search,String sort,String direction,int page,int size) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");page(page);require(Set.of(10,25,30,50).contains(size) && search!=null && search.length()<=100 && Set.of("","DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN").contains(state),"versionInput");
        boolean all=institutionReview(actor);var rows=books.versionRegister(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),all,state,search,sort,direction,page,size);
        return new Page<>(rows.stream().limit(size).toList(),page,rows.size()>size);
    }
    @Transactional(readOnly=true)
    public Details accountVersionDetails(AppUserPrincipal actor,UUID id,int historyPage,int eventPage) {
        page(historyPage);page(eventPage);var v=findVersion(actor,id);var actions=new LinkedHashSet<String>();
        if(owner(actor,v)) {
            String claim=v.operation().equals("CREATE")?"ACCOUNTING_ACCOUNTS_CREATE":"ACCOUNTING_ACCOUNTS_UPDATE";
            if(access.has(actor,claim)) {requireActor(actor,claim);if(v.state().equals("DRAFT"))actions.add("EDIT");if(Set.of("DRAFT","PENDING").contains(v.state()))actions.add("WITHDRAW");if(Set.of("REJECTED","WITHDRAWN").contains(v.state()))actions.add("REVISE");}
        }
        if(v.state().equals("PENDING") && !v.creator().equals(actor.getMemberId()) && institutionReview(actor)) {
            for(String act:List.of("APPROVE","REJECT"))if(access.has(actor,"ACCOUNTING_ACCOUNT_APPROVAL_"+act)){requireActor(actor,"ACCOUNTING_ACCOUNT_APPROVAL_"+act);actions.add(act);}
        }
        var history=books.accountVersions(actor.getSaccoId(),v.accountId(),historyPage,actor.getMemberId(),actor.getStationId(),institutionReview(actor));var events=books.accountVersionEvents(actor.getSaccoId(),id,eventPage);
        String parent=v.proposed().parentId()==null?"":books.account(actor.getSaccoId(),v.proposed().parentId()).map(a->a.code()+" · "+a.name()).orElse("");
        return new Details(v,books.currentVersion(actor.getSaccoId(),v.accountId()).map(Version::id).orElse(null),parent,Set.copyOf(actions),history.stream().limit(25).toList(),historyPage,history.size()>25,events.stream().limit(25).toList(),eventPage,events.size()>25);
    }
    private boolean owner(AppUserPrincipal actor,Version v) {return v.creator().equals(actor.getMemberId()) && Objects.equals(v.branch(),actor.getStationId());}
    private boolean institutionReview(AppUserPrincipal actor) {
        if(!access.has(actor,"ACCOUNTING_ACCOUNT_APPROVAL_VIEW"))return false;requireActor(actor,"ACCOUNTING_ACCOUNT_APPROVAL_VIEW");return true;
    }
    private Version findVersion(AppUserPrincipal actor,UUID id) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");var v=books.version(actor.getSaccoId(),id).orElseThrow(()->new AccessDeniedException("Account version unavailable"));
        if(!Set.of("BASELINE","APPROVED").contains(v.state()) && !owner(actor,v) && !institutionReview(actor))throw new AccessDeniedException("Institution review permission required");return v;
    }
    private void versionEvent(AppUserPrincipal actor,Version v,String action,String reason) {
        books.accountVersionEvent(actor.getSaccoId(),v,action,actor.getMemberId(),actor.getStationId(),reason,clock.now());
        var before=new LinkedHashMap<String,Object>();before.put("account",v.before());
        var after=new LinkedHashMap<String,Object>();after.put("account",v.proposed());after.put("versionId",v.id());after.put("version",v.number());after.put("state",v.state());after.put("mode",v.mode());after.put("policyVersion",v.policyVersion());after.put("reason",reason);
        after.put("saccoId",actor.getSaccoId());after.put("stationId",actor.getStationId());after.put("auditReferenceType","ACCOUNT");after.put("auditReferenceValue",v.proposed().code());
        audit.log("ACCOUNT",v.accountId(),"ACCOUNT_VERSION_"+action,actor.getMemberId(),before,after);
    }
    private static String requestHash(Object value) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(GeneralLedgerRepository.json(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public void requireActor(AppUserPrincipal actor,String claim) {
        requireScope(actor);if(!access.has(actor,claim))throw new AccessDeniedException("Accounting permission required");
        var current=directory.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Accounting access unavailable"));
        if(!current.isStaffAccessActive() || current.getStatus()!=MemberStatus.ACTIVE || current.getActiveStaffRolesResolved().contains(Position.ADMIN)
            || (claim.startsWith("LOAN_RECORDING_") && !current.getActiveStaffRolesResolved().contains(Position.ACCOUNTANT))
            || !Objects.equals(current.getSaccoId(),actor.getSaccoId()) || !Objects.equals(current.getStationId(),actor.getStationId())
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
        audit.logEvent("ACCOUNT",id,action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"ACCOUNT",String.valueOf(details.getOrDefault("accountCode",id)),actor.getSaccoId(),actor.getStationId(),details);
    }
    private static int page(int page) {require(page>=0 && page<=10000,"page");return page;}
    private static void text(String value,int max,String key) {require(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->c<32),key);}
    private static void require(boolean condition,String key) {if(!condition)throw invalid(key);}
    private static IllegalArgumentException invalid(String key) {return new IllegalArgumentException("accounting.error."+key);}
}
