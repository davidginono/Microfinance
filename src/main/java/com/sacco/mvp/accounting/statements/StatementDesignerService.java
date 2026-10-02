package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.accounting.reconciliation.ReconciliationService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;
import static com.sacco.mvp.accounting.statements.StatementRepository.*;

@Service @RequiredArgsConstructor
public class StatementDesignerService {
    private final StatementRepository repository;
    private final AccountingPolicyService policies;
    private final GeneralLedgerService ledger;
    private final ReconciliationService closing;
    private final RegulatoryFormatCatalog official;
    private final AccessControlService access;
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final List<StatementMappingChangeListener> mappingChangeListeners;
    public record RowValue(String id,String labelEn,String labelSw,RowKind kind,Unit unit,BigDecimal current,
            BigDecimal comparison,String status,String comparisonStatus,boolean visible,boolean collapsed,boolean mandatory,String noteEn,String noteSw) { }
    public record Result(UUID id,UUID versionId,UUID templateId,int mappingVersion,int calculationVersion,StatementDefinition definition,
            String institution,String branch,LocalDate from,LocalDate through,LocalDate comparisonFrom,LocalDate comparisonThrough,
            OffsetDateTime recordedCutoff,OffsetDateTime comparisonCutoff,UUID policyId,int policyVersion,UUID closeReviewId,
            String closeChecksum,UUID comparisonCloseId,String comparisonCloseChecksum,String coverage,String status,
            List<RowValue> rows,Map<String,BigDecimal> reconciliations,List<String> disclosures,UUID mappingReviewer,String mappingApprovalEvidence,OffsetDateTime generatedAt,UUID priorResultId) {
        public Result { rows=List.copyOf(rows);reconciliations=Collections.unmodifiableMap(new LinkedHashMap<>(reconciliations));disclosures=List.copyOf(disclosures); }
        @com.fasterxml.jackson.annotation.JsonIgnore public List<RowValue> getVisibleRows(){return rows.stream().filter(r->r.visible() || r.mandatory()).toList();}
        Result withId(UUID id){return copy(id,priorResultId);}
        Result withHistory(UUID prior){return copy(id,prior);}
        private Result copy(UUID id,UUID prior){return new Result(id,versionId,templateId,mappingVersion,calculationVersion,definition,institution,branch,from,through,comparisonFrom,comparisonThrough,recordedCutoff,comparisonCutoff,policyId,policyVersion,closeReviewId,closeChecksum,comparisonCloseId,comparisonCloseChecksum,coverage,status,rows,reconciliations,disclosures,mappingReviewer,mappingApprovalEvidence,generatedAt,prior);}
    }
    private record Source(LocalDate from,LocalDate through,OffsetDateTime cutoff,UUID closeId,String checksum,
            UUID policyId,int policyVersion,List<Balance> balances,List<Map<String,Object>> cashMovements,Map<String,Object> cashTransfers,List<String> disclosures,String reportingFramework) { }
    public record MappingApproval(UUID id,UUID templateId,int version,UUID reviewer,String evidence,String state,String definitionChecksum) { }

    @Transactional public UUID save(AppUserPrincipal actor,UUID template,StatementDefinition definition,String evidence){
        actor=authorize(actor,UserClaim.STATEMENT_DESIGN);text(evidence,1000);definition.validateMappings(catalog(actor));
        UUID id=repository.save(actor.getSaccoId(),actor.getMemberId(),template,definition,evidence,clock.now());event(actor,id,"DESIGNED");return id;
    }
    @Transactional public void approve(AppUserPrincipal actor,UUID id,String evidence){
        actor=authorize(actor,UserClaim.STATEMENT_APPROVE);text(evidence,1000);Version v=repository.version(actor.getSaccoId(),id,true);
        require(!v.maker().equals(actor.getMemberId()),"checker");require(v.state().equals("DRAFT"),"state");v.definition().validateMappings(catalog(actor));
        repository.approve(actor.getSaccoId(),id,actor.getMemberId(),evidence,clock.now());event(actor,id,"MAPPING_APPROVED");
        for(var listener:mappingChangeListeners)listener.mappingChanged(actor,v.template(),id,"MAPPING_APPROVED");
    }
    @Transactional public void retire(AppUserPrincipal actor,UUID id){actor=authorize(actor,UserClaim.STATEMENT_APPROVE);Version v=repository.version(actor.getSaccoId(),id,true);require(v.state().equals("APPROVED"),"state");repository.retire(actor.getSaccoId(),id);event(actor,id,"RETIRED");for(var listener:mappingChangeListeners)listener.mappingChanged(actor,v.template(),id,"RETIRED");}
    @Transactional(readOnly=true) public List<Version> versions(AppUserPrincipal actor,int page){actor=authorizeAny(actor,UserClaim.STATEMENT_VIEW,UserClaim.STATEMENT_DESIGN,UserClaim.STATEMENT_APPROVE);page(page);return repository.versions(actor.getSaccoId(),page);}
    @Transactional(readOnly=true) public Version version(AppUserPrincipal actor,UUID id){actor=authorizeAny(actor,UserClaim.STATEMENT_VIEW,UserClaim.STATEMENT_DESIGN,UserClaim.STATEMENT_APPROVE);return repository.version(actor.getSaccoId(),id,false);}
    @Transactional(readOnly=true) public List<Account> catalog(AppUserPrincipal actor){authorizeAny(actor,UserClaim.STATEMENT_VIEW,UserClaim.STATEMENT_DESIGN,UserClaim.STATEMENT_APPROVE);var accounts=repository.accounts(actor.getSaccoId());require(accounts.size()<=1000,"size");return accounts;}

    @Transactional(isolation=Isolation.REPEATABLE_READ,timeout=20)
    public Result preview(AppUserPrincipal actor,UUID id,LocalDate from,LocalDate through,LocalDate compareFrom,LocalDate compareThrough,OffsetDateTime cutoff){
        actor=authorize(actor,UserClaim.STATEMENT_VIEW);dates(from,through);require(cutoff!=null && !cutoff.isAfter(clock.now()),"dates");Version v=repository.version(actor.getSaccoId(),id,false);
        var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),through);require(!from.isBefore(policy.openingDate()) && policy.approvedAt()!=null && !policy.approvedAt().isAfter(cutoff),"policy");var coverage=ledger.coverage(actor);
        var balances=repository.balances(actor.getSaccoId(),actor.getStationId(),from,through,cutoff);require(balances.size()<=1000,"size");
        String coverageStatus=coverage.reviewedOpening() && repository.openingAvailable(actor.getSaccoId(),actor.getStationId(),through,cutoff)?coverage.status():"INCOMPLETE_NOT_AUTHORITATIVE";
        Source current=new Source(from,through,cutoff,null,null,policy.id(),policy.version(),balances,List.of(),Map.of(),List.of(coverageStatus),policy.decisions().get(com.sacco.mvp.accounting.policy.PolicyDecision.REPORTING_FRAMEWORK));Source comparison=null;
        if(v.definition().comparison()){dates(compareFrom,compareThrough);var comparisonPolicy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),compareThrough);require(compareThrough.isBefore(from) && !compareFrom.isBefore(comparisonPolicy.openingDate()) && comparisonPolicy.approvedAt()!=null && !comparisonPolicy.approvedAt().isAfter(cutoff),"dates");String comparisonCoverage=repository.openingAvailable(actor.getSaccoId(),actor.getStationId(),compareThrough,cutoff)?coverage.status():"INCOMPLETE_NOT_AUTHORITATIVE";comparison=new Source(compareFrom,compareThrough,cutoff,null,null,comparisonPolicy.id(),comparisonPolicy.version(),repository.balances(actor.getSaccoId(),actor.getStationId(),compareFrom,compareThrough,cutoff),List.of(),Map.of(),List.of(comparisonCoverage),comparisonPolicy.decisions().get(com.sacco.mvp.accounting.policy.PolicyDecision.REPORTING_FRAMEWORK));}
        Result result=render(v,actor,current,comparison,false);event(actor,id,"PREVIEWED");return result;
    }
    // Sources are immutable reviewed close snapshots; READ_COMMITTED makes a waiting duplicate observe the first retained result.
    @Transactional(timeout=30)
    public UUID finalize(AppUserPrincipal actor,UUID versionId,UUID closeReviewId,UUID comparisonCloseId){
        return finalize(actor,versionId,closeReviewId,comparisonCloseId,null);
    }
    @Transactional(timeout=30)
    public UUID finalize(AppUserPrincipal actor,UUID versionId,UUID closeReviewId,UUID comparisonCloseId,UUID priorResultId){
        actor=authorize(actor,UserClaim.STATEMENT_FINALIZE);verifiedVersionForPublication(actor,versionId);Version v=repository.version(actor.getSaccoId(),versionId,false);
        Source current=closed(actor,closeReviewId);Source comparison=null;
        if(v.definition().comparison()){require(comparisonCloseId!=null,"comparison");comparison=closed(actor,comparisonCloseId);require(comparison.through().isBefore(current.from()),"dates");}
        else require(comparisonCloseId==null,"comparison");
        if(current.disclosures().contains("RESTATEMENT") && repository.hasEarlierResult(actor.getSaccoId(),actor.getStationId(),v.template(),current.from(),current.through(),current.closeId()))require(priorResultId!=null,"restatement");
        if(priorResultId!=null){Result prior=verifiedResult(actor,priorResultId);require(current.disclosures().contains("RESTATEMENT") && prior.templateId().equals(v.template()) && prior.definition().kind()==v.definition().kind() && prior.from().equals(current.from()) && prior.through().equals(current.through()) && !prior.closeReviewId().equals(current.closeId()),"restatement");}
        Result result=render(v,actor,current,comparison,true).withHistory(priorResultId);String json=mapper.writeValueAsString(result);require(json.length()<=1000000,"size");
        UUID id=repository.freeze(actor.getSaccoId(),actor.getStationId(),versionId,closeReviewId,comparisonCloseId,priorResultId,json,sha(json.getBytes(StandardCharsets.UTF_8)),actor.getMemberId(),clock.now());event(actor,id,"FINALIZED");return id;
    }
    /** H captures only this immutable authoritative result; it never accepts submitted amounts or coverage flags. */
    @Transactional public Result verifiedResult(AppUserPrincipal actor,UUID id){
        actor=authorize(actor,UserClaim.STATEMENT_VIEW);StoredResult stored=repository.stored(actor.getSaccoId(),actor.getStationId(),id);require(sha(stored.json().getBytes(StandardCharsets.UTF_8)).equals(stored.checksum()),"checksum");
        Result result=mapper.readValue(stored.json(),Result.class);require(result.status().equals("FINAL") && result.closeReviewId()!=null && result.closeChecksum()!=null,"unapproved");
        require(result.institution().equals(actor.getSaccoId()) && result.branch().equals(actor.getStationId()),"scope");event(actor,id,"VIEWED_FINAL");return result.withId(id);
    }
    @Transactional public String verifiedResultDigest(AppUserPrincipal actor,UUID id){verifiedResult(actor,id);return repository.stored(actor.getSaccoId(),actor.getStationId(),id).checksum();}
    public AppUserPrincipal authorizeExport(AppUserPrincipal actor){AppUserPrincipal current=authorize(actor,UserClaim.STATEMENT_VIEW);return authorize(current,UserClaim.STATEMENT_EXPORT);}
    @Transactional(propagation=Propagation.MANDATORY)
    public MappingApproval verifiedVersionForPublication(AppUserPrincipal actor,UUID versionId){actor=authorize(actor,UserClaim.STATEMENT_VIEW);Version v=repository.versionForPublication(actor.getSaccoId(),versionId);require(v.state().equals("APPROVED") && v.checker()!=null && !v.checker().equals(v.maker()) && v.reviewEvidence()!=null && !v.reviewEvidence().isBlank(),"unapproved");v.definition().validate();return new MappingApproval(v.id(),v.template(),v.version(),v.checker(),v.reviewEvidence(),v.state(),repository.definitionChecksum(actor.getSaccoId(),versionId));}
    @SuppressWarnings("unchecked") private Source closed(AppUserPrincipal actor,UUID id){
        require(id!=null,"close");var close=closing.finalizedSnapshotForPublication(actor,id);require(close.periodClosed(),"close");require(close.checksum().equals(sha(close.snapshot().getBytes(StandardCharsets.UTF_8))),"checksum");
        Map<String,Object> snapshot=mapper.readerFor(Map.class).with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(close.snapshot());var accountRows=(List<Map<String,Object>>)snapshot.get("accounts");require(accountRows!=null && accountRows.size()<=1000,"source");var balances=new ArrayList<Balance>();
        for(var a:accountRows){require(a.get("id")!=null && a.get("type")!=null && a.get("closing")!=null && a.get("opening")!=null && a.get("period_debit")!=null && a.get("period_credit")!=null,"source");balances.add(new Balance(new Account(UUID.fromString(a.get("id").toString()),a.get("code").toString(),a.get("code").toString(),a.get("type").toString(),a.get("purpose").toString()),decimal(a.get("opening")),decimal(a.get("period_debit")),decimal(a.get("period_credit")),decimal(a.get("closing"))));}
        var policy=(Map<String,Object>)snapshot.get("accountingPolicy");require(policy!=null && policy.get("id")!=null && policy.get("policy_version")!=null && policy.get("effective_from")!=null,"source");
        var applicable=policies.requireApprovedLocalPolicy(actor.getSaccoId(),LocalDate.parse(policy.get("effective_from").toString()));require(applicable.id().toString().equals(policy.get("id").toString()) && applicable.version()==Integer.parseInt(policy.get("policy_version").toString()),"policy");
        var disclosures=new ArrayList<String>();disclosures.add(close.coverage());if(close.restatement())disclosures.add("RESTATEMENT");
        var differences=(List<Map<String,Object>>)snapshot.get("retainedDifferences");if(differences!=null)for(var d:differences)disclosures.add("REVIEWED_DIFFERENCE: "+d.get("kind")+"; "+d.get("review_evidence"));
        return new Source(LocalDate.parse(snapshot.get("from").toString()),close.asOf(),close.recordedCutoff(),close.id(),close.checksum(),applicable.id(),applicable.version(),List.copyOf(balances),(List<Map<String,Object>>)snapshot.getOrDefault("cashMovements",List.of()),(Map<String,Object>)snapshot.getOrDefault("cashTransfers",Map.of()),List.copyOf(disclosures),applicable.decisions().get(com.sacco.mvp.accounting.policy.PolicyDecision.REPORTING_FRAMEWORK));
    }
    private Result render(Version version,AppUserPrincipal actor,Source current,Source comparison,boolean finalRequested){
        StatementDefinition d=version.definition();d.validateMappings(current.balances().stream().map(Balance::account).toList());if(comparison!=null)d.validateMappings(comparison.balances().stream().map(Balance::account).toList());
        var source=values(d,current,finalRequested);var compare=comparison==null?Map.<UUID,BigDecimal>of():values(d,comparison,finalRequested);var calculated=d.calculate(source);var other=comparison==null?Map.<String,Value>of():d.calculate(compare);
        var rows=new ArrayList<RowValue>();for(var r:d.rows()){Value a=calculated.get(r.id()),b=other.get(r.id());rows.add(new RowValue(r.id(),r.labelEn(),r.labelSw(),r.kind(),r.unit(),a.amount(),b==null?null:b.amount(),a.status(),b==null?"NOT_REQUESTED":b.status(),r.visible(),r.collapsed(),false,r.noteEn(),r.noteSw()));}
        var totals=mandatory(d,current);var compareTotals=comparison==null?Map.<String,BigDecimal>of():mandatory(d,comparison);
        totals.forEach((key,value)->rows.add(new RowValue("REQUIRED_"+key,key,key,RowKind.SUBTOTAL,Unit.TZS,value,compareTotals.get(key),value==null?"UNAVAILABLE":"KNOWN",comparison==null?"NOT_REQUESTED":compareTotals.get(key)==null?"UNAVAILABLE":"KNOWN",true,false,true,null,null)));
        for(var x:d.exclusions()){Account account=current.balances().stream().map(Balance::account).filter(a->a.id().equals(x.account())).findFirst().orElseThrow();rows.add(new RowValue("EXCLUDED_"+account.code(),account.code()+": "+x.treatmentEn(),account.code()+": "+x.treatmentSw(),RowKind.NOTE,Unit.TZS,source.get(x.account()),compare.get(x.account()),source.get(x.account())==null?"UNAVAILABLE":"KNOWN",comparison==null?"NOT_REQUESTED":"KNOWN",true,false,true,x.evidence(),x.evidence()));}
        if(finalRequested){require(rows.stream().filter(r->r.unit()==Unit.TZS).noneMatch(r->r.current()==null || comparison!=null && r.comparison()==null),"unknown");require(totals.get("RECONCILIATION_DIFFERENCE").signum()==0,"reconciliation");
            if(d.kind()==Kind.CASH_FLOW){BigDecimal classified=current.cashMovements().stream().map(m->decimal(m.get("counterpart_movement")).negate()).reduce(BigDecimal.ZERO,BigDecimal::add);require(classified.compareTo(totals.get("CASH_MOVEMENT"))==0,"reconciliation");}}
        var disclosures=new ArrayList<String>(current.disclosures());disclosures.add("TZS; POSTED_ENTRIES; EFFECTIVE_DATE; RECORDED_CUTOFF; NOT_AN_AUDIT_OPINION");disclosures.add("ACCOUNTING_BASIS: "+current.reportingFramework());if(comparison!=null)disclosures.add("COMPARISON_POLICY: "+comparison.policyId()+" / "+comparison.policyVersion()+"; "+comparison.reportingFramework());
        return new Result(null,version.id(),version.template(),version.version(),d.calculationVersion(),d,actor.getSaccoId(),actor.getStationId(),current.from(),current.through(),comparison==null?null:comparison.from(),comparison==null?null:comparison.through(),current.cutoff(),comparison==null?null:comparison.cutoff(),current.policyId(),current.policyVersion(),current.closeId(),current.checksum(),comparison==null?null:comparison.closeId(),comparison==null?null:comparison.checksum(),current.disclosures().getFirst(),finalRequested?"FINAL":"DRAFT",List.copyOf(rows),totals,List.copyOf(disclosures),version.checker(),version.reviewEvidence(),clock.now(),null);
    }
    private Map<UUID,BigDecimal> values(StatementDefinition d,Source s,boolean finalRequested){
        var values=new HashMap<UUID,BigDecimal>();for(Balance b:s.balances())values.put(b.account().id(),s.closeId()==null && !s.disclosures().getFirst().equals("REVIEWED_COVERAGE")?null:d.kind()==Kind.PROFIT_AND_LOSS || d.kind()==Kind.CHANGES_IN_EQUITY?b.debit().subtract(b.credit()):b.closing());
        if(d.kind()==Kind.CASH_FLOW){values.clear();for(Balance b:s.balances())values.put(b.account().id(),finalRequested?BigDecimal.ZERO:null);
            if(finalRequested){require(!s.cashTransfers().isEmpty() && s.cashTransfers().containsKey("ambiguous_journals"),"source");require(decimal(s.cashTransfers().get("ambiguous_journals")).signum()==0 && decimal(s.cashTransfers().getOrDefault("net_cash_movement",BigDecimal.ZERO)).signum()==0,"cashAmbiguous");for(var m:s.cashMovements()){require(Integer.parseInt(m.get("money_lines").toString())==1,"cashAmbiguous");UUID account=UUID.fromString(m.get("account_id").toString());values.merge(account,decimal(m.get("counterpart_movement")).negate(),BigDecimal::add);}}}
        return values;
    }
    private Map<String,BigDecimal> mandatory(StatementDefinition d,Source s){
        var totals=new LinkedHashMap<String,BigDecimal>();var purposes=new HashMap<String,BigDecimal>();BigDecimal assets=BigDecimal.ZERO,liabilities=BigDecimal.ZERO,equity=BigDecimal.ZERO,income=BigDecimal.ZERO,expenses=BigDecimal.ZERO,openingEquity=BigDecimal.ZERO,cashOpening=BigDecimal.ZERO,cashClosing=BigDecimal.ZERO;
        for(Balance b:s.balances()) {switch(b.account().type()){case "ASSET"->assets=assets.add(b.closing());case "LIABILITY"->liabilities=liabilities.subtract(b.closing());case "EQUITY"->{equity=equity.subtract(b.closing());openingEquity=openingEquity.subtract(b.opening());}case "INCOME"->{income=income.add(b.credit().subtract(b.debit()));equity=equity.subtract(b.closing());openingEquity=openingEquity.subtract(b.opening());}case "EXPENSE"->{expenses=expenses.add(b.debit().subtract(b.credit()));equity=equity.subtract(b.closing());openingEquity=openingEquity.subtract(b.opening());}default->throw new IllegalArgumentException("statement.error.source");}
            purposes.merge(b.account().purpose(),b.closing(),BigDecimal::add);
            if(Set.of("CASH","BANK","MOBILE_MONEY").contains(b.account().purpose())){cashOpening=cashOpening.add(b.opening());cashClosing=cashClosing.add(b.closing());}}
        totals.put("ASSETS",assets);totals.put("LIABILITIES",liabilities);totals.put("EQUITY",equity);totals.put("INCOME",income);totals.put("EXPENSES",expenses);totals.put("PROFIT",income.subtract(expenses));totals.put("OPENING_EQUITY",openingEquity);totals.put("EQUITY_MOVEMENT",equity.subtract(openingEquity));totals.put("CASH_OPENING",cashOpening);totals.put("CASH_CLOSING",cashClosing);totals.put("CASH_MOVEMENT",cashClosing.subtract(cashOpening));totals.put("RECONCILIATION_DIFFERENCE",assets.subtract(liabilities).subtract(equity));
        for(String key:List.of("LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","CASH","BANK","MOBILE_MONEY"))totals.put(key,purposes.getOrDefault(key,BigDecimal.ZERO));
        for(String key:List.of("ALLOWANCE","PAYABLE","FUNDING","CAPITAL"))totals.put(key,purposes.getOrDefault(key,BigDecimal.ZERO).negate());
        totals.put("NET_LOANS",totals.get("LOAN_PRINCIPAL").add(totals.get("INTEREST_RECEIVABLE")).add(totals.get("FEE_RECEIVABLE")).subtract(totals.get("ALLOWANCE")));
        if(s.closeId()==null && !s.disclosures().getFirst().equals("REVIEWED_COVERAGE"))totals.replaceAll((key,value)->null);return totals;
    }
    @Transactional(readOnly=true) public List<OfficialFormat> formats(AppUserPrincipal actor){authorizeAny(actor,UserClaim.REGULATORY_SUBMISSION_VIEW,UserClaim.REGULATORY_SUBMISSION_CREATE,UserClaim.REGULATORY_SUBMISSION_APPROVE);var result=repository.formats();require(result.size()<=100,"size");return result;}
    @Transactional public UUID submit(AppUserPrincipal actor,UUID formatId,UUID resultId,String file,byte[] bytes,String evidence,UUID corrects){
        actor=authorize(actor,UserClaim.REGULATORY_SUBMISSION_CREATE);text(file,200);text(evidence,1000);require(file.chars().noneMatch(c->c=='/' || c=='\\') && bytes!=null && bytes.length>0 && bytes.length<=2000000,"officialFile");String checksum=sha(bytes);Result result=verifiedResult(actor,resultId);requireCurrentPublication(actor,result);var format=official.get(formatId);require(format.kind()==result.definition().kind(),"officialKind");validateOfficial(format,result);LocalDate deadline=format.deadline(result.from(),result.through());
        require(file.toLowerCase(Locale.ROOT).endsWith("."+format.fileFormat().name().toLowerCase(Locale.ROOT)),"officialFile");SubmissionFileValidation.validate(bytes,format.fileFormat());if(corrects!=null){Submission prior=repository.submission(actor.getSaccoId(),actor.getStationId(),corrects,false);require(prior.format().equals(formatId) && prior.from().equals(result.from()) && prior.through().equals(result.through()) && prior.checker()!=null,"correction");}
        UUID id=repository.submit(actor.getSaccoId(),actor.getStationId(),formatId,resultId,result.from(),result.through(),deadline,file,checksum,bytes,evidence,corrects,actor.getMemberId(),clock.now());event(actor,id,"SUBMISSION_PREPARED");return id;
    }
    @Transactional public void reviewSubmission(AppUserPrincipal actor,UUID id,String evidence){actor=authorize(actor,UserClaim.REGULATORY_SUBMISSION_APPROVE);text(evidence,1000);Submission s=repository.submission(actor.getSaccoId(),actor.getStationId(),id,true);require(s.checker()==null && !s.maker().equals(actor.getMemberId()),"checker");Result result=verifiedResult(actor,s.result());requireCurrentPublication(actor,result);validateOfficial(official.get(s.format()),result);repository.reviewSubmission(actor.getSaccoId(),id,actor.getMemberId(),evidence,clock.now());event(actor,id,"SUBMISSION_REVIEWED");}
    private void requireCurrentPublication(AppUserPrincipal actor,Result result){verifiedVersionForPublication(actor,result.versionId());closing.finalizedSnapshotForPublication(actor,result.closeReviewId());if(result.comparisonCloseId()!=null)closing.finalizedSnapshotForPublication(actor,result.comparisonCloseId());}
    private void validateOfficial(RegulatoryFormatCatalog.Format format,Result result){
        format.validate();var rows=new ArrayList<Row>();var values=new HashMap<UUID,BigDecimal>();
        for(var source:result.rows())if(source.mandatory() && source.id().startsWith("REQUIRED_")){UUID key=UUID.nameUUIDFromBytes(source.id().getBytes(StandardCharsets.UTF_8));values.put(key,source.current());rows.add(new Row(source.id(),source.id(),source.id(),RowKind.ACCOUNT_GROUP,Section.NOTES,source.unit(),List.of(key),Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null));}
        for(var c:format.calculations())rows.add(new Row(c.key(),c.key(),c.key(),c.unit()==Unit.RATIO?RowKind.RATIO:RowKind.SUBTOTAL,Section.NOTES,c.unit(),List.of(),null,c.expression(),null,null,true,false,false,null));
        var protectedDefinition=new StatementDefinition(1,1,Kind.NOTES,"Official calculations","Mahesabu rasmi",rows,List.of(),false);var calculated=protectedDefinition.calculate(values);
        for(var field:format.fields()){Value value=calculated.get(field.sourceLine());require(value!=null && value.amount()!=null && value.status().equals("KNOWN"),"officialField");}
    }
    @Transactional(readOnly=true) public List<Submission> submissions(AppUserPrincipal actor,int page){actor=authorizeAny(actor,UserClaim.REGULATORY_SUBMISSION_VIEW,UserClaim.REGULATORY_SUBMISSION_CREATE,UserClaim.REGULATORY_SUBMISSION_APPROVE);page(page);return repository.submissions(actor.getSaccoId(),actor.getStationId(),page);}
    @Transactional public byte[] submissionFile(AppUserPrincipal actor,UUID id){actor=authorize(actor,UserClaim.REGULATORY_SUBMISSION_VIEW);Submission s=repository.submission(actor.getSaccoId(),actor.getStationId(),id,false);verifiedResult(actor,s.result());byte[] bytes=repository.submissionFile(actor.getSaccoId(),actor.getStationId(),id);require(sha(bytes).equals(s.fileChecksum()),"checksum");event(actor,id,"SUBMISSION_FILE_DOWNLOADED");return bytes;}
    @Transactional(readOnly=true) public boolean hasInstitutionHistory(String id){return id!=null && repository.hasInstitutionHistory(id);}
    @Transactional(readOnly=true) public boolean hasMemberHistory(UUID id){return id!=null && repository.hasMemberHistory(id);}
    public AppUserPrincipal authorize(AppUserPrincipal actor,UserClaim claim){return authorizeAny(actor,claim);}
    private AppUserPrincipal authorizeAny(AppUserPrincipal actor,UserClaim...required){
        if(actor==null || !actor.isStaffSession() || actor.isPlatformIdentity() || actor.getMemberId()==null || actor.getSaccoId()==null || actor.getStationId()==null || actor.getSaccoId().isBlank() || actor.getStationId().isBlank())denied();
        Member member=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Statement access unavailable"));
        if(member.getStatus()!=MemberStatus.ACTIVE || !member.isStaffAccessActive() || member.getActiveStaffRolesResolved().contains(Position.ADMIN) || !Objects.equals(member.getSaccoId(),actor.getSaccoId()) || !Objects.equals(member.getStationId(),actor.getStationId()) || institutions.findActiveSacco(actor.getSaccoId()).isEmpty()
            || institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive).filter(s->s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())denied();
        AppUserPrincipal current=new AppUserPrincipal(member,claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),member.isMemberAccess()),true);if(!access.hasAny(current,required))denied();return current;
    }
    private void dates(LocalDate from,LocalDate through){require(from!=null && through!=null && !from.isAfter(through) && !through.isAfter(clock.today()) && java.time.temporal.ChronoUnit.DAYS.between(from,through)<=366,"dates");}
    private static BigDecimal decimal(Object value){require(value!=null,"unknown");try{return new BigDecimal(value.toString()).setScale(2,java.math.RoundingMode.UNNECESSARY);}catch(NumberFormatException|ArithmeticException e){throw new IllegalArgumentException("statement.error.source");}}
    private static void page(int page){require(page>=0 && page<=10000,"size");}
    private static void denied(){throw new AccessDeniedException("Statement permission and active branch required");}
    private void event(AppUserPrincipal actor,UUID id,String action){audit.log("FINANCIAL_STATEMENT",id,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));}
    public static String sha(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
