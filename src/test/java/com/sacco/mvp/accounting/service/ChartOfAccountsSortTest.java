package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.AccountFilter;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChartOfAccountsSortTest {
    @Test void orderingRetainsTenantParentFiltersLimitsAndStableTieBreaker() {
        var jdbc=mock(JdbcTemplate.class);var books=new GeneralLedgerRepository(jdbc);var parent=UUID.randomUUID();
        books.chart("I1",new AccountFilter("Cash","ASSET","POSTING","ACTIVE",parent),100,51,"name","desc");
        var call=mockingDetails(jdbc).getInvocations().iterator().next();
        assertThat((String)call.getArgument(0)).contains("where a.sacco_id=?","and a.parent_id=?","and a.type=?","and a.kind=?","and a.active=?","p.sacco_id=a.sacco_id","order by a.name desc,a.id desc limit ? offset ?");
        assertThat(call.getArguments()).contains("I1",parent,"cash","ASSET","POSTING",true,51,100);
    }
    @Test void invalidSortCannotBeInterpolatedIntoSql() {
        var jdbc=mock(JdbcTemplate.class);var books=new GeneralLedgerRepository(jdbc);var filter=new AccountFilter("","","","");
        assertThatThrownBy(()->books.chart("I1",filter,0,31,"code;drop table gl_account","asc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->books.chart("I1",filter,0,31,"code","desc;drop table gl_account")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
