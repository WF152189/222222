package com.example.svf.svf;

import com.example.svf.svf.model.SvfUserContext;

public interface SvfTokenProvider {
    String getAccessToken(SvfUserContext user);
}
