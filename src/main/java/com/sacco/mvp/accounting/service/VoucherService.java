package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.repository.VoucherRepository;
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
    private static final Set<String> COMPONENTS=Set.of("TOTAL","PRINCIPAL","INTEREST","FEES","TAX");
    public void authorize(AppUserPrincipal actor,Type type,String action){
        ledger.requireActor(actor,(type==Type.JOURNAL?"ACCOUNTING_JOURNALS_":"ACCOUNTING_BUSINESS_")+action);
    }
    @Transactional(readOnly=true)
    public Page list(AppUserPrincipal actor,Type type,Filter filter){
        authorize(actor,type,"VIEW");validateFilter(filter);
        var rows=vouchers.list(actor.getSaccoId(),actor.getStationId(),type,filter);
        return new Page(rows.stream().limit(25).toList(),filter.page(),rows.size()>25);
    }
    @Transactional(readOnly=true)
    public Voucher view(AppUserPrincipal actor,Type type,UUID id){authorize(actor,type,"VIEW");return details(scoped(actor,type,id,false));}
    @Transactional(readOnly=true)
    public Voucher export(AppUserPrincipal actor,Type type,UUID id){
        authorize(actor,type,"VIEW");ledger.requireActor(actor,"FINANCIAL_REPORTS_EXPORT");return details(scoped(actor,type,id,false));
    }
    @Transactional(readOnly=true)
    public List<Account> accounts(AppUserPrincipal actor,Type type,String search,int page){authorize(actor,type,"CREATE");query(search,page);return vouchers.accounts(actor.getSaccoId(),search,page*25);}
    @Transactional(readOnly=true)
    public List<Mapping> mappings(AppUserPrincipal actor,Type type,String search,int page){authorize(actor,type,"CREATE");query(search,page);return vouchers.mappings(actor.getSaccoId(),search,page*25);}
    @Transactional(readOnly=true)
    public Map<UUID,Account> selectedAccounts(AppUserPrincipal actor,Type type,Form form){
        authorize(actor,type,"CREATE");var ids=new TreeSet<UUID>();
        if(form.getMoneyAccountId()!=null)ids.add(form.getMoneyAccountId());
        if(form.getRows()!=null)for(var row:form.getRows()){if(row!=null){if(row.getDebitAccountId()!=null)ids.add(row.getDebitAccountId());if(row.getCreditAccountId()!=null)ids.add(row.getCreditAccountId());}}
        if(ids.size()>101)throw new Invalid("","rows");var result=new HashMap<UUID,Account>();vouchers.selectedAccounts(actor.getSaccoId(),new ArrayList<>(ids),false).forEach(a->result.put(a.id(),a));return result;
    }
    @Transactional
    public Preview preview(AppUserPrincipal actor,Type type,Form form){authorize(actor,type,"CREATE");validate(form,type,clock.today());return plan(actor,type,form);}
    @Transactional
    public Voucher post(AppUserPrincipal actor,Type type,Form form){
        authorize(actor,type,"CREATE");validate(form,type,clock.today());
        String hash=hash(type,form);vouchers.lockRequest(actor.getSaccoId(),actor.getStationId(),form.getRequestKey());
        var prior=vouchers.byRequest(actor.getSaccoId(),actor.getStationId(),form.getRequestKey());
        if(prior.isPresent()){
            require(prior.get().type()==type && vouchers.sameRequest(prior.get().id(),hash,actor.getMemberId()),"","retry");return details(prior.get());
        }
        UUID period=vouchers.period(actor.getSaccoId(),form.getEffectiveDate(),actor.getMemberId(),clock.now());
        var plan=plan(actor,type,form);
        return persist(actor,type,form,hash,plan,null,null,period);
    }
    @Transactional
    public Voucher reverse(AppUserPrincipal actor,Type type,UUID originalId,UUID key,LocalDate date,String reason){
        authorize(actor,type,"REVERSE");require(key!=null,"","request");text(reason,500,true,"description");
        require(date!=null && !date.isAfter(clock.today()),"effectiveDate","date");
        vouchers.lockRequest(actor.getSaccoId(),actor.getStationId(),key);
        var original=details(scoped(actor,type,originalId,true));
        var f=new Form();f.setRequestKey(key);f.setEffectiveDate(date);f.setParty(original.party());f.setReference(original.number());
        f.setDescription(reason.strip());f.setEvidence(original.evidence());f.setMoneyAccountId(original.moneyAccountId());f.setRows(List.of());
        String hash=hash(type,f);
        var prior=vouchers.byRequest(actor.getSaccoId(),actor.getStationId(),key);
        if(prior.isPresent()){require(Objects.equals(prior.get().reversesId(),originalId) && vouchers.sameRequest(prior.get().id(),hash,actor.getMemberId()),"","retry");return details(prior.get());}
        require(original.reversesId()==null && original.reversedBy()==null && !date.isBefore(original.effectiveDate()),"","reversal");
        UUID period=vouchers.period(actor.getSaccoId(),date,actor.getMemberId(),clock.now());
        var ids=original.transactions().stream().flatMap(t->java.util.stream.Stream.of(t.debit().id(),t.credit().id())).distinct().sorted().toList();
        require(vouchers.lockAccounts(actor.getSaccoId(),ids).size()==ids.size(),"","account");
        var rows=original.transactions().stream().map(t->new Transaction(UUID.randomUUID(),t.lineNo(),t.transactionId(),t.templateKey(),t.transactionName(),t.component(),t.description(),t.amount(),t.credit(),t.debit())).toList();
        return persist(actor,type,f,hash,new Preview(rows,original.total()),original.id(),original.journalId(),period);
    }
    private Voucher persist(AppUserPrincipal actor,Type type,Form f,String hash,Preview plan,UUID reverses,UUID journal,UUID period){
        var now=clock.now();
        var v=new Voucher(UUID.randomUUID(),vouchers.number(type,f.getEffectiveDate()),type,f.getEffectiveDate(),f.getParty().strip(),f.getReference().strip(),f.getDescription().strip(),f.getEvidence().strip(),f.getMoneyAccountId(),plan.total(),plan.rows().size(),UUID.randomUUID(),actor.getMemberId(),"",now,reverses,null,"","",plan.rows());
        vouchers.insert(v,actor.getSaccoId(),actor.getStationId(),f,hash,period,journal);
        audit.logEvent("ACCOUNTING",v.id(),reverses==null?"VOUCHER_POSTED":"VOUCHER_REVERSED",actor.getMemberId(),AuditEventStatus.SUCCESS,"Voucher posted","VOUCHER",v.number(),actor.getSaccoId(),actor.getStationId(),Map.of("type",type.name(),"amount",v.total(),"transactions",v.transactionCount(),"journal",v.journalId()));
        return details(scoped(actor,type,v.id(),false));
    }
    private Preview plan(AppUserPrincipal actor,Type type,Form f){
        var transactionIds=f.getRows().stream().map(RowForm::getTransactionId).filter(Objects::nonNull).distinct().sorted().toList();
        var mappings=transactionIds.isEmpty()?List.<Mapping>of():vouchers.lockMappings(actor.getSaccoId(),transactionIds);
        var accountIds=new TreeSet<UUID>();if(f.getMoneyAccountId()!=null)accountIds.add(f.getMoneyAccountId());
        for(var r:f.getRows())if(r.getTransactionId()==null){if(r.getDebitAccountId()!=null)accountIds.add(r.getDebitAccountId());if(r.getCreditAccountId()!=null)accountIds.add(r.getCreditAccountId());}
        require(!accountIds.isEmpty() || !mappings.isEmpty(),"rows[0].amount","account");
        var accounts=new HashMap<UUID,Account>();if(!accountIds.isEmpty())vouchers.lockAccounts(actor.getSaccoId(),new ArrayList<>(accountIds)).forEach(a->accounts.put(a.id(),a));
        Account money=accounts.get(f.getMoneyAccountId());
        require(type==Type.JOURNAL || money!=null && Set.of("CASH","BANK","MOBILE_MONEY").contains(money.purpose()),"moneyAccountId","moneyAccount");
        var result=new ArrayList<Transaction>();BigDecimal total=new BigDecimal("0.00");int n=0;
        for(var row:f.getRows()){
            String field="rows["+n+"]";Account debit,credit;String name="";UUID template=null;
            if(row.getTransactionId()!=null){
                var matches=mappings.stream().filter(m->m.transactionId().equals(row.getTransactionId()) && m.component().equals(row.getComponent())).toList();
                require(matches.size()==1,field+".transactionId","template");var m=matches.getFirst();
                require(m.templateKey().equals(row.getTemplateKey()),field+".transactionId","staleTemplate");debit=m.debit();credit=m.credit();name=m.transactionName();template=m.templateKey();
            }else {debit=type==Type.RECEIPT?money:accounts.get(row.getDebitAccountId());credit=type==Type.PAYMENT?money:accounts.get(row.getCreditAccountId());}
            require(debit!=null && credit!=null && !debit.id().equals(credit.id()),field+(type==Type.RECEIPT?".creditAccountId":".debitAccountId"),"account");
            require(type==Type.JOURNAL || (type==Type.RECEIPT?debit.id():credit.id()).equals(money.id()),field+".transactionId","direction");
            require(type==Type.JOURNAL || !Set.of("CASH","BANK","MOBILE_MONEY").contains((type==Type.RECEIPT?credit:debit).purpose()),field+".transactionId","transfer");
            BigDecimal amount=row.getAmount().setScale(2,RoundingMode.UNNECESSARY);total=total.add(amount);
            result.add(new Transaction(UUID.randomUUID(),++n,row.getTransactionId(),template,name,row.getComponent(),row.getDescription().strip(),amount,debit,credit));
        }
        require(total.precision()<=18,"","amount");return new Preview(List.copyOf(result),total);
    }
    static void validate(Form f,Type type,LocalDate today){
        require(f!=null && f.getRequestKey()!=null,"","request");require(f.getEffectiveDate()!=null && f.getEffectiveDate().getYear()>=1 && !f.getEffectiveDate().isAfter(today),"effectiveDate","date");
        text(f.getParty(),160,true,"party");text(f.getReference(),160,true,"reference");text(f.getDescription(),500,false,"description");text(f.getEvidence(),500,false,"evidence");
        require(type!=Type.JOURNAL || f.getMoneyAccountId()==null,"moneyAccountId","moneyAccount");require(f.getRows()!=null && !f.getRows().isEmpty() && f.getRows().size()<=50,"","rows");
        for(int n=0;n<f.getRows().size();n++){
            var r=f.getRows().get(n);String field="rows["+n+"]";require(r!=null,field,"rows");text(r.getDescription(),500,true,field+".description");
            require(r.getAmount()!=null && r.getAmount().signum()>0 && r.getAmount().scale()<=2 && r.getAmount().precision()-r.getAmount().scale()<=16,field+".amount","amount");
            require(r.getComponent()!=null && COMPONENTS.contains(r.getComponent()),field+".component","template");
        }
    }
    private Voucher scoped(AppUserPrincipal actor,Type type,UUID id,boolean lock){return vouchers.find(actor.getSaccoId(),actor.getStationId(),type,id,lock).orElseThrow(()->new AccessDeniedException("Voucher outside your workspace"));}
    private Voucher details(Voucher v){return new Voucher(v.id(),v.number(),v.type(),v.effectiveDate(),v.party(),v.reference(),v.description(),v.evidence(),v.moneyAccountId(),v.total(),v.transactionCount(),v.journalId(),v.postedBy(),v.postedByName(),v.postedAt(),v.reversesId(),v.reversedBy(),v.institutionName(),v.branchName(),vouchers.transactions(v.id()));}
    private String hash(Type type,Form f){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((type.name()+json.writeValueAsString(f)).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void text(String s,int max,boolean required,String field){boolean multiline=field.equals("description")||field.endsWith(".description");require(s!=null && (!required || !s.isBlank()) && s.length()<=max && s.chars().noneMatch(c->c<32 && !(multiline && (c=='\n'||c=='\r'||c=='\t'))),field,"text");}
    private static void query(String search,int page){require(search!=null && search.length()<=100 && page>=0 && page<=1000,"","filter");}
    private static void validateFilter(Filter f){require(f!=null,"","filter");query(f.search(),f.page());require(Set.of("","POSTED","REVERSED","REVERSAL").contains(f.status()) && Set.of("newest","oldest","amount").contains(f.sort()) && (f.from()==null || f.through()==null || !f.through().isBefore(f.from())),"","filter");}
    private static void require(boolean ok,String field,String code){if(!ok)throw new Invalid(field,code);}
}
