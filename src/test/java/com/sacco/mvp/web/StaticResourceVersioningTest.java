package com.sacco.mvp.web;

import com.sacco.mvp.config.AdminScopeInterceptor;
import com.sacco.mvp.config.MemberLocaleInterceptor;
import com.sacco.mvp.config.WebConfig;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaticResourceVersioningTest {
    @Test
    void actualConfigurationRewritesAndServesContentVersionedStylesheets() throws Exception {
        byte[] css = new ClassPathResource("static/css/shell.css").getContentAsByteArray();
        String expected = "/css/shell-" + DigestUtils.md5DigestAsHex(css) + ".css";
        new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
            .withUserConfiguration(WebConfig.class, TestConfig.class, AssetLinkController.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean(ResourceUrlEncodingFilter.class)).build();
                String url = mvc.perform(get("/asset-test")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
                assertThat(url).isEqualTo(expected);
                assertThat(mvc.perform(get(url)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray()).isEqualTo(css);
                mvc.perform(get("/css/shell-00000000000000000000000000000000.css"))
                    .andExpect(status().isNotFound());
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean AdminScopeInterceptor adminScopeInterceptor() { return mock(AdminScopeInterceptor.class); }
        @Bean MemberLocaleInterceptor memberLocaleInterceptor() { return mock(MemberLocaleInterceptor.class); }
    }

    @RestController
    static class AssetLinkController {
        @GetMapping("/asset-test")
        String link(HttpServletResponse response) {
            return response.encodeURL("/css/shell.css");
        }
    }
}
