package com.sacco.mvp.service;

public interface SmsGateway {
    SmsSendResult send(String phoneNumber, String message);
}
