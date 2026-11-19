package com.sacco.mvp.accounting.service;
import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class VoucherValidationTest {
 @Test void registerPageSizesAreCappedAndLegacyFiltersRemainCompatible(){
  for(int size:List.of(10,25,30,50))assertThatCode(()->VoucherService.validateFilter(new Filter("",null,null,"","newest",1,size))).doesNotThrowAnyException();
  for(int size:List.of(-1,0,51,1000))assertThatThrownBy(()->VoucherService.validateFilter(new Filter("",null,null,"","newest",0,size))).hasMessage("voucher.error.filter");
  assertThat(new Filter("",null,null,"","newest",0).size()).isEqualTo(25);
 }
 final LocalDate today=LocalDate.of(2026,10,8);
 Form form(){var f=new Form();f.setEffectiveDate(today);f.setParty("Payer");f.setReference("REF");f.getRows().getFirst().setDescription("Transaction");f.getRows().getFirst().setAmount(new BigDecimal("1.01"));return f;}
 @Test void decimalAmountsMustBePositivePreciseAndWithinStorageLimit(){for(String value:List.of("0","-1","1.001","10000000000000000")){var f=form();f.getRows().getFirst().setAmount(new BigDecimal(value));assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.amount");}}
 @Test void datesRowsAndTextAreBounded(){var future=form();future.setEffectiveDate(today.plusDays(1));assertThatThrownBy(()->VoucherService.validate(future,Type.JOURNAL,today)).hasMessage("voucher.error.date");var many=form();many.setRows(Collections.nCopies(51,many.getRows().getFirst()));assertThatThrownBy(()->VoucherService.validate(many,Type.JOURNAL,today)).hasMessage("voucher.error.rows");var blank=form();blank.setReference("");assertThatThrownBy(()->VoucherService.validate(blank,Type.JOURNAL,today)).hasMessage("voucher.error.text");}
 @Test void journalsOmitPartyWhileReceiptsAndPaymentsStillRequirePayerOrPayee(){
  var f=form();f.setParty("");assertThatCode(()->VoucherService.validate(f,Type.JOURNAL,today)).doesNotThrowAnyException();
  for(Type type:List.of(Type.RECEIPT,Type.PAYMENT))assertThatThrownBy(()->VoucherService.validate(f,type,today)).hasMessage("voucher.error.text");
  f.setParty("x".repeat(161));assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.text");
 }
 @Test void manualJournalsRejectSharedMoneyAccount(){var f=form();f.setMoneyAccountId(UUID.randomUUID());assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.moneyAccount");}
 @Test void selectingAnActivityRequiresConfiguredTransactionsForEveryVoucherType(){
  for(Type type:Type.values()){
   var f=form();f.setActivityId(UUID.randomUUID());
   assertThatThrownBy(()->VoucherService.validate(f,type,today)).hasMessage("voucher.error.activityTransaction");
   f.getRows().getFirst().setTransactionId(UUID.randomUUID());
   assertThatCode(()->VoucherService.validate(f,type,today)).doesNotThrowAnyException();
  }
 }
 @Test void approvalSnapshotsRetainTheActivityAndLegacyFormsOmitTheNewField(){
  var f=form();var mapper=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
  assertThat(mapper.writeValueAsString(f)).doesNotContain("activityId");
  f.setActivityId(UUID.randomUUID());
  var gate=new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(null,null);
  assertThat(gate.canonical(f,f.getRequestKey()).getActivityId()).isEqualTo(f.getActivityId());
  assertThat(mapper.writeValueAsString(f)).contains(f.getActivityId().toString());
 }
 @Test void splitEntryAmountsSidesAndBoundsAreValidatedBeforePlanning(){
  var f=form();var r=f.getRows().getFirst();var debit=new EntryForm();debit.setSide("DEBIT");debit.setAmount(new BigDecimal("1.01"));var credit=new EntryForm();credit.setSide("CREDIT");credit.setAmount(new BigDecimal("1.01"));r.setEntries(List.of(debit,credit));
  assertThatCode(()->VoucherService.validate(f,Type.JOURNAL,today)).doesNotThrowAnyException();
  debit.setSide(null);assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.entries");debit.setSide("DEBIT");
  debit.setAmount(new BigDecimal("0.001"));assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.amount");debit.setAmount(new BigDecimal("1.01"));
  r.setEntries(Collections.nCopies(51,debit));assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.entries");
  r.setEntries(List.of(debit,credit));r.setTransactionId(UUID.randomUUID());assertThatThrownBy(()->VoucherService.validate(f,Type.JOURNAL,today)).hasMessage("voucher.error.template");
 }
 @Test void legacyFormSerializationOmitsEmptyEntriesAndSplitSnapshotsCopyEntries(){
  var mapper=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();var f=form();assertThat(mapper.writeValueAsString(f)).doesNotContain("entries");
  var e=new EntryForm();e.setSide("DEBIT");e.setAccountId(UUID.randomUUID());e.setAmount(new BigDecimal("1.01"));f.getRows().getFirst().setEntries(List.of(e));
  var gate=new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(null,null);var copy=gate.canonical(f,f.getRequestKey());
  e.setAmount(BigDecimal.TEN);assertThat(copy.getRows().getFirst().getEntries().getFirst().getAmount()).isEqualByComparingTo("1.01");
 }
 @Test void everyVoucherTypeRejectsEachUnbalancedTransactionEvenWhenDifferencesCancel(){
  for(Type type:Type.values()){
   var f=form();f.setRows(List.of(splitRow("1.01","1.01","1.00"),splitRow("1.01","1.00","1.01")));
   assertThatThrownBy(()->VoucherService.validate(f,type,today)).isInstanceOfSatisfying(Invalid.class,e->{assertThat(e.field()).isEqualTo("rows[0].entries");assertThat(e).hasMessage("voucher.error.balance");});
   f.setRows(List.of(splitRow("1.01","1.01","1.01"),splitRow("1.01","1.02","1.01")));
   assertThatThrownBy(()->VoucherService.validate(f,type,today)).isInstanceOfSatisfying(Invalid.class,e->{assertThat(e.field()).isEqualTo("rows[1].entries");assertThat(e).hasMessage("voucher.error.balance");});
  }
 }
 @Test void balancedSidesMustAlsoMatchTheDeclaredTransactionAmountForEveryVoucherType(){
  for(Type type:Type.values())for(String amount:List.of("1.00","1.02")){
   var f=form();f.setRows(List.of(splitRow(amount,"1.01","1.01")));
   assertThatThrownBy(()->VoucherService.validate(f,type,today)).hasMessage("voucher.error.balance");
  }
 }
 @Test void everyTransactionRequiresBothSidesAndUsesExactDecimalAmounts(){
  for(Type type:Type.values()){
   var f=form();var row=splitRow("1.01","1.01","1.01");f.setRows(List.of(row));
   row.getEntries().getLast().setSide("DEBIT");assertThatThrownBy(()->VoucherService.validate(f,type,today)).hasMessage("voucher.error.balance");
   row.getEntries().forEach(e->e.setSide("CREDIT"));assertThatThrownBy(()->VoucherService.validate(f,type,today)).hasMessage("voucher.error.balance");
   row=splitRow("0.30","0.10","0.30");var extra=new EntryForm();extra.setSide("DEBIT");extra.setAmount(new BigDecimal("0.20"));row.getEntries().add(extra);f.setRows(List.of(row));
   assertThatCode(()->VoucherService.validate(f,type,today)).doesNotThrowAnyException();
   f.setRows(List.of(splitRow("9999999999999999.99","9999999999999999.99","9999999999999999.99")));
   assertThatCode(()->VoucherService.validate(f,type,today)).doesNotThrowAnyException();
  }
 }
 RowForm splitRow(String amount,String debit,String credit){
  var r=new RowForm();r.setDescription("Split transaction");r.setAmount(new BigDecimal(amount));
  var d=new EntryForm();d.setSide("DEBIT");d.setAmount(new BigDecimal(debit));
  var c=new EntryForm();c.setSide("CREDIT");c.setAmount(new BigDecimal(credit));r.setEntries(new ArrayList<>(List.of(d,c)));return r;
 }
}
