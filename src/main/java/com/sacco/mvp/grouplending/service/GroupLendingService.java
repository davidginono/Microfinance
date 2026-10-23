package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.repository.GroupLendingRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupLendingService {
    private final GroupLendingRepository groups;
    private final GeneralLedgerService access;
    private final AuditService audit;
    private final ApplicationClock clock;
    private final ObjectMapper json;
    public void authorize(AppUserPrincipal a,String action){access.requireActor(a,"GROUP_LENDING_"+action);}
    @Transactional(readOnly=true) public Page<Group> list(AppUserPrincipal a,String search,String sort,int page) {
        authorize(a,"VIEW");query(search,page);check(sort!=null && Set.of("name","name_desc","code","code_desc","legalForm","legalForm_desc","members","members_desc","status","status_desc").contains(sort),"query");
        var rows=groups.groups(a,search,sort,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
    @Transactional(readOnly=true) public Group detail(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return groups.group(a,id,false);}
    @Transactional public UUID create(AppUserPrincipal a,GroupForm f) {
        authorize(a,"CREATE");check(f!=null && f.getRequestKey()!=null,"request");text(f.getName(),160);text(f.getEvidence(),500);
        check(f.getLegalForm()!=null && Set.of("INFORMAL","REGISTERED_GROUP","ORGANIZATION").contains(f.getLegalForm()),"form");
        String hash=hash(f);groups.lockRequest(a,f.getRequestKey());var retry=groups.retry(a,f.getRequestKey(),hash,false);if(retry.isPresent())return retry.get();
        UUID id=groups.create(a,f,hash,clock.now());event(a,id,"GROUP_CREATED",Map.of("name",f.getName().strip()));return id;
    }
    @Transactional public void status(AppUserPrincipal a,UUID id,int version,boolean active) {
        authorize(a,"UPDATE");var group=groups.group(a,id,true);check(group.version()==version,"changed");
        String state=active?"ACTIVE":"INACTIVE";if(group.status().equals(state))return;
        check(groups.status(a,id,version,state),"changed");event(a,id,"GROUP_STATUS_CHANGED",Map.of("status",state));
    }
    @Transactional(readOnly=true) public Page<Membership> members(AppUserPrincipal a,UUID id,int page,String sort) {
        authorize(a,"VIEW");query("",page);check(sort!=null && Set.of("client","client_desc","office","office_desc","startsOn","startsOn_desc","endsOn","endsOn_desc","evidence","evidence_desc").contains(sort),"query");
        groups.group(a,id,false);var rows=groups.members(a,id,page,sort);
        return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
    @Transactional(readOnly=true) public Membership membership(AppUserPrincipal a,UUID group,UUID member) {
        authorize(a,"UPDATE");return groups.membership(a,group,member);
    }
    @Transactional(readOnly=true) public Optional<Choice> selectedClient(AppUserPrincipal a,UUID id) {
        authorize(a,"UPDATE");return groups.clientChoice(a,id);
    }
    @Transactional(readOnly=true) public List<Choice> clients(AppUserPrincipal a,String search,int page) {
        authorize(a,"UPDATE");query(search,page);return groups.clients(a,search,page);
    }
    @Transactional public UUID addMember(AppUserPrincipal a,UUID group,MemberForm f) {
        authorize(a,"UPDATE");check(f!=null && f.getRequestKey()!=null && f.getClientId()!=null,"request");text(f.getEvidence(),500);
        check(f.getStartsOn()!=null && !f.getStartsOn().isAfter(clock.today()),"date");
        check(f.getOffice()!=null && Set.of("MEMBER","CHAIRPERSON","SECRETARY","TREASURER").contains(f.getOffice()),"form");
        String hash=hash(List.of(group,f));groups.lockRequest(a,f.getRequestKey());var retry=groups.retry(a,f.getRequestKey(),hash,true);if(retry.isPresent())return retry.get();
        var current=groups.group(a,group,true);check("ACTIVE".equals(current.status()),"inactive");check(groups.client(a,f.getClientId()),"client");
        check(!groups.overlaps(a,group,f.getClientId(),f.getStartsOn()),"membership");
        UUID id=groups.addMember(a,group,f,hash,clock.now());event(a,group,"GROUP_MEMBER_ADDED",Map.of("membership",id,"client",f.getClientId()));return id;
    }
    @Transactional public void endMember(AppUserPrincipal a,UUID group,UUID member,LocalDate date,String reason) {
        authorize(a,"UPDATE");text(reason,500);check(date!=null && !date.isAfter(clock.today()),"date");groups.group(a,group,true);
        check(groups.endMember(a,group,member,date,reason),"membership");event(a,group,"GROUP_MEMBERSHIP_ENDED",Map.of("membership",member,"date",date));
    }
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,?> values) {
        audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"LENDING_GROUP",id.toString(),a.getSaccoId(),a.getStationId(),new LinkedHashMap<>(values));
    }
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void query(String search,int page){check(search!=null && search.length()<=100 && page>=0 && page<=10000,"query");}
    private static void text(String value,int max){check(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->c<32),"form");}
    private static void check(boolean valid,String key){if(!valid)throw new IllegalArgumentException("group.error."+key);}
}
