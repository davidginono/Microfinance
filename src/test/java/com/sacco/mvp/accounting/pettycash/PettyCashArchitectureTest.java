package com.sacco.mvp.accounting.pettycash;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class PettyCashArchitectureTest {
    @Test void onlyTheOwningServiceReachesPettyCashPersistence()throws Exception{
        var root=Path.of("src/main/java/com/sacco/mvp/accounting/pettycash");
        try(var files=Files.walk(root)){
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList()){
                String source=Files.readString(file);
                if(file.getParent().endsWith("repository"))continue;
                assertThat(source).as(file.toString()).doesNotContain("JdbcTemplate","EntityManager","java.sql.");
                if(!file.getParent().endsWith("service"))assertThat(source).as(file.toString()).doesNotContain(".repository.");
            }
        }
    }
}
