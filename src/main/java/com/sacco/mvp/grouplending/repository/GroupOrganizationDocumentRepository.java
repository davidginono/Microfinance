package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupOrganizationDocumentRepository {
    private final JdbcTemplate jdbc;
    public byte[] body(AppUserPrincipal a,UUID cycle,UUID id){return jdbc.query("select body from lending_group_org_terms where id=? and cycle_id=? and sacco_id=? and station_id=?",(r,n)->r.getBytes(1),id,cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Agreement document unavailable"));}
    public List<Signature> signatures(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        select n.name,n.client_number,s.signed_at,s.otp_required,false lender,s.preferred_language,s.paper_id is not null paper from lending_group_org_signature s
        join lending_group_org_agreement p on p.cycle_id=s.cycle_id
        join lending_group_authority_signer n on n.authority_id=p.authority_id and n.client_id=s.client_id
        where s.cycle_id=? and s.sacco_id=? and s.station_id=?
        union all select actor_name,actor_number,signed_at,false,true,null::varchar,false from lending_group_org_funding
        where cycle_id=? and sacco_id=? and station_id=? order by signed_at,client_number limit 51
        """,(r,n)->new Signature(r.getString("name"),r.getString("client_number"),r.getObject("signed_at",OffsetDateTime.class),r.getBoolean("otp_required"),r.getBoolean("lender"),r.getString("preferred_language"),r.getBoolean("paper")),cycle,a.getSaccoId(),a.getStationId(),cycle,a.getSaccoId(),a.getStationId());}
}
