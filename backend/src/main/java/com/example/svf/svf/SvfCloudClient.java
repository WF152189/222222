package com.example.svf.svf;

import com.example.svf.svf.model.SvfRenderRequest;
import com.example.svf.svf.model.SvfRenderResult;

public interface SvfCloudClient {
    SvfRenderResult renderPdf(SvfRenderRequest request);
}
