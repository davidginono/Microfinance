package com.sacco.mvp.accounting.dto;

import java.util.List;
import java.util.UUID;

/** Money enters as BigDecimal; no binary floating-point conversion is permitted. */
public final class GeneralLedgerDtos {
    private GeneralLedgerDtos() { }
    public record AccountCommand(String code, String name, String type, String normalBalance,
                                 String kind, String purpose, UUID parentId, String nameSw, String description) {
        public AccountCommand(String code,String name,String type,String normalBalance,String kind,String purpose,UUID parentId) {
            this(code,name,type,normalBalance,kind,purpose,parentId,null,null);
        }
    }
    public record Account(UUID id, String code, String name, String type, String normalBalance,
                          String kind, String purpose, UUID parentId, boolean active, String nameSw, String description) {
        public Account(UUID id,String code,String name,String type,String normalBalance,String kind,String purpose,UUID parentId,boolean active) {
            this(id,code,name,type,normalBalance,kind,purpose,parentId,active,null,null);
        }
    }
    public record AccountRow(Account account,String parentCode,String parentName,String parentNameSw) { }
    public record AccountFilter(String search,String type,String kind,String state,UUID parentId) {
        public AccountFilter(String search,String type,String kind,String state) {this(search,type,kind,state,null);}
    }
    public record Page<T>(List<T> rows, int page, boolean hasNext) { }
}
