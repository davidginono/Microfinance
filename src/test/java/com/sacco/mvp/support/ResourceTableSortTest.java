package com.sacco.mvp.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTableSortTest {
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    private void request(String column, String direction) {
        var request = new MockHttpServletRequest();request.setMethod("GET");
        int split=column.lastIndexOf('.');
        String table=column.substring(0,split);
        request.setParameter("tableSort["+table+"]",column.substring(split+1));
        request.setParameter("tableDirection["+table+"]",direction);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test void ordersBeforeTheLimitWithoutChangingOwnershipOrNestedOrdering() {
        request("admin-users-1.email","desc");
        String query = "select m.* from members m where m.sacco_id=:sacco and m.station_id=:branch "
            + "and m.id in (select id from members order by created_at desc limit 100) order by m.full_name asc limit 25 offset 50";
        assertThat(new ResourceTableSort.UsersNative().rewrite(query, Sort.unsorted()))
            .isEqualTo("select m.* from members m where m.sacco_id=:sacco and m.station_id=:branch "
                + "and m.id in (select id from members order by created_at desc limit 100) order by m.email desc nulls last,m.id desc limit 25 offset 50");
    }

    @Test void unrelatedTablesAndUnknownFieldsCannotChangeAQuery() {
        String query = "select m.* from members m where m.sacco_id=:sacco order by m.full_name asc";
        request("another-table.email","desc");
        assertThat(new ResourceTableSort.UsersNative().rewrite(query,Sort.unsorted())).isEqualTo(query);
        request("admin-users-1.email desc; delete from members","desc");
        assertThat(new ResourceTableSort.UsersNative().rewrite(query,Sort.unsorted())).isEqualTo(query);
        RequestContextHolder.resetRequestAttributes();
        assertThat(new ResourceTableSort.UsersNative().rewrite(query,Sort.unsorted())).isEqualTo(query);
    }

    @Test void directionAndSqlExpressionsComeOnlyFromTheServerWhitelist() {
        request("batches.amount","desc; drop table receipt_batch");
        assertThat(ResourceTableSort.order("batches",Map.of("amount","total"),"id","created_at desc,id"))
            .isEqualTo("total asc nulls last,id asc");
        request("batches.unsupported","desc");
        assertThat(ResourceTableSort.order("batches",Map.of("amount","total"),"id","created_at desc,id"))
            .isEqualTo("created_at desc,id");
    }

    @Test void countQueriesAndQuotedKeywordsStayIntact() {
        request("admin-users-1.name","asc");
        assertThat(new ResourceTableSort.UsersNative().rewrite("select count(*) from members m",Sort.unsorted()))
            .isEqualTo("select count(*) from members m");
        assertThat(new ResourceTableSort.UsersNative().rewrite("select m.* from members m where email='order by (limit)' order by m.id",Sort.unsorted()))
            .isEqualTo("select m.* from members m where email='order by (limit)' order by m.full_name asc nulls last,m.id asc ");
    }

    @Test void sharedInboxQueriesRetainEachTablesOwnSortAndRestoreTheirContext() {
        request("app-support-replies-1.subject","asc");
        var servlet=(MockHttpServletRequest)((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
        servlet.setParameter("tableSort[app-support-replies-2]","date");
        servlet.setParameter("tableDirection[app-support-replies-2]","desc");
        String sql="select n.* from notifications n where n.recipient_member_id=:recipient order by n.created_at desc limit 100";
        var rewriter=new ResourceTableSort.Notifications();
        assertThat(ResourceTableSort.forTables(java.util.Set.of("app-support-replies-1"),()->rewriter.rewrite(sql,Sort.unsorted())))
            .contains("coalesce(nullif(n.payload->>'subject',''),n.type) asc");
        assertThat(ResourceTableSort.forTables(java.util.Set.of("app-support-replies-2"),()->rewriter.rewrite(sql,Sort.unsorted())))
            .contains("n.created_at desc nulls last,n.id desc limit 100");
        servlet.setMethod("POST");
        assertThat(rewriter.rewrite(sql,Sort.unsorted())).isEqualTo(sql);
    }
}
