package com.example.svf.svf;

public class SvfCloudException extends RuntimeException {
    public SvfCloudException(String message) {
        super(message);
    }

    public SvfCloudException(String message, Throwable cause) {
        super(message, cause);
    }
}
