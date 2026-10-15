package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StatementPageClaimAccessTest {
    @Test void statementControlsUseClaimsAndRequireEveryPermissionInTheirCondition() throws Exception {
        var condition = Pattern.compile("<sec:authorize access=\"([^\"]+)\"");
        var claim = Pattern.compile("@access\\.has\\(principal, '([A-Z_]+)'\\)");
        int checked = 0;
        for (String page : List.of("financial-statements", "statement-mappings", "statement-result", "statement-trial", "statement-account")) {
            var conditions = condition.matcher(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/reports/" + page + ".jsp")));
            while (conditions.find()) {
                String expression = conditions.group(1);
                assertThat(expression).as(page).doesNotContain("hasAuthority").contains("@access.has(principal,");
                assertThat(evaluate(expression, EnumSet.allOf(UserClaim.class))).as(page + " entitled staff").isTrue();
                assertThat(evaluate(expression, Set.of())).as(page + " staff without claims").isFalse();
                var claims = claim.matcher(expression);
                while (claims.find()) {
                    var missing = EnumSet.allOf(UserClaim.class);
                    missing.remove(UserClaim.valueOf(claims.group(1)));
                    assertThat(evaluate(expression, missing)).as(page + " missing " + claims.group(1)).isFalse();
                }
                checked++;
            }
        }
        assertThat(checked).isEqualTo(12);
    }

    private boolean evaluate(String expression, Set<UserClaim> claims) {
        var principal = new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .position(Position.ACCOUNTANT).build(), claims, true);
        assertThat(principal.getAuthorities()).isEmpty();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, "", principal.getAuthorities());
        var context = new StandardEvaluationContext(authentication);
        context.setBeanResolver((ignored, name) -> {
            assertThat(name).isEqualTo("access");
            return new AccessControlService();
        });
        return Boolean.TRUE.equals(new SpelExpressionParser().parseExpression(expression).getValue(context, Boolean.class));
    }
}
