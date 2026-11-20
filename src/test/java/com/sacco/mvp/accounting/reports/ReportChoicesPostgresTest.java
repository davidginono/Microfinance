package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_REPORT_CONTROLS_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_report_controls_test")
class ReportChoicesPostgresTest {
    Connection connection;JdbcTemplate jdbc;AppUserPrincipal actor;
    @BeforeEach void setup()throws Exception {
        connection=DriverManager.getConnection(System.getenv("MICROFINANCE_REPORT_CONTROLS_TEST_URL"),"microfinance_test","");
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
        jdbc.execute("create temporary table loan_product_settings(id uuid, sacco_id text,product_name text,loan_type text,display_order integer,custom_recording_configuration boolean default false,product_code text)");
        jdbc.execute("create temporary table gl_activity(id uuid,sacco_id text,code text,name text,name_sw text,active boolean)");
        actor=mock(AppUserPrincipal.class);when(actor.getSaccoId()).thenReturn("I1");when(actor.getStationId()).thenReturn("B1");
    }
    @AfterEach void close()throws Exception {if(connection!=null)connection.close();}
    @Test void productsStayScopedAndPagedAndSelectionsUseExactIds(){
        var reports=new ManagementReportRepository(new NamedParameterJdbcTemplate(jdbc));
        UUID selected=UUID.randomUUID(),foreign=UUID.randomUUID();
        jdbc.update("insert into loan_product_settings(id,sacco_id,product_name,loan_type,display_order) values(?,'I1','Education Loan','EDUCATION',1),(?,'I2','Education Loan','EDUCATION',1)",selected,foreign);
        for(int n=0;n<29;n++)jdbc.update("insert into loan_product_settings(id,sacco_id,product_name,loan_type,display_order) values(?,'I1',?,'OTHER',?)",UUID.randomUUID(),"Product "+n,n+2);
        assertThat(reports.products(actor,"",0,null,"Customised loans")).hasSize(26);
        assertThat(reports.products(actor,"",1,null,"Customised loans")).hasSize(5);
        assertThat(reports.products(actor,"education",0,null,"Customised loans")).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(selected));
        assertThat(reports.products(actor,"unrelated search",0,selected,"Customised loans")).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(selected));
        assertThat(reports.products(actor,"",0,foreign,"Customised loans")).isEmpty();
    }
    @Test void voucherLookupOmitsOnlyTheSystemRepaymentActivity(){
        var vouchers=new VoucherRepository(jdbc);UUID ordinary=UUID.randomUUID();
        jdbc.update("insert into gl_activity values(?,'I1','SYSTEM.LOAN_REPAYMENT','Loan repayment','Marejesho ya mkopo',true),(?,'I1','EXPENSES','Expenses','Gharama',true),(?,'I2','OTHER','Other','Nyingine',true)",UUID.randomUUID(),ordinary,UUID.randomUUID());
        assertThat(vouchers.activities("I1","",0)).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(ordinary));
        assertThat(vouchers.activities("I1","loan",0)).isEmpty();
        assertThat(vouchers.activities("I1","gharama",0)).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(ordinary));
    }
    @Test void customisedLoansComeFirstAndSearchByTheirLocalizedLabel(){
        var reports=new ManagementReportRepository(new NamedParameterJdbcTemplate(jdbc));
        UUID custom=UUID.randomUUID(),named=UUID.randomUUID(),foreign=UUID.randomUUID();
        jdbc.update("insert into loan_product_settings values(?,'I1','Custom loan','CUSTOMIZED_LOAN',999,true,'CUSTOM'),(?,'I1','Education Loan','EDUCATION',1,false,'EDU'),(?,'I2','Custom loan','CUSTOMIZED_LOAN',1,true,'CUSTOM')",custom,named,foreign);
        assertThat(reports.products(actor,"",0,null,"Customised loans")).extracting(c->c.getLabel()).containsExactly("Customised loans","Education Loan");
        assertThat(reports.products(actor,"customised",0,null,"Customised loans")).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(custom));
        assertThat(reports.products(actor,"maalumu",0,null,"Mikopo maalumu")).singleElement().satisfies(c->{assertThat(c.id()).isEqualTo(custom);assertThat(c.getLabel()).isEqualTo("Mikopo maalumu");});
        assertThat(reports.products(actor,"",0,custom,"Customised loans")).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(custom));
        assertThat(reports.products(actor,"",0,foreign,"Customised loans")).isEmpty();
    }
}
