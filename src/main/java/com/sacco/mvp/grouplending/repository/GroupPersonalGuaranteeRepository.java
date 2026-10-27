package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Choice;
import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Challenge;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupPersonalGuaranteeRepository {
    private final JdbcTemplate jdbc;
    private static final String SOURCE="""
        select c.id cycle,l.id loan,coalesce(l.loan_id,l.application_number::text) number,b.full_name borrower,c.model,l.status,l.required_guarantors,
         l.amount principal,(l.financial_snapshot->>'interestAmount')::numeric interest,(l.financial_snapshot->>'recordedChargeTotal')::numeric charges,
         l.amount+(l.financial_snapshot->>'interestAmount')::numeric+(l.financial_snapshot->>'recordedChargeTotal')::numeric cap,
         (l.financial_snapshot->>'interestRate')::numeric rate,l.financial_snapshot->>'interestBasis' basis,l.installment_amount,l.financial_snapshot->>'interestMethod' method,l.repayment_frequency frequency,l.first_repayment_date,
         i.sacco_name institution,(select disbursement_date from loan_ledgers where loan_application_id=l.id and sacco_id=l.sacco_id and station_id=l.station_id) funded_date,l.financial_snapshot::text financial,p.max_guarantee,g.status='ACTIVE' active
        from lending_group_cycle c join lending_group g on g.id=c.group_id and g.sacco_id=c.sacco_id and g.station_id=c.station_id
        join lending_group_policy p on p.id=c.policy_id
        join (select cycle_id,loan_id,sacco_id,station_id from lending_group_facility union all select cycle_id,loan_id,sacco_id,station_id from lending_group_shared_facility) f on f.cycle_id=c.id and f.sacco_id=c.sacco_id and f.station_id=c.station_id
        join loan_applications l on l.id=f.loan_id and l.sacco_id=c.sacco_id and l.station_id=c.station_id
        join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
        join registered_saccos i on i.sacco_id=l.sacco_id
        where c.id=? and c.sacco_id=? and c.station_id=?
        """;
    public Source source(AppUserPrincipal a,UUID cycle,boolean lock){return jdbc.query(SOURCE+(lock?" for update of l":""),(r,n)->new Source(cycle,r.getObject("loan",UUID.class),r.getString("number"),r.getString("borrower"),r.getString("model"),r.getString("status"),r.getInt("required_guarantors"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("cap"),r.getBigDecimal("rate"),r.getString("basis"),r.getBigDecimal("installment_amount"),r.getString("method"),r.getString("frequency"),r.getObject("first_repayment_date",LocalDate.class),r.getObject("funded_date",LocalDate.class),r.getString("institution"),r.getString("financial"),r.getBigDecimal("max_guarantee"),r.getBoolean("active")),cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Personal guarantees unavailable"));}
    private static final String COLUMNS="""
        g.id,g.loan_id,g.cycle_id,g.client_id,g.client_number,g.client_name,g.amount_cap,g.capacity,g.capacity_evidence,g.valid_through,g.packet_hash,
        'PG-'||upper(substr(replace(g.id::text,'-',''),1,12)) code,coalesce(l.loan_id,l.application_number::text) loan_number,b.full_name borrower,g.filename,g.document_metadata->>'documentLanguage' language,g.document_metadata->>'documentReference' reference,g.sha256,octet_length(g.body) bytes,g.page_count,
        coalesce(d.decision,case when g.valid_through<?::date then 'EXPIRED' else 'PENDING' end) state,g.recorded_at,d.decided_at,coalesce(d.reason,'') reason,coalesce(d.signature_text,'') signature
        """;
    private static final RowMapper<Offer> OFFER=(r,n)->new Offer(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("cycle_id",UUID.class),r.getObject("client_id",UUID.class),r.getString("client_number"),r.getString("client_name"),r.getString("code"),r.getString("loan_number"),r.getString("borrower"),r.getBigDecimal("amount_cap"),r.getBigDecimal("capacity"),r.getString("capacity_evidence"),r.getObject("valid_through",LocalDate.class),r.getString("packet_hash"),r.getString("filename"),r.getString("language"),r.getString("reference"),r.getString("sha256"),r.getLong("bytes"),r.getInt("page_count"),r.getString("state"),r.getObject("recorded_at",OffsetDateTime.class),r.getObject("decided_at",OffsetDateTime.class),r.getString("reason"),r.getString("signature"));
    private static final String OFFER_FROM=" from lending_group_personal_guarantee g join loan_applications l on l.id=g.loan_id and l.sacco_id=g.sacco_id and l.station_id=g.station_id join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id left join lending_group_personal_decision d on d.offer_id=g.id where g.sacco_id=? and g.station_id=? ";
    private static final Map<String,String> STAFF_SORT=Map.of("code","code","guarantor","row(g.client_number,g.client_name)","cap","g.amount_cap","state","state","date","d.decided_at");
    private static final Map<String,String> CLIENT_SORT=Map.of("code","code","loan","loan_number","borrower","b.full_name","cap","g.amount_cap","state","state");
    public List<Offer> staff(AppUserPrincipal a,UUID cycle,LocalDate date,int page){return jdbc.query("select "+COLUMNS+OFFER_FROM+"and g.cycle_id=? order by "+com.sacco.mvp.support.ResourceTableSort.order("personal-guarantees",STAFF_SORT,"g.id","g.recorded_at desc,g.id desc")+" limit 26 offset ?",OFFER,date,a.getSaccoId(),a.getStationId(),cycle,page*25);}
    public List<Offer> client(AppUserPrincipal a,LocalDate date,int page){return jdbc.query("select "+COLUMNS+OFFER_FROM+"and g.client_id=? order by "+com.sacco.mvp.support.ResourceTableSort.order("my-group-guarantees",CLIENT_SORT,"g.id","g.recorded_at desc,g.id desc")+" limit 26 offset ?",OFFER,date,a.getSaccoId(),a.getStationId(),a.getMemberId(),page*25);}
    public Offer offer(AppUserPrincipal a,UUID id,LocalDate date,boolean own){return jdbc.query("select "+COLUMNS+OFFER_FROM+"and g.id=?"+(own?" and g.client_id=?":""),OFFER,own?new Object[]{date,a.getSaccoId(),a.getStationId(),id,a.getMemberId()}:new Object[]{date,a.getSaccoId(),a.getStationId(),id}).stream().findFirst().orElseThrow(()->new AccessDeniedException("Personal guarantee unavailable"));}
    public List<Choice> choices(AppUserPrincipal a,UUID cycle,String search,int page){return jdbc.query("select id,member_no||' - '||full_name from members m where sacco_id=? and station_id=? and is_member and status='ACTIVE' and strpos(lower(member_no||' '||full_name),?)>0 and not exists(select 1 from lending_group_shared_allocation s where s.cycle_id=? and s.client_id=m.id) order by member_no,id limit 26 offset ?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),cycle,page*25);}
    public Optional<Person> person(AppUserPrincipal a,UUID id){return jdbc.query("select id,member_no,full_name from members where id=? and sacco_id=? and station_id=? and is_member and status='ACTIVE'",(r,n)->new Person(id,r.getString(2),r.getString(3)),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public boolean jointBorrower(UUID cycle,UUID client){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_shared_allocation where cycle_id=? and client_id=?)",Boolean.class,cycle,client));}
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String payload){return jdbc.query("select id,payload_hash,recorded_by from lending_group_personal_guarantee where sacco_id=? and station_id=? and request_key=?",(r,n)->{if(!payload.equals(r.getString(2))||!a.getMemberId().equals(r.getObject(3,UUID.class)))throw new IllegalArgumentException("group.error.retry");return r.getObject(1,UUID.class);},a.getSaccoId(),a.getStationId(),key).stream().findFirst();}
    public void requestLock(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","PERSONAL_GUARANTEE/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public UUID record(AppUserPrincipal a,Source s,Form f,Person p,String payload,String packet,String metadata,String snapshot,String filename,String checksum,int pages,byte[] bytes,OffsetDateTime at){UUID id=UUID.randomUUID();jdbc.update("""
        insert into lending_group_personal_guarantee(id,loan_id,cycle_id,sacco_id,station_id,client_id,client_number,client_name,request_key,payload_hash,packet_hash,amount_cap,capacity,capacity_evidence,valid_through,document_metadata,snapshot,filename,sha256,page_count,body,recorded_by,recorded_at)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?)
        """,id,s.loan(),s.cycle(),a.getSaccoId(),a.getStationId(),p.id(),p.number(),p.name(),f.getRequestKey(),payload,packet,s.cap(),f.getCapacity(),f.getCapacityEvidence().strip(),f.getValidThrough(),metadata,snapshot,filename,checksum,pages,bytes,a.getMemberId(),at);return id;}
    public String documentMetadata(AppUserPrincipal a,UUID id){return jdbc.queryForObject("select document_metadata::text from lending_group_personal_guarantee where id=? and sacco_id=? and station_id=?",String.class,id,a.getSaccoId(),a.getStationId());}
    public boolean available(AppUserPrincipal a,UUID loan,UUID client){return Boolean.TRUE.equals(jdbc.queryForObject("select (select count(*) from lending_group_personal_commitment where loan_id=? and sacco_id=? and station_id=?)<50 and not exists(select 1 from lending_group_personal_commitment where loan_id=? and client_id=? and sacco_id=? and station_id=?)",Boolean.class,loan,a.getSaccoId(),a.getStationId(),loan,client,a.getSaccoId(),a.getStationId()));}
    public boolean ready(UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select lending_personal_guarantees_ready(?)",Boolean.class,loan));}
    public boolean offBranch(AppUserPrincipal a,UUID client){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_personal_commitment where client_id=? and sacco_id=? and station_id<>?)",Boolean.class,client,a.getSaccoId(),a.getStationId()));}
    public byte[] body(AppUserPrincipal a,UUID id){return jdbc.queryForObject("select body from lending_group_personal_guarantee where id=? and sacco_id=? and station_id=?",byte[].class,id,a.getSaccoId(),a.getStationId());}
    public boolean opened(AppUserPrincipal a,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_personal_opened where offer_id=? and client_id=?)",Boolean.class,id,a.getMemberId()));}
    public void opened(AppUserPrincipal a,Offer o,OffsetDateTime at){jdbc.update("insert into lending_group_personal_opened(offer_id,client_id,packet_hash,opened_at) values(?,?,?,?) on conflict do nothing",o.id(),a.getMemberId(),o.hash(),at);}
    public void decide(AppUserPrincipal a,Offer o,String decision,String reason,String signature,UUID challenge,OffsetDateTime at){jdbc.update("insert into lending_group_personal_decision(offer_id,client_id,decision,reason,actor_id,packet_hash,signature_text,decided_at,otp_required,challenge_id) values(?,?,?,?,?,?,?,?,?,?)",o.id(),o.client(),decision,reason,a.getMemberId(),o.hash(),signature,at,challenge!=null,challenge);}
    public List<Challenge> challenges(Offer o){return jdbc.query("select id,code_hash,issued_at,expires_at,delivered_at,consumed_at,attempts from lending_group_personal_challenge where offer_id=? order by issued_at desc,id desc limit 4 for update",(r,n)->new Challenge(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,OffsetDateTime.class),r.getObject(4,OffsetDateTime.class),r.getObject(5,OffsetDateTime.class),r.getObject(6,OffsetDateTime.class),r.getInt(7)),o.id());}
    public void challenge(AppUserPrincipal a,Offer o,UUID id,String hash,OffsetDateTime at,OffsetDateTime expires){jdbc.update("insert into lending_group_personal_challenge(id,offer_id,client_id,sacco_id,station_id,packet_hash,code_hash,issued_at,expires_at) values(?,?,?,?,?,?,?,?,?)",id,o.id(),a.getMemberId(),a.getSaccoId(),a.getStationId(),o.hash(),hash,at,expires);}
    public void attempted(UUID id){jdbc.update("update lending_group_personal_challenge set attempts=attempts+1 where id=? and attempts<5",id);}
    public void consumed(UUID id,OffsetDateTime at){jdbc.update("update lending_group_personal_challenge set consumed_at=? where id=? and consumed_at is null",at,id);}
    public void delivered(UUID id,OffsetDateTime at){jdbc.update("update lending_group_personal_challenge set delivered_at=? where id=? and delivered_at is null and consumed_at is null",at,id);}
}
