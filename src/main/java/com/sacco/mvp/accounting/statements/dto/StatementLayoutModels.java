package com.sacco.mvp.accounting.statements.dto;

import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.AccountChoice;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Category;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.MovementRow;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;

public final class StatementLayoutModels {
    private StatementLayoutModels() {}
    public record GroupRow(int index,String name,String nameSw,int accounts,boolean retained) {}
    public record NoteRow(int index,int number,String title,String titleSw,String basis,int accounts,boolean includeProfit) {}
    public record Layout(int version,List<GroupRow> groups,List<NoteRow> notes,List<MovementRow> movementRows) {
        public Layout(int version,List<GroupRow> groups,List<NoteRow> notes){this(version,groups,notes,List.of());}
    }
    public record GroupEditor(GroupForm form,List<AccountChoice> accounts,int positions) {}
    public record NoteEditor(NoteForm form,List<AccountChoice> accounts) {}
    public record RemovalEditor(Change form,String name,int accounts,boolean group) {}

    @Data public static class Change {
        private int version,index=-1;
        private UUID requestKey;
        private String reason="";
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class GroupForm extends Change {
        private String name="",nameSw="";
        private int position=1;
        private boolean retained;
        private List<UUID> accounts=new ArrayList<>();
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class NoteForm extends Change {
        private int number=1;
        private String title="",titleSw="",basis="NARRATIVE",text="",textSw="";
        private boolean includeProfit;
        private List<UUID> accounts=new ArrayList<>();
    }
    @Data @EqualsAndHashCode(callSuper=true) public static class MovementRowForm extends Change {
        private String name="",nameSw="";
        private Category category;
    }
}
