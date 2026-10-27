package com.sacco.mvp.grouplending.controller;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.ResponseForm;
import com.sacco.mvp.grouplending.service.GroupCoverageService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/app/group-coverage")
public class GroupCoverageClientController {
    private final GroupCoverageService coverage;
    @ModelAttribute public void privateResponse(jakarta.servlet.http.HttpServletResponse r){r.setHeader("Cache-Control","no-store");}
    @InitBinder("coverageResponse") public void bind(WebDataBinder b){b.setAllowedFields("requestKey","expectedSequence","kind","reason","amount","promiseOn","confirmed");}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("ownCoverage",true);m.addAttribute("cases",coverage.list(a,null,page,true));return "groups/coverage-list";}
    @GetMapping("/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int eventPage,@RequestParam(defaultValue="0") int paymentPage,Model m){var d=coverage.detail(a,id,eventPage,paymentPage,true);m.addAttribute("coverage",d);m.addAttribute("coverageActions",coverage.actions(a,true));if(!m.containsAttribute("coverageResponse")){var f=new ResponseForm();f.setExpectedSequence(d.header().sequence());m.addAttribute("coverageResponse",f);}return "groups/coverage-detail";}
    @PostMapping("/{id}/respond") public String respond(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("coverageResponse") ResponseForm f,BindingResult errors,Model m){if(!errors.hasErrors())try{coverage.respond(a,id,f);return "redirect:/app/group-coverage/"+id;}catch(IllegalArgumentException e){errors.reject(GroupCoverageController.key(e));}return detail(a,id,0,0,m);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("groupError",GroupCoverageController.key(e));return "groups/error";}
}
