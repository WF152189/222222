package com.example.svf.svf;

import com.example.svf.svf.model.SvfRenderOptions;
import com.example.svf.svf.model.SvfRenderRequest;
import com.example.svf.svf.model.SvfRenderResult;
import com.example.svf.svf.model.SvfUserContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class SvfCloudWebApiClient implements SvfCloudClient {
    private final SvfTokenProvider tokenProvider;
    private final RestClient restClient;

    public SvfCloudWebApiClient(SvfTokenProvider tokenProvider, RestClient.Builder builder, com.example.svf.config.SvfCloudProperties properties) {
        this.tokenProvider = tokenProvider;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public SvfRenderResult renderPdf(SvfRenderRequest request) {
        validateRequest(request);
        SvfUserContext user = request.user();
        SvfRenderOptions options = request.options();
        String token = tokenProvider.getAccessToken(user);
        PrintJobLocation job = executePrintJob(token, request);
        if (options.waitForCompletion()) {
            waitUntilCompleted(token, job.actionId(), options);
        }
        byte[] pdf = downloadArtifact(token, job.downloadUri());
        return new SvfRenderResult(pdf, job.artifactId(), job.actionId(), job.ticket(), request.artifactName(), MediaType.APPLICATION_PDF_VALUE);
    }

    private URI requestPrintJob(String token, SvfRenderRequest request) {
        SvfRenderOptions options = request.options();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", request.artifactName());
        body.add("printer", options.resolvedPrinter());
        body.add("source", options.resolvedSource());
        body.add("defaultForm", request.formPath());
        body.add("timeout", String.valueOf(options.resolvedTimeoutSeconds()));
        body.add("redirect", String.valueOf(options.redirect()));
        body.add("data/report.csv", request.csvData());

        try {
            URI location = restClient.post()
                    .uri("/v1/artifacts")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(body)
                    .exchange((httpRequest, response) -> {
                        if (response.getStatusCode() != HttpStatus.ACCEPTED && response.getStatusCode() != HttpStatus.SEE_OTHER) {
                            throw new SvfCloudException("SVF Cloud print job failed: " + response.getStatusCode());
                        }
                        URI responseLocation = response.getHeaders().getLocation();
                        if (responseLocation == null) {
                            throw new SvfCloudException("SVF Cloud print job response has no Location header");
                        }
                        return responseLocation;
                    });
            return location;
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud print job failed: " + ex.getStatusCode(), ex);
        }
    }

    private void waitUntilCompleted(String token, String actionId, SvfRenderOptions options) {
        Instant deadline = Instant.now().plusSeconds(options.resolvedTimeoutSeconds());
        while (Instant.now().isBefore(deadline)) {
            int state = retrieveActionState(token, actionId);
            if (state == 2) {
                return;
            }
            if (state >= 3) {
                throw new SvfCloudException("SVF Cloud print job failed. actionId=" + actionId + ", state=" + state);
            }
            sleep(options.resolvedPollIntervalMillis());
        }
        throw new SvfCloudException("SVF Cloud print job timed out. actionId=" + actionId);
    }

    private int retrieveActionState(String token, String actionId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/actions/{actionId}", actionId)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.get("state") == null) {
                throw new SvfCloudException("SVF Cloud action status response is invalid");
            }
            return Integer.parseInt(response.get("state").toString());
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud action status request failed: " + ex.getStatusCode(), ex);
        }
    }

    private byte[] downloadArtifact(String token, URI location) {
        try {
            byte[] response = restClient.get()
                    .uri(location)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(byte[].class);
            if (response == null || response.length == 0) {
                throw new SvfCloudException("SVF Cloud returned empty artifact");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw new SvfCloudException("SVF Cloud artifact download failed: " + ex.getStatusCode(), ex);
        }
    }

    private void validateRequest(SvfRenderRequest request) {
        if (request == null) {
            throw new SvfCloudException("SVF render request is required");
        }
        if (isBlank(request.artifactName())) {
            throw new SvfCloudException("SVF artifactName is required");
        }
        if (isBlank(request.formPath())) {
            throw new SvfCloudException("SVF formPath is required");
        }
        if (isBlank(request.csvData())) {
            throw new SvfCloudException("SVF csvData is required");
        }
        if (request.user() == null || request.user().isBlank()) {
            throw new SvfCloudException("SVF user context is required");
        }
    }

    private PrintJobLocation executePrintJob(String token, SvfRenderRequest request) {
        URI location = requestPrintJob(token, request);
        return PrintJobLocation.from(location);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(100, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SvfCloudException("Interrupted while waiting for SVF Cloud print job", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PrintJobLocation(URI downloadUri, String artifactId, String actionId, String ticket) {
        static PrintJobLocation from(URI location) {
            String path = location.getPath();
            String artifactId = path.substring(path.lastIndexOf('/') + 1);
            String actionId = queryParam(location, "action");
            String ticket = queryParam(location, "ticket");
            if (artifactId.isBlank() || actionId == null || ticket == null) {
                throw new SvfCloudException("SVF Cloud Location header is invalid: " + location);
            }
            return new PrintJobLocation(location, artifactId, actionId, ticket);
        }

        private static String queryParam(URI uri, String name) {
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return null;
            }
            for (String pair : query.split("&")) {
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                if (name.equals(key)) {
                    return keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                }
            }
            return null;
        }
    }
}
