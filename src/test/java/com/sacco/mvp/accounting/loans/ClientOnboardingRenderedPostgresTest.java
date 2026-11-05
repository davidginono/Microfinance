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
            domain.fixture();domain.owner(domain.f.client);domain.f.jdbc.update("update members set client_portal_enabled=true,password_hash='test-only' where id=?",domain.f.client);domain.f.context.getBean(com.sacco.mvp.service.MemberDirectoryService.class).find(domain.f.client).orElseThrow().setClientPortalEnabled(true);var owner=new AppUserPrincipal(domain.f.context.getBean(com.sacco.mvp.service.MemberDirectoryService.class).find(domain.f.client).orElseThrow(),Set.of(com.sacco.mvp.domain.UserClaim.MEMBER_LOANS_CREATE),false);
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
            var blocked=client.send(HttpRequest.newBuilder(URI.create(base+"/app/dashboard")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(blocked.statusCode()).isEqualTo(302);assertThat(blocked.headers().firstValue("Location")).contains("/app/onboarding");
            assertThat(get(client,base+"/app/onboarding",false)).contains("My profile","Bio data","Complete your profile and wait for verification").doesNotContain("Loan request","Complete profile","/app/onboarding/loan");
            String form=get(client,base+"/app/onboarding/edit",false);
            assertThat(form).contains("Do you have a business?","name=\"profile.hasBusiness\"","Profile status","data-resource-status=\"DRAFT\"","class=\"kyc-field-hint\"").doesNotContain("data-message-type=\"info\"");
            assertThat(form).contains("My profile","Privacy notice","We protect your personal information","Bio data","Business details","Spouse details","Bank details","Attachments and Documents","name=\"profile.dailySales\"","name=\"profile.bankAccountNumber\"").doesNotContain("id=\"profile-photo\"","name=\"profile.lawfulBasis\"","name=\"profile.retentionReference\"", "Onboarding notice", "kyc.error.noticeUnavailable");
            assertThat(form).contains("name=\"profile.incomeActivities\"", "value=\"BUSINESS\"", "value=\"EMPLOYMENT\"");
            assertThat(form.indexOf("name=\"profile.incomeActivities\"")).isLessThan(form.indexOf("name=\"profile.otherIncomeSources\""));
            assertThat(form).contains("data-profile-wizard", "/js/client-profile-wizard.js", "data-personal=\"Personal details\"", "data-review=\"Review and send\"").doesNotContain("data-profile-tabs", "data-resource-details-page");
            assertThat(form.indexOf("id=\"profile-documents\"")).isLessThan(form.indexOf("name=\"declarationAccepted\""));
            assertThat(form.indexOf("name=\"noticeAcknowledged\"")).isLessThan(form.indexOf("id=\"profile-documents\""));
            assertThat(form).contains("data-profile-confirmations", "data-profile-declaration", "data-profile-document-list", "/js/client-profile-documents.js");
            var values=hidden(form);values.put("action","DRAFT");values.put("profile.identityType","NATIONAL_ID");values.put("profile.issuingCountry","TZ");values.put("profile.identityNumber","TZ-RENDER-1234");values.put("profile.birthDate","1990-01-01");values.put("profile.identityNoExpiry","true");values.put("profile.address","Synthetic address");values.put("profile.incomeActivities","BUSINESS");values.put("profile.activityAddress","Synthetic market");values.put("profile.sourceOfFunds","Synthetic sales");values.put("profile.maritalStatus","SINGLE");values.put("profile.hasBusiness","true");values.put("profile.businessName","Rendered synthetic shop");values.put("profile.dailySales","12345.67");values.put("profile.bankAccountNumber","9876543210");
            post(client,base+"/app/onboarding/edit",values,false);
            String draft=get(client,base+"/app/onboarding",false);assertThat(draft).contains("Add document","Send for review","Rendered synthetic shop");
            assertThat(draft).contains("data-profile-add-documents");
            var missingDocuments=hidden(draft);values.forEach((key,value)->{if(key.startsWith("profile."))missingDocuments.put(key,value);});missingDocuments.put("action","SUBMITTED");missingDocuments.put("noticeAcknowledged","true");missingDocuments.put("declarationAccepted","true");
            var missingBody=new StringJoiner("&");missingDocuments.forEach((key,value)->missingBody.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
            var missingResponse=client.send(HttpRequest.newBuilder(URI.create(base+"/app/onboarding/edit")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(missingBody.toString())).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(missingResponse.statusCode()).isEqualTo(200);assertThat(missingResponse.body()).contains("upload the required files under Attachments and Documents", "data-workspace-form-error", "Rendered synthetic shop").doesNotContain("privacy evidence");
            for(String category:List.of("IDENTITY","ADDRESS","ACTIVITY","PROFILE_PHOTO","SPOUSE_PHOTO")) {
                var fields=hidden(get(client,base+"/app/onboarding/documents/new",false));fields.put("category",category);
                String boundary="kyc-"+UUID.randomUUID();var bytes=new ByteArrayOutputStream();
                for(var field:fields.entrySet())bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
                bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"evidence.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));bytes.write(domain.image);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                var response=client.send(HttpRequest.newBuilder(URI.create(base+"/app/onboarding/documents/new")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(302);
            }
            form=get(client,base+"/app/onboarding/edit",false);var submitted=hidden(form);values.forEach((key,value)->{if(key.startsWith("profile."))submitted.put(key,value);});submitted.put("action","SUBMITTED");submitted.put("noticeAcknowledged","true");submitted.put("declarationAccepted","true");post(client,base+"/app/onboarding/edit",submitted,false);
            assertThat(get(client,base+"/app/onboarding",false)).contains("awaiting review","<fieldset class=\"kyc-profile-fields\" disabled").doesNotContain("value=\"SUBMITTED\"", "value=\"DRAFT\"", "data-profile-wizard");
            String reviewUrl=base+"/finance/clients/onboarding/"+domain.f.client+"/review";
            String renderedReview=get(client,reviewUrl,true);
            assertThat(renderedReview)
                .contains("name=\"checksConfirmed\"", "Save review")
                .containsPattern("(?s)class=\"erp-detail-facts kyc-document-facts\".*?<dd>[^<]* \\x{B7} \\d{2}/\\d{2}/\\d{4}")
                .doesNotContain("Verification checks", "name=\"identityChecked\"", "name=\"addressChecked\"", "\u00c2\u00b7");
            var attachmentLink=Pattern.compile("href=\"([^\"]+/documents/[a-f0-9-]+)\" download>evidence\\.png</a>").matcher(renderedReview);
            assertThat(attachmentLink.find()).isTrue();
            var attachment=client.send(HttpRequest.newBuilder(URI.create(base+attachmentLink.group(1)))
                .header("X-Onboarding-Test-Actor","reviewer").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(attachment.statusCode()).isEqualTo(200);
            assertThat(attachment.headers().firstValue("Content-Disposition")).hasValueSatisfying(value->assertThat(value).startsWith("attachment;").contains("evidence.png"));
            assertThat(attachment.body()).isEqualTo(domain.image);
            var review=hidden(renderedReview);review.put("kind","RETURN");review.put("reason","INTERNAL-SCREENING-SECRET");review.put("clientMessage","Please correct your home address.");post(client,reviewUrl,review,true);
            String returned=get(client,base+"/app/onboarding",false);assertThat(returned).contains("Please correct your home address.").doesNotContain("INTERNAL-SCREENING-SECRET");
            assertThat(returned).contains("data-resource-status=\"WARNING\"","data-message-type=\"warning\"");
            var correction=hidden(get(client,base+"/app/onboarding/edit",false));values.forEach((key,value)->{if(key.startsWith("profile."))correction.put(key,value);});correction.put("profile.address","Synthetic corrected address");correction.put("action","SUBMITTED");correction.put("noticeAcknowledged","true");correction.put("declarationAccepted","true");post(client,base+"/app/onboarding/edit",correction,false);
            var verification=hidden(get(client,reviewUrl,true));verification.put("kind","VERIFY");verification.put("reason","Synthetic independent checks complete");
            verification.put("screening","CLEARED");verification.put("screeningReference","Synthetic dated manual screening evidence");verification.put("policyReference","Approved synthetic policy");verification.put("checkedOn",domain.day.toString());verification.put("reviewDue",domain.day.plusMonths(6).toString());
            var incomplete=submit(client,reviewUrl,verification,true);
            assertThat(incomplete.statusCode()).isEqualTo(200);
            assertThat(incomplete.body()).contains("Confirm the checks before verifying this client.","Synthetic dated manual screening evidence");
            verification.put("checksConfirmed","true");
            var completed=submit(client,reviewUrl,verification,true);
            assertThat(completed.statusCode()).as(completed.body()).isEqualTo(302);
            String detailsPath="/finance/clients/onboarding/"+domain.f.client;
            assertThat(completed.headers().firstValue("Location")).hasValueSatisfying(location->assertThat(location).endsWith(detailsPath));
            assertThat(get(client,base+detailsPath,true)).contains("Verified","Synthetic independent checks complete","Revoke verification");
            // A retry of the same decision from the older form still resolves to the saved record.
            verification.remove("checksConfirmed");
            for(String check:List.of("identityChecked","addressChecked","activityChecked","contactChecked","duplicatesChecked"))verification.put(check,"true");
            post(client,reviewUrl,verification,true);
            String verified=get(client,base+"/app/onboarding",false);assertThat(verified).contains("Synthetic corrected address","Loan assessment and approval are separate.").doesNotContain("Please correct your home address.");
            assertThat(verified).contains("data-resource-status=\"SUCCESS\"").doesNotContain("data-message-type=\"info\"");
            assertThat(verified).doesNotContain("Complete your profile and wait for verification");
            assertThat(get(client,base+"/app/dashboard",false)).isEqualTo("Borrower workspace");
            var retired=client.send(HttpRequest.newBuilder(URI.create(base+"/app/onboarding/loan")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(retired.statusCode()).isEqualTo(302);assertThat(retired.headers().firstValue("Location")).contains("/app/onboarding");
            var retiredNotice=client.send(HttpRequest.newBuilder(URI.create(base+"/finance/clients/onboarding/notice")).header("X-Onboarding-Test-Actor","reviewer").GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(retiredNotice.statusCode()).isEqualTo(302);assertThat(retiredNotice.headers().firstValue("Location")).contains("/finance/clients/onboarding");
            assertThat(get(client,base+"/app/onboarding/edit?lang=sw",false)).contains("Taarifa zangu","Tunalinda taarifa zako binafsi", "data-personal=\"Taarifa binafsi\"", "data-review=\"Kagua na tuma\"");get(client,base+"/app/onboarding?lang=en",false);
            String creation=get(client,base+"/finance/clients/onboarding/new",true);assertThat(creation).contains("Generated automatically","Business TIN","Daily sales (TZS)","Spouse details","Bank details");
            var created=hidden(creation);created.put("profile.firstName","Rendered");created.put("profile.lastName","Borrower");created.put("profile.phone",domain.phone());created.put("profile.city","Rendered city");created.put("profile.businessName","Rendered business");created.put("profile.dailyExpenditure","99.50");
            post(client,base+"/finance/clients/onboarding/new",created,true);
            String ownDetails=get(client,base+"/app/onboarding",false);assertThat(ownDetails).contains("Rendered synthetic shop","12345.67","9876543210");
            var conditional=hidden(get(client,base+"/app/onboarding/edit",false));conditional.put("action","DRAFT");conditional.put("profile.hasBusiness","false");conditional.put("profile.maritalStatus","SINGLE");conditional.put("profile.businessName","Inactive shop");conditional.put("profile.spousePhone","Not applicable");post(client,base+"/app/onboarding/edit",conditional,false);
            String conditionalForm=get(client,base+"/app/onboarding",false);assertThat(conditionalForm).contains("value=\"false\" selected=\"selected\"","data-profile-business-fields hidden","data-profile-spouse-section hidden").doesNotContain("Inactive shop","Not applicable");
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
        var response=submit(client,url,fields,staff);assertThat(response.statusCode()).as("POST %s: %s",url,response.body()).isEqualTo(302);
    }
    static HttpResponse<String> submit(HttpClient client,String url,Map<String,String> fields,boolean staff)throws Exception {
        var data=new StringJoiner("&");fields.forEach((key,value)->data.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/x-www-form-urlencoded");if(staff)request.header("X-Onboarding-Test-Actor","reviewer");
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(data.toString())).build(),HttpResponse.BodyHandlers.ofString());
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
        @Bean BorrowerFixture borrowerFixture(){return new BorrowerFixture();}
        @Bean org.springframework.web.servlet.i18n.SessionLocaleResolver localeResolver(){var resolver=new org.springframework.web.servlet.i18n.SessionLocaleResolver();resolver.setDefaultLocale(Locale.ENGLISH);return resolver;}
        @Bean SecurityFilterChain security(HttpSecurity http,AppUserPrincipal fixtureOwner,AppUserPrincipal fixtureReviewer,com.sacco.mvp.clients.onboarding.service.OnboardingService onboarding)throws Exception {
            var actors=new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
                if(Integer.getInteger("microfinance.onboarding.browser-seconds",0)>0 && Set.of("reviewer","owner").contains(Objects.toString(request.getParameter("qaActor"),"")))request.getSession().setAttribute("qaActor",request.getParameter("qaActor"));
                var actor="reviewer".equals(request.getHeader("X-Onboarding-Test-Actor"))||"reviewer".equals(request.getSession().getAttribute("qaActor"))?fixtureReviewer:fixtureOwner;
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));chain.doFilter(request,response);
            }};
            return http.authorizeHttpRequests(auth->auth.dispatcherTypeMatchers(DispatcherType.FORWARD,DispatcherType.ERROR).permitAll().anyRequest().authenticated()).addFilterBefore(actors,AnonymousAuthenticationFilter.class).addFilterAfter(new com.sacco.mvp.security.ClientOnboardingFilter(onboarding),org.springframework.security.web.access.intercept.AuthorizationFilter.class).build();
        }
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry){for(String path:List.of("css","js","fonts","images"))registry.addResourceHandler("/"+path+"/**").addResourceLocations(Path.of("src/main/resources/static",path).toAbsolutePath().toUri().toString()+"/");}
        @Override public void addInterceptors(InterceptorRegistry registry){var locale=new org.springframework.web.servlet.i18n.LocaleChangeInterceptor();locale.setParamName("lang");registry.addInterceptor(locale);registry.addInterceptor(new HandlerInterceptor(){@Override public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view){if(view!=null){var actor=(AppUserPrincipal)SecurityContextHolder.getContext().getAuthentication().getPrincipal();view.addObject("currentMember",com.sacco.mvp.web.view.CurrentUserView.builder().memberNo(actor.getMemberNo()).staffNo(actor.getStaffNo()).fullName(actor.getFullName()).email(actor.getEmail()).position(actor.getPosition()).memberAccess(actor.isMemberAccess()).staffSession(actor.isStaffSession()).build());}}});}
    }
    @org.springframework.web.bind.annotation.RestController
    static class BorrowerFixture {
        @org.springframework.web.bind.annotation.GetMapping("/app/dashboard") String dashboard(){return "Borrower workspace";}
    }
}
