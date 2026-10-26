package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupAuthorityService {
    private final GroupAuthorityRepository authorities;
    private final GroupLendingRepository groups;
    private final GeneralLedgerService access;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    public void authorize(AppUserPrincipal actor,String action){access.requireActor(actor,"GROUP_AUTHORITY_"+action);}
    @Transactional(readOnly=true) public History history(AppUserPrincipal actor,UUID group,int page,String sort){
        authorize(actor,"VIEW");check(page>=0 && page<=10000 && sort!=null && Set.of("revision","revision_desc","from","from_desc","through","through_desc","principal","principal_desc","state","state_desc").contains(sort),"query");
        var identity=groups.group(actor,group,false);var rows=authorities.history(actor,group,clock.today(),page,sort);return new History(identity,new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25));
    }
    @Transactional(readOnly=true) public Authority detail(AppUserPrincipal actor,UUID group,UUID id){authorize(actor,"VIEW");return authorities.authority(actor,group,id,clock.today());}
    @Transactional(readOnly=true) public Editor editor(AppUserPrincipal actor,UUID group,AuthorityForm submitted){
        authorize(actor,"CREATE");var identity=groups.group(actor,group,false);var form=submitted;
        if(form==null){form=new AuthorityForm();form.setExpectedPreviousId(authorities.latest(actor,group).orElse(null));form.setValidFrom(clock.today());form.getSigners().add(new SignerForm());}
        rows(form);var people=authorities.people(actor,form.getSigners().stream().map(SignerForm::getClientId).filter(Objects::nonNull).distinct().toList());return new Editor(identity,form,people);
    }
    @Transactional(readOnly=true) public List<Choice> clients(AppUserPrincipal actor,UUID group,String search,int page){authorize(actor,"CREATE");check(search!=null && search.length()<=100 && page>=0 && page<=10000,"query");groups.group(actor,group,false);return authorities.clients(actor,search,page);}
    @Transactional public UUID record(AppUserPrincipal actor,UUID group,AuthorityForm form){
        authorize(actor,"CREATE");validate(form);String hash=hash(List.of(group,form));authorities.requestLock(actor,form.getRequestKey());var retry=authorities.retry(actor,form.getRequestKey(),hash);if(retry.isPresent())return retry.get();
        var identity=groups.group(actor,group,true);check("ACTIVE".equals(identity.status()),"inactive");check(Objects.equals(authorities.latest(actor,group).orElse(null),form.getExpectedPreviousId()),"changed");
        var ids=form.getSigners().stream().map(SignerForm::getClientId).toList();var people=authorities.people(actor,ids);check(people.size()==ids.size(),"client");
        UUID id=authorities.record(actor,identity,form,hash,clock.now(),people);event(actor,id,"GROUP_AUTHORITY_RECORDED",Map.of("group",group,"signers",ids.size(),"previous",Objects.toString(form.getExpectedPreviousId(),"")));return id;
    }
    @Transactional public void revoke(AppUserPrincipal actor,UUID group,UUID id,String reason){
        authorize(actor,"UPDATE");text(reason,500);groups.group(actor,group,true);var original=authorities.authority(actor,group,id,clock.today());
        if(original.revokedAt()!=null){check(actor.getMemberId().equals(original.revokedBy()) && reason.strip().equals(original.revocationReason()),"changed");return;}
        authorities.revoke(actor,group,id,reason.strip(),clock.now());event(actor,id,"GROUP_AUTHORITY_REVOKED",Map.of("group",group,"reason",reason.strip()));
    }
    private static void rows(AuthorityForm f){check(f!=null && f.getSigners()!=null && f.getSigners().size()<=50 && f.getSigners().stream().allMatch(Objects::nonNull),"request");}
    private static void validate(AuthorityForm f){
        rows(f);check(f.getRequestKey()!=null && f.isVerified(),"authorityVerification");check(f.getValidFrom()!=null && f.getValidThrough()!=null && f.getValidFrom().getYear()>=1 && f.getValidThrough().getYear()<=9999 && !f.getValidThrough().isBefore(f.getValidFrom()),"date");
        BigDecimal amount=f.getMaxPrincipal();check(amount!=null && amount.signum()>0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");
        text(f.getCapacityEvidence(),500);text(f.getResolutionEvidence(),500);text(f.getSignatureEvidence(),500);text(f.getPayoutProvider(),120);text(f.getPayoutHolder(),160);text(f.getPayoutIdentifier(),160);text(f.getPayoutEvidence(),500);check(f.getPayoutType()!=null,"form");
        check(!f.getSigners().isEmpty() && f.getRequiredSignatures()!=null && f.getRequiredSignatures()>0 && f.getRequiredSignatures()<=f.getSigners().size(),"authoritySigners");
        check(f.getSigners().stream().allMatch(s->s.getClientId()!=null && (s.isMayApply() || s.isMaySign() || s.isMayReceive() || s.isMayViewAccount()))
            && f.getSigners().stream().map(SignerForm::getClientId).distinct().count()==f.getSigners().size()
            && f.getSigners().stream().filter(SignerForm::isMaySign).count()>=f.getRequiredSignatures()
            && f.getSigners().stream().anyMatch(SignerForm::isMayApply) && f.getSigners().stream().anyMatch(SignerForm::isMayReceive),"authoritySigners");
    }
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void event(AppUserPrincipal actor,UUID id,String action,Map<String,?> values){audit.logEvent("GROUP_LENDING",id,action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"GROUP_AUTHORITY",id.toString(),actor.getSaccoId(),actor.getStationId(),new LinkedHashMap<>(values));}
    private static void text(String value,int max){check(value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->c<32),"form");}
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("group.error."+key);}
}
