package com.sacco.mvp.clients.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class OnboardingArchitectureTest {
    @Test void onlyOwningServicesDependOnRepositoriesAndControllersNeverUsePersistence() throws Exception {
        var resources=new PathMatchingResourcePatternResolver().getResources("classpath*:com/sacco/mvp/clients/onboarding/**/*.class");
        var violations=new ArrayList<String>();
        for(var resource:resources){
            String path=resource.getURL().toString();String name=path.substring(path.indexOf("com/sacco/mvp/clients/onboarding/"),path.length()-6).replace('/','.');
            Class<?> type=Class.forName(name,false,getClass().getClassLoader());
            if(type.getName().endsWith("Test") || type.isSynthetic())continue;
            for(var field:type.getDeclaredFields()){
                Class<?> dependency=field.getType();boolean repository=dependency.isAnnotationPresent(Repository.class)
                    || org.springframework.data.repository.Repository.class.isAssignableFrom(dependency);
                boolean raw=org.springframework.jdbc.core.JdbcOperations.class.isAssignableFrom(dependency)
                    || org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations.class.isAssignableFrom(dependency)
                    || jakarta.persistence.EntityManager.class.isAssignableFrom(dependency);
                if(repository && (!type.isAnnotationPresent(Service.class) || !dependency.getPackageName().startsWith("com.sacco.mvp.clients.onboarding.repository")))
                    violations.add(type.getName()+" -> "+dependency.getName());
                if(raw && !type.isAnnotationPresent(Repository.class))violations.add(type.getName()+" -> raw persistence "+dependency.getName());
            }
        }
        assertThat(resources).isNotEmpty();assertThat(violations).isEmpty();
    }
}
