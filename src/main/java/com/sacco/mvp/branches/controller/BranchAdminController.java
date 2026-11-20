package com.sacco.mvp.branches.controller;

import com.sacco.mvp.branches.service.BranchService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/branches")
@RequiredArgsConstructor
public class BranchAdminController {
    private final BranchService branches;
    private final AdminService admin;

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,
                       @RequestParam(defaultValue="id_asc") String sort,Model model) {
        model.addAttribute("register",branches.register(actor,page,sort));
        model.addAttribute("sort",sort);
        return "admin/branches";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal actor,Model model) {
        branches.requireCreate(actor);
        model.addAttribute("create",true);
        return "admin/branch-form";
    }

    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String branch,Model model) {
        var current=branches.branchForEdit(actor,branch);
        model.addAttribute("branch",current.id());
        model.addAttribute("location",current.location());
        return "admin/branch-form";
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String branch,@RequestParam String location,
                         Model model,RedirectAttributes redirect,jakarta.servlet.http.HttpServletResponse response) {
        try {
            branches.create(actor,branch,location);
            redirect.addFlashAttribute("branchSaved",true);
            return "redirect:/admin/branches";
        } catch(IllegalArgumentException | IllegalStateException invalid) {
            model.addAttribute("create",true);
            return error(branch,location,invalid,model,response);
        }
    }

    @PostMapping("/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String branch,@RequestParam String location,
                       Model model,RedirectAttributes redirect,jakarta.servlet.http.HttpServletResponse response) {
        try {
            branches.edit(actor,branch,location);
            redirect.addFlashAttribute("branchSaved",true);
            return "redirect:/admin/branches";
        } catch(IllegalArgumentException invalid) {
            return error(branch,location,invalid,model,response);
        }
    }

    private String error(String branch,String location,RuntimeException invalid,Model model,
                         jakarta.servlet.http.HttpServletResponse response) {
        model.addAttribute("branch",branch);
        model.addAttribute("location",location);
        String key=invalid.getMessage();
        model.addAttribute("branchError",key!=null && key.startsWith("branches.error.")?key:"branches.error.save");
        response.setStatus(400);
        return "admin/branch-form";
    }

    @GetMapping("/delegation")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal,'SACCO_REGISTRY_UPDATE') and @access.has(principal,'USER_ACCESS_UPDATE')")
    public String delegation(@RequestParam String institution,@RequestParam java.util.UUID user,Model model) {
        var target=admin.userAccess(institution,null,user);
        if(target.getLockedClaims().isEmpty() || target.isMemberAccess()) throw new org.springframework.security.access.AccessDeniedException("ICT Manager required");
        model.addAttribute("institution",institution);
        model.addAttribute("target",target);
        model.addAttribute("delegated",target.getClaims().contains(UserClaim.INSTITUTION_BRANCH_ADMIN));
        return "admin/branch-delegation";
    }

    @PostMapping("/delegation")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal,'SACCO_REGISTRY_UPDATE') and @access.has(principal,'USER_ACCESS_UPDATE')")
    public String delegate(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String institution,
                           @RequestParam java.util.UUID user,@RequestParam(defaultValue="false") boolean enabled,RedirectAttributes redirect) {
        try {
            admin.delegateBranchAdministration(actor,institution,user,enabled);
            redirect.addFlashAttribute("branchSaved",true);
        } catch(IllegalArgumentException invalid) {
            redirect.addFlashAttribute("delegationError",invalid.getMessage());
        }
        redirect.addAttribute("institution",institution);
        redirect.addAttribute("user",user);
        return "redirect:/admin/branches/delegation";
    }
}
