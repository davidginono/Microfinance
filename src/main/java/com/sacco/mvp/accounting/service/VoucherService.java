package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.accounting.statements.service.EquityMovementService;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Movement;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.MovementOption;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Classification;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class VoucherService {
    private final VoucherRepository vouchers;
    private final GeneralLedgerService ledger;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final JsonMapper json;
    private final com.sacco.mvp.accounting.approval.service.VoucherApprovalGate approvals;
    private final EquityMovementService equityMovements;
    private static final Set<String> COMPONENTS=Set.of("TOTAL","PRINCIPAL","INTEREST","FEES","TAX");
    public void authorize(AppUserPrincipal actor,Type type,String action){
        ledger.requireActor(actor,(type==Type.JOURNAL?"ACCOUNTING_JOURNALS_":"ACCOUNTING_BUSINESS_")+action);
    }
    public List<Type> creatableTypes(AppUserPrincipal actor){
        return Arrays.stream(Type.values()).filter(type->{try{authorize(actor,type,"CREATE");return true;}catch(AccessDeniedException denied){return false;}}).toList();
    }
    @Transactional
    public NumberReservation reserveNumber(AppUserPrincipal actor,Type type,LocalDate date,UUID requestKey){
        authorize(actor,type,"CREATE");require(requestKey!=null,"","request");require(date!=null && date.getYear()>=1 && !date.isAfter(clock.today()),"effectiveDate","date");
        vouchers.lockRequest(actor.getSaccoId(),actor.getStationId(),requestKey);
        return vouchers.reserveNumber(actor.getSaccoId(),actor.getStationId(),type,date,requestKey,clock.now());
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void consumeNumber(AppUserPrincipal actor,Type type,UUID requestKey){
        authorize(actor,type,"CREATE");vouchers.consumeNumber(actor.getSaccoId(),actor.getStationId(),requestKey,clock.now());
    }
    @Transactional
    public NumberReservation reserveCorrectionNumber(AppUserPrincipal actor,Type type,LocalDate date,UUID requestKey){
        authorize(actor,type,"REVERSE");require(requestKey!=null,"","request");require(date!=null && date.getYear()>=1 && !date.isAfter(clock.today()),"effectiveDate","date");
        vouchers.lockRequest(actor.getSaccoId(),actor.getStationId(),requestKey);
        return vouchers.reserveNumber(actor.getSaccoId(),actor.getStationId(),type,date,requestKey,clock.now());
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void consumeCorrectionNumber(AppUserPrincipal actor,Type type,UUID requestKey){
        authorize(actor,type,"REVERSE");vouchers.consumeNumber(actor.getSaccoId(),actor.getStationId(),requestKey,clock.now());
    }
    public void requireExport(AppUserPrincipal actor){ledger.requireActor(actor,"FINANCIAL_REPORTS_EXPORT");}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireInterestWaiverPeriod(AppUserPrincipal actor,LocalDate date){
        ledger.requireActor(actor,"LOAN_INTEREST_WAIVE");vouchers.period(actor.getSaccoId(),date,actor.getMemberId(),clock.now());
    }
    @Transactional(readOnly=true)
    public Page list(AppUserPrincipal actor,Type type,Filter filter){
        authorize(actor,type,"VIEW");validateFilter(filter);
        var rows=vouchers.list(actor.getSaccoId(),actor.getStationId(),type,filter);
        return new Page(rows.stream().limit(filter.size()).toList(),filter.page(),rows.size()>filter.size());
    }
    @Transactional(readOnly=true)
    public Voucher view(AppUserPrincipal actor,Type type,UUID id){authorize(actor,type,"VIEW");return details(scoped(actor,type,id,false));}
    @Transactional(readOnly=true)
    public Voucher export(AppUserPrincipal actor,Type type,UUID id){
        authorize(actor,type,"VIEW");ledger.requireActor(actor,"FINANCIAL_REPORTS_EXPORT");return details(scoped(actor,type,id,false));
    }
    @Transactional(readOnly=true)
    public List<Account> accounts(AppUserPrincipal actor,Type type,String search,int page){authorize(actor,type,"CREATE");query(search,page);return accounts(actor,type,search,page,"");}
    @Transactional(readOnly=true)
    public List<Account> accounts(AppUserPrincipal actor,Type type,String search,int page,String purpose){
        authorize(actor,type,"CREATE");query(search,page);require(Set.of("","MONEY","OTHER","EXPENSE").contains(purpose),"","filter");
        return vouchers.accounts(actor.getSaccoId(),search,page*25,purpose);
    }
    @Transactional(readOnly=true)
    public List<Client> clients(AppUserPrincipal actor,Type type,String search,int page){authorize(actor,type,"CREATE");query(search,page);return vouchers.clients(actor.getSaccoId(),actor.getStationId(),search,page*25);}
    @Transactional(readOnly=true)
    public Optional<Client> selectedClient(AppUserPrincipal actor,Type type,UUID id){authorize(actor,type,"CREATE");return id==null?Optional.empty():vouchers.client(actor.getSaccoId(),actor.getStationId(),id,false);}
    @Transactional(readOnly=true)
    public Optional<Client> reviewClient(AppUserPrincipal actor,Type type,UUID id){approvals.authorize(actor,"VOUCHER_WORKSPACE_VIEW");authorize(actor,type,"VIEW");return id==null?Optional.empty():vouchers.client(actor.getSaccoId(),actor.getStationId(),id,false);}
    @Transactional(readOnly=true)
    public List<Mapping> mappings(AppUserPrincipal actor,Type type,String search,int page){authorize(actor,type,"CREATE");query(search,page);return vouchers.mappings(actor.getSaccoId(),search,page*25);}
    @Transactional(readOnly=true)
    public List<Mapping> mappings(AppUserPrincipal actor,Type type,UUID activity,String search,int page){authorize(actor,type,"CREATE");query(search,page);return vouchers.mappings(actor.getSaccoId(),activity,search,page*25);}
    @Transactional(readOnly=true)
    public List<Mapping> mappingsForActivities(AppUserPrincipal actor,Type type,List<UUID> activities,String search,int page){
        authorize(actor,type,"CREATE");query(search,page);validateActivityIds(activities);
        return vouchers.mappingsForActivities(actor.getSaccoId(),activities,search,page*25);
    }
    @Transactional(readOnly=true)
    public List<Activity> activities(AppUserPrincipal actor,Type type,String search,int page){authorize(actor,type,"CREATE");query(search,page);return vouchers.activities(actor.getSaccoId(),search,page*25);}
    @Transactional(readOnly=true)
    public Map<UUID,Activity> selectedActivities(AppUserPrincipal actor,Type type,Form form){
        authorize(actor,type,"CREATE");return activityLabels(actor,form);
    }
    @Transactional(readOnly=true)
    public Map<UUID,Activity> reviewActivities(AppUserPrincipal actor,Type type,Form form){
        approvals.authorize(actor,"VOUCHER_WORKSPACE_VIEW");authorize(actor,type,"VIEW");
        return activityLabels(actor,form);
    }
    private Map<UUID,Activity> activityLabels(AppUserPrincipal actor,Form form){
        var ids=form.selectedActivityIds().stream().limit(com.sacco.mvp.accounting.dto.VoucherDtos.MAX_ACTIVITIES)
            .filter(Objects::nonNull).distinct().toList();
        var result=new HashMap<UUID,Activity>();
        vouchers.selectedActivities(actor.getSaccoId(),ids).forEach(activity->result.put(activity.id(),activity));
        return result;
    }
    @Transactional(readOnly=true)
    public void validateDraftActivities(AppUserPrincipal actor,Type type,Form form){
        authorize(actor,type,"CREATE");validateActivitySelection(form);checkActivityScope(actor,form);
    }
    @Transactional(readOnly=true)
    public Map<Integer,Mapping> selectedMappings(AppUserPrincipal actor,Type type,Form form){
        authorize(actor,type,"CREATE");return mappingsByRow(actor,form);
    }
    private Map<Integer,Mapping> mappingsByRow(AppUserPrincipal actor,Form form){
        if(form.getRows()==null)return Map.of();
        var rows=form.getRows().stream().limit(50).toList();
        var ids=rows.stream().filter(Objects::nonNull).map(RowForm::getTransactionId).filter(Objects::nonNull).distinct().toList();
        var mappings=vouchers.selectedMappings(actor.getSaccoId(),ids);
        var result=new HashMap<Integer,Mapping>();
        for(int n=0;n<rows.size();n++){
            var row=rows.get(n);
            if(row==null || row.getTransactionId()==null)continue;
            var selected=mappings.stream().filter(m->m.transactionId().equals(row.getTransactionId())
                && m.templateKey().equals(row.getTemplateKey()) && m.component().equals(row.getComponent())).findFirst();
            if(selected.isPresent())result.put(n,selected.get());
        }
        return result;
    }
    @Transactional(readOnly=true)
    public Map<UUID,Account> selectedAccounts(AppUserPrincipal actor,Type type,Form form){
        authorize(actor,type,"CREATE");var ids=new TreeSet<UUID>();
        if(form.getMoneyAccountId()!=null)ids.add(form.getMoneyAccountId());
        if(form.getRows()!=null)for(var row:form.getRows()){if(row!=null){if(row.getDebitAccountId()!=null)ids.add(row.getDebitAccountId());if(row.getCreditAccountId()!=null)ids.add(row.getCreditAccountId());if(row.getEntries()!=null)for(var entry:row.getEntries())if(entry!=null && entry.getAccountId()!=null)ids.add(entry.getAccountId());}}
        if(ids.size()>501)throw new Invalid("","rows");var result=new HashMap<UUID,Account>();vouchers.selectedAccounts(actor.getSaccoId(),new ArrayList<>(ids),false).forEach(a->result.put(a.id(),a));return result;
    }
    @Transactional(readOnly=true)
    public ReviewLines reviewLines(AppUserPrincipal actor,Type type,Form form){
        approvals.authorize(actor,"VOUCHER_WORKSPACE_VIEW");authorize(actor,type,"VIEW");
        require(form.getRows()!=null && form.getRows().size()<=50,"","rows");
        var ids=new TreeSet<UUID>();if(form.getMoneyAccountId()!=null)ids.add(form.getMoneyAccountId());
        for(var row:form.getRows()){if(row.getDebitAccountId()!=null)ids.add(row.getDebitAccountId());if(row.getCreditAccountId()!=null)ids.add(row.getCreditAccountId());if(row.getEntries()!=null)for(var entry:row.getEntries())if(entry!=null && entry.getAccountId()!=null)ids.add(entry.getAccountId());}
        require(ids.size()<=501,"","rows");var byId=new HashMap<UUID,Account>();vouchers.selectedAccounts(actor.getSaccoId(),new ArrayList<>(ids),false).forEach(a->byId.put(a.id(),a));
        var mappings=mappingsByRow(actor,form);
        var result=new HashMap<String,Account>();if(byId.containsKey(form.getMoneyAccountId()))result.put("moneyAccountId",byId.get(form.getMoneyAccountId()));
        for(int n=0;n<form.getRows().size();n++){
            var row=form.getRows().get(n);Account debit=byId.get(row.getDebitAccountId()),credit=byId.get(row.getCreditAccountId());
            if(row.getTransactionId()!=null){var mapping=mappings.get(n);debit=mapping==null?null:mapping.debit();credit=mapping==null?null:mapping.credit();}
            if(row.getEntries()!=null)for(int e=0;e<row.getEntries().size();e++){var entry=row.getEntries().get(e);if(entry!=null && byId.containsKey(entry.getAccountId()))result.put("rows["+n+"].entries["+e+"].accountId",byId.get(entry.getAccountId()));}
            if(debit!=null)result.put("rows["+n+"].debitAccountId",debit);if(credit!=null)result.put("rows["+n+"].creditAccountId",credit);
        }
        return new ReviewLines(result,mappings);
    }
    @Transactional
    public Preview preview(AppUserPrincipal actor,Type type,Form form){authorize(actor,type,"CREATE");authorizeEquitySelection(actor,form);validate(form,type,clock.today());return plan(actor,type,form);}
    public void authorizeEquitySelection(AppUserPrincipal actor,Form form){if(form!=null && form.isEquity())equityMovements.authorize(actor);}
    public List<MovementOption> equityOptions(AppUserPrincipal actor,Type type,Form form){
        if(type!=Type.JOURNAL||form.isPettyCash()||!hasEquityClaims(actor))return List.of();
        return equityMovements.options(actor,form.getEquityRow(),true);
    }
    public List<MovementOption> reviewEquityOptions(AppUserPrincipal actor,Type type,Form recorded,Form submitted,boolean editable){
        if(type!=Type.JOURNAL||!recorded.isEquity())return List.of();
        if(editable&&hasEquityClaims(actor))return equityOptions(actor,type,submitted);
        return recorded.getEquityRow()==null?List.of():equityMovements.options(actor,recorded.getEquityRow(),false);
    }
    private static boolean hasEquityClaims(AppUserPrincipal actor){return actor!=null&&actor.getClaims().containsAll(Set.of("FINANCIAL_REPORTS_VIEW","FINANCIAL_REPORTS_CONFIGURE"));}
    @Transactional(noRollbackFor=IllegalArgumentException.class)
    public Preview approvalPreview(AppUserPrincipal actor,Type type,Form form){
        approvals.authorize(actor,"VOUCHER_WORKSPACE_VIEW");
        authorize(actor,type,"VIEW");
        String claim=java.util.stream.Stream.of("VOUCHER_WORKSPACE_VALIDATE","VOUCHER_APPROVAL_APPROVE","VOUCHER_APPROVAL_ESCALATED_APPROVE","VOUCHER_APPROVAL_FORCE","VOUCHER_APPROVAL_INITIATE","VOUCHER_WORKSPACE_POST")
            .filter(actor.getClaims()::contains).findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Voucher validation permission required"));
        approvals.authorize(actor,claim);validate(form,type,clock.today());return plan(actor,type,form);
    }
    @Transactional
    public Voucher post(AppUserPrincipal actor,Type type,Form form){
        authorize(actor,type,"CREATE");authorizeEquitySelection(actor,form);validate(form,type,clock.today());
        String hash=hash(type,form);
        var completed=vouchers.byRequest(actor.getSaccoId(),actor.getStationId(),form.getRequestKey());
        if(completed.isPresent()){require(completed.get().type()==type && vouchers.sameRequest(completed.get().id(),hash,actor.getMemberId()),"","retry");return details(completed.get());}
        var approvalProof=approvals.beforePosting(actor,type,form);
        vouchers.lockRequest(actor.getSaccoId(),actor.getStationId(),form.getRequestKey());
        require(!vouchers.loanRequestExists(actor.getSaccoId(),actor.getStationId(),form.getRequestKey()),"","retry");
        var prior=vouchers.byRequest(actor.getSaccoId(),actor.getStationId(),form.getRequestKey());
        if(prior.isPresent()){
            require(prior.get().type()==type && vouchers.sameRequest(prior.get().id(),hash,actor.getMemberId()),"","retry");return details(prior.get());
        }
        UUID period=vouchers.period(actor.getSaccoId(),form.getEffectiveDate(),actor.getMemberId(),clock.now());
        var plan=plan(actor,type,form);
        approvals.verifyPlan(approvalProof,plan);
        return persist(actor,type,form,hash,plan,null,null,period,client(actor,form));
    }
    @Transactional
    public Voucher reverse(AppUserPrincipal actor,Type type,UUID originalId,UUID key,LocalDate date,String reason){
        authorize(actor,type,"REVERSE");require(key!=null,"","request");text(reason,500,true,"description");
        require(date!=null && !date.isAfter(clock.today()),"effectiveDate","date");
        vouchers.lockRequest(actor.getSaccoId(),actor.getStationId(),key);
        var original=details(scoped(actor,type,originalId,true));
        var f=new Form();f.setRequestKey(key);f.setEffectiveDate(date);f.setParty(original.party());f.setReference(original.number());
        f.setClientId(original.clientId());
        f.setDescription(reason.strip());f.setEvidence(original.evidence());f.setMoneyAccountId(original.moneyAccountId());f.setRows(List.of());
        String hash=hash(type,f);
        var prior=vouchers.byRequest(actor.getSaccoId(),actor.getStationId(),key);
        if(prior.isPresent()){require(Objects.equals(prior.get().reversesId(),originalId) && vouchers.sameRequest(prior.get().id(),hash,actor.getMemberId()),"","retry");return details(prior.get());}
        require(original.reversesId()==null && original.reversedBy()==null && !date.isBefore(original.effectiveDate()),"","reversal");
        UUID period=vouchers.period(actor.getSaccoId(),date,actor.getMemberId(),clock.now());
        var ids=original.transactions().stream().flatMap(t->t.postings().stream().map(e->e.account().id())).distinct().sorted().toList();
        require(vouchers.lockAccounts(actor.getSaccoId(),ids).size()==ids.size(),"","account");
        var rows=original.transactions().stream().map(t->new Transaction(UUID.randomUUID(),t.lineNo(),t.transactionId(),t.templateKey(),t.transactionName(),t.component(),t.description(),t.amount(),t.credit(),t.debit(),t.split()?t.postings().stream().map(Entry::reversed).toList():List.of())).toList();
        return persist(actor,type,f,hash,new Preview(rows,original.total()),original.id(),original.journalId(),period,original.clientId()==null?null:new Client(original.clientId(),original.clientNumber(),""));
    }
    private Voucher persist(AppUserPrincipal actor,Type type,Form f,String hash,Preview plan,UUID reverses,UUID journal,UUID period,Client client){
        var now=clock.now();
        String number=reverses==null?reserveNumber(actor,type,f.getEffectiveDate(),f.getRequestKey()).number():vouchers.number(type,f.getEffectiveDate());
        UUID moneyId=f.getMoneyAccountId();
        if(type!=Type.JOURNAL && reverses==null)moneyId=plan.rows().stream().flatMap(t->t.postings().stream()).filter(e->e.side().equals(type==Type.RECEIPT?"DEBIT":"CREDIT")).map(e->e.account().id()).findFirst().orElseThrow();
        var v=new Voucher(UUID.randomUUID(),number,type,f.getEffectiveDate(),f.getParty().strip(),f.getReference().strip(),f.getDescription().strip(),f.getEvidence().strip(),moneyId,plan.total(),plan.rows().size(),UUID.randomUUID(),actor.getMemberId(),"",now,reverses,null,"","",plan.rows(),client==null?null:client.id(),client==null?null:client.number());
        vouchers.insert(v,actor.getSaccoId(),actor.getStationId(),f,hash,period,journal);
        if(reverses!=null)equityMovements.inheritReversal(actor,type,journal,v.journalId(),f.getDescription());
        else if(plan.equityClassification()!=null)equityMovements.classifyPosted(actor,v.journalId(),plan.equityClassification(),f.getEquityReason());
        if(reverses==null)consumeNumber(actor,type,f.getRequestKey());
        audit.logEvent("ACCOUNTING",v.id(),reverses==null?"VOUCHER_POSTED":"VOUCHER_REVERSED",actor.getMemberId(),AuditEventStatus.SUCCESS,"Voucher posted","VOUCHER",v.number(),actor.getSaccoId(),actor.getStationId(),Map.of("type",type.name(),"amount",v.total(),"transactions",v.transactionCount(),"journal",v.journalId()));
        return details(scoped(actor,type,v.id(),false));
    }
    private Preview plan(AppUserPrincipal actor,Type type,Form f){
        client(actor,f);
        checkActivityScope(actor,f);
        var transactionIds=f.getRows().stream().map(RowForm::getTransactionId).filter(Objects::nonNull).distinct().sorted().toList();
        var mappings=transactionIds.isEmpty()?List.<Mapping>of():vouchers.lockMappings(actor.getSaccoId(),transactionIds,f.selectedActivityIds());
        var accountIds=new TreeSet<UUID>();if(f.getMoneyAccountId()!=null)accountIds.add(f.getMoneyAccountId());
        for(var r:f.getRows())if(r.getTransactionId()==null){if(r.getDebitAccountId()!=null)accountIds.add(r.getDebitAccountId());if(r.getCreditAccountId()!=null)accountIds.add(r.getCreditAccountId());if(r.getEntries()!=null)for(var e:r.getEntries())if(e.getAccountId()!=null)accountIds.add(e.getAccountId());}
        require(!accountIds.isEmpty() || !mappings.isEmpty(),"rows[0].amount","account");
        var accounts=new HashMap<UUID,Account>();if(!accountIds.isEmpty())vouchers.lockAccounts(actor.getSaccoId(),new ArrayList<>(accountIds)).forEach(a->accounts.put(a.id(),a));
        Account money=accounts.get(f.getMoneyAccountId());
        require(type==Type.JOURNAL || f.getRows().stream().allMatch(r->r.getEntries()!=null && !r.getEntries().isEmpty()) || money!=null && money.moneyEligible(),"moneyAccountId","moneyAccount");
        var result=new ArrayList<Transaction>();BigDecimal total=new BigDecimal("0.00");int n=0;
        for(var row:f.getRows()){
            String field="rows["+n+"]";Account debit,credit;String name="";UUID template=null;
            if(row.getEntries()!=null && !row.getEntries().isEmpty()){
                var entries=new ArrayList<Entry>();BigDecimal debits=BigDecimal.ZERO,credits=BigDecimal.ZERO;
                var debitIds=new HashSet<UUID>();var creditIds=new HashSet<UUID>();
                for(int e=0;e<row.getEntries().size();e++){
                    var input=row.getEntries().get(e);String path=field+".entries["+e+"]";var account=accounts.get(input.getAccountId());
                    require(account!=null,path+".accountId","account");boolean isDebit="DEBIT".equals(input.getSide());
                    if(type!=Type.JOURNAL){boolean moneySide=type==Type.RECEIPT?isDebit:!isDebit;
                        require(moneySide==account.moneyEligible(),path+".accountId",moneySide?"moneyAccount":"transfer");}
                    var value=input.getAmount().setScale(2,RoundingMode.UNNECESSARY);
                    require((isDebit?debitIds:creditIds).add(account.id()),path+".accountId","account");
                    if(isDebit)debits=debits.add(value);else credits=credits.add(value);
                    entries.add(new Entry(input.getSide(),account,value));
                }
                require(Collections.disjoint(debitIds,creditIds),field+".entries","account");
                require(debits.compareTo(row.getAmount())==0 && credits.compareTo(row.getAmount())==0,field+".entries","balance");
                var amount=row.getAmount().setScale(2,RoundingMode.UNNECESSARY);total=total.add(amount);
                result.add(new Transaction(UUID.randomUUID(),++n,null,null,"","TOTAL",row.getDescription().strip(),amount,null,null,List.copyOf(entries)));continue;
            }
            if(row.getTransactionId()!=null){
                var matches=mappings.stream().filter(m->m.transactionId().equals(row.getTransactionId()) && m.component().equals(row.getComponent())).toList();
                require(matches.size()==1,field+".transactionId","template");var m=matches.getFirst();
                require(m.templateKey().equals(row.getTemplateKey()),field+".transactionId","staleTemplate");debit=m.debit();credit=m.credit();name=m.transactionName();template=m.templateKey();
            }else {debit=type==Type.RECEIPT?money:accounts.get(row.getDebitAccountId());credit=type==Type.PAYMENT?money:accounts.get(row.getCreditAccountId());}
            require(debit!=null,field+".debitAccountId","account");require(credit!=null,field+".creditAccountId","account");
            require(!debit.id().equals(credit.id()),field+".creditAccountId","account");
            require(type==Type.JOURNAL || (type==Type.RECEIPT?debit.id():credit.id()).equals(money.id()),field+".transactionId","direction");
            require(type==Type.JOURNAL || !(type==Type.RECEIPT?credit:debit).moneyEligible(),field+".transactionId","transfer");
            BigDecimal amount=row.getAmount().setScale(2,RoundingMode.UNNECESSARY);total=total.add(amount);
            result.add(new Transaction(UUID.randomUUID(),++n,row.getTransactionId(),template,name,row.getComponent(),row.getDescription().strip(),amount,debit,credit));
        }
        require(total.precision()<=18,"","amount");
        Classification classification=null;
        if(f.isEquity()){
            var selection=f.getEquityRow();
            var custom=selection==null?null:equityMovements.resolve(actor,selection)
                .orElseThrow(()->new Invalid("equityChoice","equityRow"));
            var category=custom==null?f.getEquityMovement():custom.category();
            var types=new HashMap<UUID,String>();var movements=new ArrayList<Movement>();
            for(var transaction:result)for(var entry:transaction.postings()){
                types.put(entry.account().id(),entry.account().type());boolean debit="DEBIT".equals(entry.side());
                movements.add(new Movement(entry.account().id(),category,false,debit?entry.amount():BigDecimal.ZERO,debit?BigDecimal.ZERO:entry.amount()));
            }
            try{EquityMovementService.validateMovement(category,movements,types);}
            catch(IllegalArgumentException invalid){throw new Invalid("equityChoice","equityAccounts");}
            classification=new Classification(category,custom,selection==null?null:selection.version());
        }
        var entries=new ArrayList<Map<String,Object>>();
        for(var transaction:result)for(var entry:transaction.postings())entries.add(Map.of("row",transaction.id(),"account",entry.account().id(),"side",entry.side(),"amount",entry.amount()));
        vouchers.validatePettyCash(actor.getSaccoId(),actor.getStationId(),f.getEffectiveDate(),type,json.writeValueAsString(entries));
        return new Preview(List.copyOf(result),total,classification);
    }
    private static void validateActivityIds(List<UUID> ids){
        require(ids!=null && ids.size()<=com.sacco.mvp.accounting.dto.VoucherDtos.MAX_ACTIVITIES
            && ids.stream().noneMatch(Objects::isNull) && new HashSet<>(ids).size()==ids.size(),"activityIds","activities");
    }
    private static void validateActivitySelection(Form form){
        var ids=form.selectedActivityIds();
        validateActivityIds(ids);
        require(form.getActivityId()==null || form.getActivityIds()==null || form.getActivityIds().isEmpty(),"activityIds","activities");
        require(!form.isEquity() || ids.isEmpty(),"activityIds","equityActivity");
    }
    private void checkActivityScope(AppUserPrincipal actor,Form form){
        var ids=form.getActivityIds();
        if(ids!=null && !ids.isEmpty())require(vouchers.selectedActivities(actor.getSaccoId(),ids).size()==ids.size(),"activityIds","activities");
    }
    static void validate(Form f,Type type,LocalDate today){
        require(f!=null && "OTHER".equals(f.getReceiptPurpose()) && f.getInterestAccountId()==null,"","request");
        validateActivitySelection(f);
        require(f.isEquity() || f.getEquityRow()==null && f.getEquityMovement()==null && (f.getEquityReason()==null || f.getEquityReason().isEmpty()),"equityChoice","equityActivity");
        if(f.isEquity()){
            require(!f.isPettyCash(),"equity","equityActivity");
            require(f.getEquityRow()==null?com.sacco.mvp.accounting.dto.VoucherDtos.equityTypes(type).contains(f.getEquityMovement()):
                    type==Type.JOURNAL&&f.getEquityMovement()==null&&f.getEquityRow().version()>0&&f.getEquityRow().id()!=null,
                "equityChoice","equityMovement");
            text(f.getEquityReason(),500,true,"equityReason");
            require(f.getRows()!=null && f.getRows().stream().allMatch(r->r!=null && r.getTransactionId()==null),"equity","equityActivity");
        }
        require(f.getRows()!=null && f.getRows().stream().allMatch(r->r!=null && r.getLoanId()==null),"","request");
        require(f!=null && f.getRequestKey()!=null,"","request");require(f.getEffectiveDate()!=null && f.getEffectiveDate().getYear()>=1 && !f.getEffectiveDate().isAfter(today),"effectiveDate","date");
        text(f.getParty(),160,type!=Type.JOURNAL,"party");text(f.getReference(),160,true,"reference");text(f.getDescription(),500,false,"description");text(f.getEvidence(),500,false,"evidence");
        require(type!=Type.JOURNAL || f.getMoneyAccountId()==null,"moneyAccountId","moneyAccount");require(f.getRows()!=null && !f.getRows().isEmpty() && f.getRows().size()<=50,"","rows");
        int entryCount=0;
        for(int n=0;n<f.getRows().size();n++){
            var r=f.getRows().get(n);String field="rows["+n+"]";require(r!=null,field,"rows");text(r.getDescription(),500,true,field+".description");
            require(f.selectedActivityIds().isEmpty() || r.getTransactionId()!=null,field+".transactionId","activityTransaction");
            require(r.getAmount()!=null && r.getAmount().signum()>0 && r.getAmount().scale()>=-16 && r.getAmount().scale()<=2 && r.getAmount().precision()-r.getAmount().scale()<=16,field+".amount","amount");
            require(r.getComponent()!=null && COMPONENTS.contains(r.getComponent()),field+".component","template");
            if(r.getEntries()!=null && !r.getEntries().isEmpty()){
                require(r.getTransactionId()==null && r.getTemplateKey()==null && "TOTAL".equals(r.getComponent()),field+".entries","template");
                require(r.getEntries().size()>=2 && r.getEntries().size()<=50,field+".entries","entries");
                require(r.getDebitAccountId()==null && r.getCreditAccountId()==null,field+".entries","request");
                BigDecimal debits=BigDecimal.ZERO,credits=BigDecimal.ZERO;
                for(int e=0;e<r.getEntries().size();e++){var entry=r.getEntries().get(e);String path=field+".entries["+e+"]";
                    require(entry!=null && entry.getSide()!=null && Set.of("DEBIT","CREDIT").contains(entry.getSide()),path,"entries");
                    require(entry.getAmount()!=null && entry.getAmount().signum()>0 && entry.getAmount().scale()>=-16 && entry.getAmount().scale()<=2 && entry.getAmount().precision()-entry.getAmount().scale()<=16,path+".amount","amount");
                    if("DEBIT".equals(entry.getSide()))debits=debits.add(entry.getAmount());else credits=credits.add(entry.getAmount());}
                require(debits.compareTo(r.getAmount())==0 && credits.compareTo(r.getAmount())==0,field+".entries","balance");
                entryCount+=r.getEntries().size();
            }else entryCount+=2;
            require(entryCount<=500,"","entries");
        }
    }
    private Voucher scoped(AppUserPrincipal actor,Type type,UUID id,boolean lock){return vouchers.find(actor.getSaccoId(),actor.getStationId(),type,id,lock).orElseThrow(()->new AccessDeniedException("Voucher outside your workspace"));}
    private Client client(AppUserPrincipal actor,Form f){return f.getClientId()==null?null:vouchers.client(actor.getSaccoId(),actor.getStationId(),f.getClientId(),true).orElseThrow(()->new Invalid("clientId","client"));}
    private Voucher details(Voucher v){return new Voucher(v.id(),v.number(),v.type(),v.effectiveDate(),v.party(),v.reference(),v.description(),v.evidence(),v.moneyAccountId(),v.total(),v.transactionCount(),v.journalId(),v.postedBy(),v.postedByName(),v.postedAt(),v.reversesId(),v.reversedBy(),v.institutionName(),v.branchName(),vouchers.transactions(v.id()),v.clientId(),v.clientNumber(),v.equityMovement());}
    private String hash(Type type,Form f){try{
        // Keep ordinary-voucher retry hashes compatible with the pre-unification form.
        var payload=(tools.jackson.databind.node.ObjectNode)json.valueToTree(f);
        payload.remove("receiptPurpose");if(f.getClientId()==null)payload.remove("clientId");payload.remove("interestAccountId");if(f.getActivityId()==null)payload.remove("activityId");
        if(f.getActivityIds()!=null && !f.getActivityIds().isEmpty())payload.set("activityIds",json.valueToTree(f.getActivityIds().stream().sorted().toList()));
        for(var row:payload.path("rows"))((tools.jackson.databind.node.ObjectNode)row).remove("loanId");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((type.name()+json.writeValueAsString(payload)).getBytes(StandardCharsets.UTF_8)));
    }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void text(String s,int max,boolean required,String field){boolean multiline=field.equals("equityReason")||field.equals("description")||field.endsWith(".description");require(s!=null && (!required || !s.isBlank()) && s.length()<=max && s.chars().noneMatch(c->c<32 && !(multiline && (c=='\n'||c=='\r'||c=='\t'))),field,"text");}
    private static void query(String search,int page){require(search!=null && search.length()<=100 && page>=0 && page<=1000,"","filter");}
    static void validateFilter(Filter f){require(f!=null,"","filter");query(f.search(),f.page());require(Set.of(10,25,30,50).contains(f.size()),"","filter");require(Set.of("","POSTED","REVERSED","REVERSAL").contains(f.status()) && com.sacco.mvp.accounting.dto.RegisterSort.valid(f.sort(),Set.of("number","date","party","reference","transactions","amount","status")) && (f.from()==null || f.through()==null || !f.through().isBefore(f.from())),"","filter");}
    private static void require(boolean ok,String field,String code){if(!ok)throw new Invalid(field,code);}
}
