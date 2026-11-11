package com.sacco.mvp.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final MemberLocaleInterceptor memberLocaleInterceptor;

    public WebConfig(MemberLocaleInterceptor memberLocaleInterceptor) {
        this.memberLocaleInterceptor = memberLocaleInterceptor;
    }

    @Bean
    public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new ResourceUrlEncodingFilter();
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
        var loginLocale=new org.springframework.web.servlet.i18n.LocaleChangeInterceptor();
        loginLocale.setParamName("lang");loginLocale.setHttpMethods("GET");
        registry.addInterceptor(loginLocale).addPathPatterns("/","/login","/system-admin/login");
        registry.addInterceptor(memberLocaleInterceptor)
            .addPathPatterns("/admin/**", "/staff/**",
                "/disbursement/**", "/repayments", "/repayments/**",
                "/finance/payroll", "/finance/payroll/**", "/finance/groups", "/finance/groups/**");
    }
}
