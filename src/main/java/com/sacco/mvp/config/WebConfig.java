package com.sacco.mvp.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AdminScopeInterceptor adminScopeInterceptor;
    private final MemberLocaleInterceptor memberLocaleInterceptor;

    public WebConfig(AdminScopeInterceptor adminScopeInterceptor,
                     MemberLocaleInterceptor memberLocaleInterceptor) {
        this.adminScopeInterceptor = adminScopeInterceptor;
        this.memberLocaleInterceptor = memberLocaleInterceptor;
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberLocaleInterceptor)
            .addPathPatterns("/app/**", "/admin/**", "/staff/**", "/manager/**", "/board/**",
                "/loan-officer/**", "/accountant/**", "/disbursement/**");
        registry.addInterceptor(adminScopeInterceptor)
            .addPathPatterns("/admin/**")
            .excludePathPatterns("/admin/scope", "/admin/scope/select");
    }
}
