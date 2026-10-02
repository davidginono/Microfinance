package com.sacco.mvp.reporting;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static com.sacco.mvp.reporting.OperationalReportTemplateService.Version;

@Repository
@RequiredArgsConstructor
public class OperationalReportTemplateRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public boolean hasInstitutionHistory(String institution){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM operational_report_templates WHERE sacco_id=?)",Boolean.class,institution));}
    public boolean hasMemberHistory(UUID member){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM operational_report_templates WHERE created_by=?) OR EXISTS(SELECT 1 FROM operational_report_template_versions WHERE made_by=? OR checked_by=?)",Boolean.class,member,member,member));}
    public void create(UUID id,String institution,UUID actor,OffsetDateTime now){
        jdbc.update("INSERT INTO operational_report_templates(id,sacco_id,created_by,created_at) VALUES(?,?,?,?)",id,institution,actor,now);
    }
    public List<UUID> lockOwners(UUID id,String institution){return jdbc.query("SELECT created_by FROM operational_report_templates WHERE id=? AND sacco_id=? FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),id,institution);}
    public int nextVersion(UUID template,String institution){return Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM operational_report_template_versions WHERE template_id=? AND sacco_id=?",Integer.class,template,institution));}
    public void add(UUID id,UUID template,String institution,int version,OperationalReportDefinition definition,boolean shared,UUID actor,OffsetDateTime now){
        jdbc.update("INSERT INTO operational_report_template_versions(id,template_id,sacco_id,version,dataset_version,definition,title,visibility,state,made_by,made_at) VALUES(?,?,?,?,1,?,?,?,'DRAFT',?,?)",
            id,template,institution,version,mapper.writeValueAsString(definition),definition.title(),shared?"INSTITUTION":"PRIVATE",actor,now);
    }
    public List<Version> list(String institution,UUID actor,boolean publisher,int page){
        return jdbc.query("SELECT * FROM operational_report_template_versions WHERE sacco_id=? AND (made_by=? OR (? AND state='DRAFT') OR (visibility='INSTITUTION' AND state='PUBLISHED')) ORDER BY made_at DESC,id LIMIT 25 OFFSET ?",(rs,n)->read(rs),institution,actor,publisher,page*25);
    }
    public List<Version> find(UUID id,String institution,boolean lock){return jdbc.query("SELECT * FROM operational_report_template_versions WHERE id=? AND sacco_id=?"+(lock?" FOR UPDATE":""),(rs,n)->read(rs),id,institution);}
    public void publish(UUID id,String institution,UUID checker,OffsetDateTime now){jdbc.update("UPDATE operational_report_template_versions SET state='PUBLISHED',checked_by=?,checked_at=? WHERE id=? AND sacco_id=? AND state='DRAFT'",checker,now,id,institution);}
    public void retire(UUID id,String institution){jdbc.update("UPDATE operational_report_template_versions SET state='RETIRED' WHERE id=? AND sacco_id=? AND state='PUBLISHED'",id,institution);}
    private Version read(java.sql.ResultSet rs)throws java.sql.SQLException{return new Version(rs.getObject("id",UUID.class),rs.getObject("template_id",UUID.class),rs.getInt("version"),rs.getString("state"),rs.getString("visibility"),rs.getObject("made_by",UUID.class),rs.getObject("checked_by",UUID.class),mapper.readValue(rs.getString("definition"),OperationalReportDefinition.class));}
}
