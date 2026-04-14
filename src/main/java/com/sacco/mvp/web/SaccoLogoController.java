package com.sacco.mvp.web;

import com.sacco.mvp.service.SaccoLogoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class SaccoLogoController {
    private final SaccoLogoStorageService saccoLogoStorageService;

    @GetMapping("/branding/saccos/{saccoId}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String saccoId) {
        try {
            SaccoLogoStorageService.LogoResource resource = saccoLogoStorageService.load(saccoId);
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(resource.contentType())
                .body(resource.content());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
