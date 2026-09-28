package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveLoanHydrationRouteContractTest {

    @Test
    void staffReviewControllersExposeScopedApplicantActiveLoanEndpoints() throws Exception {
        assertRoute(ManagerController.class, "/loan-applications/{id}/applicant-active-loans");
        assertRoute(AccountantController.class, "/loan-applications/{id}/applicant-active-loans");
        assertRoute(BoardController.class, "/loan-applications/{id}/applicant-active-loans");
        assertRoute(LoanOfficerController.class, "/loan-applications/{id}/applicant-active-loans");

        assertThat(ManagerController.class.getAnnotation(PreAuthorize.class).value())
            .contains("canAccessManagerArea");
        assertThat(AccountantController.class.getAnnotation(PreAuthorize.class).value())
            .contains("canAccessAccountantArea");
        assertThat(routeMethod(BoardController.class).getAnnotation(PreAuthorize.class).value())
            .contains("isBoardAssignee");
        assertThat(routeMethod(LoanOfficerController.class).getAnnotation(PreAuthorize.class).value())
            .contains("isLoanOfficerAssignee");
    }

    @Test
    void repaymentScheduleEndpointsAreScopedToMemberAndReviewRoles() throws Exception {
        assertGetRoute(
            AppController.class,
            "loanRepaymentSchedule",
            new Class<?>[] { UUID.class, AppUserPrincipal.class },
            "/loan-applications/{id}/repayment-schedule"
        );
        assertGetRoute(
            AppController.class,
            "externalActiveLoanRepaymentSchedule",
            new Class<?>[] { String.class, AppUserPrincipal.class },
            "/active-loans/{loanId}/repayment-schedule"
        );
        assertGetRoute(ManagerController.class, "repaymentSchedule", staffScheduleSignature(), "/loan-applications/{id}/repayment-schedule");
        assertGetRoute(AccountantController.class, "repaymentSchedule", staffScheduleSignature(), "/loan-applications/{id}/repayment-schedule");
        assertGetRoute(BoardController.class, "repaymentSchedule", staffScheduleSignature(), "/loan-applications/{id}/repayment-schedule");
        assertGetRoute(LoanOfficerController.class, "repaymentSchedule", staffScheduleSignature(), "/loan-applications/{id}/repayment-schedule");
        assertGetRoute(DisbursementController.class, "repaymentSchedule", staffScheduleSignature(), "/loan-applications/{id}/repayment-schedule");

        assertGetRoute(ManagerController.class, "applicantActiveLoanRepaymentSchedule", activeLoanScheduleSignature(), "/loan-applications/{id}/applicant-active-loans/{loanId}/repayment-schedule");
        assertGetRoute(AccountantController.class, "applicantActiveLoanRepaymentSchedule", activeLoanScheduleSignature(), "/loan-applications/{id}/applicant-active-loans/{loanId}/repayment-schedule");
        assertGetRoute(BoardController.class, "applicantActiveLoanRepaymentSchedule", activeLoanScheduleSignature(), "/loan-applications/{id}/applicant-active-loans/{loanId}/repayment-schedule");
        assertGetRoute(LoanOfficerController.class, "applicantActiveLoanRepaymentSchedule", activeLoanScheduleSignature(), "/loan-applications/{id}/applicant-active-loans/{loanId}/repayment-schedule");
    }

    @Test
    void staffActiveLoanEndpointsReuseExistingScopeChecks() throws Exception {
        Map<String, String> sources = Map.of(
            "manager", Files.readString(Path.of("src/main/java/com/sacco/mvp/web/ManagerController.java")),
            "accountant", Files.readString(Path.of("src/main/java/com/sacco/mvp/web/AccountantController.java")),
            "board", Files.readString(Path.of("src/main/java/com/sacco/mvp/web/BoardController.java")),
            "loanOfficer", Files.readString(Path.of("src/main/java/com/sacco/mvp/web/LoanOfficerController.java"))
        );

        assertThat(sources.get("manager"))
            .contains("managerService.get(id, principal.getSaccoId(), principal.getStationId())");
        assertThat(sources.get("accountant"))
            .contains("requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId())");
        assertThat(sources.get("board"))
            .contains("@PreAuthorize(\"@authz.isBoardAssignee(#id, principal)\")");
        assertThat(sources.get("loanOfficer"))
            .contains("@PreAuthorize(\"@access.canAccessLoanOfficerArea(principal) and @authz.isLoanOfficerAssignee(#id, principal)\")");
    }

    @Test
    void disbursementReviewDoesNotExposeApplicantActiveLoanHydrationEndpoint() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/sacco/mvp/web/DisbursementController.java"));

        assertThat(source).doesNotContain("applicant-active-loans");
        assertThat(source).doesNotContain("ActiveLoanDisplayService");
    }

    private void assertRoute(Class<?> controllerClass, String expectedPath) throws Exception {
        Method method = routeMethod(controllerClass);

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expectedPath);
        assertThat(method.getAnnotation(ResponseBody.class)).isNotNull();
    }

    private void assertGetRoute(Class<?> controllerClass,
                                String methodName,
                                Class<?>[] parameterTypes,
                                String expectedPath) throws Exception {
        Method method = controllerClass.getMethod(methodName, parameterTypes);

        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expectedPath);
        assertThat(method.getAnnotation(ResponseBody.class)).isNotNull();
    }

    private Method routeMethod(Class<?> controllerClass) throws Exception {
        return controllerClass.getMethod("applicantActiveLoans", UUID.class, AppUserPrincipal.class);
    }

    private Class<?>[] staffScheduleSignature() {
        return new Class<?>[] { UUID.class, AppUserPrincipal.class };
    }

    private Class<?>[] activeLoanScheduleSignature() {
        return new Class<?>[] { UUID.class, String.class, AppUserPrincipal.class };
    }
}
