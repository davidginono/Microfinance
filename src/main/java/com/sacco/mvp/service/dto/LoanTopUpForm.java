package com.sacco.mvp.service.dto;

import com.sacco.mvp.domain.LoanProductSetting;
import lombok.Data;

@Data
public class LoanTopUpForm {
    private boolean topUpSettingsPresent;
    private boolean topUpAllowed;
    private boolean topUpFeesEnabled;
    private boolean topUpApplicationFee;
    private boolean topUpInsuranceFee;
    private boolean topUpProcessingFee;
    private LoanProductSetting.TopUpPercentageBasis topUpPercentageBasis;
}
