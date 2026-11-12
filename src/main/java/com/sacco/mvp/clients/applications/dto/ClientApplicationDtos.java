package com.sacco.mvp.clients.applications.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

public final class ClientApplicationDtos {
    private ClientApplicationDtos() {}
    @Data public static class Form {
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm recordingDraft;
        private UUID requestKey=UUID.randomUUID(),clientId,applicationId,productId,loanOfficerId;
        private int expectedVersion;
        private String command="DRAFT",tenureUnit="MONTHS",purpose="",business="",useOfFunds="";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate applicationDate;
        private Integer tenureValue;
        private BigDecimal amount,income,expenses,otherDebt;
        private List<Guarantor> guarantors=new ArrayList<>();
        private List<Collateral> collateral=new ArrayList<>();
        private Map<String,String> fields=new LinkedHashMap<>();
        private List<String> documentNames=new ArrayList<>(),documentTypes=new ArrayList<>(),requiredAttachmentIds=new ArrayList<>();
        private boolean declarationAccepted;
    }
    @Data public static class Guarantor {
        // Retained for historical client-linked guarantors. New recording uses fullName.
        private UUID clientId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        private String fullName="";
        private String relationship="",business="",location="",phone="";
    }
    @Data public static class Collateral {
        private String itemName="",itemNumber="",itemType="",description="",owner="",phone="",location="";
        private BigDecimal estimatedValue;
    }
    public record Choice(UUID id,String label) {}
    public record Product(UUID id,String name,String tenureUnit,int minimumTenure,int maximumTenure,int guarantors,boolean collateralRequired,String rateBasis,String frequency) {}
    public record Requirement(UUID id,String name,BigDecimal maxSizeMb) {}
    public record ProductForm(Product product,List<com.sacco.mvp.service.dto.FormField> fields,List<Requirement> requirements) {}
    public record Field(String name,String type,boolean required,BigDecimal min,BigDecimal max,List<String> enumOptions,String label) {}
    public record ProductFormView(Product product,List<Field> fields,List<Requirement> requirements) {}
    public record Row(UUID id,UUID clientId,String number,String client,String product,String status,BigDecimal amount,OffsetDateTime createdAt) {}
    public record Page(List<Row> rows,int page,boolean hasNext) {}
    public record Command(UUID id,UUID applicationId,int version,String payloadHash) {}
    public record Details(UUID id,UUID clientId,String number,String name,String status,int version,String data,String attachments,String snapshot) {}
}
