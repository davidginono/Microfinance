package com.sacco.mvp.platform.onboarding.service;

import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.platform.onboarding.dto.AdminSetupForm;
import com.sacco.mvp.platform.onboarding.repository.AdminSetupRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.NameSignatureService;
import com.sacco.mvp.service.TanzaniaPhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminSetupService {
    public static final UUID BOOTSTRAP_ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final AdminSetupRepository records;
    private final PasswordEncoder passwords;
    private final NameSignatureService names;
    private final AuditService audit;

    @Transactional(timeout=10)
    public boolean provision(String password) {
        var account=records.lock(BOOTSTRAP_ID);
        if(account.isEmpty()||!account.get().required()||!account.get().hash().equals("!BOOTSTRAP_REQUIRED"))return false;
        requirePassword(password);
        records.provision(BOOTSTRAP_ID,passwords.encode(password));
        return true;
    }

    public void authorize(AppUserPrincipal actor) {
        if(actor==null||!actor.isPlatformIdentity()||!actor.isStaffSession()||!actor.isPlatformSetupRequired()
            ||!actor.getClaims().contains("PLATFORM_SETTINGS_UPDATE"))throw new AccessDeniedException("Platform setup unavailable");
    }

    @Transactional(timeout=10)
    public void complete(AppUserPrincipal actor,AdminSetupForm form) {
        authorize(actor);
        var account=records.lock(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Platform setup unavailable"));
        if(!account.required())throw new AccessDeniedException("Platform setup complete");
        if(form.getCurrentPassword()==null||!passwords.matches(form.getCurrentPassword(),account.hash()))throw invalid("currentPassword");
        requirePassword(form.getPassword());
        if(!form.getPassword().equals(form.getConfirmPassword()))throw invalid("confirmPassword");
        if(passwords.matches(form.getPassword(),account.hash()))throw invalid("reuse");
        String name;
        try {name=names.requireFullName(form.getFullName(),"");}catch(IllegalStateException e){throw invalid("name");}
        if(name.length()>255||name.codePoints().anyMatch(Character::isISOControl))throw invalid("name");
        String email=form.getEmail()==null?"":form.getEmail().trim().toLowerCase(Locale.ROOT);
        if(email.length()>160||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalid("email");
        String phone;
        try {phone=TanzaniaPhoneNumber.normalizeRequired(form.getPhone());}catch(IllegalStateException e){throw invalid("phone");}
        if(records.contactUsed(actor.getMemberId(),email,phone))throw invalid("contact");
        records.complete(actor.getMemberId(),name,email,phone,passwords.encode(form.getPassword()));
        audit.logEvent("PLATFORM_ADMIN",actor.getMemberId(),"INITIAL_SETUP",actor.getMemberId(),AuditEventStatus.SUCCESS,
            "Initial administrator setup completed","PLATFORM_ADMIN",actor.getMemberId().toString(),null,null,Map.of());
    }

    private static void requirePassword(String password) {
        if(password==null||password.length()<14||password.getBytes(StandardCharsets.UTF_8).length>72||password.isBlank())throw invalid("password");
    }
    private static IllegalArgumentException invalid(String field) {return new IllegalArgumentException("adminSetup.error."+field);}
}
