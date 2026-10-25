package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupOrganizationRepository {
    private final JdbcTemplate jdbc;
    public boolean enabled(AppUserPrincipal a,UUID policy){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_policy where id=? and sacco_id=? and station_id=? and model='ORGANIZATION' and capability_version=1)",Boolean.class,policy,a.getSaccoId(),a.getStationId()));}
    public UUID party(AppUserPrincipal a,UUID group){
        jdbc.update("insert into loan_borrower_party(id,sacco_id,kind,group_id,group_station_id) values(?,?,'ORGANIZATION',?,?) on conflict(sacco_id,group_id,kind) do nothing",UUID.randomUUID(),a.getSaccoId(),group,a.getStationId());
        return jdbc.queryForObject("select id from loan_borrower_party where sacco_id=? and group_station_id=? and group_id=? and kind='ORGANIZATION'",UUID.class,a.getSaccoId(),a.getStationId(),group);
    }
    public Exposure exposure(AppUserPrincipal a,UUID party){return jdbc.queryForObject("""
        select count(*) active_loans,count(*) filter(where status in ('PAR','DEFAULTED','WRITTEN_OFF')) risk_loans,
          coalesce(sum(case when repayment_frequency='WEEKLY' then installment_amount*52/12 else installment_amount end),0) monthly_debt,
          count(*) filter(where installment_amount is null or installment_amount<=0 or repayment_frequency is null
            or repayment_frequency not in ('MONTHLY','WEEKLY') or (status in ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') and not exists(select 1 from loan_ledgers b where b.loan_application_id=l.id))) missing_history
        from loan_applications l where sacco_id=? and station_id=? and borrower_party_id=? and status not in
          ('WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
        """,(r,n)->new Exposure(r.getInt("active_loans"),r.getInt("active_loans"),r.getInt("risk_loans"),0,r.getBigDecimal("monthly_debt"),BigDecimal.ZERO,r.getInt("missing_history")),a.getSaccoId(),a.getStationId(),party);}
    public boolean usedAuthority(AppUserPrincipal a,UUID authority){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_facility where sacco_id=? and station_id=? and authority_id=?)",Boolean.class,a.getSaccoId(),a.getStationId(),authority));}
    public void record(AppUserPrincipal a,Cycle cycle,UUID loan,OrganizationForm form,BigDecimal monthly,OffsetDateTime now){jdbc.update("""
        insert into lending_group_facility(cycle_id,loan_id,group_id,sacco_id,station_id,model,authority_id,representative_id,monthly_payment,created_by,created_at)
        values(?,?,?,?,?,'ORGANIZATION',?,?,?,?,?)
        """,cycle.id(),loan,cycle.groupId(),a.getSaccoId(),a.getStationId(),form.getAuthorityId(),form.getRepresentativeId(),monthly,a.getMemberId(),now);}
    public Optional<OrganizationLoan> loan(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        select f.*,coalesce(l.loan_id,l.application_number::text) number,l.status,l.amount,s.name representative
        from lending_group_facility f join loan_applications l on l.id=f.loan_id and l.sacco_id=f.sacco_id and l.station_id=f.station_id
        join lending_group_authority_signer s on s.authority_id=f.authority_id and s.client_id=f.representative_id
        where f.cycle_id=? and f.sacco_id=? and f.station_id=?
        """,(r,n)->new OrganizationLoan(r.getObject("loan_id",UUID.class),r.getString("number"),r.getString("status"),r.getObject("authority_id",UUID.class),r.getObject("representative_id",UUID.class),r.getString("representative"),r.getBigDecimal("amount"),r.getBigDecimal("monthly_payment")),cycle,a.getSaccoId(),a.getStationId()).stream().findFirst();}
}
