package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class StatementSetupService {
    private final StatementSetupRepository repository;
    private final GeneralLedgerService access;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private void authorize(AppUserPrincipal a){access.requireActor(a,"FINANCIAL_REPORTS_VIEW");access.requireActor(a,"FINANCIAL_REPORTS_CONFIGURE");}
    @Transactional(readOnly=true) public Form edit(AppUserPrincipal a){
        authorize(a);var setup=repository.latest(a.getSaccoId()).map(v->mapper.readValue(v.payload(),Setup.class)).orElse(Setup.empty());
        var choices=accounts(a);var codes=new HashMap<UUID,String>();choices.forEach(c->codes.put(c.id(),c.code()));
        var f=new Form();f.setVersion(setup.version());f.setRequestKey(UUID.randomUUID());
        for(var c:setup.components()){var row=new ComponentForm();row.setName(c.name());row.setNameSw(c.nameSw());row.setRetained(c.retained());row.setAccounts(c.accounts().stream().map(codes::get).collect(java.util.stream.Collectors.joining(", ")));f.getComponents().add(row);}
        for(var n:setup.notes()){var row=new NoteForm();row.setNumber(n.number());row.setIncludeProfit(n.includeProfit());row.setTitle(n.title());row.setTitleSw(n.titleSw());row.setBasis(n.basis());row.setText(n.text());row.setTextSw(n.textSw());row.setAccounts(n.accounts().stream().map(codes::get).collect(java.util.stream.Collectors.joining(", ")));f.getNotes().add(row);}
        for(var category:Category.values()){var row=new CategoryForm();row.setCategory(category);setup.categories().stream().filter(c->c.category()==category).findFirst().ifPresent(c->{row.setName(c.name());row.setNameSw(c.nameSw());});f.getCategories().add(row);}
        return f;
    }
    @Transactional(readOnly=true) public List<AccountChoice> accounts(AppUserPrincipal a){authorize(a);var accounts=repository.accounts(a.getSaccoId());require(accounts.size()<=2000,"limit");return accounts;}
    @Transactional(timeout=15) public void save(AppUserPrincipal a,Form f){
        authorize(a);require(f!=null&&f.getRequestKey()!=null,"setup");String reason=text(f.getReason(),500);require(!reason.isBlank(),"reason");
        require(f.getComponents()!=null&&f.getComponents().size()<=9&&f.getNotes()!=null&&f.getNotes().size()<=30&&f.getCategories()!=null&&f.getCategories().size()<=7,"setup");
        var choices=accounts(a);var byCode=new HashMap<String,AccountChoice>();choices.forEach(c->byCode.put(c.code(),c));
        var components=new ArrayList<Component>();var componentAccounts=new HashSet<UUID>();var names=new HashSet<String>();int retained=0;
        for(var c:f.getComponents()){String name=text(c.getName(),100);require(!name.isBlank()&&names.add(name.toLowerCase(Locale.ROOT)),"setup");var ids=resolve(c.getAccounts(),byCode);require(!ids.isEmpty(),"setup");for(UUID id:ids)require(componentAccounts.add(id)&&choices.stream().anyMatch(x->x.id().equals(id)&&x.type().equals("EQUITY")),"componentAccounts");if(c.isRetained())retained++;components.add(new Component(name,text(c.getNameSw(),100),ids,c.isRetained()));}
        require(retained<=1,"setup");var notes=new ArrayList<Note>();var numbers=new HashSet<Integer>();var noteAccounts=new HashSet<UUID>();boolean profitNote=false;
        for(var n:f.getNotes()){require(n.getNumber()>0&&n.getNumber()<=999&&numbers.add(n.getNumber()),"setup");String title=text(n.getTitle(),150);require(!title.isBlank()&&Set.of("BALANCE","MOVEMENT","NARRATIVE").contains(n.getBasis()),"setup");var ids=resolve(n.getAccounts(),byCode);require(n.getBasis().equals("NARRATIVE")?ids.isEmpty()&&!n.isIncludeProfit():!ids.isEmpty()||n.isIncludeProfit(),"noteAccounts");var types=new HashSet<String>();for(UUID id:ids){require(noteAccounts.add(id),"noteAccounts");types.add(choices.stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().type());}require(types.size()<=1,"noteAccounts");if(n.isIncludeProfit()){require(!profitNote&&n.getBasis().equals("BALANCE")&&(types.isEmpty()||types.equals(Set.of("EQUITY"))),"noteProfit");profitNote=true;}require(!n.getBasis().equals("NARRATIVE")||!text(n.getText(),5000).isBlank(),"setup");notes.add(new Note(n.getNumber(),title,text(n.getTitleSw(),150),n.getBasis(),text(n.getText(),5000),text(n.getTextSw(),5000),ids,n.isIncludeProfit()));}
        notes.sort(Comparator.comparingInt(Note::number));var categories=new ArrayList<CategoryLabel>();var categoryIds=new HashSet<Category>();
        for(var c:f.getCategories()){require(c.getCategory()!=null&&categoryIds.add(c.getCategory()),"setup");categories.add(new CategoryLabel(c.getCategory(),text(c.getName(),100),text(c.getNameSw(),100)));}
        String hash=StatementService.sha(mapper.writeValueAsString(f).getBytes(StandardCharsets.UTF_8));repository.lock(a.getSaccoId());var duplicate=repository.request(a.getSaccoId(),f.getRequestKey());if(duplicate.isPresent()){require(duplicate.get().equals(hash),"retry");return;}
        int current=repository.latest(a.getSaccoId()).map(StoredSetup::version).orElse(0);require(f.getVersion()==current,"setupStale");
        var setup=new Setup(current+1,List.copyOf(components),List.copyOf(notes),List.copyOf(categories));String payload=mapper.writeValueAsString(setup);require(payload.length()<=262144,"limit");repository.save(a,setup,payload,f.getRequestKey(),hash,reason);log(a,f.getRequestKey(),"SAVE_SETUP",reason);
    }
    @Transactional(readOnly=true) public List<Journal> journals(AppUserPrincipal a,Parameters p,int page){authorize(a);dates(p,page);return repository.journals(a,p,page);}
    @Transactional(timeout=15) public void classify(AppUserPrincipal a,UUID id,int version,Category category,String reason){
        authorize(a);require(id!=null&&category!=null&&version>=0,"setup");reason=text(reason,500);require(!reason.isBlank(),"reason");repository.lock(a.getSaccoId()+"/"+a.getStationId()+"/"+id);
        var lines=repository.journal(a,id);if(lines.isEmpty())throw new AccessDeniedException("Journal unavailable");require(lines.size()<=2000,"limit");
        var types=new HashMap<UUID,String>();accounts(a).forEach(c->types.put(c.id(),c.type()));
        var equity=lines.stream().filter(l->"EQUITY".equals(types.get(l.accountId()))).toList();require(!equity.isEmpty(),"movement");
        if(Set.of(Category.CONTRIBUTION,Category.DISTRIBUTION,Category.OCI).contains(category))require(lines.stream().noneMatch(l->Set.of("INCOME","EXPENSE").contains(types.getOrDefault(l.accountId(),""))),"movement");
        if(category==Category.TRANSFER)require(lines.size()==equity.size()&&equity.stream().map(Movement::signed).reduce(BigDecimal.ZERO,BigDecimal::add).signum()==0,"movement");
        if(category==Category.EARNINGS_TRANSFER)require(lines.size()>equity.size()&&lines.stream().allMatch(l->Set.of("EQUITY","INCOME","EXPENSE").contains(types.getOrDefault(l.accountId(),""))),"movement");
        int current=repository.classificationVersion(a,id);if(current==version+1&&repository.sameClassification(a,id,category,reason))return;require(current==version,"setupStale");repository.classify(a,id,current+1,category,reason);log(a,id,"CLASSIFY_EQUITY",reason);
    }
    private static List<UUID> resolve(String value,Map<String,AccountChoice> choices){require(value!=null&&value.length()<=20000,"setup");if(value.isBlank())return List.of();var ids=new LinkedHashSet<UUID>();for(String code:value.split(",",-1)){var account=choices.get(code.trim());require(account!=null&&ids.add(account.id()),"setupAccounts");}return List.copyOf(ids);}
    private static String text(String s,int max){require(s!=null&&s.length()<=max&&!s.chars().anyMatch(c->c<32&&c!='\n'&&c!='\r'&&c!='\t'),"setup");return s.strip();}
    private static void dates(Parameters p,int page){require(p!=null&&p.from()!=null&&p.through()!=null&&!p.through().isBefore(p.from())&&!p.through().isAfter(p.from().plusDays(366))&&page>=0&&page<=10000,"dates");}
    private void log(AppUserPrincipal a,UUID id,String action,String reason){audit.log("STATEMENT_SETUP",id,action,a.getMemberId(),null,Map.of("saccoId",a.getSaccoId(),"stationId",a.getStationId(),"reason",reason));}
    private static void require(boolean c,String key){if(!c)throw new IllegalArgumentException("fs.error."+key);}
}
