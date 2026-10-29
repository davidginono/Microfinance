package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.clients.onboarding.controller.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** Real JSP/HTTP/multipart flow with real services and PostgreSQL, using only synthetic test actors. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
class ClientOnboardingRenderedPostgresTest {
    @TempDir Path temporary;
    @Test void clientAndStaffCompleteTheFlowThroughRenderedForms()throws Exception {
        var domain=new ClientOnboardingPostgresTest();domain.start();var tomcat=new Tomcat();
        var web=new AnnotationConfigWebApplicationContext();
        try {
            domain.fixture();var owner=domain.owner(domain.f.client);domain.service.saveNotice(domain.f.actor,domain.notice());
            tomcat.setBaseDir(temporary.toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
            var context=tomcat.addContext("",Path.of("src/main/webapp").toAbsolutePath().toString());context.setParentClassLoader(getClass().getClassLoader());Tomcat.initWebappDefaults(context);context.addServletContainerInitializer(new JasperInitializer(),null);
            web.setParent(domain.f.context);web.setServletContext(context.getServletContext());web.register(WebConfig.class);
            web.addBeanFactoryPostProcessor(factory->{factory.registerSingleton("fixtureOwner",owner);factory.registerSingleton("fixtureReviewer",domain.f.operator("B1"));});
            web.refresh();context.getServletContext().setAttribute(org.springframework.web.context.WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,web);
            var servlet=Tomcat.addServlet(context,"mvc",new DispatcherServlet(web));servlet.setLoadOnStartup(1);servlet.setMultipartConfigElement(new MultipartConfigElement(temporary.toString(),6*1024*1024,7*1024*1024,0));context.addServletMappingDecoded("/","mvc");
            var filter=new org.apache.tomcat.util.descriptor.web.FilterDef();filter.setFilterName("security");filter.setFilter(new DelegatingFilterProxy("springSecurityFilterChain",web));context.addFilterDef(filter);
            var mapping=new org.apache.tomcat.util.descriptor.web.FilterMap();mapping.setFilterName("security");mapping.addURLPattern("/*");context.addFilterMap(mapping);
            tomcat.start();String base="http://127.0.0.1:"+tomcat.getConnector().getLocalPort();
            var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
            String form=get(client,base+"/app/onboarding/edit",false);
            assertThat(form).contains("My profile","Privacy notice","Synthetic English privacy notice").doesNotContain("name=\"profile.lawfulBasis\"","name=\"profile.retentionReference\"");
            var values=hidden(form);values.put("action","DRAFT");values.put("profile.identityType","NATIONAL_ID");values.put("profile.issuingCountry","TZ");values.put("profile.identityNumber","TZ-RENDER-1234");values.put("profile.birthDate","1990-01-01");values.put("profile.identityNoExpiry","true");values.put("profile.address","Synthetic address");values.put("profile.activity","Synthetic shop");values.put("profile.activityAddress","Synthetic market");values.put("profile.sourceOfFunds","Synthetic sales");
            post(client,base+"/app/onboarding/edit",values,false);
            String draft=get(client,base+"/app/onboarding",false);assertThat(draft).contains("Add document","Draft");
            for(String category:List.of("IDENTITY","ADDRESS","ACTIVITY")) {
                var fields=hidden(get(client,base+"/app/onboarding/documents/new",false));fields.put("category",category);
                String boundary="kyc-"+UUID.randomUUID();var bytes=new ByteArrayOutputStream();
                for(var field:fields.entrySet())bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
                bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"evidence.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));bytes.write(domain.image);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                var response=client.send(HttpRequest.newBuilder(URI.create(base+"/app/onboarding/documents/new")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(302);
            }
            form=get(client,base+"/app/onboarding/edit",false);var submitted=hidden(form);values.forEach((key,value)->{if(key.startsWith("profile."))submitted.put(key,value);});submitted.put("action","SUBMITTED");submitted.put("noticeAcknowledged","true");submitted.put("declarationAccepted","true");post(client,base+"/app/onboarding/edit",submitted,false);
            assertThat(get(client,base+"/app/onboarding",false)).contains("awaiting review").doesNotContain("href=\"/app/onboarding/edit\"");
            String reviewUrl=base+"/finance/clients/onboarding/"+domain.f.client+"/review";var review=hidden(get(client,reviewUrl,true));review.put("kind","RETURN");review.put("reason","INTERNAL-SCREENING-SECRET");review.put("clientMessage","Please correct your home address.");post(client,reviewUrl,review,true);
            String returned=get(client,base+"/app/onboarding",false);assertThat(returned).contains("Please correct your home address.").doesNotContain("INTERNAL-SCREENING-SECRET");
            var correction=hidden(get(client,base+"/app/onboarding/edit",false));values.forEach((key,value)->{if(key.startsWith("profile."))correction.put(key,value);});correction.put("profile.address","Synthetic corrected address");correction.put("action","SUBMITTED");correction.put("noticeAcknowledged","true");correction.put("declarationAccepted","true");post(client,base+"/app/onboarding/edit",correction,false);
            var verification=hidden(get(client,reviewUrl,true));verification.put("kind","VERIFY");verification.put("reason","Synthetic independent checks complete");
            for(String check:List.of("identityChecked","addressChecked","activityChecked","contactChecked","duplicatesChecked"))verification.put(check,"true");
            verification.put("screening","CLEARED");verification.put("screeningReference","Synthetic dated manual screening evidence");verification.put("policyReference","Approved synthetic policy");verification.put("checkedOn",domain.day.toString());verification.put("reviewDue",domain.day.plusMonths(6).toString());post(client,reviewUrl,verification,true);
            String verified=get(client,base+"/app/onboarding",false);assertThat(verified).contains("Verified","Synthetic corrected address","Loan assessment and approval are separate.").doesNotContain("Please correct your home address.");
            assertThat(get(client,base+"/app/onboarding/versions/2",false)).contains("Please correct your home address.");
            assertThat(get(client,base+"/finance/clients/onboarding/notice",true)).contains("Publish notice","Synthetic English privacy notice");
            assertThat(get(client,base+"/app/onboarding/edit?lang=sw",false)).contains("Taarifa zangu","Taarifa ya faragha ya majaribio iliyoidhinishwa.");get(client,base+"/app/onboarding?lang=en",false);
            // Optional local visual QA window; never enabled in the normal test suite.
            int seconds=Integer.getInteger("microfinance.onboarding.browser-seconds",0);
            if(seconds>0){System.out.println("ONBOARDING_BROWSER_QA="+base);for(int elapsed=0;elapsed<Math.min(seconds,600);elapsed++)Thread.sleep(1000);}
        } finally {tomcat.stop();tomcat.destroy();web.close();domain.stop();}
    }
    static String get(HttpClient client,String url,boolean staff)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60));if(staff)request.header("X-Onboarding-Test-Actor","reviewer");
        var response=client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as("GET %s: %s",url,response.body()).isEqualTo(200);return response.body();
    }
    static void post(HttpClient client,String url,Map<String,String> fields,boolean staff)throws Exception {
        var data=new StringJoiner("&");fields.forEach((key,value)->data.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/x-www-form-urlencoded");if(staff)request.header("X-Onboarding-Test-Actor","reviewer");
        var response=client.send(request.POST(HttpRequest.BodyPublishers.ofString(data.toString())).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as("POST %s: %s",url,response.body()).isEqualTo(302);
    }
    static Map<String,String> hidden(String html) {
        var values=new LinkedHashMap<String,String>();var inputs=Pattern.compile("<input[^>]*>").matcher(html);
        while(inputs.find()){String input=inputs.group();if(!input.contains("type=\"hidden\""))continue;var name=Pattern.compile("name=\"([^\"]+)\"").matcher(input);var value=Pattern.compile("value=\"([^\"]*)\"").matcher(input);if(name.find()&&value.find())values.put(name.group(1),value.group(1));}return values;
    }
    @TestConfiguration @EnableWebMvc @EnableWebSecurity @Import({ClientOnboardingController.class,OnboardingController.class})
    static class WebConfig implements WebMvcConfigurer {
        @Bean("access") AccessControlService access(){return new AccessControlService();}
        @Bean("authz") com.sacco.mvp.security.AuthzService authz(){return org.mockito.Mockito.mock(com.sacco.mvp.security.AuthzService.class);}
        @Bean ResourceBundleMessageSource messageSource(){var source=new ResourceBundleMessageSource();source.setBasename("messages");source.setDefaultEncoding("UTF-8");return source;}
        @Bean InternalResourceViewResolver viewResolver(){return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
        @Bean StandardServletMultipartResolver multipartResolver(){return new StandardServletMultipartResolver();}
        @Bean org.springframework.web.servlet.i18n.SessionLocaleResolver localeResolver(){var resolver=new org.springframework.web.servlet.i18n.SessionLocaleResolver();resolver.setDefaultLocale(Locale.ENGLISH);return resolver;}
        @Bean SecurityFilterChain security(HttpSecurity http,AppUserPrincipal fixtureOwner,AppUserPrincipal fixtureReviewer)throws Exception {
            var actors=new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
                var actor="reviewer".equals(request.getHeader("X-Onboarding-Test-Actor"))?fixtureReviewer:fixtureOwner;
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));chain.doFilter(request,response);
            }};
            return http.authorizeHttpRequests(auth->auth.dispatcherTypeMatchers(DispatcherType.FORWARD,DispatcherType.ERROR).permitAll().anyRequest().authenticated()).addFilterBefore(actors,AnonymousAuthenticationFilter.class).build();
        }
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry){for(String path:List.of("css","js","fonts","images"))registry.addResourceHandler("/"+path+"/**").addResourceLocations(Path.of("src/main/resources/static",path).toAbsolutePath().toUri().toString()+"/");}
        @Override public void addInterceptors(InterceptorRegistry registry){var locale=new org.springframework.web.servlet.i18n.LocaleChangeInterceptor();locale.setParamName("lang");registry.addInterceptor(locale);registry.addInterceptor(new HandlerInterceptor(){@Override public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view){if(view!=null){var actor=(AppUserPrincipal)SecurityContextHolder.getContext().getAuthentication().getPrincipal();view.addObject("currentMember",com.sacco.mvp.web.view.CurrentUserView.builder().memberNo(actor.getMemberNo()).staffNo(actor.getStaffNo()).fullName(actor.getFullName()).email(actor.getEmail()).position(actor.getPosition()).memberAccess(actor.isMemberAccess()).staffSession(actor.isStaffSession()).build());}}});}
    }
}
