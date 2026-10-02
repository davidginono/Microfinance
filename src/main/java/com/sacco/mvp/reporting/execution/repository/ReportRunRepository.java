package com.sacco.mvp.reporting.execution.repository;

import com.sacco.mvp.reporting.OperationalReportDefinition;
import com.sacco.mvp.reporting.OperationalReportService.Branding;
import com.sacco.mvp.reporting.OperationalReportExportService.Format;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ReportRunRepository {
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public void quotaLock(String institution){jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended(?,49001))",(org.springframework.jdbc.core.PreparedStatementCallback<Void>)statement->{statement.setString(1,institution);statement.execute();return null;});}
 public long active(String institution,UUID requester){return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM report_runs WHERE sacco_id=? AND status IN('QUEUED','RUNNING')"+(requester==null?"":" AND requested_by=?"),Long.class,requester==null?new Object[]{institution}:new Object[]{institution,requester}));}
 public List<Run> duplicate(String institution,String branch,UUID requester,UUID key){return jdbc.query("SELECT * FROM report_runs WHERE sacco_id=? AND station_id=? AND requested_by=? AND request_key=?",(rs,n)->read(rs),institution,branch,requester,key);}
 public String requestHash(UUID id){return jdbc.queryForObject("SELECT request_hash FROM report_runs WHERE id=?",String.class,id);}
 public void create(UUID id,String institution,String branch,UUID requester,Request request,String hash,int version,OperationalReportDefinition definition,OffsetDateTime now){
  jdbc.update("INSERT INTO report_runs(id,sacco_id,station_id,requested_by,request_key,request_hash,template_version_id,template_version,dataset_version,metric_version,dataset,definition,from_date,through_date,recorded_cutoff,formats,status,requested_at,restates_id,restatement_reason) VALUES(?,?,?,?,?,?,?,?,1,'OPERATIONAL_V1',?,?,?,?,?,?,'QUEUED',?,?,?)",id,institution,branch,requester,request.requestKey(),hash,request.templateVersion(),version,definition.dataset().name(),mapper.writeValueAsString(definition),request.from(),request.through(),request.recordedCutoff(),formats(request.formats()),now,request.restates(),request.restates()==null?null:request.reason());
 }
 public List<Run> scoped(UUID id,String institution,String branch,boolean lock){return jdbc.query("SELECT * FROM report_runs WHERE id=? AND sacco_id=? AND station_id=?"+(lock?" FOR UPDATE":""),(rs,n)->read(rs),id,institution,branch);}
 public List<Run> list(String institution,String branch,UUID requester,boolean reviewer,boolean collections,boolean portfolio,int page){return jdbc.query("SELECT * FROM report_runs WHERE sacco_id=? AND station_id=? AND (? OR requested_by=?) AND ((? AND dataset='COLLECTIONS') OR (? AND dataset IN('DISBURSEMENTS','LOAN_PORTFOLIO'))) ORDER BY requested_at DESC,id LIMIT 25 OFFSET ?",(rs,n)->read(rs),institution,branch,reviewer,requester,collections,portfolio,page*25);}
 public Optional<Run> claim(OffsetDateTime now){
  var runs=jdbc.query("WITH candidate AS (SELECT r.id FROM report_runs r WHERE r.status='QUEUED' AND NOT r.cancel_requested AND r.attempts<3 AND NOT EXISTS(SELECT 1 FROM report_runs active WHERE active.sacco_id=r.sacco_id AND active.status='RUNNING') ORDER BY (SELECT max(prior.started_at) FROM report_runs prior WHERE prior.sacco_id=r.sacco_id) NULLS FIRST,r.requested_at,r.id LIMIT 1 FOR UPDATE OF r SKIP LOCKED) UPDATE report_runs r SET status='RUNNING',attempts=attempts+1,worker_token=?,started_at=?,lease_until=?,failure_key=NULL FROM candidate WHERE r.id=candidate.id RETURNING r.*",(rs,n)->read(rs),UUID.randomUUID(),now,now.plusMinutes(3));
  return runs.stream().findFirst();
 }
 public int cancel(UUID id){return jdbc.update("UPDATE report_runs SET cancel_requested=true,status=CASE WHEN status='QUEUED' THEN 'CANCELLED' ELSE status END WHERE id=? AND status IN('QUEUED','RUNNING')",id);}
 public boolean cancelled(UUID id,UUID token){return !Boolean.TRUE.equals(jdbc.queryForObject("SELECT status='RUNNING' AND worker_token=? AND NOT cancel_requested AND lease_until>now() FROM report_runs WHERE id=?",Boolean.class,token,id));}
 public void page(UUID id,int page,int rows,String payload,String checksum){jdbc.update("INSERT INTO report_result_pages(run_id,page,row_count,payload,checksum) VALUES(?,?,?,?,?)",id,page,rows,payload,checksum);}
 public List<FrozenPage> pages(UUID id,int offset,int limit){return jdbc.query("SELECT page,row_count,payload,checksum FROM report_result_pages WHERE run_id=? AND page>=? ORDER BY page LIMIT ?",(rs,n)->new FrozenPage(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getString(4)),id,offset,limit);}
 public String snapshot(){return jdbc.queryForObject("SELECT pg_current_snapshot()::text",String.class);}
 public void asset(UUID id,Branding branding){jdbc.update("INSERT INTO report_run_assets(run_id,media_type,payload,checksum) VALUES(?,?,?,?)",id,branding.mediaType(),branding.logo(),branding.sha256());}
 public Optional<Branding> asset(UUID id){return jdbc.query("SELECT payload,media_type,checksum FROM report_run_assets WHERE run_id=?",(rs,n)->new Branding(rs.getBytes(1),rs.getString(2),rs.getString(3)),id).stream().findFirst();}
 public void artifact(UUID id,Format format,byte[] bytes,String checksum,OffsetDateTime now){jdbc.update("INSERT INTO report_artifacts(id,run_id,format,payload,checksum,generated_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),id,format.name(),bytes,checksum,now);}
 public List<Artifact> artifacts(UUID id){return jdbc.query("SELECT id,format,checksum,octet_length(payload) FROM report_artifacts WHERE run_id=? ORDER BY format",(rs,n)->new Artifact(rs.getObject(1,UUID.class),Format.valueOf(rs.getString(2)),rs.getString(3),rs.getLong(4)),id);}
 public Optional<Download> artifact(UUID id,UUID artifact){return jdbc.query("SELECT format,payload,checksum FROM report_artifacts WHERE run_id=? AND id=?",(rs,n)->new Download(Format.valueOf(rs.getString(1)),rs.getBytes(2),rs.getString(3)),id,artifact).stream().findFirst();}
 public int ready(UUID id,UUID token,long rows,long untracked,String coverage,String totals,String snapshot,String checksum,OffsetDateTime now){return jdbc.update("UPDATE report_runs SET status='READY',generated_at=?,row_count=?,untracked_count=?,coverage_key=?,totals=?,snapshot_id=?,result_checksum=?,lease_until=NULL WHERE id=? AND worker_token=? AND status='RUNNING' AND NOT cancel_requested",now,rows,untracked,coverage,totals,snapshot,checksum,id,token);}
 public void fail(UUID id,UUID token,String failure,OffsetDateTime now){jdbc.update("UPDATE report_runs SET status=CASE WHEN cancel_requested THEN 'CANCELLED' ELSE 'FAILED' END,failure_key=?,lease_until=NULL WHERE id=? AND worker_token=? AND status='RUNNING'",failure,id,token);}
 public int retry(UUID id){return jdbc.update("UPDATE report_runs SET status='QUEUED',worker_token=NULL,lease_until=NULL,failure_key=NULL WHERE id=? AND status='FAILED' AND attempts<3 AND NOT cancel_requested",id);}
 public int recover(OffsetDateTime now){return jdbc.update("UPDATE report_runs SET status=CASE WHEN cancel_requested THEN 'CANCELLED' ELSE 'FAILED' END,failure_key='report.run.error.interrupted',lease_until=NULL WHERE status='RUNNING' AND lease_until<?",now);}
 public void approve(UUID id,UUID actor,String evidence,OffsetDateTime now){jdbc.update("UPDATE report_runs SET status='APPROVED',approved_by=?,approved_at=?,approval_evidence=? WHERE id=? AND status='READY'",actor,now,evidence,id);}
 public boolean hasInstitutionHistory(String institution){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM report_runs WHERE sacco_id=?)",Boolean.class,institution));}
 public boolean hasMemberHistory(UUID member){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM report_runs WHERE requested_by=? OR approved_by=?)",Boolean.class,member,member));}
 private static String formats(Set<Format> formats){return formats.stream().map(Enum::name).sorted().reduce((a,b)->a+","+b).orElseThrow();}
 private Run read(ResultSet rs)throws SQLException{return new Run(rs.getObject("id",UUID.class),rs.getString("sacco_id"),rs.getString("station_id"),rs.getObject("requested_by",UUID.class),rs.getObject("template_version_id",UUID.class),rs.getInt("template_version"),mapper.readValue(rs.getString("definition"),OperationalReportDefinition.class),rs.getObject("from_date",LocalDate.class),rs.getObject("through_date",LocalDate.class),rs.getObject("recorded_cutoff",OffsetDateTime.class),Set.copyOf(Arrays.stream(rs.getString("formats").split(",")).map(Format::valueOf).toList()),rs.getString("status"),rs.getObject("requested_at",OffsetDateTime.class),rs.getObject("generated_at",OffsetDateTime.class),rs.getInt("attempts"),rs.getBoolean("cancel_requested"),rs.getString("failure_key"),rs.getLong("row_count"),rs.getLong("untracked_count"),rs.getString("coverage_key"),rs.getString("totals"),rs.getString("snapshot_id"),rs.getString("result_checksum"),rs.getObject("approved_by",UUID.class),rs.getObject("approved_at",OffsetDateTime.class),rs.getString("approval_evidence"),rs.getObject("restates_id",UUID.class),rs.getString("restatement_reason"),rs.getObject("worker_token",UUID.class));}
}
