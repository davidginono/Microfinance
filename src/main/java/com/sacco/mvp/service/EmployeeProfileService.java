package com.sacco.mvp.service;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmployeeProfileService {
    private final MemberRepository members;
    private final AuditService audit;

    @Transactional
    public void update(AppUserPrincipal actor, String value) {
        if (actor == null || !actor.isMemberAccess() || actor.isPlatformIdentity())
            throw new AccessDeniedException("Client profile required");
        var member = members.findById(actor.getMemberId()).orElseThrow(() -> new AccessDeniedException("Client unavailable"));
        if (!member.isMemberAccess() || member.getStatus() != MemberStatus.ACTIVE
            || !Objects.equals(member.getSaccoId(), actor.getSaccoId()))
            throw new AccessDeniedException("Client unavailable");
        String number = EmployeeNumber.normalize(value);
        if (members.updateEmployeeNumberForActiveClient(member.getId(), member.getSaccoId(), number) != 1)
            throw new AccessDeniedException("Client unavailable");
        audit.logEvent("EMPLOYEE_PROFILE", member.getId(), "EMPLOYEE_NUMBER_UPDATED", actor.getMemberId(),
            AuditEventStatus.SUCCESS, "Employee number updated", "CLIENT", member.getMemberNo(),
            member.getSaccoId(), member.getStationId(), Map.of("employeeNumber", number == null ? "" : number));
    }
}
