package com.sacco.mvp.config;

import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ReportExportLimitAspect {
    private final ReportExportLimiter reportExportLimiter;

    @Around("""
        execution(public byte[] com.sacco.mvp.service.LoanReportService.build*Pdf(..))
        || execution(public byte[] com.sacco.mvp.service.LoanReportService.build*Excel(..))
        || execution(public byte[] com.sacco.mvp.service.LoanPresentationService.buildPrintablePdf(..))
        """)
    public Object limitHeapHeavyExports(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!reportExportLimiter.tryAcquire()) {
            throw ReportExportLimiter.busy();
        }
        try {
            return joinPoint.proceed();
        } finally {
            reportExportLimiter.release();
        }
    }
}
