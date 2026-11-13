package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.grouplending.dto.GroupLendingDtos.GroupForm;
import com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.DraftSummary;
import com.sacco.mvp.grouplending.service.GroupLendingService;
import com.sacco.mvp.grouplending.service.SimpleGroupLoanService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupDraftSortingPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    SimpleGroupLoanService service;
    UUID group;
    @BeforeAll void start(){f.start();service=f.context.getBean(SimpleGroupLoanService.class);}
    @AfterAll void stop(){f.stop();}
    @AfterEach void clearRequest(){RequestContextHolder.resetRequestAttributes();}
    @BeforeEach void fixture(){
        f.fixture();var form=new GroupForm();form.setName("Synthetic drafts");form.setEvidence("Synthetic roster");
        group=f.context.getBean(GroupLendingService.class).create(f.actor,form);
        var other=f.operator("B1");f.jdbc.update("update members set full_name='Asha' where id=?",f.actor.getMemberId());
        f.jdbc.update("update members set full_name='Zawadi' where id=?",other.getMemberId());
        for(int n=1;n<=28;n++)f.jdbc.update("insert into staff_group_draft(id,sacco_id,station_id,actor_id,group_id,form_data,updated_at) values(?,?,?,?,?,?::jsonb,?)",
            UUID.randomUUID(),f.institution,"B1",n%2==0?f.actor.getMemberId():other.getMemberId(),group,
            "{\"principal\":"+n+"}",OffsetDateTime.parse("2026-11-13T12:00:00+03:00").plusMinutes(n));
        form.setName("Another group");form.setRequestKey(UUID.randomUUID());
        var unrelated=f.context.getBean(GroupLendingService.class).create(f.actor,form);
        f.jdbc.update("insert into staff_group_draft(id,sacco_id,station_id,actor_id,group_id,form_data,updated_at) values(?,?,?,?,?,'{\"principal\":999999}',now())",
            UUID.randomUUID(),f.institution,"B1",f.actor.getMemberId(),unrelated);
    }
    void sort(String column,String direction){
        var request=new MockHttpServletRequest("GET","/finance/groups/"+group);
        request.setParameter("tableSort[group-loan-drafts]",column);request.setParameter("tableDirection[group-loan-drafts]",direction);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @Test void everyDataColumnSortsBeforeBoundedPagingAndRetainsGroupScope(){
        Map<String,Comparator<DraftSummary>> orders=Map.of("actor",Comparator.comparing(DraftSummary::actor),
            "updated",Comparator.comparing(DraftSummary::updated),"amount",Comparator.comparing(DraftSummary::amount));
        for(var column:orders.keySet())for(var direction:List.of("asc","desc")){
            sort(column,direction);var first=service.drafts(f.actor,group,0);var last=service.drafts(f.actor,group,1);
            assertThat(first.rows()).hasSize(25);assertThat(first.hasNext()).isTrue();
            assertThat(last.rows()).hasSize(3);assertThat(last.hasNext()).isFalse();
            var all=new ArrayList<>(first.rows());all.addAll(last.rows());
            var order=orders.get(column);if(direction.equals("desc"))order=order.reversed();
            assertThat(all).isSortedAccordingTo(order);
            assertThat(all).extracting(DraftSummary::id).doesNotHaveDuplicates();
            assertThat(all).extracting(DraftSummary::amount).allSatisfy(amount->assertThat(amount).isBetween(BigDecimal.ONE,new BigDecimal("28")));
        }
        assertThatThrownBy(()->service.drafts(f.operator("B2"),group,0)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void invalidSortCannotChangeTheSqlAndKeepsNewestDraftFirst(){
        sort("amount desc; delete from staff_group_draft","desc");
        assertThat(service.drafts(f.actor,group,0).rows().getFirst().amount()).isEqualByComparingTo("28");
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_draft where group_id=?",Integer.class,group)).isEqualTo(28);
    }
}
