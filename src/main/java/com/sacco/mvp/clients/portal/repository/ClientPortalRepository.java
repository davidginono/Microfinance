package com.sacco.mvp.clients.portal.repository;

import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.*;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class ClientPortalRepository {
    private final JdbcTemplate jdbc;
    public boolean rate(String key,int maximum,int cooldown,OffsetDateTime at) {
        return !jdbc.query("""
            insert into client_portal_rate_limit(key,window_start,last_requested,requests) values(?,?,?,1)
            on conflict(key) do update set window_start=case when client_portal_rate_limit.window_start<=excluded.window_start-interval '15 minutes' then excluded.window_start else client_portal_rate_limit.window_start end,
              requests=case when client_portal_rate_limit.window_start<=excluded.window_start-interval '15 minutes' then 1 else client_portal_rate_limit.requests+1 end,last_requested=excluded.last_requested
            where client_portal_rate_limit.window_start<=excluded.window_start-interval '15 minutes'
              or (client_portal_rate_limit.requests<? and client_portal_rate_limit.last_requested<=excluded.last_requested-(?*interval '1 second')) returning key
            """,(r,n)->r.getString(1),key,at,at,maximum,cooldown).isEmpty();
    }
    public boolean scope(String institution,String branch){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from sacco_stations b join registered_saccos i on i.sacco_id=b.sacco_id where b.sacco_id=? and b.station_id=? and b.active and i.active and coalesce(b.access_status,'ACTIVE')='ACTIVE')",Boolean.class,institution,branch));}
    public List<Choice> choices(String institution,String search,int page) {
        if(institution==null)return jdbc.query("select sacco_id,sacco_name from registered_saccos where active and strpos(lower(sacco_name),?)>0 order by sacco_name,sacco_id limit 26 offset ?",(r,n)->new Choice(r.getString(1),r.getString(2)),search.toLowerCase(Locale.ROOT),page*25);
        return jdbc.query("select station_id,station_id||' - '||coalesce(address_location,'') from sacco_stations where sacco_id=? and active and coalesce(access_status,'ACTIVE')='ACTIVE' and strpos(lower(station_id||' '||coalesce(address_location,'')),?)>0 order by station_id limit 26 offset ?",(r,n)->new Choice(r.getString(1),r.getString(2)),institution,search.toLowerCase(Locale.ROOT),page*25);
    }
    public Optional<Member> client(String number) {
        var ids=jdbc.query("select id from members where upper(member_no)=? and client_portal_enabled=false and status='ACTIVE' and is_member=true and position='MEMBER' limit 2",(r,n)->r.getObject(1,UUID.class),number);
        return ids.size()==1?client(ids.getFirst()):Optional.empty();
    }
    public Optional<Member> client(UUID id) {
        return jdbc.query("select id,sacco_id,station_id,member_no,full_name,email,phone from members where id=? and client_portal_enabled=false and status='ACTIVE' and is_member=true and position='MEMBER'",
            (r,n)->Member.builder().id(r.getObject(1,UUID.class)).saccoId(r.getString(2)).stationId(r.getString(3)).memberNo(r.getString(4)).fullName(r.getString(5)).email(r.getString(6)).phone(r.getString(7)).clientPortalEnabled(false).memberAccount(true).status(MemberStatus.ACTIVE).position(Position.MEMBER).build(),id).stream().findFirst();
    }
    public void create(Challenge q,OffsetDateTime at){jdbc.update("insert into client_portal_challenge(id,client_id,sacco_id,station_id,intent,contact_hash,payload,code_hash,created_at,expires_at) values(?,?,?,?,?,?,?::jsonb,?,?,?)",q.id(),q.clientId(),q.institution(),q.branch(),q.intent(),q.contactHash(),q.payload(),q.codeHash(),at,q.expiresAt());}
    public Optional<Challenge> challenge(UUID id){return jdbc.query("select * from client_portal_challenge where id=? for update",(r,n)->new Challenge(r.getObject("id",UUID.class),r.getObject("client_id",UUID.class),r.getString("sacco_id"),r.getString("station_id"),r.getString("intent"),r.getString("contact_hash"),r.getString("payload"),r.getString("code_hash"),r.getObject("expires_at",OffsetDateTime.class),r.getObject("delivered_at",OffsetDateTime.class),r.getObject("consumed_at",OffsetDateTime.class),r.getInt("attempts")),id).stream().findFirst();}
    public void delivered(UUID id,OffsetDateTime at){jdbc.update("update client_portal_challenge set delivered_at=? where id=? and consumed_at is null",at,id);}
    public void attempted(UUID id){jdbc.update("update client_portal_challenge set attempts=attempts+1 where id=? and attempts<5 and consumed_at is null",id);}
    public void consumed(UUID id,OffsetDateTime at){jdbc.update("update client_portal_challenge set consumed_at=?,payload='{}'::jsonb,code_hash='!CONSUMED' where id=? and consumed_at is null",at,id);}
    public void prune(OffsetDateTime before){jdbc.update("delete from client_portal_challenge where id in(select id from client_portal_challenge where expires_at<? order by expires_at limit 500)",before);jdbc.update("delete from client_portal_rate_limit where key in(select key from client_portal_rate_limit where last_requested<? order by last_requested limit 500)",before);}
}
