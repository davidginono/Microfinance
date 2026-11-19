package com.sacco.mvp.accounting.statements.dto;

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
    public record MovementRow(UUID id,Category category,String name,String nameSw) {}
    public record MovementOption(String value,MovementRow row) {}
    public record MovementSelection(int version,UUID id) {
        public String value(){return version+":"+id;}
        public static MovementSelection parse(String value){
            if(value.length()>47)throw new IllegalArgumentException("Invalid movement row");
            int separator=value.indexOf(':');
            if(separator<1)throw new IllegalArgumentException("Invalid movement row");
            int version=Integer.parseInt(value.substring(0,separator));
            UUID id=UUID.fromString(value.substring(separator+1));
            var selection=new MovementSelection(version,id);
            if(version<1||!selection.value().equals(value))throw new IllegalArgumentException("Invalid movement row");
            return selection;
        }
    }
    public record Classification(Category category,MovementRow row,Integer setupVersion) {}
    public record Setup(int version,List<Component> components,List<Note> notes,List<CategoryLabel> categories,List<MovementRow> movementRows,
                        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Boolean useChartNotes) {
        public Setup { movementRows=movementRows==null?List.of():List.copyOf(movementRows); }
        public Setup(int version,List<Component> components,List<Note> notes,List<CategoryLabel> categories,List<MovementRow> movementRows){this(version,components,notes,categories,movementRows,null);}
        public Setup(int version,List<Component> components,List<Note> notes,List<CategoryLabel> categories){this(version,components,notes,categories,List.of());}
        public static Setup empty(){return new Setup(0,List.of(),List.of(),List.of());}
        public boolean hasLayoutNotes(){return version>0&&!Boolean.TRUE.equals(useChartNotes);}
    }
    public record StoredSetup(int version,String payload) {}
    public record AccountChoice(UUID id,String code,String name,String type) {}
    public record Movement(UUID accountId,Category category,boolean previous,BigDecimal debit,BigDecimal credit,
                           @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) MovementRow row) {
        public Movement(UUID accountId,Category category,boolean previous,BigDecimal debit,BigDecimal credit){this(accountId,category,previous,debit,credit,null);}
        public BigDecimal signed(){return debit.subtract(credit);}
    }
    public record Journal(UUID id,LocalDate date,String reference,String description,Category category,int version,BigDecimal debit,BigDecimal credit,MovementRow row) {
        public Journal(UUID id,LocalDate date,String reference,String description,Category category,int version,BigDecimal debit,BigDecimal credit){this(id,date,reference,description,category,version,debit,credit,null);}
    }
}
