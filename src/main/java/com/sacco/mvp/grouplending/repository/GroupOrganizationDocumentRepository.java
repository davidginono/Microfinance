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
    private static final String COLUMNS="id,revision,filename,language,sha256,octet_length(body) size_bytes,page_count,reference,institution_name,station_id,institution_address,contact_phone,contact_email,complaint_phone,complaint_email,complaint_address,recorded_at";
    private static final RowMapper<Document> MAP=(r,n)->new Document(r.getObject("id",UUID.class),r.getInt("revision"),r.getString("filename"),r.getString("language"),r.getString("sha256"),r.getLong("size_bytes"),r.getInt("page_count"),r.getString("reference"),r.getString("institution_name"),r.getString("station_id"),r.getString("institution_address"),r.getString("contact_phone"),r.getString("contact_email"),r.getString("complaint_phone"),r.getString("complaint_email"),r.getString("complaint_address"),r.getObject("recorded_at",OffsetDateTime.class));
    public Optional<Document> latest(AppUserPrincipal a,UUID cycle){return jdbc.query("select "+COLUMNS+" from lending_group_org_terms where cycle_id=? and sacco_id=? and station_id=? order by revision desc limit 1",MAP,cycle,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public Optional<UUID> retry(AppUserPrincipal a,UUID cycle,UUID key,String hash){var rows=jdbc.queryForList("select id,cycle_id,recorded_by,payload_hash from lending_group_org_terms where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!cycle.equals(row.get("cycle_id")) || !a.getMemberId().equals(row.get("recorded_by")) || !hash.equals(row.get("payload_hash")))throw new IllegalArgumentException("group.error.changed");return Optional.of((UUID)row.get("id"));}
    public UUID record(AppUserPrincipal a,UUID cycle,DocumentForm f,Document prior,String institution,String filename,String hash,String checksum,int pages,byte[] bytes,OffsetDateTime at){var id=UUID.randomUUID();jdbc.update("""
        insert into lending_group_org_terms(id,cycle_id,sacco_id,station_id,revision,previous_id,request_key,payload_hash,filename,language,body,sha256,page_count,reference,institution_name,institution_address,contact_phone,contact_email,complaint_phone,complaint_email,complaint_address,recorded_by,recorded_at)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,id,cycle,a.getSaccoId(),a.getStationId(),prior==null?1:prior.revision()+1,prior==null?null:prior.id(),f.getDocumentRequestKey(),hash,filename,f.getDocumentLanguage(),bytes,checksum,pages,f.getDocumentReference(),institution,f.getInstitutionAddress(),f.getContactPhone(),f.getContactEmail(),f.getComplaintPhone(),f.getComplaintEmail(),f.getComplaintAddress(),a.getMemberId(),at);return id;}
    public byte[] body(AppUserPrincipal a,UUID cycle,UUID id){return jdbc.query("select body from lending_group_org_terms where id=? and cycle_id=? and sacco_id=? and station_id=?",(r,n)->r.getBytes(1),id,cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Agreement document unavailable"));}
    public boolean opened(AppUserPrincipal a,UUID cycle,UUID document,String hash){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_org_document_access where cycle_id=? and client_id=? and document_id=? and sacco_id=? and station_id=? and packet_hash=?)",Boolean.class,cycle,a.getMemberId(),document,a.getSaccoId(),a.getStationId(),hash));}
    public void opened(AppUserPrincipal a,UUID cycle,UUID document,String hash,OffsetDateTime at){jdbc.update("insert into lending_group_org_document_access(cycle_id,client_id,document_id,sacco_id,station_id,packet_hash,requested_at) values(?,?,?,?,?,?,?) on conflict(cycle_id,client_id,document_id) do nothing",cycle,a.getMemberId(),document,a.getSaccoId(),a.getStationId(),hash,at);}
    public List<Signature> signatures(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        select n.name,n.client_number,s.signed_at,s.otp_required,false lender from lending_group_org_signature s
        join lending_group_org_agreement p on p.cycle_id=s.cycle_id
        join lending_group_authority_signer n on n.authority_id=p.authority_id and n.client_id=s.client_id
        where s.cycle_id=? and s.sacco_id=? and s.station_id=?
        union all select actor_name,actor_number,signed_at,false,true from lending_group_org_funding
        where cycle_id=? and sacco_id=? and station_id=? order by signed_at,client_number limit 51
        """,(r,n)->new Signature(r.getString("name"),r.getString("client_number"),r.getObject("signed_at",OffsetDateTime.class),r.getBoolean("otp_required"),r.getBoolean("lender")),cycle,a.getSaccoId(),a.getStationId(),cycle,a.getSaccoId(),a.getStationId());}
}
