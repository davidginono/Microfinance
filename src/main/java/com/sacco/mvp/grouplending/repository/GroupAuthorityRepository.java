package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.domain.PaymentDestinationType;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupAuthorityRepository {
    private final JdbcTemplate jdbc;
    private static final String STATE="""
        case when r.authority_id is not null then 'REVOKED'
          when exists(select 1 from lending_group_authority n where n.group_id=a.group_id and n.revision>a.revision) then 'SUPERSEDED'
          when g.status<>'ACTIVE' then 'INACTIVE' when a.valid_from>?::date then 'NOT_YET_VALID'
          when a.valid_through<?::date then 'EXPIRED' else 'CURRENT' end state
        """;
    private static final String FROM="""
        from lending_group_authority a join lending_group g on g.id=a.group_id and g.sacco_id=a.sacco_id and g.station_id=a.station_id
        left join lending_group_authority_revocation r on r.authority_id=a.id
        where a.sacco_id=? and a.station_id=? and a.group_id=?
        """;
    public void requestLock(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","GROUP_AUTHORITY/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String hash){
        var rows=jdbc.queryForList("select id,payload_hash,recorded_by from lending_group_authority where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!hash.equals(row.get("payload_hash")) || !a.getMemberId().equals(row.get("recorded_by")))throw new IllegalArgumentException("group.error.retry");return Optional.of((UUID)row.get("id"));
    }
    public Optional<UUID> latest(AppUserPrincipal a,UUID group){return jdbc.query("select id from lending_group_authority where sacco_id=? and station_id=? and group_id=? order by revision desc limit 1",(r,n)->r.getObject(1,UUID.class),a.getSaccoId(),a.getStationId(),group).stream().findFirst();}
    public List<Summary> history(AppUserPrincipal a,UUID group,LocalDate date,int page,String sort){
        var column=Map.of("revision","a.revision","from","a.valid_from","through","a.valid_through","principal","a.max_principal","state","state").get(sort.replace("_desc",""));
        if(column==null)throw new IllegalArgumentException("group.error.query");
        return jdbc.query("select a.id,a.revision,a.valid_from,a.valid_through,a.max_principal,"+STATE+FROM+" order by "+column+(sort.endsWith("_desc")?" desc":" asc")+",a.id limit 26 offset ?",
            (r,n)->new Summary(r.getObject("id",UUID.class),r.getInt("revision"),r.getObject("valid_from",LocalDate.class),r.getObject("valid_through",LocalDate.class),r.getBigDecimal("max_principal"),r.getString("state")),date,date,a.getSaccoId(),a.getStationId(),group,page*25);
    }
    public List<Choice> clients(AppUserPrincipal a,String search,int page){return jdbc.query("select id,member_no||' - '||full_name label from members where sacco_id=? and station_id=? and is_member and status='ACTIVE' and strpos(lower(member_no||' '||full_name),?)>0 order by member_no,id limit 26 offset ?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Map<UUID,Signer> people(AppUserPrincipal a,List<UUID> ids){
        if(ids.isEmpty())return Map.of();var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId()));args.addAll(ids);var result=new LinkedHashMap<UUID,Signer>();
        jdbc.query("select id,member_no,full_name from members where sacco_id=? and station_id=? and is_member and status='ACTIVE' and id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by id",(RowCallbackHandler)r->{var id=r.getObject(1,UUID.class);result.put(id,new Signer(id,r.getString(2),r.getString(3),false,false,false,false));},args.toArray());return result;
    }
    public UUID record(AppUserPrincipal a,Group group,AuthorityForm f,String hash,OffsetDateTime now,Map<UUID,Signer> people){
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into lending_group_authority(id,group_id,sacco_id,station_id,revision,previous_id,request_key,payload_hash,
              group_name,group_code,legal_form,valid_from,valid_through,max_principal,required_signatures,signer_count,
              capacity_evidence,resolution_evidence,signature_evidence,payout_type,payout_provider,payout_holder,payout_identifier,payout_evidence,recorded_by,recorded_by_name,recorded_at)
            select ?,?,?,?,coalesce((select max(revision) from lending_group_authority where group_id=?),0)+1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,m.full_name,?
            from members m where m.id=? and m.sacco_id=? and m.station_id=?
            """,id,group.id(),a.getSaccoId(),a.getStationId(),group.id(),f.getExpectedPreviousId(),f.getRequestKey(),hash,
            group.name(),group.code(),group.legalForm(),f.getValidFrom(),f.getValidThrough(),f.getMaxPrincipal(),f.getRequiredSignatures(),f.getSigners().size(),
            f.getCapacityEvidence().strip(),f.getResolutionEvidence().strip(),f.getSignatureEvidence().strip(),f.getPayoutType().name(),f.getPayoutProvider().strip(),f.getPayoutHolder().strip(),f.getPayoutIdentifier().strip(),f.getPayoutEvidence().strip(),a.getMemberId(),now,a.getMemberId(),a.getSaccoId(),a.getStationId());
        var rows=new ArrayList<Object[]>();int ordinal=0;for(var signer:f.getSigners()){var p=people.get(signer.getClientId());rows.add(new Object[]{id,++ordinal,p.clientId(),p.number(),p.name(),signer.isMayApply(),signer.isMaySign(),signer.isMayReceive(),signer.isMayViewAccount()});}
        jdbc.batchUpdate("insert into lending_group_authority_signer(authority_id,ordinal,client_id,client_number,name,may_apply,may_sign,may_receive,may_view_account) values(?,?,?,?,?,?,?,?,?)",rows);return id;
    }
    public Authority authority(AppUserPrincipal actor,UUID group,UUID id,LocalDate date){
        var rows=jdbc.query("select a.*,r.reason,r.actor_id revoked_by,r.actor_name revoked_by_name,r.recorded_at revoked_at,"+STATE+FROM+" and a.id=?",(r,n)->new Authority(r.getObject("id",UUID.class),group,r.getInt("revision"),r.getObject("previous_id",UUID.class),r.getString("group_name"),r.getString("group_code"),r.getString("legal_form"),r.getObject("valid_from",LocalDate.class),r.getObject("valid_through",LocalDate.class),r.getBigDecimal("max_principal"),r.getInt("required_signatures"),r.getString("capacity_evidence"),r.getString("resolution_evidence"),r.getString("signature_evidence"),PaymentDestinationType.valueOf(r.getString("payout_type")),r.getString("payout_provider"),r.getString("payout_holder"),r.getString("payout_identifier"),r.getString("payout_evidence"),r.getObject("recorded_by",UUID.class),r.getString("recorded_by_name"),r.getObject("recorded_at",OffsetDateTime.class),r.getString("state"),r.getString("reason"),r.getObject("revoked_by",UUID.class),r.getString("revoked_by_name"),r.getObject("revoked_at",OffsetDateTime.class),signers(id)),date,date,actor.getSaccoId(),actor.getStationId(),group,id);
        if(rows.isEmpty())throw new AccessDeniedException("Authority unavailable");return rows.getFirst();
    }
    private List<Signer> signers(UUID authority){return jdbc.query("select client_id,client_number,name,may_apply,may_sign,may_receive,may_view_account from lending_group_authority_signer where authority_id=? order by ordinal limit 50",(r,n)->new Signer(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBoolean(4),r.getBoolean(5),r.getBoolean(6),r.getBoolean(7)),authority);}
    public void revoke(AppUserPrincipal a,UUID group,UUID id,String reason,OffsetDateTime now){jdbc.update("insert into lending_group_authority_revocation(authority_id,group_id,sacco_id,station_id,actor_id,actor_name,recorded_at,reason) select ?,?,?,?,?,m.full_name,?,? from members m where m.id=? and m.sacco_id=? and m.station_id=?",id,group,a.getSaccoId(),a.getStationId(),a.getMemberId(),now,reason,a.getMemberId(),a.getSaccoId(),a.getStationId());}
}
