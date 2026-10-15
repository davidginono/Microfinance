package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.dto.AccountOnboardingForm;
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
        requireActor(actor,"ACCOUNTING_ACCOUNTS_CREATE");books.lockAccounts(actor.getSaccoId());
        var parent=onboardingParent(actor,form.getParentId(),group);
        String code=trim(form.getCode());
        boolean validCode=group
            ? (mainGroup(parent)?code.matches(parent.code().substring(0,1)+"[1-9]0000"):code.matches(parent.code().substring(0,2)+"[1-9]000"))
            : code.matches(parent.code().substring(0,3)+"[0-9]{3}") && !code.endsWith("000");
        require(validCode,"chartCode");require(books.accountCode(actor.getSaccoId(),code).isEmpty(),"duplicate");
        String kind=group?"HEADING":form.getKind(),purpose=group?"OTHER":form.getPurpose();
        require(group || Set.of("POSTING","CONTROL").contains(kind),"accountClassification");
        return persistAccount(actor,new AccountCommand(code,trim(form.getName()),parent.type(),group?normalBalance(parent.type()):form.getNormalBalance(),kind,purpose,parent.id(),trim(form.getNameSw()),trim(form.getDescription())));
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
        books.lockAccounts(actor.getSaccoId());return persistAccount(actor,c);
    }
    private UUID persistAccount(AppUserPrincipal actor,AccountCommand c) {
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
        UUID id=UUID.randomUUID(); books.createAccount(actor.getSaccoId(),id,c,actor.getMemberId(),clock.now());
        event(actor,id,"ACCOUNT_CREATED",Map.of("accountCode",c.code()));return id;
    }
    @Transactional
    public void deactivateAccount(AppUserPrincipal actor,UUID id) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");
        books.lockAccounts(actor.getSaccoId());
        var account=books.account(actor.getSaccoId(),id).orElseThrow(()->invalid("account"));
        require(!mainGroup(account),"mainGroupProtected");
        require(!books.activeChildren(actor.getSaccoId(),id),"activeChildren");
        books.deactivate(actor.getSaccoId(),id);event(actor,id,"ACCOUNT_DEACTIVATED",Map.of());
    }
    @Transactional
    public void reactivateAccount(AppUserPrincipal actor,UUID id) {
        requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");books.lockAccounts(actor.getSaccoId());
        var account=books.account(actor.getSaccoId(),id).orElseThrow(()->invalid("account"));
        if(account.parentId()!=null) require(books.account(actor.getSaccoId(),account.parentId()).map(Account::active).orElse(false),"parent");
        books.reactivate(actor.getSaccoId(),id);event(actor,id,"ACCOUNT_REACTIVATED",Map.of());
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
        audit.logEvent("ACCOUNTING",id,action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"JOURNAL",id.toString(),actor.getSaccoId(),actor.getStationId(),details);
    }
    private static int page(int page) {require(page>=0 && page<=10000,"page");return page;}
    private static void text(String value,int max,String key) {require(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->c<32),key);}
    private static void require(boolean condition,String key) {if(!condition)throw invalid(key);}
    private static IllegalArgumentException invalid(String key) {return new IllegalArgumentException("accounting.error."+key);}
}
