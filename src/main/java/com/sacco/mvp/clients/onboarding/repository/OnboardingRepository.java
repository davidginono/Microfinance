package com.sacco.mvp.clients.onboarding.repository;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class OnboardingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final String CLIENT_SELECT="""
        select m.id,m.member_no,m.full_name,m.phone,m.email,c.id revision_id,coalesce((select max(sequence) from client_onboarding_revision r where r.client_id=m.id),0) sequence,c.review_due,
          case when c.state='VERIFY' and (m.full_name is distinct from c.profile->>'fullName' or m.phone is distinct from c.profile->>'phone' or m.email is distinct from c.profile->>'email') then 'STALE' when c.state='VERIFY' and (c.review_due<? or (c.profile->>'identityExpiresOn')::date<? or (c.profile->>'retentionReviewOn')::date<?
            or exists(select 1 from client_onboarding_document_link x join client_onboarding_document d on d.id=x.document_id where x.revision_id=c.id and d.expires_on<?))
            then 'EXPIRED' else coalesce(c.state,'NOT_STARTED') end state
        from members m left join lateral(select * from client_onboarding_current where client_id=m.id limit 1) c on c.sacco_id=m.sacco_id and c.station_id=m.station_id
        where m.sacco_id=? and m.station_id=? and (m.is_member or (m.is_member is null and m.position='MEMBER'))
        """;
    private final RowMapper<Client> clientMapper=(r,n)->new Client(r.getObject("id",UUID.class),r.getString("member_no"),r.getString("full_name"),r.getString("phone"),r.getString("email"),r.getString("state"),r.getObject("revision_id",UUID.class),r.getInt("sequence"),r.getObject("review_due",LocalDate.class));
    public List<Client> clients(AppUserPrincipal a,String search,String state,int page,String order,LocalDate day) {
        return jdbc.query("select * from ("+CLIENT_SELECT+" and (m.member_no ilike ? escape '!' or m.full_name ilike ? escape '!')) q where (?='' or state=?) order by "+order+",id limit 26 offset ?",
            clientMapper,day,day,day,day,a.getSaccoId(),a.getStationId(),pattern(search),pattern(search),state,state,page*25);
    }
    public Client client(AppUserPrincipal a,UUID id,LocalDate day) {
        return jdbc.query(CLIENT_SELECT+" and m.id=?",clientMapper,day,day,day,day,a.getSaccoId(),a.getStationId(),id).stream().findFirst().orElseThrow(()->new AccessDeniedException("Client unavailable"));
    }
    public Optional<Revision> latest(AppUserPrincipal a,UUID client) {
        return jdbc.query("select r.*,m.full_name maker from client_onboarding_revision r join members m on m.id=r.actor_id where r.client_id=? and r.sacco_id=? and r.station_id=? order by r.sequence desc limit 1",
            (r,n)->new Revision(r.getObject("id",UUID.class),client,r.getInt("sequence"),r.getString("mode"),json.readValue(r.getString("profile"),Profile.class),r.getObject("actor_id",UUID.class),r.getString("maker"),r.getObject("recorded_at",OffsetDateTime.class)),client,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public List<Decision> decisions(UUID revision) {
        return jdbc.query("select d.*,m.full_name actor from client_onboarding_decision d join members m on m.id=d.actor_id where revision_id=? order by d.recorded_at desc,(d.kind='REVOKE') desc limit 2",
            (r,n)->new Decision(r.getObject("id",UUID.class),r.getString("kind"),r.getString("reason"),r.getString("client_message"),r.getString("screening"),r.getString("screening_reference"),r.getObject("checked_on",LocalDate.class),r.getObject("review_due",LocalDate.class),r.getString("policy_reference"),r.getObject("actor_id",UUID.class),r.getString("actor"),r.getObject("recorded_at",OffsetDateTime.class)),revision);
    }
    public Revision version(AppUserPrincipal a,UUID client,int sequence) {
        return jdbc.query("select r.*,m.full_name maker from client_onboarding_revision r join members m on m.id=r.actor_id where r.client_id=? and r.sacco_id=? and r.station_id=? and r.sequence=?",
            (r,n)->new Revision(r.getObject("id",UUID.class),client,r.getInt("sequence"),r.getString("mode"),json.readValue(r.getString("profile"),Profile.class),r.getObject("actor_id",UUID.class),r.getString("maker"),r.getObject("recorded_at",OffsetDateTime.class)),client,a.getSaccoId(),a.getStationId(),sequence).stream().findFirst().orElseThrow(()->new AccessDeniedException("Version unavailable"));
    }
    public List<History> history(AppUserPrincipal a,UUID client,int page) {
        return jdbc.query("""
            select r.sequence,r.mode,coalesce(d.kind,r.mode) result,m.full_name,r.recorded_at from client_onboarding_revision r
            join members m on m.id=r.actor_id left join lateral(select kind from client_onboarding_decision where revision_id=r.id order by recorded_at desc,(kind='REVOKE') desc limit 1)d on true
            where r.client_id=? and r.sacco_id=? and r.station_id=? order by r.sequence desc limit 26 offset ?
            """,(r,n)->new History(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,OffsetDateTime.class)),client,a.getSaccoId(),a.getStationId(),page*25);
    }
    public void lock(UUID client) {lock("CLIENT_KYC/"+client);}
    public void lock(String key) {jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,key);}
    public Optional<UUID> retry(AppUserPrincipal a,String table,UUID request,String hash) {
        var rows=jdbc.query("select id,case when actor_id=? then payload_hash else '' end from "+table+" where sacco_id=? and station_id=? and request_key=?",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),a.getMemberId(),a.getSaccoId(),a.getStationId(),request);
        if(rows.isEmpty())return Optional.empty();
        if(!rows.getFirst().getValue().equals(hash))throw new IllegalArgumentException("kyc.error.retry");
        return Optional.of(rows.getFirst().getKey());
    }
    public UUID created(UUID request) {return jdbc.queryForObject("select client_id from client_onboarding_creation where id=?",UUID.class,request);}
    public void creation(AppUserPrincipal a,UUID client,UUID id,UUID key,String hash,OffsetDateTime now) {
        jdbc.update("insert into client_onboarding_creation values(?,?,?,?,?,?,?,?)",id,client,a.getSaccoId(),a.getStationId(),key,hash,a.getMemberId(),now);
    }
    public void revision(AppUserPrincipal a,UUID client,UUID id,int sequence,String mode,Profile profile,String identity,UUID key,String hash,OffsetDateTime now) {
        jdbc.update("insert into client_onboarding_revision values(?,?,?,?,?,?,?::jsonb,?,?,?,?,?)",id,client,a.getSaccoId(),a.getStationId(),sequence,mode,json.writeValueAsString(profile),identity,key,hash,a.getMemberId(),now);
    }
    public void copyDocuments(AppUserPrincipal a,UUID client,UUID from,UUID to) {
        if(from==null)return;
        jdbc.update("""
            insert into client_onboarding_document_link
            select ?,x.document_id,x.client_id,x.sacco_id,x.station_id from client_onboarding_document_link x
            where x.revision_id=? and x.document_id in(select distinct on(d.category) d.id from client_onboarding_document_link y join client_onboarding_document d on d.id=y.document_id where y.revision_id=? order by d.category,d.sequence desc)
            """,to,from,from);
    }
    public List<Document> documents(UUID revision) {
        return jdbc.query("""
            select distinct on(d.category) d.id,d.category,u.original_name,d.expires_on,u.sha256,m.full_name,d.recorded_at
            from client_onboarding_document_link x join client_onboarding_document d on d.id=x.document_id join stored_uploads u on u.id=d.upload_id join members m on m.id=d.actor_id
            where x.revision_id=? order by d.category,d.sequence desc
            """,(r,n)->new Document(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,LocalDate.class),r.getString(5),r.getString(6),r.getObject(7,OffsetDateTime.class)),revision);
    }
    public void document(AppUserPrincipal a,UUID client,UUID revision,UUID id,UUID upload,DocumentForm f,String hash,OffsetDateTime now) {
        jdbc.update("insert into client_onboarding_document values(?,?,?,?,?,?,?,?,?,?,?, (select coalesce(max(sequence),0)+1 from client_onboarding_document where client_id=?))",
            id,client,a.getSaccoId(),a.getStationId(),upload,f.getCategory(),f.getExpiresOn(),f.getRequestKey(),hash,a.getMemberId(),now,client);
        jdbc.update("insert into client_onboarding_document_link values(?,?,?,?,?)",revision,id,client,a.getSaccoId(),a.getStationId());
    }
    public UUID upload(AppUserPrincipal a,UUID client,UUID document) {
        return jdbc.query("select upload_id from client_onboarding_document where id=? and client_id=? and sacco_id=? and station_id=?",(r,n)->r.getObject(1,UUID.class),document,client,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Document unavailable"));
    }
    public boolean contributed(UUID revision,UUID actor) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from client_onboarding_document_link x join client_onboarding_document d on d.id=x.document_id where x.revision_id=? and d.actor_id=?)",Boolean.class,revision,actor));
    }
    public boolean duplicate(String institution,String identity,UUID client) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from client_onboarding_identity where sacco_id=? and identity_key=? and client_id<>?)
              or exists(select 1 from client_onboarding_revision r where r.sacco_id=? and r.identity_key=? and r.client_id<>? and r.mode='SUBMITTED'
                and not exists(select 1 from client_onboarding_revision newer where newer.client_id=r.client_id and newer.sequence>r.sequence)
                and not exists(select 1 from client_onboarding_decision d where d.revision_id=r.id and d.kind in('RETURN','REJECT','REVOKE')))
            """,Boolean.class,institution,identity,client,institution,identity,client));
    }
    public void decision(AppUserPrincipal a,UUID client,UUID id,ReviewForm f,String hash,OffsetDateTime now) {
        jdbc.update("insert into client_onboarding_decision values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,f.getRevisionId(),client,a.getSaccoId(),a.getStationId(),f.getKind(),f.getReason().strip(),f.isIdentityChecked(),f.isAddressChecked(),f.isActivityChecked(),f.isContactChecked(),f.isDuplicatesChecked(),f.getScreening(),f.getScreeningReference().strip(),f.getCheckedOn(),f.getReviewDue(),f.getPolicyReference().strip(),f.getRequestKey(),hash,a.getMemberId(),now,f.getClientMessage().strip());
    }
    public Optional<Notice> notice(AppUserPrincipal a) {
        return jdbc.query("select * from client_onboarding_notice where sacco_id=? and station_id=? order by sequence desc limit 1",
            (r,n)->new Notice(r.getObject("id",UUID.class),r.getInt("sequence"),r.getBoolean("enabled"),r.getString("version"),r.getString("english"),r.getString("kiswahili"),r.getString("lawful_basis"),r.getString("retention_reference"),r.getObject("retention_review_on",LocalDate.class)),a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public void notice(AppUserPrincipal a,UUID id,NoticeForm f,String hash,OffsetDateTime now) {
        jdbc.update("insert into client_onboarding_notice values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),f.getExpectedSequence()+1,f.isEnabled(),f.getVersion(),f.getEnglish(),f.getKiswahili(),f.getLawfulBasis(),f.getRetentionReference(),f.getRetentionReviewOn(),f.getRequestKey(),hash,a.getMemberId(),now);
    }
    public Feedback feedback(AppUserPrincipal a,UUID client,UUID revision) {
        return jdbc.query("select d.kind,d.client_message,d.recorded_at from client_onboarding_decision d join client_onboarding_revision r on r.id=d.revision_id where d.client_id=? and d.sacco_id=? and d.station_id=? and (?::uuid is null or d.revision_id=?) order by r.sequence desc,d.recorded_at desc,(d.kind='REVOKE') desc,d.id desc limit 1",
            (r,n)->new Feedback(r.getString(1),r.getString(2),r.getObject(3,OffsetDateTime.class)),client,a.getSaccoId(),a.getStationId(),revision,revision).stream().findFirst().filter(f->!f.message().isBlank()).orElse(null);
    }
    private static String pattern(String s) {return "%"+s.replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
}
