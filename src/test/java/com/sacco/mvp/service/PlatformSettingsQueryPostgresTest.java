package com.sacco.mvp.service;

import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real repository query against session-local temporary tables; no migrations or persistent writes. */
@EnabledIfEnvironmentVariable(named = "MICROFINANCE_PLATFORM_TEST_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class PlatformSettingsQueryPostgresTest {
    @Test
    void topUpQueryPagesFiltersAndKeepsDefaultFeatureState() {
        var source = new SingleConnectionDataSource(System.getenv("MICROFINANCE_PLATFORM_TEST_URL"),
            System.getenv("MICROFINANCE_PLATFORM_TEST_USER"), System.getenv("MICROFINANCE_PLATFORM_TEST_PASSWORD"), true);
        var factory = new LocalContainerEntityManagerFactoryBean();
        try {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("create temporary table registered_saccos (sacco_id varchar primary key, sacco_name varchar, active boolean)");
            jdbc.execute("create temporary table sacco_settings (sacco_id varchar primary key, loan_top_up_enabled boolean)");
            jdbc.execute("insert into registered_saccos values ('INST-01','Alpha',true),('INST-02','Alpha',true),('INST-03','Beta',true),('PLATFORM','Platform',true),('INACTIVE','Inactive',false)");
            jdbc.execute("insert into sacco_settings values ('INST-02',false)");
            factory.setDataSource(source);
            factory.setManagedTypes(PersistenceManagedTypes.of(RegisteredSacco.class.getName(), SaccoSettings.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.boot.allow_jdbc_metadata_access", false,
                "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"));
            factory.afterPropertiesSet();
            var entityManager = factory.getObject().createEntityManager();
            try {
                var repository = new JpaRepositoryFactory(entityManager).getRepository(RegisteredSaccoRepository.class);
                var first = repository.loanTopUpAccess(null, "", PageRequest.of(0, 1));
                assertThat(first.getTotalElements()).isEqualTo(3);
                assertThat(first.getContent()).singleElement().satisfies(row -> {
                    assertThat(row.getSaccoId()).isEqualTo("INST-01");assertThat(row.isLoanTopUpEnabled()).isTrue();
                });
                assertThat(repository.loanTopUpAccess(null, "", PageRequest.of(1, 1)).getContent()).singleElement().satisfies(row -> {
                    assertThat(row.getSaccoId()).isEqualTo("INST-02");assertThat(row.isLoanTopUpEnabled()).isFalse();
                });
                assertThat(repository.loanTopUpAccess(null, "beta", PageRequest.of(0, 25)).getContent()).singleElement()
                    .extracting(RegisteredSaccoRepository.LoanTopUpRow::getSaccoId).isEqualTo("INST-03");
                assertThat(repository.loanTopUpAccess("INST-02", "", PageRequest.of(0, 1)).getContent()).hasSize(1);
                assertThat(repository.loanTopUpAccess("PLATFORM", "", PageRequest.of(0, 1))).isEmpty();
            } finally { entityManager.close(); }
        } finally { factory.destroy(); source.destroy(); }
    }
}
