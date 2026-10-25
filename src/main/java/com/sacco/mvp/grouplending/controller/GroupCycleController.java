package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequestMapping("/finance/groups") @RequiredArgsConstructor
public class GroupCycleController {
    private final GroupCycleService cycles;
    private final GroupLendingService groups;
    private final ApplicationClock clock;
    private final MessageSource messages;
    @InitBinder("policyForm") public void policyBinder(WebDataBinder b){b.setAllowedFields("requestKey","productId","model","minMembers","maxMembers","maxPrincipal","maxGuarantee","maxActiveCycles","ownLoanGuarantors","enabled","policyReference","agreementReference");}
    @InitBinder("cycleForm") public void cycleBinder(WebDataBinder b){b.setAutoGrowCollectionLimit(50);b.setAllowedFields("requestKey","policyId","expectedVersion","applicationDate","firstPaymentDate","tenureUnit","tenureValue","purpose",
        "participants[*].clientId","participants[*].principal","participants[*].income","participants[*].expenses","participants[*].otherDebt","participants[*].guaranteeCapacity","participants[*].assessmentEvidence","participants[*].capacityEvidence","participants[*].collateral","participants[*].collateralValue","participants[*].insurancePremium","participants[*].insuranceEvidence");}
    @InitBinder("terminationForm") public void terminationBinder(WebDataBinder b){b.setAllowedFields("expectedVersion","reason");}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.beans.InvalidPropertyException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(RuntimeException e,Model m){m.addAttribute("groupError",e instanceof IllegalArgumentException argument?error(argument):"group.error.request");return "groups/error";}
    @GetMapping("/product-rules") public String policies(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("policies",cycles.policies(a,page));return "groups/policies";}
    @GetMapping("/product-rules/new") public String newPolicy(@AuthenticationPrincipal AppUserPrincipal a,Model m){cycles.authorize(a,"CONFIGURE");m.addAttribute("policyForm",new PolicyForm());return "groups/policy-form";}
    @PostMapping("/product-rules") public String policy(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute PolicyForm policyForm,BindingResult errors,Model m){
        cycles.authorize(a,"CONFIGURE");if(!errors.hasErrors())try{cycles.configure(a,policyForm);return "redirect:/finance/groups/product-rules";}
        catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.duplicate");}m.addAttribute("selectedProduct",cycles.selectedProduct(a,policyForm.getProductId()).orElse(null));return "groups/policy-form";
    }
    @GetMapping("/products") @ResponseBody public ResponseEntity<?> products(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cycles.products(a,search,page));}
    @GetMapping("/cycle-policies") @ResponseBody public ResponseEntity<?> policyChoices(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Locale locale){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cycles.policyChoices(a,search,page).stream().map(p->new com.sacco.mvp.grouplending.dto.GroupLendingDtos.Choice(p.id(),p.product()+" · "+messages.getMessage("group.model."+p.model(),null,locale))).toList());}
    @GetMapping("/{group}/member-choices") @ResponseBody public ResponseEntity<?> memberChoices(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID group,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cycles.memberChoices(a,group,search,page));}
    @GetMapping("/{group}/cycles") public String list(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID group,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("group",groups.detail(a,group));m.addAttribute("cycles",cycles.list(a,group,page));return "groups/cycles";}
    @GetMapping("/{group}/cycles/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID group,Model m){cycles.authorize(a,"CREATE");var f=new CycleForm();f.setApplicationDate(clock.today());f.getParticipants().add(new ParticipantForm());f.getParticipants().add(new ParticipantForm());m.addAttribute("cycleForm",f);editor(a,group,m);return "groups/cycle-form";}
    @PostMapping("/{group}/cycles") public String create(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID group,@ModelAttribute CycleForm cycleForm,BindingResult errors,@RequestParam(defaultValue="save") String action,Model m){
        cycles.authorize(a,"CREATE");if(rows(cycleForm,action)){editor(a,group,m);return "groups/cycle-form";}
        if(!errors.hasErrors())try{UUID id=cycles.create(a,group,cycleForm);return "redirect:/finance/groups/cycles/"+id;}
        catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.duplicate");}editor(a,group,m);return "groups/cycle-form";
    }
    @GetMapping("/cycles/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){
        var c=cycles.detail(a,id);m.addAttribute("cycle",c);m.addAttribute("policy",cycles.policy(a,c.policyId()));
        if("DRAFT".equals(c.state())){var f=c.form();f.setExpectedVersion(c.version());m.addAttribute("cycleForm",f);editor(a,c.groupId(),m);return "groups/cycle-form";}
        var participants=cycles.participants(a,id);m.addAttribute("participants",participants);m.addAttribute("termination",cycles.termination(a,id).orElse(null));m.addAttribute("canWithdraw","SUBMITTED".equals(c.state()) && participants.stream().noneMatch(p->Set.of("DISBURSED","PAR","DEFAULTED","WRITTEN_OFF","PAID","DISBURSEMENT_REVERSED").contains(p.loanStatus())));m.addAttribute("hasFundedLoans",participants.stream().anyMatch(p->Set.of("DISBURSED","PAR","DEFAULTED").contains(p.loanStatus())));return "groups/cycle-details";
    }
    @PostMapping("/cycles/{id}") public String save(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute CycleForm cycleForm,BindingResult errors,
        @RequestParam(defaultValue="save") String action,Model m){
        cycles.authorize(a,action.equals("submit")?"SUBMIT":"UPDATE");if(!errors.hasErrors())try{
            if(rows(cycleForm,action)){var c=cycles.detail(a,id);m.addAttribute("cycle",c);editor(a,c.groupId(),m);return "groups/cycle-form";}
            if(action.equals("submit"))cycles.submit(a,id,cycleForm);else if(action.equals("save"))cycles.save(a,id,cycleForm);else throw new IllegalArgumentException("group.error.request");
            return "redirect:/finance/groups/cycles/"+id;
        }catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.financialValidation");}
        var c=cycles.detail(a,id);m.addAttribute("cycle",c);editor(a,c.groupId(),m);return "groups/cycle-form";
    }
    @GetMapping("/cycles/{id}/withdraw") public String withdraw(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){
        cycles.authorize(a,"SUBMIT");var c=cycles.detail(a,id);if("CANCELLED".equals(c.state()))return "redirect:/finance/groups/cycles/"+id;
        var f=new TerminationForm();f.setExpectedVersion(c.version());m.addAttribute("terminationForm",f);withdrawalPage(c,m);return "groups/terminate";
    }
    @PostMapping("/cycles/{id}/withdraw") public String withdraw(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute TerminationForm terminationForm,BindingResult errors,Model m){
        cycles.authorize(a,"SUBMIT");if(!errors.hasErrors())try{cycles.withdraw(a,id,terminationForm);return "redirect:/finance/groups/cycles/"+id;}
        catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.fundedCycle");}
        withdrawalPage(cycles.detail(a,id),m);return "groups/terminate";
    }
    private void withdrawalPage(com.sacco.mvp.grouplending.dto.GroupCycleDtos.Cycle c,Model m){m.addAttribute("cycle",c);m.addAttribute("terminationGroup",c.groupName());m.addAttribute("terminationAction","/finance/groups/cycles/"+c.id()+"/withdraw");m.addAttribute("terminationBack","/finance/groups/cycles/"+c.id());m.addAttribute("terminationTitle","group.withdraw");}
    private void editor(AppUserPrincipal a,UUID group,Model m){m.addAttribute("group",groups.detail(a,group));m.addAttribute("policies",cycles.policies(a,0));
        if(m.getAttribute("cycleForm") instanceof CycleForm form){m.addAttribute("memberLabels",cycles.memberLabels(a,group,form));if(form.getPolicyId()!=null)m.addAttribute("selectedPolicy",cycles.policy(a,form.getPolicyId()));}}
    private boolean rows(CycleForm form,String action){
        if("addRow".equals(action)){if(form.getParticipants()==null || form.getParticipants().size()>=50)throw new IllegalArgumentException("group.error.members");form.getParticipants().add(new ParticipantForm());return true;}
        if(action.startsWith("removeRow:")){int index;try{index=Integer.parseInt(action.substring(10));}catch(NumberFormatException e){throw new IllegalArgumentException("group.error.request");}
            if(form.getParticipants()==null || index<0 || index>=form.getParticipants().size())throw new IllegalArgumentException("group.error.request");form.getParticipants().remove(index);return true;}
        return false;
    }
    private String error(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().matches("(?:group|recording|charges|prepayment)\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.form";}
}
