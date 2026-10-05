package com.sacco.mvp.accounting.ledger.exception;

/** Stable localized error keys; financial failures must roll back the whole command. */
public final class LedgerException extends IllegalArgumentException {
    public LedgerException(String code) { super("accounting.ledger.error." + code); }
}
