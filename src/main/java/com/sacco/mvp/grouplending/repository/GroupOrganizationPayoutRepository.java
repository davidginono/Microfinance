package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Challenge;
import com.sacco.mvp.grouplending.dto.GroupOrganizationPayoutDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.PaymentDetailsService.PaymentDetailsView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupOrganizationPayoutRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public Packet own(AppUserPrincipal a,UUID cycle,LocalDate date){return jdbc.query("""
        select p.loan_id,coalesce(l.loan_id,l.application_number::text) number,p.packet_hash,p.snapshot->'organization'->>'name' organization,
          (p.snapshot->'loan'->>'principal')::numeric principal,
          (p.snapshot->'financial'->>'recordedChargeDeductions')::numeric deductions,
          (p.snapshot->'financial'->>'recordedNetCashEstimate')::numeric net,p.snapshot->'payout' destination,
          (select count(*) from lending_group_org_signature s where s.cycle_id=p.cycle_id) signed_count,
          h.required_signatures,c.state,l.status loan_status,p.snapshot->'document'->>'id' is not null full_document,
          g.status='ACTIVE' and h.valid_from<=?::date and h.valid_through>=?::date
            and not exists(select 1 from lending_group_authority_revocation r where r.authority_id=h.id)
            and not exists(select 1 from lending_group_authority n where n.group_id=h.group_id and n.revision>h.revision) current_authority,
          x.client_id confirmed_by,x.client_name,x.client_number,x.authorized_at,x.otp_required
        from lending_group_org_agreement p join lending_group_cycle c on c.id=p.cycle_id
        join loan_applications l on l.id=p.loan_id join lending_group g on g.id=c.group_id
        join lending_group_authority h on h.id=p.authority_id
        join lending_group_authority_signer s on s.authority_id=p.authority_id and s.client_id=? and s.may_receive
        left join lending_group_org_payout x on x.cycle_id=p.cycle_id
        where p.cycle_id=? and p.sacco_id=? and p.station_id=?
        """,(r,n)->new Packet(cycle,r.getObject("loan_id",UUID.class),r.getString("number"),r.getString("organization"),r.getString("packet_hash"),r.getBigDecimal("principal"),r.getBigDecimal("deductions"),r.getBigDecimal("net"),json.readValue(r.getString("destination"),PaymentDetailsView.class),r.getInt("signed_count"),r.getInt("required_signatures"),r.getString("state"),r.getString("loan_status"),r.getBoolean("current_authority"),r.getBoolean("full_document"),false,false,
            r.getObject("confirmed_by")==null?null:new Confirmation(r.getObject("confirmed_by",UUID.class),r.getString("client_name"),r.getString("client_number"),r.getObject("authorized_at",OffsetDateTime.class),r.getBoolean("otp_required"))),date,date,a.getMemberId(),cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Organization payout unavailable"));}
    public void lock(AppUserPrincipal a,UUID cycle){
        if(jdbc.queryForList("""
            select c.id from lending_group_cycle c join lending_group_org_agreement p on p.cycle_id=c.id
            join lending_group_authority_signer s on s.authority_id=p.authority_id and s.client_id=? and s.may_receive
            where c.id=? and c.sacco_id=? and c.station_id=? for share of c
            """,a.getMemberId(),cycle,a.getSaccoId(),a.getStationId()).isEmpty())throw new AccessDeniedException("Organization payout unavailable");
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","ORG_PAYOUT/"+cycle);
        jdbc.queryForList("select g.id from lending_group g join lending_group_cycle c on c.group_id=g.id where c.id=? and c.sacco_id=? and c.station_id=? for share of g",cycle,a.getSaccoId(),a.getStationId());
    }
    public List<Challenge> challenges(AppUserPrincipal a,UUID cycle){return jdbc.query("select id,code_hash,issued_at,expires_at,delivered_at,consumed_at,attempts from lending_group_org_payout_challenge where cycle_id=? and sacco_id=? and station_id=? and client_id=? order by issued_at desc,id desc limit 4 for update",
        (r,n)->new Challenge(r.getObject("id",UUID.class),r.getString("code_hash"),r.getObject("issued_at",OffsetDateTime.class),r.getObject("expires_at",OffsetDateTime.class),r.getObject("delivered_at",OffsetDateTime.class),r.getObject("consumed_at",OffsetDateTime.class),r.getInt("attempts")),cycle,a.getSaccoId(),a.getStationId(),a.getMemberId());}
    public void challenge(AppUserPrincipal a,UUID cycle,String hash,UUID id,String code,OffsetDateTime at,OffsetDateTime expires){jdbc.update("insert into lending_group_org_payout_challenge(id,cycle_id,sacco_id,station_id,client_id,packet_hash,code_hash,issued_at,expires_at) values(?,?,?,?,?,?,?,?,?)",id,cycle,a.getSaccoId(),a.getStationId(),a.getMemberId(),hash,code,at,expires);}
    public void delivered(UUID id,OffsetDateTime at){jdbc.update("update lending_group_org_payout_challenge set delivered_at=? where id=? and delivered_at is null and consumed_at is null",at,id);}
    public void attempt(UUID id){jdbc.update("update lending_group_org_payout_challenge set attempts=attempts+1 where id=? and attempts<5 and consumed_at is null",id);}
    public void consumed(UUID id,OffsetDateTime at){jdbc.update("update lending_group_org_payout_challenge set consumed_at=? where id=? and consumed_at is null",at,id);}
    public void confirm(AppUserPrincipal a,UUID cycle,String hash,String signature,UUID challenge,OffsetDateTime at){jdbc.update("""
        insert into lending_group_org_payout(cycle_id,sacco_id,station_id,client_id,packet_hash,principal,deductions,net_amount,destination,
          client_name,client_number,signature_text,authorized_at,otp_required,challenge_id)
        select p.cycle_id,p.sacco_id,p.station_id,?,p.packet_hash,(p.snapshot->'loan'->>'principal')::numeric,
          (p.snapshot->'financial'->>'recordedChargeDeductions')::numeric,(p.snapshot->'financial'->>'recordedNetCashEstimate')::numeric,p.snapshot->'payout',
          m.full_name,m.member_no,?,?,?,? from lending_group_org_agreement p join members m on m.id=?
        where p.cycle_id=? and p.sacco_id=? and p.station_id=? and p.packet_hash=?
        """,a.getMemberId(),signature,at,challenge!=null,challenge,a.getMemberId(),cycle,a.getSaccoId(),a.getStationId(),hash);}
}
