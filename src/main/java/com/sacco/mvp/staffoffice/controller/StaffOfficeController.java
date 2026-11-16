package com.sacco.mvp.staffoffice.controller;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.service.StaffOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

@Controller @RequiredArgsConstructor
@RequestMapping("/staff/offices")
public class StaffOfficeController {
    private final StaffOfficeService offices;
    @GetMapping("/{office}/dashboard")
    public String formerDashboard(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable String office,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        offices.requireOffice(actor,office);
        return "redirect:/staff/dashboard";
    }
    @GetMapping("/{office}/{section:queue|reports}")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable String office,@PathVariable String section,
            @RequestParam(defaultValue="") String source,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String sort,@RequestParam(defaultValue="0") int page,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try {model.addAttribute("officeView",offices.read(actor,office,section,source,search,sort,page));}
        catch(IllegalArgumentException invalid) {throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,null,invalid);}
        model.addAttribute("search",search);model.addAttribute("sort",sort.isBlank()?"number":sort);
        return "staff/office";
    }
}
