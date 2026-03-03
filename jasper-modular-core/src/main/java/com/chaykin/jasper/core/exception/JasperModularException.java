package com.chaykin.jasper.core.exception;

public class JasperModularException extends RuntimeException {

    public JasperModularException() {
    }

    public JasperModularException(String message) {
        super(message);
    }

    public JasperModularException(String message, Throwable cause) {
        super(message + "| cause: " + cause.getMessage(), cause);
    }

    public JasperModularException(Throwable cause) {
        super(cause);
    }

    public JasperModularException(String message, Throwable cause,
                                  boolean enableSuppression,
                                  boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
