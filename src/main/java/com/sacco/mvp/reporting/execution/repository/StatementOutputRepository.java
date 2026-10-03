package com.sacco.mvp.reporting.execution.repository;

import com.sacco.mvp.accounting.statements.StatementDesignerService.Result;
import com.sacco.mvp.reporting.OperationalReportService.Branding;
import com.sacco.mvp.reporting.OperationalReportExportService.Format;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.*;
import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class StatementOutputRepository {
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public Optional<UUID> duplicate(String institution,String branch,UUID result,String layoutChecksum){return jdbc.queryForList("SELECT id FROM statement_output_sets WHERE sacco_id=? AND station_id=? AND result_id=? AND layout_checksum=?",UUID.class,institution,branch,result,layoutChecksum).stream().findFirst();}
 public void lock(String institution,String branch,UUID result){jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended(?,49102))",(org.springframework.jdbc.core.PreparedStatementCallback<Void>)ps->{ps.setString(1,institution+"/"+branch+"/"+result);ps.execute();return null;});}
 public void create(Output o){var logo=o.branding();String json=mapper.writeValueAsString(o.result());jdbc.update("INSERT INTO statement_output_sets(id,sacco_id,station_id,result_id,source_checksum,result_json,result_checksum,layout_json,layout_checksum,font_checksum,generated_by,generated_at,logo,logo_media_type,logo_checksum) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",o.id(),o.institution(),o.branch(),o.resultId(),o.sourceChecksum(),json,sha(json),mapper.writeValueAsString(o.layout()),o.layoutChecksum(),o.fontChecksum(),o.generatedBy(),o.generatedAt(),logo==null?null:logo.logo(),logo==null?null:logo.mediaType(),logo==null?null:logo.sha256());}
 public Optional<Output> get(UUID id,String institution,String branch,boolean lock){if(lock)jdbc.queryForList("SELECT id FROM statement_output_sets WHERE id=? AND sacco_id=? AND station_id=? FOR UPDATE",UUID.class,id,institution,branch);return jdbc.query("SELECT s.*,r.reviewer,r.evidence,r.reviewed_at FROM statement_output_sets s LEFT JOIN statement_output_reviews r ON r.output_id=s.id WHERE s.id=? AND s.sacco_id=? AND s.station_id=?",(rs,n)->{String json=rs.getString("result_json");if(!sha(json).equals(rs.getString("result_checksum")))throw new IllegalStateException("Frozen statement checksum failed");return new Output(rs.getObject("id",UUID.class),rs.getString("sacco_id"),rs.getString("station_id"),rs.getObject("result_id",UUID.class),rs.getString("source_checksum"),mapper.readValue(json,Result.class),mapper.readValue(rs.getString("layout_json"),Layout.class),rs.getString("layout_checksum"),rs.getString("font_checksum"),rs.getBytes("logo")==null?null:new Branding(rs.getBytes("logo"),rs.getString("logo_media_type"),rs.getString("logo_checksum")),rs.getObject("generated_by",UUID.class),rs.getObject("generated_at",OffsetDateTime.class),rs.getObject("reviewer",UUID.class),rs.getString("evidence"),rs.getObject("reviewed_at",OffsetDateTime.class));},id,institution,branch).stream().findFirst();}
 public List<Summary> list(String institution,String branch,int page){return jdbc.query("SELECT s.id,s.result_json::jsonb#>>'{definition,titleEn}' title_en,s.result_json::jsonb#>>'{definition,titleSw}' title_sw,(s.result_json::jsonb->>'from')::date starts_on,(s.result_json::jsonb->>'through')::date ends_on,s.generated_at,r.reviewer FROM statement_output_sets s LEFT JOIN statement_output_reviews r ON r.output_id=s.id WHERE s.sacco_id=? AND s.station_id=? ORDER BY s.generated_at DESC,s.id LIMIT 25 OFFSET ?",(rs,n)->new Summary(rs.getObject("id",UUID.class),rs.getString("title_en"),rs.getString("title_sw"),rs.getObject("starts_on",java.time.LocalDate.class),rs.getObject("ends_on",java.time.LocalDate.class),rs.getObject("generated_at",OffsetDateTime.class),rs.getObject("reviewer",UUID.class)),institution,branch,page*25);}
 public void artifact(UUID output,Format format,byte[] payload,String checksum){jdbc.update("INSERT INTO statement_output_artifacts(id,output_id,format,payload,checksum) VALUES(?,?,?,?,?)",UUID.randomUUID(),output,format.name(),payload,checksum);}
 public List<Artifact> artifacts(UUID output){return jdbc.query("SELECT id,format,checksum,octet_length(payload) FROM statement_output_artifacts WHERE output_id=? ORDER BY format",(rs,n)->new Artifact(rs.getObject(1,UUID.class),Format.valueOf(rs.getString(2)),rs.getString(3),rs.getLong(4)),output);}
 public Optional<Download> download(UUID output,UUID artifact){return jdbc.query("SELECT format,payload,checksum FROM statement_output_artifacts WHERE output_id=? AND id=?",(rs,n)->new Download(Format.valueOf(rs.getString(1)),rs.getBytes(2),rs.getString(3)),output,artifact).stream().findFirst();}
 public void review(UUID output,UUID reviewer,String evidence,OffsetDateTime now){jdbc.update("INSERT INTO statement_output_reviews(output_id,reviewer,evidence,reviewed_at) VALUES(?,?,?,?)",output,reviewer,evidence,now);}
 private static String sha(String text){return com.sacco.mvp.reporting.execution.service.ReportRunService.sha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}

