package com.sacco.mvp.reporting.operational.controller;

import com.sacco.mvp.reporting.operational.dto.OperationalReportResult;
import com.sacco.mvp.reporting.operational.dto.OperationalPreviewResponse;
import com.sacco.mvp.reporting.operational.service.OperationalReportService;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/reports/operational/api")
public class OperationalReportApiController {
    private final OperationalReportService service;
    public record PreviewRequest(@NotBlank @Size(max=16384) String definitionJson,
                                 @Min(0) @Max(1000) int page,@Min(1) @Max(100) int size) { }
    @PostMapping("/preview") @PreAuthorize("@access.has(principal,'REPORTS_RUN')")
    public OperationalPreviewResponse preview(@AuthenticationPrincipal AppUserPrincipal actor,@Valid @RequestBody PreviewRequest request) {
        return OperationalPreviewResponse.from(service.preview(actor,service.parse(request.definitionJson()),request.page(),request.size()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid() { return ResponseEntity.badRequest().body(Map.of("error","opreport.error.definition")); }
}
