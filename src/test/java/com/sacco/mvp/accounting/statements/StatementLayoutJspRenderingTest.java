package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementLayoutModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.AccountChoice;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class StatementLayoutJspRenderingTest {
    @TempDir Path temporary;
    final UUID capital=UUID.randomUUID(),bank=UUID.randomUUID();
    @Test void focusedFormsRenderSelectionsFiguresTranslationsAndRecoverableErrors()throws Exception {
        Path views=Path.of("src/main/webapp/WEB-INF/jsp/accounting/statements");
        String tags="""
            <%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            <spring:htmlEscape defaultHtmlEscape="true" />
            """;
        for(String kind:List.of("group","note")){
            String source=Files.readString(views.resolve("layout-"+kind+".jsp"));
            Files.writeString(temporary.resolve(kind+".jsp"),tags+source.substring(source.indexOf("<c:set var=\"layoutFormName\""),source.indexOf("</form:form>")+12));
        }
        for(String fragment:List.of("layout-accounts.jspf","layout-errors.jspf"))Files.copy(views.resolve(fragment),temporary.resolve(fragment));
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);
        var context=tomcat.addContext("",temporary.toString());context.setParentClassLoader(getClass().getClassLoader());context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new StaticWebApplicationContext();
        context.addServletContainerInitializer((classes,servletContext)->{
            spring.setServletContext(servletContext);var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");spring.getStaticMessageSource().setParentMessageSource(messages);spring.refresh();servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);
        },null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        Tomcat.addServlet(context,"fixture",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws java.io.IOException,jakarta.servlet.ServletException{
                response.setLocale("sw".equals(request.getParameter("lang"))?Locale.forLanguageTag("sw"):Locale.ENGLISH);
                request.setAttribute("_csrf",Map.of("parameterName","_csrf","token","synthetic-token"));
                boolean noAccounts="true".equals(request.getParameter("empty"));
                request.setAttribute("accounts",noAccounts?List.of():List.of(new AccountChoice(capital,"311001","Capital <safe>","EQUITY"),new AccountChoice(bank,"111001","Bank","ASSET")));
                boolean group="group".equals(request.getParameter("kind"));Change form;
                if(group){var f=new GroupForm();f.setName("Owner capital");f.setNameSw("Mtaji");f.setAccounts(noAccounts?List.of():List.of(capital));form=f;request.setAttribute("positions",2);}
                else{var f=new NoteForm();f.setNumber(2);f.setTitle("Policy");f.setTitleSw("Sera");f.setText("Recorded disclosure");f.setBasis(request.getParameter("basis"));if(!f.getBasis().equals("NARRATIVE"))f.setAccounts(List.of(bank));form=f;}
                form.setIndex("true".equals(request.getParameter("edit"))?0:-1);form.setVersion(3);form.setRequestKey(UUID.randomUUID());form.setReason("Retain entered reason");
                String name=group?"layoutGroup":"layoutNote";request.setAttribute(name,form);
                if("true".equals(request.getParameter("error"))){var errors=new BeanPropertyBindingResult(form,name);errors.rejectValue("reason","fs.error.reason");request.setAttribute(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX+name,errors);}
                request.getRequestDispatcher(group?"/group.jsp":"/note.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/fixture","fixture");
        try{
            tomcat.getConnector();tomcat.start();var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/fixture?";
            String group=page(client,base+"kind=group&edit=true&error=true");
            assertThat(group).contains("name=\"accounts\"","value=\""+capital+"\"","checked=\"checked\"","Capital &lt;safe&gt;","Retain entered reason","data-field-error-for=\"reason\"","data-workspace-response-error");
            assertThat(element(group,"textarea","reason")).doesNotContain("required");
            assertThat(element(page(client,base+"kind=group"),"textarea","reason")).contains("required");
            assertThat(page(client,base+"kind=group&empty=true")).contains("No accounts are available for this form").doesNotContain("class=\"fs-layout-account\"");
            for(String lang:List.of("en","sw"))for(String basis:List.of("NARRATIVE","BALANCE","MOVEMENT")){
                String note=page(client,base+"kind=note&basis="+basis+"&lang="+lang);
                var fieldset=Pattern.compile("<fieldset[^>]*data-layout-note-accounts[^>]*>").matcher(note);assertThat(fieldset.find()).isTrue();
                if(basis.equals("NARRATIVE")){assertThat(fieldset.group()).contains("hidden","disabled");assertThat(element(note,"textarea","text")).contains("required");}
                else{assertThat(fieldset.group()).doesNotContain("hidden","disabled");assertThat(element(note,"textarea","text")).doesNotContain("required");}
                assertThat(note).contains("name=\"requestKey\"","value=\"3\"","name=\"_csrf\"","Recorded disclosure");assertThat(note).doesNotContain("components[","notes[","categories[","/statements/setup");
                assertThat(note).contains(lang.equals("en")?"No figures":"Bila takwimu");
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
    String page(HttpClient client,String url)throws Exception{var response=client.send(HttpRequest.newBuilder(URI.create(url)).header("Accept-Language",url.contains("lang=sw")?"sw":"en").timeout(Duration.ofSeconds(90)).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return response.body();}
    String element(String html,String tag,String name){var match=Pattern.compile("<"+tag+"[^>]*name=\""+name+"\"[^>]*>").matcher(html);assertThat(match.find()).as(name).isTrue();return match.group();}
}
