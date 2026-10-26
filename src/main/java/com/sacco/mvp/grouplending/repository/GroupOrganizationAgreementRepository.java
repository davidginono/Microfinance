package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupOrganizationAgreementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Challenge;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupOrganizationAgreementRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public Optional<Row> staffLoan(AppUserPrincipal a,UUID loan){return jdbc.query("""
        select p.*,c.state,l.status loan_status,(select count(*) from lending_group_org_signature s where s.cycle_id=p.cycle_id) signed_count
        from lending_group_org_agreement p join lending_group_cycle c on c.id=p.cycle_id join loan_applications l on l.id=p.loan_id
        where p.loan_id=? and p.sacco_id=? and p.station_id=?
        """,(r,n)->new Row(r.getObject("cycle_id",UUID.class),json.readValue(r.getString("snapshot"),Snapshot.class),r.getString("packet_hash"),r.getObject("prepared_at",OffsetDateTime.class),r.getString("state"),r.getString("loan_status"),r.getInt("signed_count"),false,null,false),loan,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public Row own(AppUserPrincipal a,UUID cycle,LocalDate date){return jdbc.query("""
        select p.*,c.state,l.status loan_status,own.signed_at,
          (select count(*) from lending_group_org_signature s where s.cycle_id=p.cycle_id) signed_count,
          g.status='ACTIVE' and h.valid_from<=?::date and h.valid_through>=?::date
          and not exists(select 1 from lending_group_authority_revocation r where r.authority_id=h.id)
          and not exists(select 1 from lending_group_authority n where n.group_id=h.group_id and n.revision>h.revision) current_authority
        from lending_group_org_agreement p join lending_group_cycle c on c.id=p.cycle_id
        join loan_applications l on l.id=p.loan_id join lending_group_authority h on h.id=p.authority_id
        join lending_group g on g.id=c.group_id
        join lending_group_authority_signer s on s.authority_id=p.authority_id and s.client_id=? and s.may_sign
        left join lending_group_org_signature own on own.cycle_id=p.cycle_id and own.client_id=s.client_id
        where p.cycle_id=? and p.sacco_id=? and p.station_id=?
        """,(r,n)->new Row(cycle,json.readValue(r.getString("snapshot"),Snapshot.class),r.getString("packet_hash"),r.getObject("prepared_at",OffsetDateTime.class),r.getString("state"),r.getString("loan_status"),r.getInt("signed_count"),r.getObject("signed_at")!=null,r.getObject("signed_at",OffsetDateTime.class),r.getBoolean("current_authority")),date,date,a.getMemberId(),cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Organization agreement unavailable"));}
    public void lock(AppUserPrincipal a,UUID cycle){
        var rows=jdbc.queryForList("""
            select c.id from lending_group_cycle c join lending_group_org_agreement p on p.cycle_id=c.id
            join lending_group_authority_signer s on s.authority_id=p.authority_id and s.client_id=? and s.may_sign
            where c.id=? and c.sacco_id=? and c.station_id=? for share of c
            """,a.getMemberId(),cycle,a.getSaccoId(),a.getStationId());
        if(rows.isEmpty())throw new AccessDeniedException("Organization agreement unavailable");
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","ORG_AGREEMENT/"+cycle+"/"+a.getMemberId());
        jdbc.queryForList("select g.id from lending_group g join lending_group_cycle c on c.group_id=g.id where c.id=? and c.sacco_id=? and c.station_id=? for share of g",cycle,a.getSaccoId(),a.getStationId());
    }
    public List<Challenge> challenges(AppUserPrincipal a,UUID cycle){return jdbc.query("select * from lending_group_org_challenge where cycle_id=? and sacco_id=? and station_id=? and client_id=? order by issued_at desc,id desc limit 4 for update",
        (r,n)->new Challenge(r.getObject("id",UUID.class),r.getString("code_hash"),r.getObject("issued_at",OffsetDateTime.class),r.getObject("expires_at",OffsetDateTime.class),r.getObject("delivered_at",OffsetDateTime.class),r.getObject("consumed_at",OffsetDateTime.class),r.getInt("attempts")),cycle,a.getSaccoId(),a.getStationId(),a.getMemberId());}
    public void challenge(AppUserPrincipal a,UUID cycle,String packet,UUID id,String code,OffsetDateTime issued,OffsetDateTime expires){jdbc.update("insert into lending_group_org_challenge(id,cycle_id,sacco_id,station_id,client_id,packet_hash,code_hash,issued_at,expires_at) values(?,?,?,?,?,?,?,?,?)",id,cycle,a.getSaccoId(),a.getStationId(),a.getMemberId(),packet,code,issued,expires);}
    public void delivered(UUID id,OffsetDateTime at){jdbc.update("update lending_group_org_challenge set delivered_at=? where id=? and delivered_at is null and consumed_at is null",at,id);}
    public void attempt(UUID id){jdbc.update("update lending_group_org_challenge set attempts=attempts+1 where id=? and attempts<5 and consumed_at is null",id);}
    public void consumed(UUID id,OffsetDateTime at){jdbc.update("update lending_group_org_challenge set consumed_at=? where id=? and consumed_at is null",at,id);}
    public void sign(AppUserPrincipal a,UUID cycle,String packet,String signature,UUID challenge,OffsetDateTime at){jdbc.update("insert into lending_group_org_signature(cycle_id,sacco_id,station_id,client_id,packet_hash,signature_text,signed_at,otp_required,challenge_id) values(?,?,?,?,?,?,?,?,?)",cycle,a.getSaccoId(),a.getStationId(),a.getMemberId(),packet,signature,at,challenge!=null,challenge);}
}
