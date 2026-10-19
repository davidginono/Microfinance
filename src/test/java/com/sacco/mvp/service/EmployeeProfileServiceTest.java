package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeProfileServiceTest {
    @Test void editsOnlyTheAuthenticatedClientAndDoesNotAlterStaffLoginNumber() {
        var members=mock(MemberRepository.class);var audit=mock(AuditService.class);
        var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("C1").staffNo("STAFF1").memberAccount(true).position(Position.MEMBER).status(MemberStatus.ACTIVE).build();
        when(members.findById(member.getId())).thenReturn(Optional.of(member));
        when(members.updateEmployeeNumberForActiveClient(eq(member.getId()),eq("I1"),any())).thenReturn(1);
        var actor=new AppUserPrincipal(member,Set.of(),false);var service=new EmployeeProfileService(members,audit);
        service.update(actor,"  000123/AB  ");assertThat(member.getStaffNo()).isEqualTo("STAFF1");
        service.update(actor,"");
        verify(members).updateEmployeeNumberForActiveClient(member.getId(),"I1","000123/AB");
        verify(members).updateEmployeeNumberForActiveClient(member.getId(),"I1",null);
        verify(members,times(2)).findById(actor.getMemberId());
        verifyNoMoreInteractions(members);
    }
    @Test void rejectsInvalidNumbersAndForeignOrInactiveAccounts() {
        assertThat(EmployeeNumber.normalize(null)).isNull();assertThat(EmployeeNumber.normalize("0001")).isEqualTo("0001");
        for(var value:List.of("a".repeat(51),"a\nb","<script>","A\tB"))assertThatThrownBy(()->EmployeeNumber.normalize(value)).hasMessage("payroll.employeeNumber.invalid");
        var members=mock(MemberRepository.class);var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").memberAccount(true).position(Position.MEMBER).status(MemberStatus.ACTIVE).build();var actor=new AppUserPrincipal(member,Set.of(),false);
        member.setSaccoId("I2");when(members.findById(member.getId())).thenReturn(Optional.of(member));
        assertThatThrownBy(()->new EmployeeProfileService(members,mock(AuditService.class)).update(actor,"001")).isInstanceOf(AccessDeniedException.class);verify(members,never()).save(any());
    }
    @Test void employeeNumberValidationIsOptionalAtRegistrationButRejectsControlCharacters() {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();var form=new com.sacco.mvp.web.form.MemberRegistrationForm();
            assertThat(validator.validate(form)).noneMatch(e->e.getPropertyPath().toString().equals("employeeNumber"));
            form.setEmployeeNumber("001\nXYZ");assertThat(validator.validate(form)).anyMatch(e->e.getPropertyPath().toString().equals("employeeNumber"));
        }
    }
    @Test void deactivationBetweenReadAndWriteCannotBeOverwrittenByProfileSave() {
        var members=mock(MemberRepository.class);var audit=mock(AuditService.class);
        var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").memberNo("C1").memberAccount(true).position(Position.MEMBER).status(MemberStatus.ACTIVE).build();
        when(members.findById(member.getId())).thenReturn(Optional.of(member));
        when(members.updateEmployeeNumberForActiveClient(member.getId(),"I1","001")).thenReturn(0);
        assertThatThrownBy(()->new EmployeeProfileService(members,audit).update(new AppUserPrincipal(member,Set.of(),false),"001")).isInstanceOf(AccessDeniedException.class);
        verify(members,never()).save(any());verifyNoInteractions(audit);
    }
}
