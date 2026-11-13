package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.Profile;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.*;
import com.sacco.mvp.grouplending.repository.SimpleGroupLoanRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class SimpleGroupLoanService {
    private final SimpleGroupLoanRepository records;
    private final GeneralLedgerService access;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final LoanQualificationPolicyService qualification;
    private final java.util.concurrent.Semaphore paperValidation=new java.util.concurrent.Semaphore(2);
    private void authorize(AppUserPrincipal a,String action){access.requireActor(a,"GROUP_LENDING_"+action);}
    @Transactional(readOnly=true) public Setup setup(AppUserPrincipal a,UUID group){authorize(a,"VIEW");return records.setup(a,group,false);}
    @Transactional(readOnly=true) public RecordingContext recordingContext(AppUserPrincipal a,UUID group){
        authorize(a,"VIEW");access.requireActor(a,"LOAN_RECORDING_CREATE");var setup=records.setup(a,group,false);
        var borrowers=new LinkedHashMap<UUID,BorrowerInput>();
        for(var row:records.profiles(a,group,clock.today())) {
            var profile=row.get("profile")==null?new Profile():json.readValue(row.get("profile").toString(),Profile.class);
            var missing=new ArrayList<String>();
            if(profile.getMonthlyIncome()==null||profile.getMonthlyIncome().signum()<=0)missing.add("monthlyIncome");
            if(profile.getMonthlyExpenses()==null||profile.getMonthlyExpenses().signum()<0)missing.add("monthlyExpenses");
            if(profile.getMonthlyDebtRepayments()==null||profile.getMonthlyDebtRepayments().signum()<0)missing.add("monthlyDebtRepayments");
            borrowers.put((UUID)row.get("id"),new BorrowerInput(incomeDocument(row).isBlank(),missing));
        }
        return new RecordingContext(setup,borrowers);
    }
    private String incomeDocument(Map<String,Object> row){return row.get("income_document")==null?"":"CLIENT_DOCUMENT:"+row.get("income_document");}
    @Transactional(readOnly=true) public boolean hasHistory(AppUserPrincipal a,UUID loan){access.requireActor(a,"LOAN_RECORDING_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_VIEW");authorize(a,"VIEW");return records.hasHistory(a,loan);}
    @Transactional(readOnly=true) public com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page<HistoricalDocument> history(AppUserPrincipal a,UUID loan,int page){access.requireActor(a,"LOAN_RECORDING_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_VIEW");authorize(a,"VIEW");check(page>=0&&page<=10000,"request");boolean coverage=a.getClaims().contains("GROUP_COVERAGE_VIEW");if(coverage)access.requireActor(a,"GROUP_COVERAGE_VIEW");var rows=records.history(a,loan,page,coverage);return new com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional public byte[] historicalDocument(AppUserPrincipal a,UUID loan,String kind,UUID id){access.requireActor(a,"LOAN_RECORDING_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");authorize(a,"VIEW");check(Set.of("PAPER","ORGANIZATION","SHARED","GUARANTEE","COVERAGE").contains(kind),"document");if(kind.equals("COVERAGE")){access.requireActor(a,"GROUP_COVERAGE_VIEW");access.requireActor(a,"GROUP_COVERAGE_EXPORT");}var bytes=records.historicalDocument(a,loan,kind,id);event(a,loan,"HISTORICAL_GROUP_DOCUMENT_EXPORTED");return bytes;}
    @Transactional(readOnly=true) public String fingerprint(AppUserPrincipal a,RecordForm f,List<String> files){access.requireActor(a,"LOAN_RECORDING_CREATE");return records.fingerprint(json.writeValueAsString(Arrays.asList(f,files)));}
    @Transactional public Optional<UUID> retry(AppUserPrincipal a,RecordForm f){access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");check(f.getGroupApplication()!=null&&f.getGroupApplication().getGroupId()!=null,"request");records.setup(a,f.getGroupApplication().getGroupId(),true);return records.retry(a,f.getRequestKey(),f.getGroupRequestHash());}
    @Transactional(readOnly=true) public Optional<UUID> completedRetry(AppUserPrincipal a,RecordForm f){access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");return records.retry(a,f.getRequestKey(),f.getGroupRequestHash());}
    private UUID persistDraft(AppUserPrincipal a,RecordForm f){access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");check(f!=null&&"GROUP".equals(f.getLoanFor())&&f.getRequestKey()!=null&&f.getGroupApplication()!=null&&f.getGroupApplication().getGroupId()!=null,"request");validateDraft(f);records.setup(a,f.getGroupApplication().getGroupId(),true);check(records.active(a,f.getGroupApplication().getGroupId()),"inactive");return records.draft(a,f.getApplicationId(),f.getRequestKey(),f.getExpectedVersion(),f.getGroupApplication().getGroupId(),json.writeValueAsString(f),clock.now());}
    @Transactional public UUID saveDraft(AppUserPrincipal a,RecordForm f,List<MultipartFile> files,MultipartFile agreement){
        access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");check(f!=null&&f.getGroupApplication()!=null&&f.getGroupApplication().getGroupId()!=null,"request");records.setup(a,f.getGroupApplication().getGroupId(),true);var stored=f.getApplicationId()==null?new ArrayList<DraftFile>():new ArrayList<>(Arrays.asList(json.readValue(records.draftFiles(a,f.getApplicationId()),DraftFile[].class)));
        if(f.getApplicationId()!=null&&!sameMaterial(readForm(records.draft(a,f.getApplicationId(),true).data()),f))stored.removeIf(x->x.agreement()||"GUARANTOR_CONSENT".equals(x.purpose()));
        appendFiles(stored,f,files,agreement);f.setGroupRequestHash(draftFileHash(stored));UUID id=persistDraft(a,f);records.draftFiles(a,id,json.writeValueAsString(stored));event(a,id,"GROUP_DRAFT_SAVED");return id;
    }
    private String draftFileHash(List<DraftFile> files){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(files)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void appendFiles(List<DraftFile> stored,RecordForm f,List<MultipartFile> files,MultipartFile agreement){
        check(files!=null&&files.size()<=10,"document");var details=f.getApplicationDetails();
        try{for(int i=0;i<files.size();i++){var file=files.get(i);if(file==null||file.isEmpty())continue;check(file.getSize()<=10*1024*1024,"document");String title=details!=null&&i<details.getDocumentNames().size()?details.getDocumentNames().get(i):Objects.toString(file.getOriginalFilename(),"Document");String purpose=details!=null&&i<details.getDocumentTypes().size()?details.getDocumentTypes().get(i):"OTHER";String requirement=details!=null&&i<details.getRequiredAttachmentIds().size()?details.getRequiredAttachmentIds().get(i):"";var added=new DraftFile(file.getOriginalFilename(),file.getContentType(),file.getBytes(),title,purpose,requirement,false);if(stored.stream().noneMatch(x->!x.agreement()&&Objects.equals(x.filename(),added.filename())&&Objects.equals(x.purpose(),added.purpose())&&Objects.equals(x.title(),added.title())&&Objects.equals(x.requirement(),added.requirement())&&Arrays.equals(x.bytes(),added.bytes())))stored.add(added);}
            if(agreement!=null&&!agreement.isEmpty()){check(agreement.getSize()<=10*1024*1024,"document");stored.removeIf(DraftFile::agreement);stored.add(new DraftFile(agreement.getOriginalFilename(),"application/pdf",agreement.getBytes(),"","","",true));}
        }catch(java.io.IOException e){throw new IllegalArgumentException("simpleGroup.error.document",e);}
        check(stored.stream().filter(x->!x.agreement()).count()<=10&&stored.stream().mapToLong(x->x.bytes().length).sum()<=30L*1024*1024,"document");
    }
    @Transactional(readOnly=true) public DraftFiles draftFiles(AppUserPrincipal a,RecordForm f,List<MultipartFile> files,MultipartFile agreement){
        access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");if(f.getApplicationId()==null)return new DraftFiles(files,agreement);
        var previous=records.draft(a,f.getApplicationId(),false);if((agreement==null||agreement.isEmpty())&&!sameMaterial(readForm(previous.data()),f))throw new IllegalArgumentException("simpleGroup.error.freshAgreement");
        var stored=Arrays.asList(json.readValue(records.draftFiles(a,f.getApplicationId()),DraftFile[].class));var result=new ArrayList<MultipartFile>();var details=f.getApplicationDetails();
        if(details==null){details=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ApplicationDetails();f.setApplicationDetails(details);}
        var names=new ArrayList<String>();var types=new ArrayList<String>();var requirements=new ArrayList<String>();
        for(var upload:stored)if(upload.agreement()){if(agreement==null||agreement.isEmpty())agreement=new SavedFile(upload);}else{result.add(new SavedFile(upload));names.add(upload.title());types.add(upload.purpose());requirements.add(upload.requirement());}
        result.addAll(files);names.addAll(details.getDocumentNames());types.addAll(details.getDocumentTypes());requirements.addAll(details.getRequiredAttachmentIds());details.setDocumentNames(names);details.setDocumentTypes(types);details.setRequiredAttachmentIds(requirements);
        check(result.size()<=10,"document");return new DraftFiles(result,agreement);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> draftDocuments(AppUserPrincipal a,UUID id){access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");records.draft(a,id,false);return records.draftDocuments(a,id);}
    @Transactional public DraftFile draftDocument(AppUserPrincipal a,UUID id,int index){access.requireActor(a,"LOAN_RECORDING_CREATE");access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");authorize(a,"VIEW");check(index>=0&&index<=10,"document");var file=records.draftFile(a,id,index,json);event(a,id,"GROUP_DRAFT_DOCUMENT_EXPORTED");return file;}
    private boolean sameMaterial(RecordForm first,RecordForm second){return material(first).equals(material(second));}
    private tools.jackson.databind.JsonNode material(RecordForm form){
        var tree=(tools.jackson.databind.node.ObjectNode)json.valueToTree(form);
        for(String key:List.of("applicationId","expectedVersion","requestKey","groupAgreementChecksum","groupRequestHash","notes"))tree.remove(key);
        if(tree.get("groupApplication") instanceof tools.jackson.databind.node.ObjectNode group)group.remove("checked");
        if(tree.get("applicationDetails") instanceof tools.jackson.databind.node.ObjectNode details)for(String key:List.of("documentNames","documentTypes","requiredAttachmentIds"))details.remove(key);
        return tree;
    }
    private record SavedFile(DraftFile source) implements MultipartFile {
        public String getName(){return "attachments";}public String getOriginalFilename(){return source.filename();}public String getContentType(){return source.contentType();}public boolean isEmpty(){return source.bytes().length==0;}public long getSize(){return source.bytes().length;}public byte[] getBytes(){return source.bytes().clone();}public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(source.bytes());}public void transferTo(java.io.File target)throws java.io.IOException{java.nio.file.Files.write(target.toPath(),source.bytes());}
    }
    private RecordForm readForm(String data){
        var form=json.readValue(data,RecordForm.class);
        // Older drafts stored individual dates. Reuse one only when every date agrees.
        if(form.getGroupApplication().getSignedOn()==null){var signers=json.readTree(data).path("groupApplication").path("signers");var dates=new HashSet<String>();
            for(var signer:signers)dates.add(signer.path("signedOn").asString(""));
            if(dates.size()==1&&!dates.contains(""))form.getGroupApplication().setSignedOn(LocalDate.parse(dates.iterator().next()));}
        return form;
    }
    @Transactional(readOnly=true) public RecordForm draft(AppUserPrincipal a,UUID id){access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");var d=records.draft(a,id,false);var f=readForm(d.data());f.setApplicationId(id);f.setExpectedVersion(d.version());f.getGroupApplication().setChecked(false);if(f.getApplicationDetails()!=null){f.getApplicationDetails().setDocumentNames(new ArrayList<>());f.getApplicationDetails().setDocumentTypes(new ArrayList<>());f.getApplicationDetails().setRequiredAttachmentIds(new ArrayList<>());}return f;}
    @Transactional(readOnly=true) public com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page<DraftSummary> drafts(AppUserPrincipal a,UUID group,int page){access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");check(page>=0&&page<=10000,"request");records.setup(a,group,false);var rows=records.drafts(a,group,page);return new com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional public void consumeDraft(AppUserPrincipal a,RecordForm f){if(f.getApplicationId()!=null)records.consumeDraft(a,f.getApplicationId(),f.getExpectedVersion());}
    @Transactional public void configure(AppUserPrincipal a,UUID group,SetupForm f){
        authorize(a,"UPDATE");var s=records.setup(a,group,true);
        check(f!=null&&f.getLeaderId()!=null&&s.people().stream().anyMatch(p->p.id().equals(f.getLeaderId())),"leader");
        check(f.isChecked()&&Set.of("BANK_ACCOUNT","MOBILE_MONEY").contains(f.getDestinationType()),"destination");
        text(f.getProvider(),120);text(f.getHolder(),160);text(f.getIdentifier(),160);text(f.getEvidence(),500);
        if(Objects.equals(f.getLeaderId(),s.leaderId())&&Objects.equals(f.getDestinationType(),s.destinationType())&&Objects.equals(f.getProvider().strip(),s.provider())&&Objects.equals(f.getHolder().strip(),s.holder())&&Objects.equals(f.getIdentifier().strip(),s.identifier())&&Objects.equals(f.getEvidence().strip(),s.evidence()))return;
        check(f.getExpectedVersion()==s.version(),"changed");check(records.saveSetup(a,group,f),"changed");audit.logEvent("GROUP_LENDING",group,"GROUP_PAYMENT_DETAILS_SAVED",a.getMemberId(),AuditEventStatus.SUCCESS,"Group leader and checked payment account recorded","LENDING_GROUP",group.toString(),a.getSaccoId(),a.getStationId(),Map.of("previousVersion",s.version(),"version",s.version()+1,"leader",f.getLeaderId(),"evidence",f.getEvidence().strip(),"accountFingerprint",records.fingerprint(json.writeValueAsString(List.of(f.getDestinationType(),f.getProvider().strip(),f.getHolder().strip(),f.getIdentifier().strip())))));
    }
    @Transactional(readOnly=true) public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> choices(AppUserPrincipal a,String search,int page){
        authorize(a,"VIEW");access.requireActor(a,"LOAN_RECORDING_CREATE");check(search!=null&&search.length()<=100&&page>=0&&page<=10000,"request");return records.choices(a,search,page);
    }
    @Transactional public Prepared prepare(AppUserPrincipal a,RecordForm f,LoanProductSetting product,LoanAmortizationCalculator.Result quote,boolean submitting){
        authorize(a,"VIEW");access.requireActor(a,"LOAN_RECORDING_CREATE");
        var g=f.getGroupApplication();check(g!=null&&g.getGroupId()!=null&&f.getClientId()==null&&f.getTopUpSourceLoanId()==null,"request");
        var s=records.setup(a,g.getGroupId(),submitting);check(s.version()==g.getExpectedVersion(),"changed");
        check(records.active(a,s.groupId()),"inactive");
        check(s.people().size()>=2&&s.people().size()<=50&&s.leaderId()!=null&&s.people().stream().anyMatch(p->p.id().equals(s.leaderId())),"leader");
        check(s.destinationType()!=null&&s.provider()!=null&&s.holder()!=null&&s.identifier()!=null&&s.evidence()!=null,"destination");
        if(f.getApplicationDetails()==null)f.setApplicationDetails(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ApplicationDetails());
        var memberIds=s.people().stream().map(Person::id).toList();
        if(submitting){
            var guarantees=f.getApplicationDetails().getGuarantors();check(guarantees!=null&&guarantees.size()<=20,"guarantee");
            var references=guarantees.stream().map(x->x.getClientId()==null?Objects.toString(x.getClientNumber(),"").strip():x.getClientId().toString()).toList();var candidates=records.guarantorPeople(a,references);
            for(int index=0;index<guarantees.size();index++){var person=candidates.get(references.get(index));check(person!=null&&!memberIds.contains(person.id()),"guarantee");guarantees.get(index).setClientId(person.id());guarantees.get(index).setFullName(person.name());guarantees.get(index).setClientNumber(person.number());}
            var locks=new HashSet<UUID>(memberIds);guarantees.forEach(x->locks.add(x.getClientId()));records.lockClients(a,locks.stream().sorted().toList());
        }
        var rows=records.profiles(a,s.groupId(),clock.today());check(rows.size()==s.people().size(),"members");
        check(g.getSigners()!=null&&g.getSigners().size()<=50,"signatures");
        var people=new HashMap<UUID,SignerForm>();for(var signer:g.getSigners()){check(signer!=null&&signer.getClientId()!=null&&people.put(signer.getClientId(),signer)==null,"signatures");}
        if(submitting)check(g.getSignedOn()!=null&&!g.getSignedOn().isAfter(clock.today())&&!g.getSignedOn().isBefore(f.getApplicationDate()),"signatures");
        check(g.getDebts()!=null&&g.getDebts().size()<=50,"budget");
        var externalByClient=new HashMap<UUID,BigDecimal>();var debtReferences=new HashSet<String>();BigDecimal externalDebt=BigDecimal.ZERO;
        for(var obligation:g.getDebts()){
            check(obligation!=null,"budget");text(obligation.getReference(),200);money(obligation.getMonthlyPayment(),true);
            check(debtReferences.add(obligation.getReference().strip().toLowerCase(Locale.ROOT))&&obligation.getClientIds()!=null&&!obligation.getClientIds().isEmpty()&&obligation.getClientIds().size()<=50,"budget");
            var named=new HashSet<>(obligation.getClientIds());check(named.size()==obligation.getClientIds().size()&&memberIds.containsAll(named),"budget");
            for(var id:named)externalByClient.merge(id,obligation.getMonthlyPayment(),BigDecimal::add);externalDebt=externalDebt.add(obligation.getMonthlyPayment());
        }
        var assessments=new ArrayList<Assessment>();BigDecimal capacity=BigDecimal.ZERO,income=BigDecimal.ZERO,expenses=BigDecimal.ZERO,debt=BigDecimal.ZERO;
        if(f.getApplicationDetails()==null)f.setApplicationDetails(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ApplicationDetails());
        f.getApplicationDetails().getGroupClientValues().clear();
        var sources=new HashSet<String>();var ids=s.people().stream().map(Person::id).toList();var exposures=records.exposures(a,ids);
        var policy=qualification.groupPolicy(a.getSaccoId(),a.getStationId());
        for(var row:rows){
            UUID id=(UUID)row.get("id");check(row.get("revision")!=null&&row.get("profile")!=null,"budget");
            var profile=json.readValue(row.get("profile").toString(),Profile.class);var signer=people.get(id);
            if(row.get("kyc")!=null)throw new IllegalArgumentException(row.get("kyc").toString());
            var e=exposures.get(id);check(e!=null&&e.missingHistory()==0&&e.pendingApplications()==0,"history");
            check(e.activeLoans()==0||(product.isApplicationWithActiveLoanAllowed()&&(product.getMaximumActiveLoans()==null||e.activeLoans()<product.getMaximumActiveLoans())),"activeLoans");
            check(policy.applicantDefaultLimit()==null||e.riskLoans()<policy.applicantDefaultLimit(),"history");
            BigDecimal i=profile.getMonthlyIncome(),x=profile.getMonthlyExpenses(),d=profile.getMonthlyDebtRepayments();money(i,true);money(x,false);money(d,false);
            check(d.subtract(e.monthlyDebt().setScale(2,RoundingMode.UP)).max(BigDecimal.ZERO).compareTo(externalByClient.getOrDefault(id,BigDecimal.ZERO))==0,"debts");
            BigDecimal disposable=i.subtract(x).subtract(d.max(e.monthlyDebt().setScale(2,RoundingMode.UP)));check(disposable.signum()>0,"budget");
            var values=new LinkedHashMap<String,String>();values.put("monthlyIncome",i.toPlainString());values.put("monthlyExpenses",x.toPlainString());values.put("otherDebtRepayments",d.toPlainString());
            values.put("businessOrEmploymentType",profile.getActivity());values.put("businessOrEmployerName","Employment".equals(profile.getActivity())?profile.getEmployerName():profile.getBusinessName());values.put("incomeSource",profile.getSourceOfFunds());
            f.getApplicationDetails().getGroupClientValues().add(values);
            String source=incomeDocument(row);if(source.isBlank())source=signer==null?"":Objects.toString(signer.getIncomeSource(),"");
            // The profile revision is the provenance of the recorded cash flow, not proof of independent verification.
            String budgetEvidence="CLIENT_PROFILE:"+row.get("revision");
            if(submitting){check(signer!=null,"signatures");text(source,200);check(sources.add(source.strip().toLowerCase(Locale.ROOT)),"incomeSource");}
            var ratio=product.getResolvedMaxRepaymentToDisposableIncomeRatio();check(ratio!=null&&ratio.signum()>0&&ratio.compareTo(BigDecimal.ONE)<=0,"budget");
            capacity=capacity.add(disposable.multiply(ratio).setScale(2,RoundingMode.DOWN));income=income.add(i);expenses=expenses.add(x);debt=debt.add(d);
            assessments.add(new Assessment(id,(UUID)row.get("revision"),Objects.toString(row.get("member_no")),Objects.toString(row.get("full_name")),i,x,d,source.strip(),budgetEvidence));
        }
        debt=records.monthlyDebt(a,s.groupId()).add(externalDebt);
        capacity=income.subtract(expenses).subtract(debt).multiply(product.getResolvedMaxRepaymentToDisposableIncomeRatio()).setScale(2,RoundingMode.DOWN);
        BigDecimal monthly=LoanAmortizationCalculator.monthlyEquivalent(quote.maximumInstallment(),product.getResolvedRepaymentFrequency());check(capacity.signum()>0&&monthly.compareTo(capacity)<=0,"affordability");
        check(f.getFirstPaymentDate()!=null&&f.getFirstPaymentDate().isAfter(f.getApplicationDate()),"request");
        if(submitting){check(g.isChecked()&&people.keySet().equals(new HashSet<>(ids)),"signatures");text(g.getReference(),500);check(Set.of("EN","SW").contains(g.getLanguage()),"signatures");

        }
        f.setIncome(income);f.setExpenses(expenses);f.setOtherDebt(debt);f.setBusiness(s.name());f.setInformationSource(submitting?"DOCUMENTED":"DECLARED");
        String destination=json.writeValueAsString(Map.of("available",true,"destinationType",s.destinationType(),"provider",s.provider(),"accountHolderName",s.holder(),"accountIdentifier",s.identifier(),"sourceLabel","SIGNED_GROUP_INSTRUCTION"));
        return new Prepared(submitting?records.party(a,s.groupId()):null,s,assessments,capacity,destination);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED) public SignedPaper validatePaper(AppUserPrincipal a,MultipartFile file){
        access.requireActor(a,"LOAN_RECORDING_CREATE");authorize(a,"VIEW");check(file!=null&&!file.isEmpty()&&file.getSize()<=10*1024*1024,"document");check(paperValidation.tryAcquire(),"documentBusy");
        try{byte[] body=file.getBytes();check(body.length>=5&&new String(body,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"),"document");GroupAgreementDocumentValidation.pages(body);String filename=Objects.toString(file.getOriginalFilename(),"group-agreement.pdf").replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1);text(filename,200);return new SignedPaper(filename,body,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body)));}
        catch(java.io.IOException|java.security.NoSuchAlgorithmException|IllegalArgumentException e){throw new IllegalArgumentException("simpleGroup.error.document",e);}finally{paperValidation.release();}
    }
    @Transactional public void record(AppUserPrincipal a,UUID loan,Prepared p,RecordForm f,SignedPaper paper){
        access.requireActor(a,"LOAN_RECORDING_CREATE");byte[] body=paper.body();String filename=paper.filename();
            String snapshot=json.writeValueAsString(Map.of("version","STAFF_GROUP_V1","setup",p.setup(),"assessments",p.assessments(),"destination",p.destination(),"capacity",p.capacity()));
            if(records.simple(a,loan)){check(records.sameContract(a,loan,snapshot,body),"changed");return;}
            records.contract(a,loan,p,f.getGroupApplication(),f.getGroupRequestHash(),snapshot,body,filename,clock.now());event(a,loan,"GROUP_LOAN_RECORDED");
    }
    @Transactional(readOnly=true) public boolean partyContract(AppUserPrincipal a,UUID party){access.requireActor(a,"LOAN_RECORDING_VIEW");return records.partyContract(a,party);}
    @Transactional(readOnly=true) public boolean simple(AppUserPrincipal a,UUID loan){access.requireActor(a,"LOAN_RECORDING_VIEW");return records.simple(a,loan);}
    @Transactional(readOnly=true) public boolean simpleForFunding(AppUserPrincipal a,UUID loan){access.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");return records.simple(a,loan);}
    @Transactional(readOnly=true) public Optional<Contract> contract(AppUserPrincipal a,UUID loan){
        access.requireActor(a,"LOAN_RECORDING_VIEW");return readContract(a,loan);
    }
    @Transactional(readOnly=true) public Optional<Contract> fundingContract(AppUserPrincipal a,UUID loan){access.requireActor(a,"DISBURSEMENT_QUEUE_VIEW");return readContract(a,loan);}
    private Optional<Contract> readContract(AppUserPrincipal a,UUID loan){return records.contract(a,loan).map(c->{var tree=json.readTree(c.name());var s=json.treeToValue(tree.get("setup"),Setup.class);return new Contract(loan,c.groupId(),s.name(),s.leader(),s.holder()+" · "+s.provider()+" · "+s.identifier(),c.reference(),c.language(),records.borrowers(a,loan));});}
    @Transactional public byte[] document(AppUserPrincipal a,UUID loan){access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");access.requireActor(a,"LOAN_RECORDING_VIEW");var body=records.document(a,loan);event(a,loan,"GROUP_AGREEMENT_EXPORTED");return body;}
    @Transactional public byte[] fundingDocument(AppUserPrincipal a,UUID loan){access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");access.requireActor(a,"DISBURSEMENT_QUEUE_VIEW");var body=records.document(a,loan);event(a,loan,"GROUP_AGREEMENT_EXPORTED");return body;}
    @Transactional public boolean fund(AppUserPrincipal a,UUID loan,UUID posting,UUID journal,LocalDate day){
        access.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");if(!records.simple(a,loan))return false;access.requireActor(a,"GROUP_LENDING_EXECUTE");check(!records.related(a,loan),"independent");check(records.mayFund(a,loan,day),"funding");records.funding(a,loan,posting,journal,clock.now());return true;
    }
    @Transactional(readOnly=true) public void payment(AppUserPrincipal a,UUID loan,String payer){if(records.simple(a,loan)){access.requireActor(a,"GROUP_LENDING_POST");text(payer,160);}}
    @Transactional(readOnly=true) public void correction(AppUserPrincipal a,UUID loan){if(records.simple(a,loan))access.requireActor(a,"GROUP_LENDING_REVERSE");}
    @Transactional public void recordGuarantors(AppUserPrincipal a,UUID loan,RecordForm f,LoanProductSetting product){
        access.requireActor(a,"LOAN_RECORDING_CREATE");var people=f.getApplicationDetails().getGuarantors();int required=product.getGuarantorsRequired()==null?0:product.getGuarantorsRequired();
        check(people!=null&&people.size()>=required&&people.size()<=20,"guarantee");if(people.isEmpty())return;
        var ids=people.stream().map(com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor::getClientId).toList();check(ids.stream().allMatch(Objects::nonNull)&&new HashSet<>(ids).size()==ids.size(),"guarantee");
        ids.stream().sorted().forEach(id->records.lockGuarantor(a,id));var exposures=records.exposures(a,ids);var policy=qualification.groupPolicy(a.getSaccoId(),a.getStationId());var cap=records.totalPayable(loan);
        for(var g:people){money(g.getGuaranteeBudget(),true);text(g.getGuaranteeEvidence(),500);check(g.getSignedOn()!=null&&!g.getSignedOn().isAfter(clock.today())&&!g.getSignedOn().isBefore(f.getApplicationDate()),"guarantee");var e=exposures.get(g.getClientId());
            check(e!=null&&e.missingHistory()==0&&(policy.guarantorActiveLoanAllowed()||e.activeLoans()==0)&&(policy.guarantorDefaultLimit()==null||e.riskLoans()<policy.guarantorDefaultLimit())&&(policy.guaranteeCountLimit()==null||e.guaranteeCount()<policy.guaranteeCountLimit())&&e.reserved().add(cap).compareTo(g.getGuaranteeBudget())<=0,"guarantee");records.guarantor(loan,g,cap);
        }
    }
    @Transactional(readOnly=true) public boolean guaranteesReady(AppUserPrincipal a,UUID loan){access.requireActor(a,"LOAN_RECORDING_VIEW");return records.guaranteesReady(a,loan);}
    private void event(AppUserPrincipal a,UUID id,String action){audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"GROUP",id.toString(),a.getSaccoId(),a.getStationId(),Map.of());}
    private static void money(BigDecimal n,boolean positive){check(n!=null&&n.scale()<=2&&n.precision()-n.scale()<=16&&(positive?n.signum()>0:n.signum()>=0),"budget");}
    private static void validateDraft(RecordForm f){
        check(f.getGroupApplication().getSigners()!=null&&f.getGroupApplication().getSigners().size()<=50&&f.getGroupApplication().getDebts()!=null&&f.getGroupApplication().getDebts().size()<=50,"request");
        for(var debt:f.getGroupApplication().getDebts()){check(debt!=null&&debt.getReference()!=null&&debt.getReference().length()<=200&&debt.getClientIds()!=null&&debt.getClientIds().size()<=50,"request");if(debt.getMonthlyPayment()!=null)money(debt.getMonthlyPayment(),true);}
        check(f.getPurpose()!=null&&f.getPurpose().length()<=500&&f.getNotes()!=null&&f.getNotes().length()<=500,"request");
        if(f.getPrincipal()!=null)money(f.getPrincipal(),true);
        var details=f.getApplicationDetails();if(details!=null){check(details.getFields()!=null&&details.getFields().size()<=100&&details.getGuarantors()!=null&&details.getGuarantors().size()<=20&&details.getCollateral()!=null&&details.getCollateral().size()<=20,"request");check(details.getFields().values().stream().allMatch(v->v!=null&&v.length()<=1000),"request");}
        var g=f.getGroupApplication();check(g.getReference()!=null&&g.getReference().length()<=500,"request");for(var s:g.getSigners())check(s!=null&&s.getIncomeSource()!=null&&s.getIncomeSource().length()<=200,"request");
    }
    private static void text(String s,int max){check(s!=null&&!s.isBlank()&&s.length()<=max&&s.codePoints().noneMatch(Character::isISOControl),"request");}
    private static void check(boolean ok,String error){if(!ok)throw new IllegalArgumentException("simpleGroup.error."+error);}
}
