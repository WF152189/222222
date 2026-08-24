package com.example.svf.svf;

import com.example.svf.svf.model.SvfUserContext;

public interface SvfJwtAssertionFactory {
    String createAssertion(SvfUserContext user);
}
