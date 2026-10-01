package com.sacco.mvp.web.form;

import com.sacco.mvp.domain.LoanRepaymentTransaction;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter
public class LoanRepaymentForm {
    @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2)
    private BigDecimal amount;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paymentDate;
    @NotNull private LoanRepaymentTransaction.Channel channel;
    @NotBlank @Size(max = 100) private String reference;
    @NotNull private UUID requestKey;
    @AssertTrue private boolean confirmed;
}
