package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupOrganizationAgreementDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
        """,(r,n)->new Row(r.getObject("cycle_id",UUID.class),json.readValue(r.getString("snapshot"),Snapshot.class),r.getString("packet_hash"),r.getObject("prepared_at",OffsetDateTime.class),r.getString("state"),r.getString("loan_status"),r.getInt("signed_count"),false,null,false,false),loan,a.getSaccoId(),a.getStationId()).stream().findFirst();}

}
