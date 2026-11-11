package com.sacco.mvp.clients.onboarding.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.repository.OnboardingRepository;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OnboardingService {
    public static final Set<String> IDENTITY_TYPES=Set.of("NATIONAL_ID","PASSPORT","VOTER_ID","DRIVING_LICENCE","OTHER");
    public static final Set<String> DOCUMENT_CATEGORIES=Set.of("IDENTITY","ADDRESS","ACTIVITY","CONSENT","PROFILE_PHOTO","SPOUSE_PHOTO");
    private static final List<String> SELF_EVIDENCE=List.of("IDENTITY","ADDRESS","ACTIVITY");
    public static final Set<String> PHOTO_CATEGORIES=Set.of("PROFILE_PHOTO","SPOUSE_PHOTO");
    public static final String[] PERSONAL_FIELDS={"identityType","issuingCountry","identityNumber","birthDate","identityExpiresOn","identityNoExpiry","address","activity","incomeActivities","activityAddress","sourceOfFunds","gender","maritalStatus","dependants","familiarName","groupId","postalAddress","city","ward","villageStreet","nearbyPlace","hasBusiness","businessName","businessTin","businessType","businessLocation","businessWard","businessCity","businessLicence","otherIncomeSources","dailySales","dailyExpenditure","spouseName","spouseRelationship","spousePhone","spouseWork","spouseWorkLocation","bankName","bankAccountName","bankAccountNumber","employeeNumber"};
    private final OnboardingRepository records;
    private final GeneralLedgerService access;
    private final MemberRegistrationService registration;
    private final StoredUploadStorageService uploads;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;

    public void authorize(AppUserPrincipal a,String action) {
        access.requireActor(a,"CLIENT_ONBOARDING_VIEW");
        if(!action.equals("VIEW"))access.requireActor(a,"CLIENT_ONBOARDING_"+action);
    }
    public LocalDate today() {return clock.today();}
    @Transactional(readOnly=true) public ContactCheck checkContact(AppUserPrincipal a,UUID client,String field,String value) {
        authorize(a,client==null?"CREATE":"UPDATE");
        check(Set.of("phone","email").contains(field),"query");
        if(value==null || value.length()>(field.equals("phone")?16:255))return new ContactCheck(false,false);
        String normalized=value.strip().toLowerCase(Locale.ROOT);
        if(field.equals("phone"))normalized=TanzaniaPhoneNumber.normalizeOptional(value);
        if(normalized==null || (field.equals("phone") && normalized.isBlank()) || (field.equals("email") && !normalized.isBlank() && !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))return new ContactCheck(false,false);
        return new ContactCheck(true,registration.contactRegistered(a,client,field,normalized));
    }
    @Transactional(readOnly=true)
    public Page<Client> clients(AppUserPrincipal a,String search,String state,int page,String sort) {
        authorize(a,"VIEW");check(search!=null&&search.length()<=100,"query");page(page);
        check(Set.of("","NOT_STARTED","DRAFT","RECORDED","SUBMITTED","VERIFY","RETURN","REJECT","REVOKE","EXPIRED","STALE").contains(state),"query");
        var columns=Map.of("number","member_no","name","full_name","state","state","due","review_due");
        String value=sort==null||sort.isBlank()?"number_asc":sort;int split=value.lastIndexOf('_');check(split>0,"query");
        String column=columns.get(value.substring(0,split)),direction=value.substring(split+1);check(column!=null&&Set.of("asc","desc").contains(direction),"query");
        var rows=records.clients(a,search,state,page,column+" "+direction+" nulls last",today());
        return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
    @Transactional(readOnly=true) public Client client(AppUserPrincipal a,UUID client) {authorize(a,"VIEW");return records.client(a,client,today());}
    @Transactional(readOnly=true) public Optional<Revision> latest(AppUserPrincipal a,UUID client) {client(a,client);return records.latest(a,client);}
    @Transactional(readOnly=true) public Revision version(AppUserPrincipal a,UUID client,int sequence) {client(a,client);return records.version(a,client,sequence);}
    @Transactional(readOnly=true) public List<Document> versionDocuments(AppUserPrincipal a,UUID client,int sequence) {return records.attachments(version(a,client,sequence).id());}
    @Transactional(readOnly=true) public List<Decision> versionDecisions(AppUserPrincipal a,UUID client,int sequence) {return records.decisions(version(a,client,sequence).id());}
    @Transactional(readOnly=true) public List<Document> documents(AppUserPrincipal a,UUID client) {var c=client(a,client);return c.revisionId()==null?List.of():records.attachments(c.revisionId());}
    @Transactional(readOnly=true) public List<Decision> decisions(AppUserPrincipal a,UUID client) {var c=client(a,client);return c.revisionId()==null?List.of():records.decisions(c.revisionId());}
    @Transactional(readOnly=true) public Page<History> history(AppUserPrincipal a,UUID client,int page) {client(a,client);page(page);var rows=records.history(a,client,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public boolean duplicate(AppUserPrincipal a,UUID client) {var r=latest(a,client);return r.isPresent()&&!identity(r.get().profile()).isBlank()&&records.duplicate(a.getSaccoId(),identity(r.get().profile()),client);}

    public UUID create(AppUserPrincipal a,CreateForm f) {
        authorize(a,"CREATE");check(f.getDocuments()!=null&&f.getDocuments().size()<=DocumentForm.MAX_FILES,"uploadLimit");
        var documents=new ArrayList<PreparedCreationDocument>();long bytes=0;
        for(var document:f.getDocuments()) {
            check(document!=null,"file");if(document.getFile()==null||document.getFile().isEmpty())continue;
            check(DOCUMENT_CATEGORIES.contains(document.getCategory()),"category");
            check(!PHOTO_CATEGORIES.contains(document.getCategory())||document.getExpiresOn()==null,"photoExpiry");
            check(document.getExpiresOn()==null||!document.getExpiresOn().isBefore(today()),"documentExpiry");
            var content=prepare(document.getFile());bytes+=content.bytes().length;check(bytes<=DocumentForm.MAX_TOTAL_BYTES,"uploadLimit");
            var metadata=new DocumentForm();metadata.setRequestKey(UUID.nameUUIDFromBytes((f.getRequestKey()+"/document/"+documents.size()).getBytes(StandardCharsets.UTF_8)));metadata.setCategory(document.getCategory());metadata.setExpiresOn(document.getExpiresOn());
            documents.add(new PreparedCreationDocument(metadata,content,hash(content.bytes())));
        }
        var tx=new TransactionTemplate(transactions);tx.setTimeout(30);
        return tx.execute(status->create(a,f,documents));
    }
    private record PreparedCreationDocument(DocumentForm metadata,Download content,String checksum) {}
    private UUID create(AppUserPrincipal a,CreateForm f,List<PreparedCreationDocument> documents) {
        authorize(a,"CREATE");check(f.getProfile()!=null,"profile");text(f.getName(),255,"name",true);text(f.getEmail(),255,"email",false);
        check(f.getName().strip().split("\\s+").length>=2,"name");check(f.getEmail().isBlank()||f.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"email");
        boolean structured=!f.getProfile().getFirstName().isBlank()||!f.getProfile().getLastName().isBlank();if(structured)names(f.getProfile());
        try {String phone=TanzaniaPhoneNumber.normalizeRequired(f.getPhone());f.setPhone(phone);if(structured)f.getProfile().setPhone(phone);}catch(IllegalStateException e){throw invalid("phone");}
        String hash=documents.isEmpty()?hash(f):hash(List.of(f,documents.stream().map(d->List.of(d.metadata().getCategory(),Objects.toString(d.metadata().getExpiresOn(),""),d.content().name(),d.checksum())).toList()));var old=retry(a,"client_onboarding_creation",f.getRequestKey(),hash);if(old.isPresent())return records.created(old.get());
        var form=new MemberRegistrationForm();form.setEmail(f.getEmail());form.setPhone(f.getPhone());form.setEmployeeNumber(f.getProfile().getEmployeeNumber());form.setSaccoId(a.getSaccoId());form.setStationId(a.getStationId());
        UUID client;
        try {client=registration.registerAssisted(form,f.getName(),a.getSaccoId(),a.getStationId()).getId();}catch(IllegalStateException e){throw invalid("registration");}
        records.creation(a,client,UUID.randomUUID(),f.getRequestKey(),hash,clock.now());event(a,client,"CLIENT_ASSISTED_REGISTRATION");
        if(structured||!documents.isEmpty()){var profile=new ProfileForm();profile.setRequestKey(f.getRequestKey());profile.setAction("RECORDED");profile.setProfile(json.readValue(json.writeValueAsString(f.getProfile()),Profile.class));saveRevision(a,client,profile,hash(List.of(client,profile)));}
        if(!documents.isEmpty()) {
            var c=records.client(a,client,today());
            for(var document:documents) {
                UUID id=UUID.randomUUID();var content=document.content();var metadata=document.metadata();metadata.setRevisionId(c.revisionId());
                var upload=uploads.store(null,"CLIENT_KYC",id.toString(),metadata.getCategory(),content.name(),content.contentType(),content.bytes());
                records.document(a,client,c.revisionId(),id,upload.getId(),metadata,hash(List.of(client,metadata,document.checksum())),clock.now());event(a,client,"CLIENT_KYC_DOCUMENT");
            }
        }
        return client;
    }
    @Transactional(readOnly=true) public ProfileForm profileForm(AppUserPrincipal a,UUID id) {
        authorize(a,"UPDATE");var c=records.client(a,id,today());var f=new ProfileForm();f.setExpectedSequence(c.sequence());var latest=records.latest(a,id);latest.ifPresent(r->f.setProfile(r.profile()));identityFields(f.getProfile(),c);f.getProfile().setEmployeeNumber(registration.employeeNumber(a,id));return f;
    }

    @Transactional(readOnly=true) public List<GroupChoice> groups(AppUserPrincipal a,UUID client,String search,int page) {
        if(client==null)authorize(a,"CREATE");else client(a,client);
        text(search,100,"query",false);page(page);return records.groups(a,search,page);
    }
    @Transactional(timeout=20)
    public UUID save(AppUserPrincipal a,UUID client,ProfileForm f) {
        authorize(a,"UPDATE");records.client(a,client,today());
        check("DRAFT".equals(f.getAction())||"RECORDED".equals(f.getAction()),"action");
        String hash=hash(List.of(client,f));var old=retry(a,"client_onboarding_revision",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        var command=json.readValue(json.writeValueAsString(f),ProfileForm.class);
        check(command.getProfile()!=null,"profile");var p=command.getProfile();standardNotice(p,records.notice());p.setSelfService(false);
        records.latest(a,client).map(r->records.documents(r.id())).orElse(List.of()).stream()
            .filter(d->"CONSENT".equals(d.category())).findFirst().ifPresent(d->{
                p.setConsentReference("Client consent document / "+d.id());
                p.setConsentOn(d.recordedAt().atZoneSameInstant(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate());
            });
        return saveRevision(a,client,command,hash);
    }
    private UUID saveRevision(AppUserPrincipal a,UUID client,ProfileForm f,String hash) {
        records.client(a,client,today());var old=retry(a,"client_onboarding_revision",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        records.lock(client);var c=records.client(a,client,today());

        check(c.sequence()==f.getExpectedSequence(),"changed");
        check(f.getProfile()!=null,"profile");var p=json.readValue(json.writeValueAsString(f.getProfile()),Profile.class);
        if(!p.getFirstName().isBlank()||!p.getLastName().isBlank()) {
            names(p);String fullName=java.util.stream.Stream.of(p.getFirstName(),p.getMiddleName(),p.getLastName()).filter(v->!v.isBlank()).map(String::strip).collect(java.util.stream.Collectors.joining(" "));
            try{registration.correctProvisionalContact(a,client,fullName,p.getPhone(),p.getEmail());}catch(IllegalStateException e){throw invalid("registration");}
            c=records.client(a,client,today());
        }
        registration.employeeNumber(a,client,p.getEmployeeNumber());
        identityFields(p,c);p.setGroupName(p.getGroupId()==null?"":records.group(a,p.getGroupId()).orElseThrow(()->invalid("groupId")).label());
        p.setFullName(c.name());p.setPhone(c.phone());p.setEmail(c.email());p.setClientNumber(c.number());validate(p,false);
        if("RECORDED".equals(f.getAction()))check(c.phone()!=null&&c.phone().matches("255[0-9]{9}"),"phone");
        UUID id=UUID.randomUUID();records.revision(a,client,id,c.sequence()+1,f.getAction(),p,identity(p),f.getRequestKey(),hash,clock.now());records.copyDocuments(a,client,c.revisionId(),id);
        event(a,client,"CLIENT_KYC_"+f.getAction());return id;
    }
    public UUID attach(AppUserPrincipal a,UUID client,DocumentForm f,MultipartFile file) {
        authorize(a,"UPDATE");records.client(a,client,today());return attachStaff(a,client,f,file);
    }
    private UUID attachStaff(AppUserPrincipal a,UUID client,DocumentForm f,MultipartFile file) {
        records.client(a,client,today());check(f.getRevisionId()!=null&&f.getRequestKey()!=null,"request");check(DOCUMENT_CATEGORIES.contains(f.getCategory()),"category");
        boolean photo=PHOTO_CATEGORIES.contains(f.getCategory());check(!photo||f.getExpiresOn()==null,"photoExpiry");
        check(f.getExpiresOn()==null||!f.getExpiresOn().isBefore(today()),"documentExpiry");
        var content=prepare(file);String hash=hash(List.of(client,f,hash(content.bytes())));
        var tx=new TransactionTemplate(transactions);tx.setTimeout(20);
        return tx.execute(status->{
            authorize(a,"UPDATE");records.client(a,client,today());var old=retry(a,"client_onboarding_document",f.getRequestKey(),hash);if(old.isPresent())return old.get();
            var before=records.uploadUsage(a,client);check(before.files()<DocumentForm.MAX_FILES&&before.bytes()+content.bytes().length<=DocumentForm.MAX_TOTAL_BYTES,"uploadLimit");
            UUID id=UUID.randomUUID();var upload=uploads.store(null,"CLIENT_KYC",id.toString(),f.getCategory(),content.name(),content.contentType(),content.bytes());
            records.lock(client);var c=records.client(a,client,today());check((c.state().equals("DRAFT")||c.state().equals("RECORDED"))&&f.getRevisionId().equals(c.revisionId()),"changed");
            var usage=records.uploadUsage(a,client);check(usage.files()<DocumentForm.MAX_FILES&&usage.bytes()+content.bytes().length<=DocumentForm.MAX_TOTAL_BYTES,"uploadLimit");
            records.document(a,client,c.revisionId(),id,upload.getId(),f,hash,clock.now());event(a,client,"CLIENT_KYC_DOCUMENT");return id;
        });
    }
    @Transactional(timeout=20)
    public UUID review(AppUserPrincipal a,UUID client,ReviewForm f) {
        authorize(a,"REVIEW");records.client(a,client,today());
        // A combined confirmation is equivalent to all five checks on an older form.
        if ("VERIFY".equals(f.getKind()) && f.isChecksConfirmed()) f.confirmChecks();
        check(f.getRequestKey()!=null,"request");
        records.lock("client_onboarding_decision/"+a.getSaccoId()+"/"+a.getStationId()+"/"+f.getRequestKey());
        var old=records.retryReview(a,client,f);if(old.isPresent())return old.get();
        // Retired inputs cannot imply that screening was completed.
        f.setScreening("UNAVAILABLE");f.setScreeningReference("");f.setPolicyReference("");f.setReviewDue(null);
        String hash=hash(List.of(client,f));
        records.lock(client);var c=records.client(a,client,today());check(f.getRevisionId()!=null&&f.getRevisionId().equals(c.revisionId()),"changed");
        check(Set.of("VERIFY","RETURN","REJECT","REVOKE").contains(f.getKind()),"action");
        check(f.getKind().equals("REVOKE")?Set.of("VERIFY","EXPIRED","STALE").contains(c.state()):Set.of("SUBMITTED","RECORDED").contains(c.state()),"state");
        text(f.getReason(),1000,"reason",true);
        text(f.getClientMessage(),1000,"clientMessage",Set.of("RETURN","REJECT","REVOKE").contains(f.getKind()));
        var r=records.latest(a,client).orElseThrow();
        if(f.getKind().equals("VERIFY")) {
            check(!client.equals(a.getMemberId())&&!r.maker().equals(a.getMemberId())&&!records.contributed(r.id(),a.getMemberId()),"independent");
            validate(r.profile(),true);checkDocuments(r.profile(),records.documents(r.id()));
            check(Objects.equals(c.name(),r.profile().getFullName())&&Objects.equals(c.phone(),r.profile().getPhone())&&Objects.equals(c.email(),r.profile().getEmail()),"contactChanged");
            check(f.isIdentityChecked()&&f.isAddressChecked()&&f.isActivityChecked()&&f.isContactChecked()&&f.isDuplicatesChecked(),"checks");
            check(f.getCheckedOn()!=null&&!f.getCheckedOn().isAfter(today())&&!f.getCheckedOn().isBefore(r.recordedAt().toLocalDate()),"checkedOn");
            String identity=identity(r.profile());records.lock("CLIENT_IDENTITY/"+a.getSaccoId()+"/"+identity);check(!records.duplicate(a.getSaccoId(),identity,client),"duplicate");
        }
        UUID id=UUID.randomUUID();records.decision(a,client,id,f,hash,clock.now());
        event(a,client,"CLIENT_KYC_"+f.getKind());return id;
    }
    @Transactional(timeout=15)
    public Download download(AppUserPrincipal a,UUID client,UUID document) {
        authorize(a,"DOCUMENTS");records.client(a,client,today());UUID uploadId=records.upload(a,client,document);
        var upload=uploads.load(uploadId,"CLIENT_KYC",document.toString());
        check(upload.getContent().length<=5*1024*1024&&uploads.verify(upload,upload.getContent()),"file");
        event(a,client,"CLIENT_KYC_DOCUMENT_READ");return new Download(upload.getContent(),upload.getContentType(),upload.getOriginalName());
    }
    @Transactional(timeout=15) public Download photo(AppUserPrincipal a,UUID client,UUID document) {
        authorize(a,"DOCUMENTS");records.client(a,client,today());check(records.photo(a,client,document),"photoFile");return download(a,client,document);
    }

    @Transactional(readOnly=true)
    public Notice staffNotice(AppUserPrincipal a) {authorize(a,"UPDATE");return records.notice();}

    @Transactional(readOnly=true) public DocumentForm documentForm(AppUserPrincipal a,UUID client) {authorize(a,"UPDATE");var c=records.client(a,client,today());check(Set.of("DRAFT","RECORDED").contains(c.state()),"state");return uploadForm(a,c);}
    private DocumentForm uploadForm(AppUserPrincipal a,Client c) {var usage=records.uploadUsage(a,c.id());var f=new DocumentForm();f.setRevisionId(c.revisionId());f.setRemainingFiles((int)Math.max(0,DocumentForm.MAX_FILES-usage.files()));f.setRemainingBytes(Math.max(0,DocumentForm.MAX_TOTAL_BYTES-usage.bytes()));return f;}

    private void standardNotice(Profile p,Notice n) {
        p.setNoticeId(n.id());p.setNoticeVersion(n.version());p.setNoticeEnglish(n.english());p.setNoticeKiswahili(n.kiswahili());p.setLawfulBasis("CONSENT");
        p.setRetentionReference("");p.setRetentionReviewOn(null);p.setConsentReference("");p.setConsentOn(null);
        p.setNoticeAcknowledged(false);p.setDeclarationAccepted(false);p.setAcknowledgedAt(null);
    }

    private void identityFields(ClientProfile p,Client c) {
        String[] parts=c.name().strip().split("\\s+");p.setFirstName(parts[0]);p.setLastName(parts.length>1?parts[parts.length-1]:"");
        p.setMiddleName(parts.length>2?String.join(" ",Arrays.copyOfRange(parts,1,parts.length-1)):"");p.setPhone(Objects.toString(c.phone(),""));p.setEmail(Objects.toString(c.email(),""));
    }
    private void names(ClientProfile p) {
        text(p.getFirstName(),80,"firstName",true);text(p.getLastName(),80,"lastName",true);text(p.getMiddleName(),80,"middleName",false);
        check(p.getFirstName().strip().matches("\\S+")&&p.getLastName().strip().matches("\\S+")&&(p.getMiddleName().isBlank()||p.getMiddleName().strip().matches("\\S+")),"nameParts");
    }
    private void checkDocuments(Profile p,List<Document> documents) {
        var requirements=evidenceRequirements(documents,today());
        check(requirements.stream().noneMatch(r->"missing".equals(r.status()))
            && (p.isSelfService()||documents.stream().anyMatch(d->"CONSENT".equals(d.category()))),"documents");
        check(requirements.stream().noneMatch(r->"expired".equals(r.status())),"documentExpiry");
    }
    private List<EvidenceRequirement> evidenceRequirements(List<Document> documents,LocalDate date) {
        var statusByCategory=new LinkedHashMap<String,String>();
        SELF_EVIDENCE.forEach(category->statusByCategory.put(category,"missing"));
        for (Document document:documents) {
            if (document.expiresOn()!=null && document.expiresOn().isBefore(date)) {
                statusByCategory.put(document.category(),"expired");
            } else if (statusByCategory.containsKey(document.category())) {
                statusByCategory.put(document.category(),"added");
            }
        }
        return statusByCategory.entrySet().stream().map(entry->new EvidenceRequirement(entry.getKey(),entry.getValue())).toList();
    }
    private void validate(Profile p,boolean complete) {
        if(Boolean.FALSE.equals(p.getHasBusiness())) {
            p.setBusinessName("");p.setBusinessType("");p.setBusinessTin("");p.setBusinessLicence("");p.setBusinessLocation("");p.setBusinessWard("");p.setBusinessCity("");p.setDailySales(null);p.setDailyExpenditure(null);
        }
        if(Set.of("SINGLE","DIVORCED","WIDOWED").contains(p.getMaritalStatus())) {
            p.setSpouseName("");p.setSpouseRelationship("");p.setSpousePhone("");p.setSpouseWork("");p.setSpouseWorkLocation("");
        }
        text(p.getGender(),20,"gender",false);check(p.getGender().isBlank()||Set.of("MALE","FEMALE","OTHER","NOT_DISCLOSED").contains(p.getGender()),"gender");
        text(p.getMaritalStatus(),20,"maritalStatus",false);check(p.getMaritalStatus().isBlank()||Set.of("SINGLE","MARRIED","DIVORCED","SEPARATED","WIDOWED","NOT_DISCLOSED").contains(p.getMaritalStatus()),"maritalStatus");
        check(p.getDependants()==null||p.getDependants()>=0&&p.getDependants()<=100,"dependants");
        var fields=new LinkedHashMap<String,String>();fields.put("familiarName",p.getFamiliarName());fields.put("postalAddress",p.getPostalAddress());fields.put("city",p.getCity());fields.put("ward",p.getWard());fields.put("villageStreet",p.getVillageStreet());fields.put("nearbyPlace",p.getNearbyPlace());fields.put("businessName",p.getBusinessName());fields.put("businessType",p.getBusinessType());fields.put("businessLocation",p.getBusinessLocation());fields.put("businessWard",p.getBusinessWard());fields.put("businessCity",p.getBusinessCity());fields.put("spouseName",p.getSpouseName());fields.put("spouseRelationship",p.getSpouseRelationship());fields.put("spouseWork",p.getSpouseWork());fields.put("spouseWorkLocation",p.getSpouseWorkLocation());fields.put("bankName",p.getBankName());fields.put("bankAccountName",p.getBankAccountName());
        fields.forEach((field,value)->text(value,255,field,false));text(p.getBusinessTin(),50,"businessTin",false);text(p.getBusinessLicence(),100,"businessLicence",false);text(p.getOtherIncomeSources(),1000,"otherIncomeSources",false);
        text(p.getBankAccountNumber(),80,"bankAccountNumber",false);check(p.getBankAccountNumber().isBlank()||p.getBankAccountNumber().matches("[A-Za-z0-9 /._-]+"),"bankAccountNumber");
        money(p.getDailySales(),"dailySales");money(p.getDailyExpenditure(),"dailyExpenditure");text(p.getSpousePhone(),32,"spousePhone",false);
        if(!p.getSpousePhone().isBlank())try{p.setSpousePhone(TanzaniaPhoneNumber.normalizeRequired(p.getSpousePhone()));}catch(IllegalStateException e){throw invalid("spousePhone");}
        if(complete) {
            text(p.getFullName(),255,"name",true);
            check(p.getPhone()!=null&&p.getPhone().matches("255[0-9]{9}"),"phone");
        }
        text(p.getIdentityType(),30,"identityType",complete);check(p.getIdentityType().isEmpty()||IDENTITY_TYPES.contains(p.getIdentityType()),"identityType");
        text(p.getIssuingCountry(),2,"issuingCountry",complete);check(p.getIssuingCountry().isEmpty()||Arrays.asList(Locale.getISOCountries()).contains(p.getIssuingCountry()),"issuingCountry");
        text(p.getIdentityNumber(),64,"identityNumber",complete);check(p.getIdentityNumber().isEmpty()||p.getIdentityNumber().matches("[A-Za-z0-9 /-]{4,64}"),"identityNumber");
        check(p.getBirthDate()==null?!complete:!p.getBirthDate().isAfter(today())&&p.getBirthDate().getYear()>=1901,"birthDate");
        check(!(p.isIdentityNoExpiry()&&p.getIdentityExpiresOn()!=null),"identityExpiry");
        if(complete)check(p.isIdentityNoExpiry()||(p.getIdentityExpiresOn()!=null&&!p.getIdentityExpiresOn().isBefore(today())),"identityExpiry");
        text(p.getAddress(),1000,"address",complete);text(p.getActivity(),1000,"activity",complete);text(p.getActivityAddress(),1000,"activityAddress",complete);text(p.getSourceOfFunds(),1000,"sourceOfFunds",complete);
        text(p.getNoticeVersion(),100,"noticeVersion",complete);text(p.getConsentReference(),1000,"consentReference",complete);text(p.getLawfulBasis(),30,"lawfulBasis",complete);
        check(p.getLawfulBasis().isEmpty()||Set.of("CONSENT","CONTRACT","LEGAL_OBLIGATION").contains(p.getLawfulBasis()),"lawfulBasis");
        check(p.getConsentOn()==null?!complete:!p.getConsentOn().isAfter(today())&&p.getConsentOn().getYear()>=1901,"consentOn");
        boolean standard=Notice.STANDARD_ID.equals(p.getNoticeId());
        text(p.getRetentionReference(),500,"retentionReference",complete&&!standard);text(p.getLegalHoldReference(),500,"legalHoldReference",false);
        if(complete&&!standard)check(p.getRetentionReviewOn()!=null&&!p.getRetentionReviewOn().isBefore(today()),"retentionReviewOn");
        if(complete&&p.isSelfService())check(p.isNoticeAcknowledged()&&p.isDeclarationAccepted()&&p.getAcknowledgedAt()!=null&&p.getNoticeId()!=null&&!p.getNoticeEnglish().isBlank()&&!p.getNoticeKiswahili().isBlank(),"acknowledgement");
    }
    private void money(java.math.BigDecimal value,String field) {check(value==null||value.signum()>=0&&value.scale()<=2&&value.compareTo(new java.math.BigDecimal("9999999999999999.99"))<=0,field);}
    private Download prepare(MultipartFile f) {
        check(f!=null&&!f.isEmpty()&&f.getSize()<=DocumentForm.MAX_FILE_BYTES,"file");
        try {
            byte[] bytes;try(var input=f.getInputStream()){bytes=input.readNBytes(DocumentForm.MAX_FILE_BYTES+1);}check(bytes.length>0&&bytes.length<=DocumentForm.MAX_FILE_BYTES,"file");
            String original=Objects.toString(f.getOriginalFilename(),"").replace('\\','/');original=original.substring(original.lastIndexOf('/')+1);text(original,255,"file",true);String name=original.toLowerCase(Locale.ROOT);
            boolean png=name.endsWith(".png"),jpg=name.endsWith(".jpg")||name.endsWith(".jpeg");check(png||jpg,"file");
            try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(in);check(readers.hasNext(),"file");var reader=readers.next();
                try {reader.setInput(in);String format=reader.getFormatName();check((png&&format.equalsIgnoreCase("png"))||(jpg&&format.equalsIgnoreCase("JPEG")),"file");check((long)reader.getWidth(0)*reader.getHeight(0)<=9_000_000,"file");check(reader.read(0)!=null,"file");}finally{reader.dispose();}
            }
            return new Download(bytes,png?"image/png":"image/jpeg",original);
        } catch(IOException|RuntimeException e) {if(e instanceof IllegalArgumentException i&&Objects.toString(i.getMessage(),"").startsWith("kyc.error."))throw i;throw invalid("file");}
    }
    private Optional<UUID> retry(AppUserPrincipal a,String table,UUID key,String hash) {check(key!=null,"request");records.lock(table+"/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);return records.retry(a,table,key,hash);}
    private void event(AppUserPrincipal a,UUID client,String action) {audit.logEvent("CLIENT_KYC",client,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"CLIENT",client.toString(),a.getSaccoId(),a.getStationId(),Map.of());}
    private String identity(Profile p) {if(p.getIdentityType().isBlank()||p.getIssuingCountry().isBlank()||p.getIdentityNumber().isBlank())return "";return digest((p.getIdentityType()+"/"+p.getIssuingCountry()+"/"+p.getIdentityNumber().replaceAll("[ /-]","").toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));}
    private String hash(Object value) {return digest(value instanceof byte[] b?b:json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));}
    private static String digest(byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void page(int n) {check(n>=0&&n<=10000,"query");}
    private static void text(String v,int max,String field,boolean required) {check(v!=null&&v.length()<=max&&(!required||!v.isBlank())&&v.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),field);}
    private static void check(boolean condition,String key) {if(!condition)throw invalid(key);}
    private static IllegalArgumentException invalid(String key) {return new IllegalArgumentException("kyc.error."+key);}
}
