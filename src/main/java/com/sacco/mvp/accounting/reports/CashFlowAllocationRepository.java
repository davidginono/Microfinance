package com.sacco.mvp.accounting.reports;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static com.sacco.mvp.accounting.reports.CashFlowAllocation.*;

@Repository @RequiredArgsConstructor
public class CashFlowAllocationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public record JournalChoice(UUID id,String reference,java.time.LocalDate date) { }
    public List<JournalChoice> journalChoices(String institution,String branch,int page){return jdbc.query("select j.id,j.source_reference,j.effective_date from gl_journal j where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.source_type<>'OPENING' and exists(select 1 from gl_journal_line l join gl_account a on a.id=l.account_id where l.journal_id=j.id and a.purpose in('CASH','BANK','MOBILE_MONEY')) order by j.effective_date desc,j.id limit 26 offset ?",(r,n)->new JournalChoice(r.getObject("id",UUID.class),r.getString("source_reference"),r.getObject("effective_date",java.time.LocalDate.class)),institution,branch,page*25);}
    public Source source(String institution,String branch,UUID journal) {
        var header=jdbc.queryForList("select id,policy_id,policy_version,source_reference from gl_journal where sacco_id=? and station_id=? and id=? and state='POSTED' and source_type<>'OPENING'",institution,branch,journal);
        if(header.size()!=1)throw new AccessDeniedException("Posted source unavailable");
        var lines=jdbc.query("select l.id,l.account_id,a.code,a.type,a.purpose,l.debit-l.credit amount from gl_journal_line l join gl_account a on (a.id,a.sacco_id)=(l.account_id,l.sacco_id) where l.sacco_id=? and l.station_id=? and l.journal_id=? order by l.id limit 201",(r,n)->new SourceLine(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getString("code"),r.getString("type"),r.getString("purpose"),r.getBigDecimal("amount")),institution,branch,journal);
        var h=header.getFirst();return new Source(journal,(UUID)h.get("policy_id"),(Integer)h.get("policy_version"),h.get("source_reference").toString(),lines);
    }
    public void lockSource(String institution,String branch,UUID journal) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","CASH_FLOW/"+institution+"/"+branch+"/"+journal);
    }
    /** The same setup/period order as closing; no source guard precedes these locks. */
    public void lockReviewSource(String institution,String branch,UUID journal) {
        lockSourcePeriods(institution,branch,List.of(journal));lockSource(institution,branch,journal);
    }
    public void lockProofSources(String institution,String branch,List<UUID> journals) {
        lockSourcePeriods(institution,branch,journals);
        if(journals.isEmpty())return;
        var keys=journals.stream().distinct().map(id->"CASH_FLOW/"+institution+"/"+branch+"/"+id).sorted().toList();
        String values=String.join(",",Collections.nCopies(keys.size(),"(?)"));
        jdbc.queryForList("select pg_advisory_xact_lock_shared(hashtextextended(key,0)) from (select column1 key from (values "+values+") guards order by column1) ordered_guards",keys.toArray());
    }
    public void lockProofPeriodRange(String institution,java.time.LocalDate from,java.time.LocalDate through) {
        jdbc.queryForList("select pg_advisory_xact_lock_shared(hashtextextended(?,0))","GL_SETUP/"+institution);
        jdbc.queryForList("select id from accounting_period where sacco_id=? and starts_on<=? and ends_on>=? order by starts_on,id for share",UUID.class,institution,through,from);
    }
    private void lockSourcePeriods(String institution,String branch,List<UUID> journals) {
        require(journals!=null && journals.size()<=1000 && journals.stream().allMatch(Objects::nonNull),"size");
        jdbc.queryForList("select pg_advisory_xact_lock_shared(hashtextextended(?,0))","GL_SETUP/"+institution);
        if(journals.isEmpty())return;
        String marks=String.join(",",Collections.nCopies(journals.size(),"?"));var args=new ArrayList<Object>(List.of(institution,institution,branch));args.addAll(journals);
        jdbc.queryForList("select p.id from accounting_period p where p.sacco_id=? and exists(select 1 from gl_journal j where j.sacco_id=? and j.station_id=? and j.period_id=p.id and j.id in ("+marks+")) order by p.starts_on,p.id for share of p",UUID.class,args.toArray());
    }
    public int latestApprovedVersion(String institution,String branch,UUID journal) {
        return jdbc.queryForObject("select coalesce(max(a.version),0) from cash_flow_allocations a join cash_flow_allocation_reviews r on r.allocation_id=a.id where a.sacco_id=? and a.station_id=? and a.journal_id=?",Integer.class,institution,branch,journal);
    }
    public Map<UUID,UUID> currentApprovedIdentifiers(String institution,String branch,List<UUID> journals) {
        require(journals!=null && journals.size()<=1000,"size");if(journals.isEmpty())return Map.of();
        String marks=String.join(",",Collections.nCopies(journals.size(),"?"));var args=new ArrayList<Object>(List.of(institution,branch));args.addAll(journals);
        var rows=jdbc.query("select distinct on(a.journal_id) a.journal_id,a.id from cash_flow_allocations a join cash_flow_allocation_reviews r on r.allocation_id=a.id where a.sacco_id=? and a.station_id=? and a.journal_id in ("+marks+") order by a.journal_id,a.version desc,a.id limit 1001",(r,n)->Map.entry(r.getObject("journal_id",UUID.class),r.getObject("id",UUID.class)),args.toArray());
        var result=new HashMap<UUID,UUID>();rows.forEach(row->result.put(row.getKey(),row.getValue()));return Map.copyOf(result);
    }
    public Optional<Version> retry(String institution,String branch,UUID request) {
        return query("a.sacco_id=? and a.station_id=? and a.request_key=?",institution,branch,request).stream().findFirst();
    }
    public UUID save(String institution,String branch,UUID request,UUID maker,OffsetDateTime now,String evidence,String noncash,Source source,List<Split> splits,String sourceHash,String definitionHash) {
        int version=jdbc.queryForObject("select coalesce(max(version),0)+1 from cash_flow_allocations where journal_id=?",Integer.class,source.journalId());UUID id=UUID.randomUUID();
        jdbc.update("insert into cash_flow_allocations(id,sacco_id,station_id,journal_id,version,request_key,made_by,made_at,evidence,noncash_evidence,source_json,source_checksum,definition_json,definition_checksum) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,institution,branch,source.journalId(),version,request,maker,now,evidence,noncash,mapper.writeValueAsString(source),sourceHash,mapper.writeValueAsString(splits),definitionHash);return id;
    }
    public Version version(String institution,String branch,UUID id,boolean lock) {
        if(lock) {var ids=jdbc.queryForList("select id from cash_flow_allocations where sacco_id=? and station_id=? and id=? for update",UUID.class,institution,branch,id);if(ids.isEmpty())throw new AccessDeniedException("Cash flow review unavailable");}
        var rows=query("a.sacco_id=? and a.station_id=? and a.id=?",institution,branch,id);if(rows.size()!=1)throw new AccessDeniedException("Cash flow review unavailable");return rows.getFirst();
    }
    public void approve(String institution,String branch,UUID id,UUID checker,String evidence,OffsetDateTime now){jdbc.update("insert into cash_flow_allocation_reviews(allocation_id,sacco_id,station_id,checker_id,evidence,reviewed_at) values(?,?,?,?,?,?)",id,institution,branch,checker,evidence,now);}
    public List<Version> approved(String institution,String branch,OffsetDateTime cutoff,List<UUID> journals) {
        require(journals!=null && journals.size()<=1000,"size");
        if(journals.isEmpty())return List.of();
        String marks=String.join(",",Collections.nCopies(journals.size(),"?"));var args=new ArrayList<Object>(List.of(institution,branch,cutoff));args.addAll(journals);
        return select("distinct on(a.journal_id) a.*","a.sacco_id=? and a.station_id=? and r.reviewed_at<=? and a.journal_id in ("+marks+") order by a.journal_id,a.version desc,a.id limit 1001",args.toArray());
    }
    public boolean allSourcesAvailable(String institution,String branch,OffsetDateTime cutoff,List<UUID> journals) {
        if(journals.isEmpty())return true;
        String marks=String.join(",",Collections.nCopies(journals.size(),"?"));var args=new ArrayList<Object>(List.of(institution,branch,cutoff,cutoff));args.addAll(journals);
        return Objects.equals(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=? and station_id=? and state='POSTED' and source_type<>'OPENING' and recorded_at<=? and posted_at<=? and id in ("+marks+")",Long.class,args.toArray()),(long)journals.size());
    }
    public List<UUID> qualifying(String institution,String branch,java.time.LocalDate from,java.time.LocalDate through,OffsetDateTime cutoff) {
        return jdbc.query("""
            select j.id from gl_journal j join gl_journal_line l on l.journal_id=j.id
            join gl_account a on (a.id,a.sacco_id)=(l.account_id,l.sacco_id)
            where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.source_type<>'OPENING'
              and j.effective_date between ? and ? and j.posted_at<=? and j.recorded_at<=?
            group by j.id having count(*) filter(where a.purpose in('CASH','BANK','MOBILE_MONEY'))>0
              and (count(*) filter(where a.purpose not in('CASH','BANK','MOBILE_MONEY'))>1
                or count(*) filter(where a.purpose in('CASH','BANK','MOBILE_MONEY'))>1
                  and count(*) filter(where a.purpose not in('CASH','BANK','MOBILE_MONEY'))>0)
            order by j.id limit 1001
            """,(r,n)->r.getObject("id",UUID.class),institution,branch,from,through,cutoff,cutoff);
    }
    /** One bounded batch verifies retained source records against their actual immutable posted journals. */
    public Map<UUID,Source> sources(String institution,String branch,OffsetDateTime cutoff,List<UUID> journals) {
        if(journals.isEmpty())return Map.of();String marks=String.join(",",Collections.nCopies(journals.size(),"?"));
        var args=new ArrayList<Object>(List.of(institution,branch,cutoff,cutoff));args.addAll(journals);
        var header=jdbc.queryForList("select id,policy_id,policy_version,source_reference from gl_journal where sacco_id=? and station_id=? and state='POSTED' and source_type<>'OPENING' and recorded_at<=? and posted_at<=? and id in ("+marks+") order by id",args.toArray());
        if(header.size()!=journals.size())throw new AccessDeniedException("Posted sources unavailable");
        var lineArgs=new ArrayList<Object>(List.of(institution,branch));lineArgs.addAll(journals);
        var lines=jdbc.query("select l.journal_id,l.id,l.account_id,a.code,a.type,a.purpose,l.debit-l.credit amount from gl_journal_line l join gl_account a on (a.id,a.sacco_id)=(l.account_id,l.sacco_id) where l.sacco_id=? and l.station_id=? and l.journal_id in ("+marks+") order by l.journal_id,l.id limit 10001",(r,n)->Map.entry(r.getObject("journal_id",UUID.class),new SourceLine(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getString("code"),r.getString("type"),r.getString("purpose"),r.getBigDecimal("amount"))),lineArgs.toArray());
        require(lines.size()<=10000,"size");var grouped=new HashMap<UUID,List<SourceLine>>();lines.forEach(e->grouped.computeIfAbsent(e.getKey(),k->new ArrayList<>()).add(e.getValue()));
        var result=new LinkedHashMap<UUID,Source>();for(var h:header){UUID id=(UUID)h.get("id");result.put(id,new Source(id,(UUID)h.get("policy_id"),(Integer)h.get("policy_version"),h.get("source_reference").toString(),grouped.getOrDefault(id,List.of())));}return Map.copyOf(result);
    }
    public List<Version> list(String institution,String branch,int page){return query("a.sacco_id=? and a.station_id=? order by a.made_at desc,a.id limit 26 offset ?",institution,branch,page*25);}
    public Set<UUID> supersededVersions(String institution,String branch,List<UUID> versions){
        require(versions!=null && versions.size()<=25 && versions.stream().allMatch(Objects::nonNull),"size");if(versions.isEmpty())return Set.of();
        String marks=String.join(",",Collections.nCopies(versions.size(),"?"));var args=new ArrayList<Object>(List.of(institution,branch));args.addAll(versions);
        return Set.copyOf(jdbc.query("select a.id from cash_flow_allocations a where a.sacco_id=? and a.station_id=? and a.id in ("+marks+") and exists(select 1 from cash_flow_allocations newer join cash_flow_allocation_reviews r on r.allocation_id=newer.id where newer.sacco_id=a.sacco_id and newer.station_id=a.station_id and newer.journal_id=a.journal_id and newer.version>a.version) order by a.id limit 26",(r,n)->r.getObject("id",UUID.class),args.toArray()));
    }
    public boolean hasInstitutionHistory(String institution){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from cash_flow_allocations where sacco_id=?)",Boolean.class,institution));}
    public boolean hasMemberHistory(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from cash_flow_allocations where made_by=?) or exists(select 1 from cash_flow_allocation_reviews where checker_id=?)",Boolean.class,id,id));}
    private List<Version> query(String predicate,Object...args) {
        return select("a.*",predicate,args);
    }
    private List<Version> select(String projection,String predicate,Object...args) {
        return jdbc.query("select "+projection+",r.checker_id,r.reviewed_at,r.evidence review_evidence from cash_flow_allocations a left join cash_flow_allocation_reviews r on r.allocation_id=a.id where "+predicate,(r,n)->new Version(r.getObject("id",UUID.class),r.getObject("journal_id",UUID.class),r.getInt("version"),r.getObject("made_by",UUID.class),r.getObject("made_at",OffsetDateTime.class),r.getString("evidence"),r.getString("noncash_evidence"),r.getString("source_checksum"),r.getString("definition_checksum"),mapper.readValue(r.getString("source_json"),Source.class),mapper.readValue(r.getString("definition_json"),mapper.getTypeFactory().constructCollectionType(List.class,Split.class)),r.getObject("checker_id",UUID.class),r.getObject("reviewed_at",OffsetDateTime.class),r.getString("review_evidence")),args);
    }
}
