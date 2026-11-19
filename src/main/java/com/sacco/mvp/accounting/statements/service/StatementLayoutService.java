package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementLayoutModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;

/** Focused edits append the same immutable reporting metadata consumed by saved statements. */
@Service @RequiredArgsConstructor
public class StatementLayoutService {
    public static final int MAX_MOVEMENT_ROWS=30;
    private final StatementSetupRepository repository;
    private final GeneralLedgerService access;
    private final ObjectMapper mapper;
    private final AuditService audit;

    @Transactional(readOnly=true)
    public Layout overview(AppUserPrincipal actor) {
        authorize(actor);var setup=current(actor);
        var groups=new ArrayList<GroupRow>();var notes=new ArrayList<NoteRow>();
        for(int i=0;i<setup.components().size();i++){var c=setup.components().get(i);groups.add(new GroupRow(i,c.name(),c.nameSw(),c.accounts().size(),c.retained()));}
        for(int i=0;i<setup.notes().size();i++){var n=setup.notes().get(i);notes.add(new NoteRow(i,n.number(),n.title(),n.titleSw(),n.basis(),n.accounts().size(),n.includeProfit()));}
        return new Layout(setup.version(),List.copyOf(groups),List.copyOf(notes),setup.movementRows());
    }

    @Transactional(readOnly=true)
    public GroupEditor group(AppUserPrincipal actor,int index,Integer version) {
        authorize(actor);var setup=current(actor);target(setup,index,version,true);
        require(index>=0||setup.components().size()<9,"limit");
        var form=new GroupForm();initialize(form,setup,index);form.setPosition(index<0?setup.components().size()+1:index+1);
        if(index>=0){var c=setup.components().get(index);form.setName(c.name());form.setNameSw(c.nameSw());form.setAccounts(new ArrayList<>(c.accounts()));form.setRetained(c.retained());}
        var accounts=choices(actor,true);known(form.getAccounts(),accounts);
        return new GroupEditor(form,accounts,setup.components().size()+(index<0?1:0));
    }

    @Transactional(readOnly=true)
    public NoteEditor note(AppUserPrincipal actor,int index,Integer version) {
        authorize(actor);var setup=current(actor);target(setup,index,version,false);
        require(index>=0||setup.notes().size()<30,"limit");
        var form=new NoteForm();initialize(form,setup,index);
        var used=new HashSet<Integer>();setup.notes().forEach(n->used.add(n.number()));while(used.contains(form.getNumber()))form.setNumber(form.getNumber()+1);
        if(index>=0){var n=setup.notes().get(index);form.setNumber(n.number());form.setTitle(n.title());form.setTitleSw(n.titleSw());form.setBasis(n.basis());form.setText(n.text());form.setTextSw(n.textSw());form.setAccounts(new ArrayList<>(n.accounts()));form.setIncludeProfit(n.includeProfit());}
        var accounts=choices(actor,false);known(form.getAccounts(),accounts);return new NoteEditor(form,accounts);
    }

    @Transactional(readOnly=true)
    public List<AccountChoice> accounts(AppUserPrincipal actor,boolean equityOnly) {authorize(actor);return choices(actor,equityOnly);}

    @Transactional(readOnly=true)
    public MovementRowForm movementRow(AppUserPrincipal actor,int index,Integer version) {
        authorize(actor);
        var setup=current(actor);
        var form=new MovementRowForm();
        initialize(form,setup,index);
        index(form,setup.movementRows().size());
        if(index<0)require(setup.movementRows().size()<MAX_MOVEMENT_ROWS,"limit");
        else {
            require(version!=null&&version==setup.version(),"setupStale");
            var row=setup.movementRows().get(index);
            form.setCategory(row.category());
            form.setName(row.name());
            form.setNameSw(row.nameSw());
        }
        return form;
    }

    @Transactional(timeout=15)
    public boolean saveMovementRow(AppUserPrincipal actor,MovementRowForm form) {
        return update(actor,form,"SAVE_MOVEMENT_ROW",setup->{
            var rows=new ArrayList<>(setup.movementRows());
            index(form,rows.size());
            int index=form.getIndex();
            UUID id=index<0?UUID.randomUUID():rows.get(index).id();
            var row=new MovementRow(id,form.getCategory(),movementName(form.getName()),movementName(form.getNameSw()));
            if(index<0)rows.add(row);else rows.set(index,row);
            return new Setup(setup.version()+1,setup.components(),setup.notes(),setup.categories(),rows,!setup.hasLayoutNotes());
        });
    }

    @Transactional(readOnly=true)
    public RemovalEditor removal(AppUserPrincipal actor,boolean group,int index,int version) {
        authorize(actor);var setup=current(actor);require(index>=0,"selection");target(setup,index,version,group);
        var form=new Change();initialize(form,setup,index);
        return group?new RemovalEditor(form,setup.components().get(index).name(),setup.components().get(index).accounts().size(),true)
            :new RemovalEditor(form,setup.notes().get(index).title(),setup.notes().get(index).accounts().size(),false);
    }

    @Transactional(timeout=15)
    public boolean saveGroup(AppUserPrincipal actor,GroupForm form) {
        return update(actor,form,"SAVE_GROUP",setup->{
            var groups=new ArrayList<>(setup.components());index(form,groups.size());
            if(form.getIndex()>=0)groups.remove(form.getIndex());
            require(form.getPosition()>=1&&form.getPosition()<=groups.size()+1,"layoutPosition");
            var accounts=stableIds(form.getAccounts(),form.getIndex()<0?null:setup.components().get(form.getIndex()).accounts());
            groups.add(form.getPosition()-1,new Component(text(form.getName(),100),text(form.getNameSw(),100),accounts,form.isRetained()));
            return new Setup(setup.version()+1,List.copyOf(groups),setup.notes(),setup.categories(),setup.movementRows());
        });
    }

    @Transactional(timeout=15)
    public boolean saveNote(AppUserPrincipal actor,NoteForm form) {
        return update(actor,form,"SAVE_NOTE",setup->{
            var notes=new ArrayList<>(setup.notes());index(form,notes.size());
            var accounts=stableIds(form.getAccounts(),form.getIndex()<0?null:setup.notes().get(form.getIndex()).accounts());
            var note=new Note(form.getNumber(),text(form.getTitle(),150),text(form.getTitleSw(),150),form.getBasis(),text(form.getText(),5000),text(form.getTextSw(),5000),accounts,form.isIncludeProfit());
            if(form.getIndex()<0)notes.add(note);else notes.set(form.getIndex(),note);
            notes.sort(Comparator.comparingInt(Note::number));return new Setup(setup.version()+1,setup.components(),List.copyOf(notes),setup.categories(),setup.movementRows());
        });
    }

    @Transactional(timeout=15)
    public void remove(AppUserPrincipal actor,boolean group,Change form) {
        update(actor,form,group?"REMOVE_GROUP":"REMOVE_NOTE",setup->{
            require(form.getIndex()>=0,"selection");var groups=new ArrayList<>(setup.components());var notes=new ArrayList<>(setup.notes());
            index(form,group?groups.size():notes.size());if(group)groups.remove(form.getIndex());else notes.remove(form.getIndex());
            return new Setup(setup.version()+1,List.copyOf(groups),List.copyOf(notes),setup.categories(),setup.movementRows());
        });
    }

    private boolean update(AppUserPrincipal actor,Change form,String action,UnaryOperator<Setup> edit) {
        authorize(actor);require(form!=null&&form.getRequestKey()!=null&&form.getVersion()>=0,"setup");
        String reason=text(form.getReason(),500);
        String hash=StatementService.sha((actor.getMemberId()+"/"+action+"/"+mapper.writeValueAsString(form)).getBytes(StandardCharsets.UTF_8));
        repository.lock(actor.getSaccoId());var duplicate=repository.request(actor.getSaccoId(),form.getRequestKey());
        if(duplicate.isPresent()){require(duplicate.get().equals(hash),"retry");return true;}
        var before=current(actor);require(before.version()==form.getVersion(),"setupStale");
        var after=edit.apply(before);
        if(after.components().equals(before.components())&&after.notes().equals(before.notes())&&after.categories().equals(before.categories())&&after.movementRows().equals(before.movementRows()))return false;
        require(!reason.isBlank(),"reason");validate(after,choices(actor,false));
        String payload=mapper.writeValueAsString(after);require(payload.length()<=262144,"limit");
        repository.save(actor,after,payload,form.getRequestKey(),hash,reason);
        audit.log("STATEMENT_SETUP",form.getRequestKey(),action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId(),"reason",reason));
        return true;
    }

    private void validate(Setup setup,List<AccountChoice> accounts) {
        require(setup.components().size()<=9&&setup.notes().size()<=30&&setup.movementRows().size()<=MAX_MOVEMENT_ROWS,"limit");
        var choices=new HashMap<UUID,AccountChoice>();accounts.forEach(a->choices.put(a.id(),a));
        var names=new HashSet<String>();var groupAccounts=new HashSet<UUID>();int retained=0;
        for(var group:setup.components()){
            require(!group.name().isBlank()&&names.add(group.name().toLowerCase(Locale.ROOT)),"layoutGroupName");require(!group.accounts().isEmpty(),"componentAccounts");
            for(var id:group.accounts())require(choices.containsKey(id)&&"EQUITY".equals(choices.get(id).type())&&groupAccounts.add(id),"componentAccounts");
            if(group.retained())retained++;
        }
        require(retained<=1,"layoutRetained");
        var movementNames=new HashSet<String>();var movementNamesSw=new HashSet<String>();
        for(var row:setup.movementRows()){
            require(row.category()!=null&&row.category()!=Category.UNCLASSIFIED,"layoutMovementCategory");
            require(!row.name().isBlank()&&movementNames.add(row.name().toLowerCase(Locale.ROOT))&&
                movementNamesSw.add((row.nameSw().isBlank()?row.name():row.nameSw()).toLowerCase(Locale.ROOT)),"layoutMovementName");
        }
        var numbers=new HashSet<Integer>();var noteAccounts=new HashSet<UUID>();boolean profit=false;
        for(var note:setup.notes()){
            require(note.number()>0&&note.number()<=999&&numbers.add(note.number()),"layoutNoteNumber");require(!note.title().isBlank(),"layoutNoteTitle");
            require(note.basis()!=null&&Set.of("BALANCE","MOVEMENT","NARRATIVE").contains(note.basis()),"setup");
            require(note.basis().equals("NARRATIVE")?note.accounts().isEmpty()&&!note.includeProfit():!note.accounts().isEmpty()||note.includeProfit(),"noteAccounts");
            var types=new HashSet<String>();for(var id:note.accounts()){require(choices.containsKey(id)&&noteAccounts.add(id),"noteAccounts");types.add(choices.get(id).type());}
            require(types.size()<=1,"noteAccounts");
            if(note.includeProfit()){require(!profit&&note.basis().equals("BALANCE")&&(types.isEmpty()||types.equals(Set.of("EQUITY"))),"noteProfit");profit=true;}
            require(!note.basis().equals("NARRATIVE")||!note.text().isBlank(),"layoutNoteText");
        }
    }
    private Setup current(AppUserPrincipal actor){return repository.latest(actor.getSaccoId()).map(v->mapper.readValue(v.payload(),Setup.class)).orElse(Setup.empty());}
    private List<AccountChoice> choices(AppUserPrincipal actor,boolean equityOnly){var accounts=repository.accounts(actor.getSaccoId());require(accounts.size()<=2000,"limit");return equityOnly?accounts.stream().filter(a->a.type().equals("EQUITY")).toList():accounts;}
    private static void target(Setup setup,int index,Integer version,boolean group){require(index>=-1&&index<(group?setup.components().size():setup.notes().size())||index==-1,"selection");if(index>=0)require(version!=null&&version==setup.version(),"setupStale");}
    private static void initialize(Change form,Setup setup,int index){form.setVersion(setup.version());form.setIndex(index);form.setRequestKey(UUID.randomUUID());}
    private static void index(Change form,int size){require(form.getIndex()>=-1&&(form.getIndex()<size||form.getIndex()==-1),"selection");}
    private static List<UUID> ids(List<UUID> ids){require(ids!=null&&ids.size()<=2000&&ids.stream().noneMatch(Objects::isNull)&&new HashSet<>(ids).size()==ids.size(),"setupAccounts");return List.copyOf(ids);}
    private static List<UUID> stableIds(List<UUID> submitted,List<UUID> existing){var chosen=ids(submitted);return existing!=null&&new HashSet<>(chosen).equals(new HashSet<>(existing))?existing:chosen;}
    private static void known(List<UUID> ids,List<AccountChoice> choices){var known=new HashSet<UUID>();choices.forEach(a->known.add(a.id()));require(known.containsAll(ids),"setupAccounts");}
    private static String text(String value,int max){require(value!=null&&value.length()<=max&&!value.chars().anyMatch(c->c<32&&c!='\n'&&c!='\r'&&c!='\t'),"setup");return value.strip();}
    private static String movementName(String value){require(value!=null&&value.length()<=100&&value.chars().noneMatch(Character::isISOControl),"layoutMovementName");return value.strip();}
    private void authorize(AppUserPrincipal actor){access.requireActor(actor,"FINANCIAL_REPORTS_VIEW");access.requireActor(actor,"FINANCIAL_REPORTS_CONFIGURE");}
    private static void require(boolean valid,String key){if(!valid)throw new IllegalArgumentException("fs.error."+key);}
}
