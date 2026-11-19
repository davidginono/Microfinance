package com.sacco.mvp.accounting.statements.controller;

import com.sacco.mvp.accounting.statements.dto.StatementLayoutModels.*;
import com.sacco.mvp.accounting.statements.service.StatementLayoutService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/financial/statements/layout")
public class StatementLayoutController {
    private static final String ROOT="accounting/statements/";
    private final StatementLayoutService layouts;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @InitBinder("layoutGroup") void groupBinder(WebDataBinder binder){binder.setAllowedFields("version","index","requestKey","reason","name","nameSw","position","retained","accounts");binder.setAutoGrowCollectionLimit(2000);}
    @InitBinder("layoutNote") void noteBinder(WebDataBinder binder){binder.setAllowedFields("version","index","requestKey","reason","number","title","titleSw","basis","text","textSw","includeProfit","accounts");binder.setAutoGrowCollectionLimit(2000);}
    @InitBinder("layoutRemoval") void removalBinder(WebDataBinder binder){binder.setAllowedFields("version","index","requestKey","reason");}

    @GetMapping public String overview(@AuthenticationPrincipal AppUserPrincipal actor,Model model){model.addAttribute("layout",layouts.overview(actor));return ROOT+"layout";}
    @GetMapping("/groups/new") public String newGroup(@AuthenticationPrincipal AppUserPrincipal actor,Model model){return group(actor,-1,null,model);}
    @GetMapping("/groups/{index}/edit") public String group(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable int index,@RequestParam(required=false) Integer version,Model model){var editor=layouts.group(actor,index,version);model.addAttribute("layoutGroup",editor.form());model.addAttribute("accounts",editor.accounts());model.addAttribute("positions",editor.positions());return ROOT+"layout-group";}
    @GetMapping("/notes/new") public String newNote(@AuthenticationPrincipal AppUserPrincipal actor,Model model){return note(actor,-1,null,model);}
    @GetMapping("/notes/{index}/edit") public String note(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable int index,@RequestParam(required=false) Integer version,Model model){var editor=layouts.note(actor,index,version);model.addAttribute("layoutNote",editor.form());model.addAttribute("accounts",editor.accounts());return ROOT+"layout-note";}

    @PostMapping("/groups") public String saveGroup(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("layoutGroup") GroupForm form,BindingResult binding,Model model){
        if(!binding.hasErrors())try{return saved(layouts.saveGroup(actor,form));}catch(IllegalArgumentException error){reject(binding,error,true);}
        model.addAttribute("accounts",layouts.accounts(actor,true));model.addAttribute("positions",Math.max(1,Math.min(9,layouts.overview(actor).groups().size()+(form.getIndex()<0?1:0))));return ROOT+"layout-group";
    }
    @PostMapping("/notes") public String saveNote(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("layoutNote") NoteForm form,BindingResult binding,Model model){
        if(!binding.hasErrors())try{return saved(layouts.saveNote(actor,form));}catch(IllegalArgumentException error){reject(binding,error,false);}
        model.addAttribute("accounts",layouts.accounts(actor,false));return ROOT+"layout-note";
    }
    @GetMapping("/{kind}/{index}/remove") public String removal(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable String kind,@PathVariable int index,@RequestParam int version,Model model){
        var editor=layouts.removal(actor,group(kind),index,version);model.addAttribute("layoutRemoval",editor.form());model.addAttribute("removal",editor);model.addAttribute("kind",kind);return ROOT+"layout-remove";
    }
    @PostMapping("/{kind}/remove") public String remove(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable String kind,@ModelAttribute("layoutRemoval") Change form,BindingResult binding,Model model){
        boolean group=group(kind);
        if(!binding.hasErrors())try{layouts.remove(actor,group,form);return saved(true);}catch(IllegalArgumentException error){binding.reject(key(error));}
        try{model.addAttribute("removal",layouts.removal(actor,group,form.getIndex(),form.getVersion()));}catch(IllegalArgumentException stale){/* Keep the submitted version; never relabel a stale removal as a different record. */}
        model.addAttribute("kind",kind);return ROOT+"layout-remove";
    }
    private static String saved(boolean changed){return "redirect:/reports/financial/statements/layout?"+(changed?"saved=true":"unchanged=true");}
    private static boolean group(String kind){return switch(kind){case "groups"->true;case "notes"->false;default->throw new IllegalArgumentException("fs.error.selection");};}
    private static String key(IllegalArgumentException error){return error.getMessage()!=null&&error.getMessage().startsWith("fs.error.")?error.getMessage():"fs.error.setup";}
    private static void reject(BindingResult binding,IllegalArgumentException error,boolean group){
        String key=key(error);
        String field=switch(key){
            case "fs.error.reason"->"reason";
            case "fs.error.setupAccounts","fs.error.componentAccounts","fs.error.noteAccounts"->"accounts";
            case "fs.error.layoutGroupName"->group?"name":null;
            case "fs.error.layoutRetained"->group?"retained":null;
            case "fs.error.layoutPosition"->group?"position":null;
            case "fs.error.layoutNoteTitle"->group?null:"title";
            case "fs.error.layoutNoteNumber"->group?null:"number";
            case "fs.error.layoutNoteText"->group?null:"text";
            case "fs.error.noteProfit"->group?null:"includeProfit";
            default->null;
        };
        if(field==null)binding.reject(key);else binding.rejectValue(field,key);
    }
    @ExceptionHandler(IllegalArgumentException.class) public String error(IllegalArgumentException error,Model model,jakarta.servlet.http.HttpServletResponse response){response.setStatus(400);model.addAttribute("errorKey",key(error));return ROOT+"error";}
}
