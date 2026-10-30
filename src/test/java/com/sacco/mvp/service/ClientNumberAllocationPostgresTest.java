package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// A dedicated disposable loopback database, never the application's datasource.
@EnabledIfEnvironmentVariable(named="MICROFINANCE_REGISTRATION_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_registration_test")
class ClientNumberAllocationPostgresTest {
    @Test void concurrentRegistrationsPreserveExistingNumbersAndNeverReuseRolledBackAllocations() throws Exception {
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc=context.getBean(JdbcTemplate.class);
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var service=context.getBean(MemberRegistrationService.class);
            assertThat(jdbc.queryForObject("select count(*) from members where username is null",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select md5(coalesce(string_agg((to_jsonb(m)-'username')::text,'' order by id),'')) from members m",String.class))
                .isEqualTo(Config.beforeUsernameMigration);
            assertThat(jdbc.queryForList("select username from members where member_no like 'LOGIN-BEFORE-%' order by member_no",String.class))
                .containsExactly("asha", "asha_2", "asha_3", "user_123", "user_a");
            assertThat(jdbc.queryForObject("select count(*)-count(distinct username) from members",Integer.class)).isZero();
            assertThatThrownBy(()->jdbc.update("update members set username='ASHA' where member_no='LOGIN-BEFORE-1'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            var now=OffsetDateTime.now();
            tx.executeWithoutResult(s->{
                context.getBean(RegisteredSaccoRepository.class).saveAndFlush(RegisteredSacco.builder().saccoId("NUMBER-TEST").saccoName("Synthetic institution").active(true).createdAt(now).updatedAt(now).build());
                context.getBean(SaccoStationRepository.class).saveAndFlush(SaccoStation.builder().id(UUID.randomUUID()).saccoId("NUMBER-TEST").stationId("B1").active(true).createdAt(now).updatedAt(now).build());
                context.getBean(SaccoSettingsRepository.class).saveAndFlush(SaccoSettings.builder().saccoId("NUMBER-TEST").externalStationId("B1").requiredGuarantors(3).boardSize(3).boardQuorum(2).maxLoanSavingsRatio(new java.math.BigDecimal("0.3333")).defaultLanguage("en").createdAt(now).updatedAt(now).build());
            });
            // Checking details for an OTP never allocates an identity.
            long before=jdbc.queryForObject("select last_value from client_number_seq",Long.class);
            assertThat(service.verifyExternalMember(form(90)).memberNo()).isEmpty();
            assertThat(jdbc.queryForObject("select last_value from client_number_seq",Long.class)).isEqualTo(before);
            var start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(8)) {
                var results=new ArrayList<Future<Member>>();
                for(int i=0;i<24;i++){final int n=i;results.add(pool.submit(()->{start.await();return service.register(form(n));}));}
                start.countDown();var numbers=new HashSet<String>();
                for(var result:results){var member=result.get(60,TimeUnit.SECONDS);assertThat(member.getMemberNo()).matches("CL-[0-9]{8,}");assertThat(numbers.add(member.getMemberNo())).isTrue();assertThat(member.getUsername()).matches("client[0-9]+");}
                assertThat(numbers).hasSize(24);

                var sameUsernameStart=new CountDownLatch(1);
                var sameUsernameResults=new ArrayList<Future<Boolean>>();
                for(int i=200;i<202;i++) {
                    var candidate=form(i);candidate.setUsername("Shared.Login");
                    sameUsernameResults.add(pool.submit(()->{
                        sameUsernameStart.await();
                        try {service.register(candidate);return true;}
                        catch(MemberRegistrationService.UsernameTakenException taken){return false;}
                    }));
                }
                sameUsernameStart.countDown();
                assertThat(List.of(sameUsernameResults.get(0).get(60,TimeUnit.SECONDS),sameUsernameResults.get(1).get(60,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
                assertThat(jdbc.queryForObject("select count(*) from members where username='shared.login'",Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from user_settings s join members m on m.id=s.member_id where m.username='shared.login'",Integer.class)).isEqualTo(1);
            }
            assertThat(jdbc.queryForList("select member_no from members where full_name='Existing number fixture' order by member_no",String.class)).containsExactly("CL-00000041","LEGACY-7");
            // An older/manual identity added after migration is skipped, including case differences.
            long collision=jdbc.queryForObject("select last_value+1 from client_number_seq",Long.class);
            seed(jdbc,"cl-"+String.format(Locale.ROOT,"%08d",collision));
            assertThat(service.register(form(91)).getMemberNo()).isNotEqualToIgnoringCase("CL-"+String.format(Locale.ROOT,"%08d",collision));
            String[] rolledBack={null};
            assertThatThrownBy(()->tx.executeWithoutResult(s->{rolledBack[0]=service.register(form(92)).getMemberNo();throw new IllegalStateException("Synthetic rollback");})).hasMessage("Synthetic rollback");
            assertThat(jdbc.queryForObject("select count(*) from members where member_no=?",Integer.class,rolledBack[0])).isZero();
            assertThat(service.register(form(93)).getMemberNo()).isNotEqualTo(rolledBack[0]);
            assertThat(service.registerAssisted(form(94),"ASSISTED-KEEP","Synthetic Assisted Client","NUMBER-TEST","B1").getMemberNo()).isEqualTo("ASSISTED-KEEP");
        }
    }
    static MemberRegistrationForm form(int n) {
        var f=new MemberRegistrationForm();f.setUsername("client"+n);f.setFirstName("Synthetic");f.setMiddleName("Client");f.setLastName("Test");f.setEmail("client"+n+"@example.invalid");f.setPhone("2557"+String.format(Locale.ROOT,"%08d",n));f.setSaccoId("NUMBER-TEST");f.setStationId("B1");f.setPassword("SyntheticTestPassword");return f;
    }
    static void seed(JdbcTemplate jdbc,String number) {
        jdbc.update("insert into members(id,member_no,full_name,position,status,password_hash,created_at,is_member) values(?,?,?,'MEMBER','ACTIVE','!TEST',now(),true)",UUID.randomUUID(),number,"Existing number fixture");
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @EnableJpaRepositories(basePackages="com.sacco.mvp.repository")
    static class Config {
        static String beforeUsernameMigration;
        @Bean(destroyMethod="close") DataSource dataSource() {var ds=new HikariDataSource();ds.setJdbcUrl(System.getenv("MICROFINANCE_REGISTRATION_TEST_URL"));ds.setUsername("postgres");ds.setMaximumPoolSize(8);ds.setMinimumIdle(1);return ds;}
        @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
        @Bean Flyway flyway(DataSource ds) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("135").load().migrate();
            var jdbc=new JdbcTemplate(ds);seed(jdbc,"LEGACY-7");seed(jdbc,"CL-00000041");
            String[] emails={"Asha@example.invalid","asha_2@example.invalid","asha@other.invalid","123@example.invalid","a@example.invalid"};
            for(int i=0;i<emails.length;i++) jdbc.update("insert into members(id,member_no,full_name,email,position,status,password_hash,created_at,is_member) values(?,?,?,?,'MEMBER','ACTIVE','!KEEP-EXISTING',timestamptz '2020-01-01 00:00:00+00'+(?*interval '1 second'),true)",UUID.randomUUID(),"LOGIN-BEFORE-"+(i+1),"Username migration fixture",emails[i],i);
            beforeUsernameMigration=jdbc.queryForObject("select md5(coalesce(string_agg(to_jsonb(m)::text,'' order by id),'')) from members m",String.class);
            var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();flyway.migrate();return flyway;
        }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.sacco.mvp.domain");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean MemberRegistrationService registration(MemberRepository members,RegisteredSaccoRepository institutions,SaccoStationRepository branches,SaccoSettingsRepository settings,UserSettingsRepository users) {
            var passwords=mock(PasswordEncoder.class);when(passwords.encode(any())).thenReturn("!SYNTHETIC");return new MemberRegistrationService(members,institutions,branches,settings,users,passwords,new NameSignatureService());
        }
    }
}
