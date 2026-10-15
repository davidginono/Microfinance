package com.sacco.mvp.accounting.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AccountingResetRoutesTest {
    @Test void retiredRoutesAreAbsentAndCoreRoutesRemain() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        var routes = new HashSet<String>();
        for (var bean : scanner.findCandidateComponents("com.sacco.mvp")) {
            var type = Class.forName(bean.getBeanClassName(), false, getClass().getClassLoader());
            var base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            for (var method : type.getDeclaredMethods()) {
                var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                for (var prefix : paths(base)) for (var path : paths(mapping)) routes.add(prefix + path);
            }
        }
        for (var retired : List.of("/finance/policies", "/finance/business", "/finance/periods", "/finance/journals",
            "/finance/reconciliation", "/finance/closing", "/finance/accounting-release", "/reports/statements")) {
            assertThat(routes).noneMatch(path -> path.equals(retired) || path.startsWith(retired + "/"));
        }
        assertThat(routes).contains("/finance/accounts", "/finance/vouchers/{kind}",
            "/finance/loan-records", "/finance/loan-disbursements", "/finance/loan-repayments",
            "/reports/financial/portfolio");
    }

    private static List<String> paths(RequestMapping mapping) {
        if (mapping == null || mapping.value().length == 0) return List.of("");
        return List.of(mapping.value());
    }
}
