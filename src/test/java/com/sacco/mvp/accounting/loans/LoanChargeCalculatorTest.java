package com.sacco.mvp.accounting.loans;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static com.sacco.mvp.accounting.charges.service.LoanChargeCalculator.*;
import static org.assertj.core.api.Assertions.*;
class LoanChargeCalculatorTest {
 @Test void componentRoundingUsesDecimalHalfUp(){assertThat(percent(new BigDecimal("0.50"),new BigDecimal("0.01"))).isEqualByComparingTo("0.01");assertThat(percent(new BigDecimal("99999999999999.99"),new BigDecimal("0.0001"))).isEqualByComparingTo("10000000000.00");assertThat(amount(null)).isEqualByComparingTo("0.00");}
 @Test void invalidRatesAndPrecisionFailClosed(){assertThatThrownBy(()->percent(BigDecimal.TEN,new BigDecimal("1.1"))).hasMessage("charges.error.rate");assertThatThrownBy(()->amount(new BigDecimal("-0.01"))).hasMessage("charges.error.amount");assertThatThrownBy(()->amount(new BigDecimal("10000000000000000"))).hasMessage("charges.error.amount");}
}
