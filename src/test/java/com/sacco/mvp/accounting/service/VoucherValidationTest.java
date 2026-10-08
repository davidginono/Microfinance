package com.sacco.mvp.accounting.service;
import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class VoucherValidationTest {
 final LocalDate today=LocalDate.of(2026,10,8);
 Form form(){var f=new Form();f.setEffectiveDate(today);f.setParty("Payer");f.setReference("REF");f.getRows().getFirst().setDescription("Transaction");f.getRows().getFirst().setAmount(new BigDecimal("1.01"));return f;}
 @Test void decimalAmountsMustBePositivePreciseAndWithinStorageLimit(){for(String value:List.of("0","-1","1.001","10000000000000000")){var f=form();f.getRows().getFirst().setAmount(new BigDecimal(value));assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.amount");}}
 @Test void datesRowsAndTextAreBounded(){var future=form();future.setEffectiveDate(today.plusDays(1));assertThatThrownBy(()->VoucherService.validate(future,Type.JOURNAL,today)).hasMessage("voucher.error.date");var many=form();many.setRows(Collections.nCopies(51,many.getRows().getFirst()));assertThatThrownBy(()->VoucherService.validate(many,Type.JOURNAL,today)).hasMessage("voucher.error.rows");var blank=form();blank.setParty("");assertThatThrownBy(()->VoucherService.validate(blank,Type.JOURNAL,today)).hasMessage("voucher.error.text");}
 @Test void manualJournalsRejectSharedMoneyAccount(){var f=form();f.setMoneyAccountId(UUID.randomUUID());assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.moneyAccount");}
}
