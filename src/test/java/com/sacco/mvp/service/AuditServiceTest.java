package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.AuditLog;
import com.sacco.mvp.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    @Mock private AuditLogRepository auditLogRepository;

    @Test
    void logEnrichesFailureStatusScopeActionAndReference() {
        AuditService service = new AuditService(auditLogRepository, new ObjectMapper());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID loanId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("result", "ERROR");
        after.put("saccoId", "SACCO-1");
        after.put("stationId", "ST-1");
        after.put("applicationNumber", 42L);
        after.put("auditActionDescription", "OTP verification");

        service.log("LOAN_APPLICATION", loanId, "WEB_VERIFY_APPLICANT_SIGNATURE_OTP", actorId, null, after);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventStatus()).isEqualTo(AuditEventStatus.FAIL);
        assertThat(saved.getSaccoId()).isEqualTo("SACCO-1");
        assertThat(saved.getStationId()).isEqualTo("ST-1");
        assertThat(saved.getActionDescription()).isEqualTo("OTP verification");
        assertThat(saved.getReferenceValue()).isEqualTo("Loan Application #42");
    }

    @Test
    void logEventStoresReportExportReference() {
        AuditService service = new AuditService(auditLogRepository, new ObjectMapper());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID actorId = UUID.randomUUID();

        service.logEvent(
            "REPORT",
            null,
            "REPORT_EXPORTED",
            actorId,
            AuditEventStatus.SUCCESS,
            "Report exported",
            "REPORT",
            "Report station-loan-analytics",
            "SACCO-1",
            "ST-1",
            Map.of("reportName", "station-loan-analytics")
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getDisplayStatus()).isEqualTo("Success");
        assertThat(saved.getDisplayAction()).isEqualTo("Report exported");
        assertThat(saved.getShortEntityReference()).isEqualTo("Report station-loan-analytics");
    }
}
