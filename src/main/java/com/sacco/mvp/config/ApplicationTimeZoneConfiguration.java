package com.sacco.mvp.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.ZoneId;
import java.util.TimeZone;

@Configuration(proxyBeanMethods = false)
public class ApplicationTimeZoneConfiguration {
    @Bean
    static BeanFactoryPostProcessor applicationTimeZone(Environment environment) {
        ZoneId zone = ZoneId.of(environment.getProperty("app.time-zone", "Africa/Nairobi"));
        return beanFactory -> TimeZone.setDefault(TimeZone.getTimeZone(zone));
    }
}
