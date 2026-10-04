package com.sacco.mvp.accounting.business.repository;

import com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.*;
import com.sacco.mvp.accounting.business.service.BusinessOpeningImport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class BusinessOpeningRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public String payload(Preview preview){return mapper.writeValueAsString(preview);}
    public void lockRequest(String institution,String branch,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","BUSINESS_OPENING/"+institution+"/"+branch+"/"+key);}
    public Optional<UUID> byRequest(String institution,String branch,UUID key){return jdbc.query("select id from accounting_business_opening where sacco_id=? and station_id=? and request_key=?",(r,n)->r.getObject(1,UUID.class),institution,branch,key).stream().findFirst();}
    public void insert(UUID id,String institution,String branch,UUID maker,BusinessOpeningImport.Parsed parsed,OffsetDateTime at){
        var p=parsed.preview();var c=p.command();String json=payload(p);
        jdbc.update("""
            insert into accounting_business_opening(id,sacco_id,station_id,request_key,account_id,general_ledger_opening_id,
                through_date,purpose,complete_coverage,signed_balance,maker_id,evidence,filename,file_bytes,file_checksum,
                preview_json,payload_checksum,imported_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,institution,branch,c.requestKey(),c.account(),c.generalLedgerOpening(),c.through(),c.purpose(),c.completeCoverage(),p.signedBalance(),maker,c.evidence(),p.filename(),parsed.bytes(),p.fileChecksum(),json,BusinessOpeningImport.sha(json.getBytes(StandardCharsets.UTF_8)),at);
    }
    public Optional<Opening> opening(String institution,String branch,UUID id,boolean lock){
        if(lock)jdbc.query("select id from accounting_business_opening where sacco_id=? and station_id=? and id=? for update",(r,n)->r.getObject(1,UUID.class),institution,branch,id);
        return jdbc.query("""
            select o.id,o.sacco_id,o.station_id,o.maker_id,o.preview_json,o.payload_checksum,o.imported_at,
                r.decision,r.reviewer_id,r.evidence review_evidence,r.reviewed_at
            from accounting_business_opening o left join accounting_business_opening_review r on r.opening_id=o.id
            where o.sacco_id=? and o.station_id=? and o.id=?
            """,(r,n)->new Opening(r.getObject("id",UUID.class),r.getString("sacco_id"),r.getString("station_id"),r.getObject("maker_id",UUID.class),mapper.readValue(r.getString("preview_json"),Preview.class),r.getString("payload_checksum"),r.getObject("imported_at",OffsetDateTime.class),r.getString("decision"),r.getObject("reviewer_id",UUID.class),r.getString("review_evidence"),r.getObject("reviewed_at",OffsetDateTime.class)),institution,branch,id).stream().findFirst();
    }
    public Optional<File> file(String institution,String branch,UUID id){return jdbc.query("select filename,file_bytes,file_checksum from accounting_business_opening where sacco_id=? and station_id=? and id=?",(r,n)->new File(r.getString(1),r.getBytes(2),r.getString(3)),institution,branch,id).stream().findFirst();}
    public List<Summary> list(String institution,String branch,int offset){return jdbc.query("""
        select o.id,o.account_id,o.through_date,o.purpose,o.signed_balance,o.complete_coverage,r.decision,o.imported_at
        from accounting_business_opening o left join accounting_business_opening_review r on r.opening_id=o.id
        where o.sacco_id=? and o.station_id=? order by o.imported_at desc,o.id limit 26 offset ?
        """,(r,n)->new Summary(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,LocalDate.class),r.getString(4),r.getBigDecimal(5),r.getBoolean(6),r.getString(7),r.getObject(8,OffsetDateTime.class)),institution,branch,offset);}
    public void requireReviewable(UUID id){jdbc.queryForList("select business_opening_assert_reviewable(?)",id);}
    public void review(Opening o,UUID reviewer,String decision,String evidence,OffsetDateTime at){jdbc.update("insert into accounting_business_opening_review(opening_id,sacco_id,station_id,account_id,reviewer_id,decision,evidence,reviewed_at) values(?,?,?,?,?,?,?,?)",o.id(),o.institution(),o.branch(),o.preview().command().account(),reviewer,decision,evidence,at);}
}
