package com.sacco.mvp.clients.applications.service;

import com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.*;
import com.sacco.mvp.clients.applications.repository.ClientApplicationRepository;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClientApplicationService {
    public static final List<String> DOCUMENT_TYPES=List.of("SIGNED_APPLICATION","BUSINESS_PROOF","INCOME_PROOF","EXISTING_DEBT","COLLATERAL_PHOTO","COLLATERAL_OWNERSHIP","GUARANTOR_PHOTO","OTHER");
    private final ClientApplicationRepository records;
    private final OnboardingService onboarding;
    private final FormSchemaService schemas;
    private final LoanWorkflowService loans;
    private final LoanProductRequiredAttachmentService requirements;
    private final LoanAttachmentService attachments;
    private final FinancialDetailsService financials;
    private final ApplicationClock clock;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final AuditService audit;
    public Optional<Choice> officerChoice(AppUserPrincipal a,UUID id){authorize(a,false);if(!a.isStaffSession())throw new AccessDeniedException("Staff required");return id==null?Optional.empty():records.officer(a,id);}
    @Transactional(readOnly=true) public Map<UUID,String> guarantorLabels(AppUserPrincipal a,Form f){client(a,f.getClientId(),false);var labels=new LinkedHashMap<UUID,String>();if(f.getGuarantors()!=null){var ids=f.getGuarantors().stream().filter(Objects::nonNull).map(Guarantor::getClientId).filter(Objects::nonNull).limit(20).collect(java.util.stream.Collectors.toSet());records.guarantors(a,ids).forEach(v->labels.put(v.id(),v.label()));}return labels;}
    public Choice clientChoice(AppUserPrincipal a,UUID id){client(a,id,false);if(a.isStaffSession()){var c=onboarding.client(a,id);return new Choice(id,c.number()+" - "+c.name());}return new Choice(id,onboarding.ownWorkspace(a,0,null).client().number());}
    public Form recorded(AppUserPrincipal a,UUID id){details(a,id);return records.latestForm(a,id,a.isStaffSession()?null:a.getMemberId()).map(s->json.readValue(s,Form.class)).orElseGet(Form::new);}
    public UUID sendToReview(AppUserPrincipal a,UUID id,UUID request,int version,boolean accepted){
        authorize(a,true);check(request!=null&&accepted,"declaration");var tx=new TransactionTemplate(transactions);tx.setTimeout(30);
        return tx.execute(status->{records.lock("CLIENT_APPLICATION_REVIEW/"+id);var d=details(a,id);client(a,d.clientId(),true);records.lock("CLIENT_KYC/"+d.clientId());verified(a,d.clientId());String hash=hash(List.of(id,version,accepted));var retry=records.retry(a,request);if(retry.isPresent()){check(hash.equals(retry.get().payloadHash()),"retry");return id;}check(d.version()==version,"changed");check(d.status().equals("ALL_GUARANTORS_APPROVED"),"state");check(documents(a,id).stream().anyMatch(v->"SIGNED_APPLICATION".equals(v.get("documentPurpose"))),"signedApplication");loans.submitToManager(id,d.clientId());var f=recorded(a,id);records.command(a,id,d.clientId(),version+1,request,hash,json.writeValueAsString(f),clock.now());audit.logEvent("LOAN_APPLICATION",id,"CLIENT_APPLICATION_SENT_TO_REVIEW",a.getMemberId(),AuditEventStatus.SUCCESS,"Client application sent for review","LOAN_APPLICATION",id.toString(),a.getSaccoId(),a.getStationId(),Map.of("client",d.clientId(),"assisted",a.isStaffSession()));return id;});
    }
    public void authorize(AppUserPrincipal a,boolean write){if(a==null)throw new AccessDeniedException("Client unavailable");if(a.isStaffSession())onboarding.authorize(a,write?"UPDATE":"VIEW");else if(!a.isProvisionalPortal())throw new AccessDeniedException("Client portal required");}
    private void client(AppUserPrincipal a,UUID id,boolean write){authorize(a,write);if(id==null)throw new IllegalArgumentException("applications.error.client");if(a.isStaffSession()){if(onboarding.client(a,id).portalEnabled())throw new AccessDeniedException("Provisional client required");}else if(!id.equals(a.getMemberId()))throw new AccessDeniedException("Client unavailable");}
    private void verified(AppUserPrincipal a,UUID id){String state=a.isStaffSession()?onboarding.client(a,id).state():onboarding.ownStatus(a).state();check(state.equals("VERIFY"),"verification");}
    @Transactional(readOnly=true) public Page list(AppUserPrincipal a,String search,int page){authorize(a,false);query(search,page);var rows=records.list(a,a.isStaffSession()?null:a.getMemberId(),search,page);return new Page(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public List<Choice> choices(AppUserPrincipal a,String kind,UUID client,String search,int page){authorize(a,false);query(search,page);return switch(kind){case "clients"->{if(!a.isStaffSession())throw new AccessDeniedException("Staff required");yield records.clients(a,search,page);}case "products"->records.products(a,search,page);case "officers"->{if(!a.isStaffSession())throw new AccessDeniedException("Staff required");yield records.officers(a,search,page);}case "guarantors"->{client(a,client,false);yield loans.searchGuarantorCandidates(a.getSaccoId(),a.getStationId(),client,search,"name",(com.sacco.mvp.domain.LoanProductSetting)null,page,25).stream().filter(LoanWorkflowService.GuarantorCandidate::eligible).map(g->new Choice(g.id(),g.memberNo()+" - "+g.fullName())).toList();}default->throw new IllegalArgumentException("applications.error.query");};}
    @Transactional(readOnly=true) public Form form(AppUserPrincipal a,UUID client,UUID application){authorize(a,true);var f=new Form();if(application!=null){var d=details(a,application);f=records.latestForm(a,application,a.isStaffSession()?null:a.getMemberId()).map(s->json.readValue(s,Form.class)).orElseThrow(()->new IllegalArgumentException("applications.error.state"));check(d.status().equals("DRAFT"),"state");f.setApplicationId(application);f.setClientId(d.clientId());f.setExpectedVersion(d.version());f.setRequestKey(UUID.randomUUID());f.setCommand("DRAFT");f.setDocumentNames(new ArrayList<>());f.setDocumentTypes(new ArrayList<>());f.setRequiredAttachmentIds(new ArrayList<>());}else{f.setClientId(a.isStaffSession()?client:a.getMemberId());f.setApplicationDate(clock.today());}if(f.getClientId()!=null)client(a,f.getClientId(),true);return f;}
    @Transactional(readOnly=true) public Details details(AppUserPrincipal a,UUID application){authorize(a,false);return records.details(a,application,a.isStaffSession()?null:a.getMemberId());}
    @Transactional(readOnly=true) public ProductForm product(AppUserPrincipal a,UUID id){authorize(a,false);var p=schemas.getSchema(a.getSaccoId(),id,null);return new ProductForm(new Product(p.getId(),p.getDisplayName(),p.getResolvedTenureUnit(),p.getResolvedMinimumTenure(),p.getResolvedMaximumTenure(),p.getGuarantorsRequired(),p.isCollateralRequired(),"PRODUCT",p.getResolvedRepaymentFrequency().name()),schemas.toFormModel(p.getLoanType(),p.getFormSchema()).getFields(),requirements.activeForProduct(id).stream().limit(25).map(r->new Requirement(r.getId(),r.getAttachmentName(),r.getMaxSizeMb())).toList());}
    @Transactional(readOnly=true) public List<Map<String,Object>> documents(AppUserPrincipal a,UUID application){return attachments.parse(details(a,application).attachments());}
    public com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.Download download(AppUserPrincipal a,UUID application,String attachment){var detail=details(a,application);var loaded=attachments.load(application,attachment,detail.attachments());return new com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.Download(loaded.getContent(),loaded.getContentType(),loaded.getOriginalName());}
    @Transactional(timeout = 30)
    public void removeDraftDocument(AppUserPrincipal actor, UUID id, String file, UUID requestKey, int version) {
        var detail = details(actor, id);
        client(actor, detail.clientId(), true);
        records.lock("CLIENT_APPLICATION_REQUEST/" + actor.getSaccoId() + "/" + actor.getStationId() + "/" + requestKey);
        String payloadHash = hash(List.of("REMOVE_DOCUMENT", id, file, version));
        var retry = records.retry(actor, requestKey);
        if (retry.isPresent()) {
            check(payloadHash.equals(retry.get().payloadHash()), "retry");
            return;
        }
        records.lock("CLIENT_APPLICATION/" + detail.clientId());
        detail = details(actor, id);
        check(detail.status().equals("DRAFT"), "state");
        check(detail.version() == version, "changed");
        Form previous = recorded(actor, id);
        loans.removeApplicationAttachment(id, detail.clientId(), file, actor.getMemberId());
        records.command(actor, id, detail.clientId(), version + 1, requestKey, payloadHash, json.writeValueAsString(previous), clock.now());
    }

    @Transactional(readOnly=true) public Map<String,Object> quote(AppUserPrincipal a,Form f){client(a,f.getClientId(),false);basic(f);var p=schemas.getSchema(a.getSaccoId(),f.getProductId(),null);var t=new LoanTenure(f.getTenureUnit(),f.getTenureValue());t.validate(p);return financials.generateSnapshot(a.getSaccoId(),f.getClientId(),p,f.getAmount(),t.compatibilityMonths(),null,t);}
    public UUID save(AppUserPrincipal a,Form f,List<MultipartFile> files){
        if(a!=null&&!a.isStaffSession())f.setLoanOfficerId(f.getApplicationId()==null?null:recorded(a,f.getApplicationId()).getLoanOfficerId());
        client(a,f.getClientId(),true);if(f.getPurpose()!=null)f.setPurpose(f.getPurpose().strip().toUpperCase(Locale.ROOT));basic(f);check(Set.of("DRAFT","SUBMIT").contains(f.getCommand()),"command");
        var p=schemas.getSchema(a.getSaccoId(),f.getProductId(),null);var definitions=requirements.activeForProduct(p.getId());var required=new HashMap<UUID,LoanProductRequiredAttachment>();definitions.forEach(d->required.put(d.getId(),d));
        check(files==null||files.size()<=10,"attachments");var uploads=new ArrayList<LoanAttachmentService.NamedUpload>();long total=0;
        if(files!=null)for(int i=0;i<files.size();i++){var file=files.get(i);if(file==null||file.isEmpty())continue;total+=file.getSize();check(file.getSize()<=5*1024*1024&&total<=25*1024*1024,"attachments");String title=at(f.getDocumentNames(),i),type=at(f.getDocumentTypes(),i),id=at(f.getRequiredAttachmentIds(),i);text(title,120,true);check(DOCUMENT_TYPES.contains(type),"attachments");UUID requiredId=id.isBlank()?null:UUID.fromString(id);if(requiredId!=null){var def=required.get(requiredId);check(def!=null&&file.getSize()<=requirements.maxSizeBytes(def),"attachments");title=def.getAttachmentName();}uploads.add(new LoanAttachmentService.NamedUpload(file,title,type,requiredId));}
        check(f.getGuarantors()!=null&&f.getGuarantors().size()<=20&&f.getCollateral()!=null&&f.getCollateral().size()<=20,"details");
        f.getGuarantors().removeIf(g->g!=null&&g.getClientId()==null&&g.getRelationship().isBlank()&&g.getBusiness().isBlank()&&g.getLocation().isBlank()&&g.getPhone().isBlank());
        f.getCollateral().removeIf(c->c!=null&&c.getItemName().isBlank()&&c.getItemNumber().isBlank()&&c.getItemType().isBlank()&&c.getDescription().isBlank()&&c.getEstimatedValue()==null&&c.getOwner().isBlank()&&c.getPhone().isBlank()&&c.getLocation().isBlank());
        var labels=guarantorLabels(a,f);
        for(var g:f.getGuarantors()){check(g!=null&&g.getClientId()!=null&&!g.getClientId().equals(f.getClientId())&&labels.containsKey(g.getClientId()),"guarantors");text(g.getRelationship(),255,false);text(g.getBusiness(),255,false);text(g.getLocation(),255,false);phone(g.getPhone());}
        for(var c:f.getCollateral()){check(c!=null,"collateral");text(c.getItemName(),255,true);text(c.getItemNumber(),100,false);text(c.getItemType(),100,false);text(c.getDescription(),500,false);text(c.getOwner(),255,false);text(c.getLocation(),255,false);phone(c.getPhone());money(c.getEstimatedValue(),false);}
        var selected=f.getGuarantors().stream().map(Guarantor::getClientId).toList();check(new HashSet<>(selected).size()==selected.size(),"guarantors");
        var fields=parameters(f,p);String hash=hash(List.of(f,uploads.stream().map(u->List.of(u.title(),u.purpose(),Objects.toString(u.requiredId(),""),Objects.toString(u.file().getOriginalFilename(),""),fileHash(u.file()))).toList()));
        var tx=new TransactionTemplate(transactions);tx.setTimeout(30);
        return tx.execute(status->{
            client(a,f.getClientId(),true);records.lock("CLIENT_APPLICATION_REQUEST/"+a.getSaccoId()+"/"+a.getStationId()+"/"+f.getRequestKey());var retry=records.retry(a,f.getRequestKey());if(retry.isPresent()){check(hash.equals(retry.get().payloadHash()),"retry");return retry.get().applicationId();}
            records.lock("CLIENT_APPLICATION/"+f.getClientId());if(f.getApplicationId()!=null){var old=details(a,f.getApplicationId());check(old.clientId().equals(f.getClientId())&&old.version()==f.getExpectedVersion(),"changed");check(old.status().equals("DRAFT"),"state");}else check(f.getExpectedVersion()==0,"changed");
            if(a.isStaffSession()&&f.getLoanOfficerId()!=null)check(records.officerExists(a,f.getLoanOfficerId()),"officer");
            UUID id=loans.saveRecordingDraft(a.getSaccoId(),a.getStationId(),f.getClientId(),a.getMemberId(),f,fields,uploads);
            if(f.getCommand().equals("SUBMIT")){
                records.lock("CLIENT_KYC/"+f.getClientId());verified(a,f.getClientId());check(f.isDeclarationAccepted(),"declaration");
                var saved=loans.getMine(id,f.getClientId());check(attachments.parse(saved.getAttachmentsJson()).stream().anyMatch(d->"SIGNED_APPLICATION".equals(d.get("documentPurpose"))),"signedApplication");
                // Server pricing, schema, required attachments, affordability, guarantor capacity and routing stay authoritative.
                loans.saveAndSubmit(a.getSaccoId(),f.getClientId(),p.getId(),p.getLoanType(),f.getAmount(),new LoanTenure(f.getTenureUnit(),f.getTenureValue()).compatibilityMonths(),fields,id,selected,json.writeValueAsString(quote(a,f)),null,List.of(),Map.of());
            }
            records.command(a,id,f.getClientId(),f.getExpectedVersion()+1,f.getRequestKey(),hash,json.writeValueAsString(f),clock.now());
            audit.logEvent("LOAN_APPLICATION",id,f.getCommand().equals("SUBMIT")?"CLIENT_APPLICATION_SUBMITTED":"CLIENT_APPLICATION_DRAFT",a.getMemberId(),AuditEventStatus.SUCCESS,"Client application recording","LOAN_APPLICATION",id.toString(),a.getSaccoId(),a.getStationId(),Map.of("client",f.getClientId(),"assisted",a.isStaffSession()));return id;
        });
    }
    private Map<String,String> parameters(Form f,LoanProductSetting p){
        check(f.getFields()!=null&&f.getFields().size()<=100,"details");var allowed=schemas.toFormModel(p.getLoanType(),p.getFormSchema()).getFields().stream().map(com.sacco.mvp.service.dto.FormField::getName).collect(java.util.stream.Collectors.toSet());
        var data=new LinkedHashMap<String,String>();f.getFields().forEach((k,v)->{check(allowed.contains(k),"details");text(v,1000,false);data.put(k,v);});
        data.put("purpose",f.getPurpose());data.put("business",f.getBusiness());put(data,"monthlyIncome",f.getIncome());put(data,"monthlyExpenses",f.getExpenses());put(data,"otherDebtRepayments",f.getOtherDebt());data.put("tenureUnit",f.getTenureUnit());data.put("tenureValue",f.getTenureValue().toString());data.put("guarantorApprovalMode","LOGIN");
        if(!f.getCollateral().isEmpty()){data.put("collateralDescription",f.getCollateral().stream().map(c->c.getItemName()+": "+c.getDescription()).collect(java.util.stream.Collectors.joining("; ")));if(f.getCollateral().stream().allMatch(c->c.getEstimatedValue()!=null))put(data,"collateralEstimatedValue",f.getCollateral().stream().map(Collateral::getEstimatedValue).reduce(BigDecimal.ZERO,BigDecimal::add));}
        return data;
    }
    private void basic(Form f){check(f!=null&&f.getRequestKey()!=null&&f.getProductId()!=null&&f.getTenureValue()!=null,"details");check(f.getApplicationDate()!=null&&!f.getApplicationDate().isAfter(clock.today()),"date");new LoanTenure(f.getTenureUnit(),f.getTenureValue());money(f.getAmount(),true);money(f.getIncome(),false);money(f.getExpenses(),false);money(f.getOtherDebt(),false);text(f.getPurpose(),255,false);text(f.getBusiness(),1000,false);text(f.getUseOfFunds(),2000,false);}
    private static void query(String s,int page){check(s!=null&&s.length()<=100&&page>=0&&page<=10000,"query");}
    private static void money(BigDecimal v,boolean required){check(v==null?!required:v.signum()>=(required?1:0)&&v.scale()<=2&&v.precision()<=18,"money");}
    private static void text(String s,int max,boolean required){check(s!=null&&s.length()<=max&&(!required||!s.isBlank())&&s.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),"details");}
    private static void phone(String s){text(s,32,false);if(!s.isBlank())try{TanzaniaPhoneNumber.normalizeRequired(s);}catch(IllegalStateException e){throw new IllegalArgumentException("applications.error.phone");}}
    private static String at(List<String> values,int i){return values!=null&&i<values.size()?Objects.toString(values.get(i),""):"";}
    private static void put(Map<String,String> data,String key,BigDecimal v){if(v!=null)data.put(key,v.toPlainString());}
    private String hash(Object value){return digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));}
    private String fileHash(MultipartFile file){try{return digest(file.getBytes());}catch(java.io.IOException e){throw new IllegalArgumentException("applications.error.attachments");}}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("applications.error."+key);}
}
