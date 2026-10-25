package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.CycleForm;
import com.sacco.mvp.grouplending.service.GroupOrganizationService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/groups/cycles/{id}/organization")
public class GroupOrganizationController {
    private final GroupOrganizationService organizations;
    @InitBinder("cycleForm") public void binder(WebDataBinder b){b.setAutoGrowCollectionLimit(0);b.setAllowedFields("requestKey","policyId","expectedVersion","applicationDate","firstPaymentDate","tenureUnit","tenureValue","purpose",
        "organization.authorityId","organization.representativeId","organization.principal","organization.income","organization.expenses","organization.otherDebt","organization.business","organization.assessmentEvidence","organization.collateral","organization.collateralValue","organization.insurancePremium","organization.insuranceEvidence");}
    @GetMapping public String detail(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){return page(a,id,null,m);}
    @PostMapping public String save(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute CycleForm cycleForm,BindingResult errors,@RequestParam(defaultValue="save") String action,Model m){
        organizations.authorize(a,"submit".equals(action)?"SUBMIT":"UPDATE");
        if(!errors.hasErrors())try{
            if("useCurrentAuthority".equals(action)){organizations.useCurrentAuthority(a,id,cycleForm);return page(a,id,cycleForm,m);}
            if("submit".equals(action))organizations.submit(a,id,cycleForm);else if("save".equals(action))organizations.save(a,id,cycleForm);else throw new IllegalArgumentException("group.error.request");
            return "redirect:/finance/groups/cycles/"+id+"/organization";
        }catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.financialValidation");}
        return page(a,id,cycleForm,m);
    }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.beans.InvalidPropertyException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(RuntimeException e,Model m){m.addAttribute("groupError",e instanceof IllegalArgumentException x?error(x):"group.error.request");return "groups/error";}
    private String page(AppUserPrincipal a,UUID id,CycleForm f,Model m){var w=organizations.workspace(a,id,f);m.addAttribute("workspace",w);m.addAttribute("cycleForm",w.form());return "groups/organization";}
    private String error(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().matches("(?:group|recording|charges|prepayment)\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.form";}
}
