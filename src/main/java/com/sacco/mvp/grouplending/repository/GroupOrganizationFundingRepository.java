package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupOrganizationFundingDtos.Context;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupOrganizationFundingRepository {
    private final JdbcTemplate jdbc;
    public void lock(AppUserPrincipal a,UUID loan){
        var cycles=jdbc.queryForList("select c.id from lending_group_facility f join lending_group_cycle c on c.id=f.cycle_id where f.loan_id=? and f.sacco_id=? and f.station_id=? for share of c",loan,a.getSaccoId(),a.getStationId());
        if(!cycles.isEmpty())jdbc.queryForList("select g.id from lending_group_facility f join lending_group g on g.id=f.group_id where f.loan_id=? and f.sacco_id=? and f.station_id=? for share of g",loan,a.getSaccoId(),a.getStationId());
    }
    public Optional<Context> context(AppUserPrincipal a,UUID loan,LocalDate date){return jdbc.query("""
        select p.cycle_id,p.packet_hash,l.amount principal,q.net_amount,l.first_repayment_date,
          c.state='SUBMITTED' and l.status='READY_FOR_DISBURSEMENT' and g.status='ACTIVE'
          and ?::date between h.valid_from and h.valid_through
          and not exists(select 1 from lending_group_authority_revocation r where r.authority_id=h.id)
          and not exists(select 1 from lending_group_authority n where n.group_id=h.group_id and n.revision>h.revision)
          and p.snapshot->'document' is not null and p.snapshot->'document'<>'null'::jsonb
          and (select count(*) from lending_group_org_signature s where s.cycle_id=p.cycle_id)>=h.required_signatures
          and q.cycle_id is not null
          and p.snapshot->'financial'->>'tenureUnit' in ('MONTHS','YEARS')
          and exists(select 1 from lending_group_authority_signer s join members m on m.id=s.client_id
            where s.authority_id=h.id and s.client_id=q.client_id and s.may_receive
            and m.sacco_id=p.sacco_id and m.station_id=p.station_id and m.status='ACTIVE' and m.is_member) ready
        from lending_group_org_agreement p join loan_applications l on l.id=p.loan_id
        join lending_group_cycle c on c.id=p.cycle_id join lending_group g on g.id=c.group_id
        join lending_group_authority h on h.id=p.authority_id
        left join lending_group_org_payout q on q.cycle_id=p.cycle_id and q.packet_hash=p.packet_hash
        where p.loan_id=? and p.sacco_id=? and p.station_id=?
        """,(r,n)->new Context(r.getObject("cycle_id",UUID.class),r.getString("packet_hash"),r.getBigDecimal("principal"),r.getBigDecimal("net_amount"),r.getObject("first_repayment_date",LocalDate.class),r.getBoolean("ready")),date,loan,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public void execute(AppUserPrincipal a,UUID loan,UUID posting,UUID journal,String hash,OffsetDateTime at){jdbc.update("""
        insert into lending_group_org_funding(cycle_id,loan_id,sacco_id,station_id,posting_id,journal_id,packet_hash,
          actor_id,actor_name,actor_number,signature_text,signed_at)
        select p.cycle_id,p.loan_id,p.sacco_id,p.station_id,?,?,p.packet_hash,m.id,m.full_name,coalesce(m.staff_no,m.member_no),m.signature_text,?
        from lending_group_org_agreement p join members m on m.id=? and m.sacco_id=p.sacco_id and m.station_id=p.station_id
        where p.loan_id=? and p.sacco_id=? and p.station_id=? and p.packet_hash=?
        """,posting,journal,at,a.getMemberId(),loan,a.getSaccoId(),a.getStationId(),hash);}
}
