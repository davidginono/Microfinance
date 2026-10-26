package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.service.GroupSharedStatementService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor
public class GroupSharedStatementController {
    private final GroupSharedStatementService statements;
    @GetMapping("/finance/groups/cycles/{id}/allocations") public String statement(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model model){model.addAttribute("statement",statements.statement(a,id,false));return "groups/shared-balances";}
}
