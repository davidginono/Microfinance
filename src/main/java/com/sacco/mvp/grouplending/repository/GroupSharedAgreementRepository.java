package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupSharedAgreementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
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

}
