package com.sacco.mvp.repository;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.dto.OfficeWork;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @RequiredArgsConstructor
public class AdminOfficeWorkRepository {
    private final JdbcTemplate jdbc;
    public List<OfficeWork.Task> staff(AppUserPrincipal a,String search,String sort,int page) {
        String order=switch(sort){case "oldest"->"m.created_at,m.id";case "number"->"m.staff_no,m.id";case "number_desc"->"m.staff_no desc,m.id desc";default->"m.created_at desc,m.id desc";};
        return jdbc.query("""
            select m.id,m.staff_no,m.full_name,m.status from members m where m.sacco_id=? and m.station_id=?
                and m.status='INVITED' and m.position<>'ADMIN'
                and (m.position<>'MEMBER' or exists(select 1 from member_staff_roles staff where staff.member_id=m.id and staff.role_name<>'MEMBER'))
                and not exists(select 1 from member_staff_roles r where r.member_id=m.id and r.role_name='ADMIN')
                and (strpos(lower(coalesce(m.staff_no,'')),?)>0 or strpos(lower(m.full_name),?)>0)
            """+" order by "+order+" limit 26 offset ?",
            (r,n)->new OfficeWork.Task(r.getString("staff_no"),r.getString("full_name"),"office.invited",null,
                a.getClaims().contains("USER_ACCESS_UPDATE")?"/admin/users/"+r.getObject("id",UUID.class)+"/edit":null),
            a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),search.toLowerCase(Locale.ROOT),page*25);
    }
    public List<OfficeWork.Task> support(AppUserPrincipal a,String search,String sort,int page) {
        String order=switch(sort){case "oldest"->"i.created_at,i.id";case "number"->"i.id";case "number_desc"->"i.id desc";default->"i.created_at desc,i.id desc";};
        // Incidents lack a branch field. Unscoped reporters are deliberately excluded.
        return jdbc.query("""
            select i.id,i.subject,i.status from admin_incidents i join members reporter on reporter.id=i.reported_by_member_id
            where i.sacco_id=? and reporter.sacco_id=i.sacco_id and reporter.station_id=?
                and i.status in ('OPEN','IN_PROGRESS') and strpos(lower(i.subject),?)>0
            """+" order by "+order+" limit 26 offset ?",
            (r,n)->new OfficeWork.Task(r.getObject("id",UUID.class).toString(),r.getString("subject"),"office.incident."+r.getString("status"),null,
                "/admin/incidents/"+r.getObject("id",UUID.class)),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);
    }
}
