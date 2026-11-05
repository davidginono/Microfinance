package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.clients.applications.dto.ClientApplicationDtos;
import com.sacco.mvp.clients.applications.service.ClientApplicationService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanRecordingEvidenceService {
    private static final Set<String> STANDARD = Set.of("purpose","business","monthlyIncome","monthlyExpenses",
        "otherDebtRepayments","collateralDescription","collateralEstimatedValue");
    private final LoanRecordingRepository records;
    private final GeneralLedgerService accounting;
    private final FormSchemaService schemas;
    private final LoanProductRequiredAttachmentService requirements;
    private final ClientApplicationService applications;

    @Transactional(readOnly=true)
    public List<Choice> choices(AppUserPrincipal actor,String kind,String search,int page) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");
        check(search!=null && search.length()<=100 && page>=0 && page<=10000,"query");
        return switch(kind) {
            case "officers" -> records.officers(actor,search,page);
            case "guarantors" -> records.clients(actor,search,page);
            default -> throw new IllegalArgumentException("applications.error.query");
        };
    }

    @Transactional(readOnly=true)
    public ClientApplicationDtos.ProductForm product(AppUserPrincipal actor,UUID id) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");
        var product=schemas.getSchema(actor.getSaccoId(),id,null);
        return new ClientApplicationDtos.ProductForm(
            new ClientApplicationDtos.Product(product.getId(),product.getDisplayName(),product.getResolvedTenureUnit(),
                product.getResolvedMinimumTenure(),product.getResolvedMaximumTenure(),product.getGuarantorsRequired(),
                product.isCollateralRequired(),"PRODUCT",product.getResolvedRepaymentFrequency().name()),
            schemas.toFormModel(product.getLoanType(),product.getFormSchema()).getFields().stream()
                .filter(field->!STANDARD.contains(field.getName())).toList(),
            requirements.activeForProduct(id).stream()
                .map(r->new ClientApplicationDtos.Requirement(r.getId(),r.getAttachmentName(),r.getMaxSizeMb())).toList());
    }

    @Transactional(readOnly=true)
    public Map<UUID,String> labels(AppUserPrincipal actor,ApplicationDetails details) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");
        if(details==null || details.getGuarantors()==null)return Map.of();
        check(details.getGuarantors().size()<=20,"guarantors");
        return records.evidenceClients(actor,details.getGuarantors().stream().filter(Objects::nonNull)
            .map(ClientApplicationDtos.Guarantor::getClientId).filter(Objects::nonNull).toList());
    }

    public List<LoanAttachmentService.NamedUpload> prepare(AppUserPrincipal actor,RecordForm form,List<MultipartFile> files) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");
        var details=form.getApplicationDetails();
        if(details==null)return null;
        text(details.getUseOfFunds(),2000);
        check(details.getLoanOfficerId()==null || records.officer(actor,details.getLoanOfficerId()),"officer");
        details.setLoanOfficerLabel(details.getLoanOfficerId()==null?"":records.officerLabel(actor,details.getLoanOfficerId()));
        details.setFieldLabels(new LinkedHashMap<>());
        check(details.getGuarantors()!=null && details.getGuarantors().size()<=20 && details.getCollateral()!=null
            && details.getCollateral().size()<=20 && details.getFields()!=null && details.getFields().size()<=100 && details.getDocumentNames()!=null && details.getDocumentTypes()!=null && details.getRequiredAttachmentIds()!=null
            && details.getDocumentNames().size()<=10 && details.getDocumentTypes().size()<=10 && details.getRequiredAttachmentIds().size()<=10,"details");
        var labels=labels(actor,details);
        var selected=new HashSet<UUID>();
        for(var guarantor:details.getGuarantors()) {
            check(guarantor!=null && guarantor.getClientId()!=null && !guarantor.getClientId().equals(form.getClientId())
                && labels.containsKey(guarantor.getClientId()) && selected.add(guarantor.getClientId()),"guarantors");
            text(guarantor.getRelationship(),255);text(guarantor.getBusiness(),255);text(guarantor.getLocation(),255);phone(guarantor.getPhone());
        }
        for(var asset:details.getCollateral()) {
            check(asset!=null && asset.getItemName()!=null && !asset.getItemName().isBlank(),"collateral");
            text(asset.getItemName(),255);text(asset.getItemNumber(),100);text(asset.getItemType(),100);text(asset.getDescription(),500);
            text(asset.getOwner(),255);text(asset.getLocation(),255);phone(asset.getPhone());
            BigDecimal value=asset.getEstimatedValue();
            check(value==null || value.signum()>=0 && value.scale()<=2 && value.precision()<=18,"collateral");
        }
        var definitions="CONFIGURED".equals(form.getLoanMode())?requirements.activeForProduct(form.getProductId()):List.<com.sacco.mvp.domain.LoanProductRequiredAttachment>of();
        var required=new HashMap<UUID,com.sacco.mvp.domain.LoanProductRequiredAttachment>();definitions.forEach(d->required.put(d.getId(),d));
        if("CONFIGURED".equals(form.getLoanMode())) {
            var product=schemas.getSchema(actor.getSaccoId(),form.getProductId(),null);
            var formFields=schemas.toFormModel(product.getLoanType(),product.getFormSchema()).getFields();
            formFields.forEach(field->details.getFieldLabels().put(field.getName(),field.getLabelKey()));
            Set<String> allowed=formFields.stream()
                .map(com.sacco.mvp.service.dto.FormField::getName).collect(java.util.stream.Collectors.toSet());
            details.getFields().forEach((key,value)->{check(allowed.contains(key)&&!STANDARD.contains(key),"details");text(value,1000);});
            var values=new LinkedHashMap<>(details.getFields());
            values.put("purpose",form.getPurpose());values.put("business",form.getBusiness());
            if(form.getIncome()!=null)values.put("monthlyIncome",form.getIncome().toPlainString());
            if(form.getExpenses()!=null)values.put("monthlyExpenses",form.getExpenses().toPlainString());
            if(form.getOtherDebt()!=null)values.put("otherDebtRepayments",form.getOtherDebt().toPlainString());
            values.put("collateralDescription",details.getCollateral().isEmpty()?form.getCollateral():details.getCollateral().stream()
                .map(asset->asset.getItemName()+": "+asset.getDescription()).collect(java.util.stream.Collectors.joining("; ")));
            if(!details.getCollateral().isEmpty() && details.getCollateral().stream().allMatch(asset->asset.getEstimatedValue()!=null))
                values.put("collateralEstimatedValue",details.getCollateral().stream().map(ClientApplicationDtos.Collateral::getEstimatedValue).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString());
            values.keySet().retainAll(allowed);
            schemas.validateAgainstSchema(product.getFormSchema(),schemas.extractFormData(values,product.getFormSchema()));
        } else check(details.getFields().isEmpty(),"details");
        if(details.getDocumentNames().isEmpty() && details.getDocumentTypes().isEmpty() && details.getRequiredAttachmentIds().isEmpty())return null;
        check(files.size()<=10,"attachments");
        var uploads=new ArrayList<LoanAttachmentService.NamedUpload>();
        for(int i=0;i<files.size();i++) {
            var file=files.get(i);if(file==null || file.isEmpty())continue;
            String name=at(details.getDocumentNames(),i),type=at(details.getDocumentTypes(),i),slot=at(details.getRequiredAttachmentIds(),i);
            text(name,120);check(!name.isBlank() && ClientApplicationService.DOCUMENT_TYPES.contains(type),"attachments");
            UUID requirement=slot.isBlank()?null:UUID.fromString(slot);
            if(requirement!=null) {
                var definition=required.get(requirement);
                check(definition!=null && file.getSize()<=requirements.maxSizeBytes(definition),"attachments");
                name=definition.getAttachmentName();
            }
            uploads.add(new LoanAttachmentService.NamedUpload(file,name,type,requirement));
        }
        return uploads;
    }

    @Transactional(readOnly=true)
    public RecordForm draftForm(AppUserPrincipal actor,UUID id) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");
        var draft=applications.form(actor,null,id);
        var form=draft.getRecordingDraft()==null?new RecordForm():draft.getRecordingDraft();
        form.setRequestKey(draft.getRequestKey());form.setApplicationId(id);form.setExpectedVersion(draft.getExpectedVersion());
        form.setClientId(draft.getClientId());form.setProductId(draft.getProductId());form.setLoanMode("CONFIGURED");
        form.setPrincipal(draft.getAmount());form.setApplicationDate(draft.getApplicationDate());form.setTenureUnit(draft.getTenureUnit());form.setTenureValue(draft.getTenureValue());
        form.setPurpose(draft.getPurpose());form.setBusiness(draft.getBusiness());form.setIncome(draft.getIncome());form.setExpenses(draft.getExpenses());form.setOtherDebt(draft.getOtherDebt());
        var details=form.getApplicationDetails()==null?new ApplicationDetails():form.getApplicationDetails();
        details.setLoanOfficerId(draft.getLoanOfficerId());details.setUseOfFunds(draft.getUseOfFunds());
        details.setGuarantors(draft.getGuarantors());details.setCollateral(draft.getCollateral());details.setFields(draft.getFields());
        details.setDocumentNames(new ArrayList<>());details.setDocumentTypes(new ArrayList<>());details.setRequiredAttachmentIds(new ArrayList<>());
        details.setDeclarationAccepted(false);form.setApplicationDetails(details);return form;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> draftDocuments(AppUserPrincipal actor,UUID id) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");return applications.documents(actor,id);
    }

    @Transactional
    public UUID saveApplication(AppUserPrincipal actor,RecordForm form,List<MultipartFile> files,boolean submit) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");
        check("CONFIGURED".equals(form.getLoanMode()) && form.getTopUpSourceLoanId()==null,"details");
        records.lockRequest(actor,form.getRequestKey());
        check(!records.directRequest(actor,form.getRequestKey()),"retry");
        var draft=application(form);
        draft.setCommand(submit?"SUBMIT":"DRAFT");
        draft.setRecordingDraft(form);
        if(draft.getDocumentNames().isEmpty())for(var file:files) {
            draft.getDocumentNames().add(Objects.toString(file.getOriginalFilename(),"Document"));
            draft.getDocumentTypes().add("OTHER");draft.getRequiredAttachmentIds().add("");
        }
        return applications.save(actor,draft,files);
    }

    private ClientApplicationDtos.Form application(RecordForm form) {
        var draft=new ClientApplicationDtos.Form();
        draft.setApplicationId(form.getApplicationId());draft.setExpectedVersion(form.getExpectedVersion());
        draft.setRequestKey(form.getRequestKey());draft.setClientId(form.getClientId());draft.setProductId(form.getProductId());
        draft.setAmount(form.getPrincipal());draft.setApplicationDate(form.getApplicationDate());
        draft.setTenureUnit(form.getTenureUnit());draft.setTenureValue(form.getTenureValue());draft.setPurpose(form.getPurpose());
        draft.setBusiness(form.getBusiness());draft.setIncome(form.getIncome());draft.setExpenses(form.getExpenses());draft.setOtherDebt(form.getOtherDebt());
        var details=form.getApplicationDetails();
        if(details!=null) {
            draft.setLoanOfficerId(details.getLoanOfficerId());draft.setUseOfFunds(details.getUseOfFunds());
            draft.setGuarantors(details.getGuarantors());draft.setCollateral(details.getCollateral());draft.setFields(details.getFields());
            draft.setDocumentNames(details.getDocumentNames());draft.setDocumentTypes(details.getDocumentTypes());
            draft.setRequiredAttachmentIds(details.getRequiredAttachmentIds());draft.setDeclarationAccepted(details.isDeclarationAccepted());
        }
        return draft;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> applicationSnapshot(AppUserPrincipal actor,RecordForm form) {
        accounting.requireActor(actor,"LOAN_RECORDING_CREATE");
        applications.details(actor,form.getApplicationId());
        return applications.quote(actor,application(form));
    }

    public LoanRecordingService.Quote applicationQuote(AppUserPrincipal actor,RecordForm form,Map<String,Object> snapshot) {
        var principal=new BigDecimal(snapshot.get("principalAmount").toString());
        var tenure=new LoanTenure(form.getTenureUnit(),form.getTenureValue());
        var calculation=LoanAmortizationCalculator.estimate(principal,tenure.compatibilityMonths(),snapshot);
        var rows=new ArrayList<ScheduleRow>();
        BigDecimal balance=principal;
        for(var payment:calculation.rows()) {
            var ending=balance.subtract(payment.principal());
            rows.add(new ScheduleRow(payment.number(),null,payment.principal(),payment.interest(),payment.amount(),balance,ending));balance=ending;
        }
        return new LoanRecordingService.Quote(product(actor,form.getProductId()).product().name(),new BigDecimal(snapshot.get("interestRate").toString()),
            snapshot.get("interestMethod").toString(),snapshot.get("repaymentFrequency").toString(),calculation.installment(),calculation.totalInterest(),rows,tenure.unit(),tenure.value());
    }

    private static String at(List<String> values,int index) {return values!=null && index<values.size()?Objects.toString(values.get(index),""):"";}
    private static void text(String value,int max) {check(value!=null && value.length()<=max && value.codePoints().noneMatch(c->Character.isISOControl(c)&&c!=10&&c!=13&&c!=9),"details");}
    private static void phone(String value) {text(value,32);if(!value.isBlank())try{TanzaniaPhoneNumber.normalizeRequired(value);}catch(IllegalStateException ex){throw new IllegalArgumentException("applications.error.phone");}}
    private static void check(boolean value,String key) {if(!value)throw new IllegalArgumentException("applications.error."+key);}
}
