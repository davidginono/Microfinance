package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupOrganizationAccountDtos.Account;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupOrganizationAccountRepository {
    private final JdbcTemplate jdbc;
    private static final String SOURCE="""
        from lending_group_cycle c
        join lending_group g on g.id=c.group_id and g.sacco_id=c.sacco_id and g.station_id=c.station_id
        join lending_group_facility f on f.cycle_id=c.id and f.sacco_id=c.sacco_id and f.station_id=c.station_id
        join loan_applications l on l.id=f.loan_id and l.sacco_id=c.sacco_id and l.station_id=c.station_id
        join lending_group_authority h on h.group_id=c.group_id and h.sacco_id=c.sacco_id and h.station_id=c.station_id
        join lending_group_authority_signer s on s.authority_id=h.id and s.client_id=? and s.may_view_account
        where c.sacco_id=? and c.station_id=? and c.model='ORGANIZATION' and g.status='ACTIVE'
          and h.valid_from<=?::date and h.valid_through>=?::date
          and not exists(select 1 from lending_group_authority_revocation r where r.authority_id=h.id)
          and not exists(select 1 from lending_group_authority n where n.group_id=h.group_id and n.revision>h.revision)
        """;
    public List<Account> list(AppUserPrincipal a,LocalDate date,int page){return jdbc.query("select c.id,g.code||' - '||g.name name,coalesce(l.loan_id,l.application_number::text) loan,l.status "+SOURCE+" order by "+com.sacco.mvp.support.ResourceTableSort.order("organization-accounts",Map.of("group","name","loan","loan","state","l.status"),"c.id","c.submitted_at desc,c.id desc")+" limit 26 offset ?",(r,n)->new Account(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4)),a.getMemberId(),a.getSaccoId(),a.getStationId(),date,date,page*25);}
    public boolean permitted(AppUserPrincipal a,UUID cycle,LocalDate date){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 "+SOURCE+" and c.id=?)",Boolean.class,a.getMemberId(),a.getSaccoId(),a.getStationId(),date,date,cycle));}
}
