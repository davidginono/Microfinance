package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupSharedAgreementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.PaymentDetailsService.PaymentDetailsView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupSharedAgreementRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public void lock(AppUserPrincipal a,UUID cycle){
        var rows=jdbc.queryForList("select id from lending_group_cycle where id=? and sacco_id=? and station_id=? and model='SHARED' for share",cycle,a.getSaccoId(),a.getStationId());
        if(rows.isEmpty())throw new AccessDeniedException("Shared agreement unavailable");
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","SHARED-PAYOUT/"+cycle+"/"+a.getMemberId());
    }
    public Optional<Document> document(AppUserPrincipal a,UUID cycle){return jdbc.query("select * from lending_group_shared_document where cycle_id=? and sacco_id=? and station_id=?",(r,n)->document(r),cycle,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    private Document document(java.sql.ResultSet r)throws java.sql.SQLException{
        var f=json.readValue(r.getString("metadata"),DocumentForm.class);
        return new Document(r.getObject("id",UUID.class),1,r.getString("filename"),f.getDocumentLanguage(),r.getString("content_hash"),r.getLong("size_bytes"),r.getInt("page_count"),f.getDocumentReference(),r.getString("institution_name"),r.getString("station_id"),f.getInstitutionAddress(),f.getContactPhone(),f.getContactEmail(),f.getComplaintPhone(),f.getComplaintEmail(),f.getComplaintAddress(),r.getObject("recorded_at",OffsetDateTime.class));
    }
    public Optional<UUID> retry(AppUserPrincipal a,UUID cycle,UUID key,String hash){var rows=jdbc.queryForList("select id,payload_hash,recorded_by,request_key from lending_group_shared_document where cycle_id=? and sacco_id=? and station_id=?",cycle,a.getSaccoId(),a.getStationId());if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();if(!Objects.equals(r.get("request_key"),key)||!Objects.equals(r.get("payload_hash"),hash)||!Objects.equals(r.get("recorded_by"),a.getMemberId()))throw new IllegalArgumentException("group.error.changed");return Optional.of((UUID)r.get("id"));}
    public UUID record(AppUserPrincipal a,UUID cycle,DocumentForm form,String institution,String filename,String checksum,String hash,int pages,byte[] body,OffsetDateTime at){
        UUID id=UUID.randomUUID();jdbc.update("insert into lending_group_shared_document(id,cycle_id,sacco_id,station_id,metadata,institution_name,filename,content_hash,size_bytes,page_count,body,request_key,payload_hash,recorded_by,recorded_at) values(?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?)",id,cycle,a.getSaccoId(),a.getStationId(),json.writeValueAsString(form),institution,filename,checksum,body.length,pages,body,form.getDocumentRequestKey(),hash,a.getMemberId(),at);return id;
    }
    public byte[] body(AppUserPrincipal a,UUID cycle,UUID document){return jdbc.query("select body from lending_group_shared_document where cycle_id=? and id=? and sacco_id=? and station_id=?",(r,n)->r.getBytes(1),cycle,document,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Agreement unavailable"));}
    public Row own(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        with own as (
          select f.cycle_id,f.loan_id,f.liability_rule,f.sacco_id,f.station_id,s.client_id,s.client_name,s.client_number,s.principal,s.interest,s.charges,s.deductions,s.net,s.monthly_payment,s.liability_cap,c.state,l.status loan_status,
            coalesce(l.loan_id,l.application_number::text) number,g.name group_name,
            coalesce(p.destination,jsonb_build_object('available',m.member_id is not null,'destinationType',m.destination_type,
              'destinationTypeLabel',m.destination_type,'provider',m.provider,'accountHolderName',m.account_holder_name,
              'accountIdentifier',m.account_identifier,'sourceLabel','PERSONAL_INSTRUCTION')) destination,
            p.accepted_at,p.otp_required,
            exists(select 1 from lending_group_consent_evidence e where e.cycle_id=f.cycle_id and e.client_id=s.client_id) consented
          from lending_group_shared_facility f join lending_group_shared_allocation s on s.cycle_id=f.cycle_id
          join lending_group_cycle c on c.id=f.cycle_id join lending_group g on g.id=c.group_id join loan_applications l on l.id=f.loan_id
          left join member_payment_details m on m.member_id=s.client_id left join lending_group_shared_payout p on p.cycle_id=f.cycle_id and p.client_id=s.client_id
          where f.cycle_id=? and f.sacco_id=? and f.station_id=? and s.client_id=?
        ) select o.*,d.id,d.metadata,d.institution_name,d.filename,d.content_hash,d.size_bytes,d.page_count,d.recorded_at,o.cycle_id own_cycle,
          lending_group_shared_packet(o.cycle_id,o.client_id,o.destination) packet_hash,
          exists(select 1 from lending_group_shared_opened v where v.cycle_id=o.cycle_id and v.client_id=o.client_id
            and v.packet_hash=lending_group_shared_packet(o.cycle_id,o.client_id,o.destination)) opened
        from own o left join lending_group_shared_document d on d.cycle_id=o.cycle_id
        """,(r,n)->new Row(r.getObject("own_cycle",UUID.class),r.getObject("loan_id",UUID.class),r.getString("number"),r.getString("group_name"),r.getString("client_name"),r.getString("client_number"),r.getString("packet_hash"),r.getString("state"),r.getString("loan_status"),r.getObject("id")==null?null:document(r),new SharedObligation(SharedLiability.valueOf(r.getString("liability_rule")),r.getBigDecimal("liability_cap"),new SharedShare(a.getMemberId(),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("deductions"),r.getBigDecimal("net"),r.getBigDecimal("monthly_payment"))),json.readValue(r.getString("destination"),PaymentDetailsView.class),r.getBoolean("consented"),r.getBoolean("opened"),r.getObject("accepted_at")==null?null:new Confirmation(r.getObject("accepted_at",OffsetDateTime.class),r.getBoolean("otp_required"))),cycle,a.getSaccoId(),a.getStationId(),a.getMemberId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Own shared agreement unavailable"));}
    public void opened(AppUserPrincipal a,UUID cycle,String hash,OffsetDateTime at){jdbc.update("insert into lending_group_shared_opened(cycle_id,client_id,sacco_id,station_id,packet_hash,opened_at) values(?,?,?,?,?,?) on conflict do nothing",cycle,a.getMemberId(),a.getSaccoId(),a.getStationId(),hash,at);}
    public void confirm(AppUserPrincipal a,Row row,String signature,UUID challenge,OffsetDateTime at){jdbc.update("insert into lending_group_shared_payout(cycle_id,client_id,sacco_id,station_id,document_id,packet_hash,destination,net_amount,signature_text,otp_required,challenge_id,accepted_at) values(?,?,?,?,?,?,?::jsonb,?,?,?,?,?)",row.cycle(),a.getMemberId(),a.getSaccoId(),a.getStationId(),row.document().id(),row.hash(),json.writeValueAsString(row.destination()),row.obligation().allocation().net(),signature,challenge!=null,challenge,at);}
    public List<PublicShare> parties(AppUserPrincipal a,UUID cycle){return jdbc.query("select s.client_name,s.client_number,s.principal from lending_group_shared_allocation s join lending_group_shared_facility f on f.cycle_id=s.cycle_id where f.cycle_id=? and f.sacco_id=? and f.station_id=? order by s.ordinal limit 50",(r,n)->new PublicShare(r.getString(1),r.getString(2),r.getBigDecimal(3)),cycle,a.getSaccoId(),a.getStationId());}
    public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow> schedule(AppUserPrincipal a,UUID cycle){var text=jdbc.queryForObject("select (snapshot->'schedule')::text from lending_group_shared_facility where cycle_id=? and sacco_id=? and station_id=?",String.class,cycle,a.getSaccoId(),a.getStationId());return json.readValue(text,json.getTypeFactory().constructCollectionType(List.class,com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow.class));}
    public List<Challenge> challenges(AppUserPrincipal a,UUID cycle){return jdbc.query("select * from lending_group_shared_payout_challenge where cycle_id=? and client_id=? and sacco_id=? and station_id=? order by issued_at desc,id desc limit 4 for update",(r,n)->new Challenge(r.getObject("id",UUID.class),r.getString("code_hash"),r.getString("packet_hash"),r.getObject("issued_at",OffsetDateTime.class),r.getObject("expires_at",OffsetDateTime.class),r.getObject("delivered_at",OffsetDateTime.class),r.getObject("consumed_at",OffsetDateTime.class),r.getInt("attempts")),cycle,a.getMemberId(),a.getSaccoId(),a.getStationId());}
    public void challenge(AppUserPrincipal a,UUID cycle,String hash,UUID id,String codeHash,OffsetDateTime issued,OffsetDateTime expires){jdbc.update("insert into lending_group_shared_payout_challenge(id,cycle_id,client_id,sacco_id,station_id,packet_hash,code_hash,issued_at,expires_at) values(?,?,?,?,?,?,?,?,?)",id,cycle,a.getMemberId(),a.getSaccoId(),a.getStationId(),hash,codeHash,issued,expires);}
    public void delivered(UUID id,OffsetDateTime at){jdbc.update("update lending_group_shared_payout_challenge set delivered_at=? where id=? and delivered_at is null and consumed_at is null",at,id);}
    public void attempted(UUID id){jdbc.update("update lending_group_shared_payout_challenge set attempts=attempts+1 where id=? and consumed_at is null and attempts<5",id);}
    public void consumed(UUID id,OffsetDateTime at){jdbc.update("update lending_group_shared_payout_challenge set consumed_at=? where id=? and consumed_at is null",at,id);}
}
