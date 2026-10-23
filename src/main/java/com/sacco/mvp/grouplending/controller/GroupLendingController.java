package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.service.GroupLendingService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequestMapping("/finance/groups") @RequiredArgsConstructor
public class GroupLendingController {
    private final GroupLendingService service;
    private final ApplicationClock clock;
    @InitBinder("groupForm") public void groupBinder(WebDataBinder binder){binder.setAllowedFields("requestKey","name","legalForm","evidence");}
    @InitBinder("memberForm") public void memberBinder(WebDataBinder binder){binder.setAllowedFields("requestKey","clientId","office","startsOn","evidence");}
    @InitBinder("endForm") public void endBinder(WebDataBinder binder){binder.setAllowedFields("date","reason");}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IllegalArgumentException error,Model model){model.addAttribute("groupError",error(error));return "groups/error";}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("groups",service.list(actor,search,sort,page));model.addAttribute("search",search);model.addAttribute("sort",sort);return "groups/register";
    }
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal actor,Model model) {
        service.authorize(actor,"CREATE");model.addAttribute("groupForm",new GroupForm());return "groups/create";
    }
    @PostMapping public String create(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute GroupForm groupForm,BindingResult errors) {
        service.authorize(actor,"CREATE");if(!errors.hasErrors())try{return "redirect:/finance/groups/"+service.create(actor,groupForm);}
        catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.duplicate");}
        return "groups/create";
    }
    @GetMapping("/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="startsOn_desc") String sort,Model model) {
        var form=new MemberForm();form.setStartsOn(clock.today());model.addAttribute("memberForm",form);details(actor,id,page,sort,model);return "groups/details";
    }
    @PostMapping("/{id}/members") public String member(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
        @ModelAttribute MemberForm memberForm,BindingResult errors,Model model) {
        service.authorize(actor,"UPDATE");if(!errors.hasErrors())try{service.addMember(actor,id,memberForm);return "redirect:/finance/groups/"+id;}
        catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.duplicate");}
        details(actor,id,0,"startsOn_desc",model);service.selectedClient(actor,memberForm.getClientId()).ifPresent(c->model.addAttribute("selectedClient",c));return "groups/details";
    }
    @GetMapping("/{id}/members/{member}") public String endForm(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable UUID member,Model model) {
        var form=new EndForm();form.setDate(clock.today());model.addAttribute("endForm",form);endContext(actor,id,member,model);return "groups/end-membership";
    }
    @PostMapping("/{id}/members/{member}/end") public String end(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
        @PathVariable UUID member,@ModelAttribute EndForm endForm,BindingResult errors,Model model) {
        service.authorize(actor,"UPDATE");if(!errors.hasErrors())try{service.endMember(actor,id,member,endForm.getDate(),endForm.getReason());return "redirect:/finance/groups/"+id;}
        catch(IllegalArgumentException e){errors.reject(error(e));}
        endContext(actor,id,member,model);return "groups/end-membership";
    }
    @PostMapping("/{id}/status") public String status(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
        @RequestParam int version,@RequestParam boolean active,org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        try{service.status(actor,id,version,active);}catch(IllegalArgumentException e){flash.addFlashAttribute("groupError",error(e));}
        return "redirect:/finance/groups/"+id;
    }
    @GetMapping("/clients") @ResponseBody public ResponseEntity<List<Choice>> clients(@AuthenticationPrincipal AppUserPrincipal actor,
        @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.clients(actor,search,page));
    }
    private void details(AppUserPrincipal actor,UUID id,int page,String sort,Model model) {
        model.addAttribute("group",service.detail(actor,id));model.addAttribute("members",service.members(actor,id,page,sort));model.addAttribute("sort",sort);model.addAttribute("today",clock.today());
    }
    private void endContext(AppUserPrincipal actor,UUID id,UUID member,Model model){model.addAttribute("group",service.detail(actor,id));model.addAttribute("membership",service.membership(actor,id,member));}
    private String error(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.form";}
}
