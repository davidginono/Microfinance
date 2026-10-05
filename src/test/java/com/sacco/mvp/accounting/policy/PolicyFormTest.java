package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.accounting.policy.dto.PolicyForm;
import com.sacco.mvp.accounting.policy.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class PolicyFormTest {
    @Test void missingDecisionStaysMissingRatherThanDefaultingEnabledOrApproved() {
        var content=PolicyForm.decode(valid());
        assertThat(content.decisions()).isEmpty();
        assertThat(content.postingRules()).isEmpty();
    }
    @Test void typedMatrixPreservesOnlyExplicitDecisionAndCodes() {
        var form=valid(); form.put("rule.REPAYMENT.enabled","false");
        form.put("rule.REPAYMENT.treatment","Disabled pending review");
        form.put("rule.REPAYMENT.evidence","Minute 9");
        var rule=PolicyForm.decode(form).postingRules().get(AccountingEvent.REPAYMENT);
        assertThat(rule.enabled()).isFalse(); assertThat(rule.treatment()).isEqualTo("Disabled pending review");
    }
    @Test void forgedScopeUnknownFieldsInvalidDatesAndInvalidBooleanAreRejected() {
        for(var entry:Map.of("saccoId","other","authority","LOCAL'; DROP TABLE","effectiveFrom","bad-date","rule.REPAYMENT.enabled","1").entrySet()) {
            var form=valid();form.put(entry.getKey(),entry.getValue());
            assertThatThrownBy(()->PolicyForm.decode(form)).hasMessage("policy.error.invalid");
        }
    }
    @Test void oversizedFieldsAreRejectedBeforeServiceUse() {
        var form=valid(); form.put("decision.REPORTING_FRAMEWORK","A".repeat(2001));
        assertThatThrownBy(()->PolicyForm.decode(form)).hasMessage("policy.error.invalid");
    }
    @Test void policyJspStaticLabelsExistInBothLanguages() throws Exception {
        for (String language:List.of("en","sw")) {
            Properties messages=new Properties();
            try(var reader=Files.newBufferedReader(Path.of("src/main/resources/messages_"+language+".properties"))) { messages.load(reader); }
            try(var files=Files.list(Path.of("src/main/webapp/WEB-INF/jsp/accounting/policies"))) {
                for(var path:files.toList()) {
                    var matcher=Pattern.compile("<spring:message code=\"([^\"]+)\"").matcher(Files.readString(path));
                    while(matcher.find()) {
                        String code=matcher.group(1);
                        if(!code.contains("${")) assertThat(messages).as(path+" "+language+" label "+code).containsKey(code);
                        else assertThat(code.substring(0,code.indexOf("${"))).doesNotContain("(",")");
                    }
                }
            }
        }
    }
    private Map<String,String> valid() {
        return new HashMap<>(Map.of("authority","LOCAL","effectiveFrom","2026-10-04","openingDate","2026-09-01","authorityEvidence","Minute A","requestKey",UUID.randomUUID().toString(),"_csrf","token"));
    }
}
