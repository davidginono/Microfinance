package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupLendingRepository {
    private final JdbcTemplate jdbc;
    private static final String GROUPS = """
        select g.*,(select count(*) from lending_group_member gm where gm.group_id=g.id and gm.ends_on is null) members
        from lending_group g where g.sacco_id=? and g.station_id=?
        """;
    private static final RowMapper<Group> GROUP = (r,n) -> new Group(r.getObject("id",UUID.class),
        r.getString("code"),r.getString("name"),r.getString("legal_form"),r.getString("identity_evidence"),
        r.getString("status"),r.getInt("version"),r.getLong("members"));

    public void lockRequest(AppUserPrincipal a, UUID key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",
            "GROUP/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);
    }
    public Optional<UUID> retry(AppUserPrincipal a, UUID key, String hash, boolean membership) {
        String table=membership?"lending_group_member":"lending_group";
        var rows=jdbc.queryForList("select id,payload_hash,created_by from "+table+
            " where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();
        if(!hash.equals(row.get("payload_hash")) || !a.getMemberId().equals(row.get("created_by")))
            throw new IllegalArgumentException("group.error.retry");
        return Optional.of((UUID)row.get("id"));
    }
    public List<Group> groups(AppUserPrincipal a, String search, String sort, int page) {
        String order=switch(sort){case "code"->"g.code,g.id";case "code_desc"->"g.code desc,g.id";
            case "name_desc"->"g.name desc,g.id";case "legalForm"->"g.legal_form,g.id";case "legalForm_desc"->"g.legal_form desc,g.id";
            case "status"->"g.status,g.id";case "status_desc"->"g.status desc,g.id";
            case "members"->"members,g.id";case "members_desc"->"members desc,g.id";default->"g.name,g.id";};
        return jdbc.query(GROUPS+" and strpos(lower(g.code||' '||g.name),?)>0 order by "+order+" limit 26 offset ?",
            GROUP,a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);
    }
    public Group group(AppUserPrincipal a, UUID id, boolean lock) {
        return jdbc.query(GROUPS+" and g.id=?"+(lock?" for update of g":""),GROUP,a.getSaccoId(),a.getStationId(),id)
            .stream().findFirst().orElseThrow(()->new AccessDeniedException("Group unavailable"));
    }
    public UUID create(AppUserPrincipal a, GroupForm f, String hash, OffsetDateTime now) {
        UUID id=UUID.randomUUID();String code="GRP-"+id.toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);
        jdbc.update("""
            insert into lending_group(id,sacco_id,station_id,request_key,payload_hash,code,name,legal_form,identity_evidence,created_by,created_at)
            values(?,?,?,?,?,?,?,?,?,?,?)
            """,id,a.getSaccoId(),a.getStationId(),f.getRequestKey(),hash,code,f.getName().strip(),f.getLegalForm(),f.getEvidence().strip(),a.getMemberId(),now);
        return id;
    }
    public boolean status(AppUserPrincipal a,UUID id,int version,String state) {
        return jdbc.update("update lending_group set status=?,version=version+1 where id=? and sacco_id=? and station_id=? and version=?",
            state,id,a.getSaccoId(),a.getStationId(),version)==1;
    }
    private static final RowMapper<Membership> MEMBERSHIP=(r,n)->new Membership(r.getObject("id",UUID.class),r.getObject("client_id",UUID.class),r.getString("member_no"),r.getString("full_name"),
        r.getString("office"),r.getObject("starts_on",LocalDate.class),r.getObject("ends_on",LocalDate.class),r.getString("evidence"),r.getString("end_reason"));
    public List<Membership> members(AppUserPrincipal a,UUID group,int page,String sort) {
        String order=switch(sort){case "client"->"m.member_no,m.id";case "client_desc"->"m.member_no desc,m.id";
            case "office"->"gm.office,gm.id";case "office_desc"->"gm.office desc,gm.id";
            case "startsOn"->"gm.starts_on,gm.id";case "endsOn"->"gm.ends_on nulls last,gm.id";case "endsOn_desc"->"gm.ends_on desc nulls last,gm.id";
            case "evidence"->"gm.evidence,gm.id";case "evidence_desc"->"gm.evidence desc,gm.id";default->"gm.starts_on desc,gm.id";};
        return jdbc.query("""
            select gm.*,m.member_no,m.full_name from lending_group_member gm join members m on m.id=gm.client_id
            where gm.group_id=? and gm.sacco_id=? and gm.station_id=? order by
            """+order+" limit 26 offset ?",MEMBERSHIP,
            group,a.getSaccoId(),a.getStationId(),page*25);
    }
    public Membership membership(AppUserPrincipal a,UUID group,UUID member) {
        return jdbc.query("""
            select gm.*,m.member_no,m.full_name from lending_group_member gm join members m on m.id=gm.client_id
            where gm.id=? and gm.group_id=? and gm.sacco_id=? and gm.station_id=?
            """,MEMBERSHIP,member,group,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Membership unavailable"));
    }
    public Optional<Choice> clientChoice(AppUserPrincipal a,UUID id) {
        return jdbc.query("select id,member_no||' - '||full_name from members where id=? and sacco_id=? and station_id=? and is_member",
            (r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),id,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public boolean client(AppUserPrincipal a,UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from members where id=? and sacco_id=? and station_id=? and is_member and status='ACTIVE')",
            Boolean.class,id,a.getSaccoId(),a.getStationId()));
    }
    public List<Choice> clients(AppUserPrincipal a,String search,int page) {
        return jdbc.query("""
            select id,member_no||' - '||full_name label from members where sacco_id=? and station_id=? and is_member and status='ACTIVE'
            and strpos(lower(member_no||' '||full_name),?)>0 order by member_no,id limit 26 offset ?
            """,(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);
    }
    public boolean overlaps(AppUserPrincipal a,UUID group,UUID client,LocalDate start) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from lending_group_member where group_id=? and sacco_id=? and station_id=? and client_id=?
            and (ends_on is null or ends_on>=?))
            """,Boolean.class,group,a.getSaccoId(),a.getStationId(),client,start));
    }
    public UUID addMember(AppUserPrincipal a,UUID group,MemberForm f,String hash,OffsetDateTime now) {
        UUID id=UUID.randomUUID();jdbc.update("""
            insert into lending_group_member(id,group_id,sacco_id,station_id,client_id,office,starts_on,evidence,created_by,created_at,request_key,payload_hash)
            values(?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,group,a.getSaccoId(),a.getStationId(),f.getClientId(),f.getOffice(),f.getStartsOn(),f.getEvidence().strip(),a.getMemberId(),now,f.getRequestKey(),hash);
        return id;
    }
    public boolean endMember(AppUserPrincipal a,UUID group,UUID member,LocalDate date,String reason) {
        return jdbc.update("""
            update lending_group_member set ends_on=?,ended_by=?,end_reason=? where id=? and group_id=? and sacco_id=? and station_id=?
            and ends_on is null and starts_on<=?
            """,date,a.getMemberId(),reason.strip(),member,group,a.getSaccoId(),a.getStationId(),date)==1;
    }
}
