package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ReconciliationValidationTest {
    private StatementCommand command(String content,String closing) {return new StatementCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-02"),new BigDecimal("100.00"),new BigDecimal(closing),"bank.csv","Synthetic verified source",content);}
    @Test void exactSignedCentsAndDuplicateFlagsPreserveEveryRow() {
        var rows=ReconciliationService.parse(command("date,reference,amount,kind\n2026-10-01,R1,10.01,RECEIPT\n2026-10-01,F1,-0.01,CHARGE\n2026-10-01,R1,10.01,RECEIPT","120.01"));
        assertThat(rows).hasSize(3);assertThat(rows.get(0).amount()).isEqualByComparingTo("10.01");assertThat(rows.get(2).duplicate()).isTrue();
    }
    @Test void rejectsPrecisionUnbalancedDatesAndUnsupportedLayout() {
        for(String row:new String[]{"2026-10-01,R,1.001,RECEIPT","2026-10-03,R,1.00,RECEIPT","2026-10-01,R,0,RECEIPT","2026-10-01,R,1e2,RECEIPT","2026-10-01,R,1.00,SQL"})assertThatThrownBy(()->ReconciliationService.parse(command("date,reference,amount,kind\n"+row,"101.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ReconciliationService.parse(command("date,reference,amount,kind\n2026-10-01,R,1.00,RECEIPT","100.00"))).hasMessage("reconciliation.error.statementBalance");
    }
    @Test void importLimitsFilenameAndHostileEvidenceFailClosed() {
        var c=command("date,reference,amount,kind\n2026-10-01,R,1.00,RECEIPT","101.00");
        assertThatThrownBy(()->ReconciliationService.parse(new StatementCommand(c.key(),c.account(),c.format(),c.from(),c.through(),c.opening(),c.closing(),"../../bank.csv",c.evidence(),c.content()))).hasMessage("reconciliation.error.filename");
        assertThatThrownBy(()->ReconciliationService.text("<script>",500)).hasMessage("reconciliation.error.text");
        assertThatThrownBy(()->ReconciliationService.parse(command("date,reference,amount,kind\n"+"2026-10-01,R,1.00,RECEIPT\n".repeat(2001),"2101.00"))).hasMessage("reconciliation.error.format");
    }
}
