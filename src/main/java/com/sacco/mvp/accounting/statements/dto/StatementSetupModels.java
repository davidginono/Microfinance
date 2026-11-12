package com.sacco.mvp.accounting.statements.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class StatementSetupModels {
    private StatementSetupModels() {}
    public enum Category { CONTRIBUTION, DISTRIBUTION, TRANSFER, EARNINGS_TRANSFER, OCI, ADJUSTMENT, UNCLASSIFIED }
    public record Component(String name,String nameSw,List<UUID> accounts,boolean retained) {}
    public record Note(int number,String title,String titleSw,String basis,String text,String textSw,List<UUID> accounts,boolean includeProfit) {
        public Note(int number,String title,String titleSw,String basis,String text,String textSw,List<UUID> accounts){this(number,title,titleSw,basis,text,textSw,accounts,false);}
    }
    public record CategoryLabel(Category category,String name,String nameSw) {}
    public record Setup(int version,List<Component> components,List<Note> notes,List<CategoryLabel> categories) {
        public static Setup empty(){return new Setup(0,List.of(),List.of(),List.of());}
    }
    public record StoredSetup(int version,String payload) {}
    public record AccountChoice(UUID id,String code,String name,String type) {}
    public record Movement(UUID accountId,Category category,boolean previous,BigDecimal debit,BigDecimal credit) {
        public BigDecimal signed(){return debit.subtract(credit);}
    }
    public record Journal(UUID id,LocalDate date,String reference,String description,Category category,int version,BigDecimal debit,BigDecimal credit) {}
    @Data public static class Form {
        private int version;
        private UUID requestKey;
        private String reason="";
        private List<ComponentForm> components=new ArrayList<>();
        private List<NoteForm> notes=new ArrayList<>();
        private List<CategoryForm> categories=new ArrayList<>();
    }
    @Data public static class ComponentForm {
        private String name="",nameSw="",accounts="";
        private boolean retained;
    }
    @Data public static class NoteForm {
        private int number;
        private boolean includeProfit;
        private String title="",titleSw="",basis="BALANCE",text="",textSw="",accounts="";
    }
    @Data public static class CategoryForm {
        private Category category;
        private String name="",nameSw="";
    }
}
