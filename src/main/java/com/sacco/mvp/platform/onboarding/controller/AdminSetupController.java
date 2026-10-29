package com.sacco.mvp.platform.onboarding.controller;

import com.sacco.mvp.platform.onboarding.dto.AdminSetupForm;
import com.sacco.mvp.platform.onboarding.service.AdminSetupService;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/system-admin/setup")
public class AdminSetupController {
    private final AdminSetupService setup;
    @InitBinder("setupForm") void binder(WebDataBinder binder) {binder.setAllowedFields("fullName","email","phone","currentPassword","password","confirmPassword");}
    @GetMapping public String form(@AuthenticationPrincipal AppUserPrincipal actor,Model model) {
        setup.authorize(actor);var form=new AdminSetupForm();form.setFullName(actor.getFullName());form.setEmail(actor.getEmail());
        model.addAttribute("setupForm",form);return "auth/admin-setup";
    }
    @PostMapping public String save(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("setupForm") AdminSetupForm form,
                                    BindingResult errors,Model model,HttpServletRequest request,HttpServletResponse response) {
        try {
            setup.complete(actor,form);
            new SecurityContextLogoutHandler().logout(request,response,null);
            return "redirect:/system-admin/login?setupComplete";
        }catch(IllegalArgumentException e) {
            form.setCurrentPassword("");form.setPassword("");form.setConfirmPassword("");
            String key=e.getMessage()!=null&&e.getMessage().matches("adminSetup\\.error\\.[A-Za-z]+")?e.getMessage():"adminSetup.error.password";
            String field=switch(key.substring("adminSetup.error.".length())) {
                case "name" -> "fullName";case "email" -> "email";case "phone" -> "phone";
                case "currentPassword" -> "currentPassword";case "confirmPassword" -> "confirmPassword";
                case "password","reuse" -> "password";default -> null;
            };
            if(field==null)model.addAttribute("setupError",key);else errors.rejectValue(field,key);
            return "auth/admin-setup";
        }
    }
}
