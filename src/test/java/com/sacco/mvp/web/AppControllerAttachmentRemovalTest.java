package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerAttachmentRemovalTest {
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private AppUserPrincipal principal;

    @InjectMocks private AppController controller;

    @Test
    void removeApplicationAttachmentReturnsApplicantToAttachmentStep() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String attachmentId = UUID.randomUUID().toString();
        when(principal.getMemberId()).thenReturn(memberId);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String result = controller.removeApplicationAttachment(appId, attachmentId, principal, redirect);

        assertThat(result).isEqualTo("redirect:/app/loan-applications/" + appId + "/edit?step=4");
        assertThat(redirect.getFlashAttributes().get("message")).isEqualTo("Attachment removed successfully.");
        verify(loanWorkflowService).removeApplicationAttachment(appId, memberId, attachmentId);
    }
}
