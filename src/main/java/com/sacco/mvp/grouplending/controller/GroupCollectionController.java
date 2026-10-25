package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.Form;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.Row;
import com.sacco.mvp.grouplending.service.GroupCollectionService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequestMapping("/finance/groups/cycles/{cycle}/receipts") @RequiredArgsConstructor
public class GroupCollectionController {
    private final GroupCollectionService collections;
    private final ApplicationClock clock;
    @InitBinder("collectionForm") public void bind(WebDataBinder binder){binder.setAutoGrowCollectionLimit(50);binder.setAllowedFields("requestKey","effectiveDate","moneyAccountId","remitter","reference","evidence","previewHash","rows[*].loanId","rows[*].payerId","rows[*].amount","rows[*].description");}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.beans.InvalidPropertyException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(Model model){model.addAttribute("groupError","group.error.request");return "groups/error";}
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID cycle,Model model){
        collections.authorize(actor,"POST");var form=new Form();form.setEffectiveDate(clock.today());model.addAttribute("collectionForm",form);model.addAttribute("receiptNumber",collections.number(actor,cycle,form));context(actor,cycle,form,model);return "groups/collection-form";
    }
    @GetMapping("/choices/{kind}") @ResponseBody public ResponseEntity<?> choices(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID cycle,@PathVariable String kind,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(collections.choices(actor,cycle,kind,search,page));}
    @PostMapping public String submit(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID cycle,@ModelAttribute("collectionForm") Form form,BindingResult errors,@RequestParam(defaultValue="preview") String action,Model model){
        collections.authorize(actor,"POST");
        if(action.equals("addRow")){if(form.getRows().size()>=50)errors.reject("group.error.members");else form.getRows().add(new Row());form.setPreviewHash("");}
        else if(action.startsWith("removeRow:")){int index=Integer.parseInt(action.substring(10));if(index<0 || index>=form.getRows().size())throw new IllegalArgumentException();form.getRows().remove(index);form.setPreviewHash("");}
        else if(!errors.hasErrors())try{
            if(action.equals("post")){var saved=collections.post(actor,cycle,form);return "redirect:/finance/vouchers/receipts/"+saved.header().id();}
            if(!action.equals("preview"))throw new IllegalArgumentException("group.error.request");
            var preview=collections.preview(actor,cycle,form);form.setPreviewHash(preview.hash());model.addAttribute("preview",preview);model.addAttribute("receiptNumber",preview.number());
        }catch(IllegalArgumentException e){errors.reject(error(e));form.setPreviewHash("");}catch(DataIntegrityViolationException e){errors.reject("group.error.collectionConflict");form.setPreviewHash("");}
        context(actor,cycle,form,model);return "groups/collection-form";
    }
    private void context(AppUserPrincipal actor,UUID cycle,Form form,Model model){model.addAttribute("groupCycle",collections.cycle(actor,cycle));model.addAttribute("labels",collections.selected(actor,cycle,form));}
    private String error(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().matches("(?:group|recording|charges|prepayment|repayment|voucher)\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.form";}
}
