package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class RecordedBorrowerAssessmentTest {
    final LoanPresentationService presentation=new LoanPresentationService(new ObjectMapper(),null,null,null,null,null,null,null,null,null);
    LoanApplication loan(String form){return LoanApplication.builder().financialSnapshot("{\"recordingSource\":\"ACCOUNTANT_DIRECT\"}").formData(form).build();}
    @Test void organizationAssessmentKeepsExactLargeDecimalsAndItsDocumentedSource(){
        var value=presentation.recordedBorrowerAssessment(loan("""
            {"business":"Organization <QA>","income":9999999999999999.99,"expenses":1000.23,"otherDebt":0.00,
             "informationSource":"DOCUMENTED","evidence":"Recorded supporting documents","productId":"ignored-view-property"}
            """));
        assertThat(value.income().toPlainString()).isEqualTo("9999999999999999.99");assertThat(value.expenses().toPlainString()).isEqualTo("1000.23");assertThat(value.otherDebt().toPlainString()).isEqualTo("0.00");assertThat(value.informationSource()).isEqualTo("DOCUMENTED");assertThat(value.business()).isEqualTo("Organization <QA>");
    }
    @Test void missingAmountsStayUnavailableRatherThanBecomingZero(){
        var value=presentation.recordedBorrowerAssessment(loan("{\"income\":123.45}"));assertThat(value.expenses()).isNull();assertThat(value.otherDebt()).isNull();assertThat(value.informationSource()).isEqualTo("UNAVAILABLE");
    }
    @Test void personalOrUnrecordedLoansCannotProduceAnOrganizationAssessment(){
        var personal=loan("{\"income\":1000}");personal.setApplicantMemberId(UUID.randomUUID());assertThat(presentation.recordedBorrowerAssessment(personal)).isNull();
        var unrecorded=loan("{\"income\":1000}");unrecorded.setFinancialSnapshot("{}");assertThat(presentation.recordedBorrowerAssessment(unrecorded)).isNull();
    }
    @Test void invalidOrAbsentRecordedEvidenceProducesNoAssessment(){
        assertThat(presentation.recordedBorrowerAssessment(loan("bad-json"))).isNull();assertThat(presentation.recordedBorrowerAssessment(loan(null))).isNull();assertThat(presentation.recordedBorrowerAssessment(loan("null"))).isNull();
    }
}
