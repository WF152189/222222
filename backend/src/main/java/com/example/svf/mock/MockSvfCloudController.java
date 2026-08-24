package com.example.svf.mock;

import com.example.svf.config.SvfCloudProperties;
import com.example.svf.svf.model.SvfUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"", "/svf-mock"})
public class MockSvfCloudController {
    private static final String JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final SvfCloudProperties properties;
    private final MockPdfRenderer pdfRenderer;
    private final MockTokenStore tokenStore;
    private final MockArtifactStore artifactStore;
    private final ObjectMapper objectMapper;

    public MockSvfCloudController(
            SvfCloudProperties properties,
            MockPdfRenderer pdfRenderer,
            MockTokenStore tokenStore,
            MockArtifactStore artifactStore,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.pdfRenderer = pdfRenderer;
        this.tokenStore = tokenStore;
        this.artifactStore = artifactStore;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> retrieveAccessToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> form) {
        if (properties.resolvedMockRateLimitEnabled()) {
            return tooManyRequests();
        }
        if (!isValidBasicAuthorization(authorization)) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid client authentication");
        }
        String grantType = form.getFirst("grant_type");
        String assertion = form.getFirst("assertion");
        if (!JWT_BEARER_GRANT_TYPE.equals(grantType)) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "grant_type must be " + JWT_BEARER_GRANT_TYPE);
        }
        Optional<SvfUserContext> assertionUser = parseJwtAssertion(assertion);
        if (assertionUser.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid JWT bearer assertion");
        }

        MockTokenStore.TokenIssueResult token = tokenStore.issue(assertionUser.get());
        return ResponseEntity.ok(Map.of("token", token.token(), "expiration", token.expiration()));
    }

    @PostMapping(value = "/oauth2/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> revokeAccessToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> form) {
        if (tokenStore.extractValidBearerUser(authorization).isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid access token");
        }
        String token = form.getFirst("token");
        if (token == null || token.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "token is required");
        }
        tokenStore.revoke(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/v1/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> executePrintJob(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> form,
            HttpServletRequest request) {
        if (properties.resolvedMockRateLimitEnabled()) {
            return tooManyRequests();
        }
        Optional<SvfUserContext> tokenUser = tokenStore.extractValidBearerUser(authorization);
        if (tokenUser.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid access token");
        }
        if (properties.resolvedMockForceError()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Mock SVF Cloud is configured to fail");
        }

        String printer = Optional.ofNullable(form.getFirst("printer")).orElse("PDF");
        String source = Optional.ofNullable(form.getFirst("source")).orElse("CSV");
        String formPath = Optional.ofNullable(form.getFirst("defaultForm")).orElse("");
        if (!"PDF".equals(printer)) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Only printer=PDF is supported by this mock");
        }
        if (!"CSV".equals(source)) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Only source=CSV is supported by this mock");
        }
        if (formPath.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "defaultForm is required");
        }

        String csv = findCsvPart(form);
        if (csv.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "data/{name} CSV part is required");
        }
        String artifactName = Optional.ofNullable(form.getFirst("name")).orElse("report");
        if (artifactName.contains("/") || artifactName.indexOf('\0') >= 0) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Artifact name contains forbidden characters");
        }

        String artifactId = artifactStore.newArtifactId();
        String actionId = artifactStore.newActionId();
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        byte[] pdf = pdfRenderer.render(artifactName, formPath, csv, tokenUser.get().userName());
        MockArtifact artifact = new MockArtifact(
                artifactId,
                actionId,
                ticket,
                artifactName,
                source,
                artifactName + ".pdf",
                pdf,
                Instant.now(),
                tokenUser.get().userId(),
                tokenUser.get().userName());
        artifactStore.save(artifact);

        URI location = URI.create(request.getRequestURL().toString() + "/" + artifactId + "?action=" + actionId + "&ticket=" + ticket);
        boolean redirect = !"false".equalsIgnoreCase(form.getFirst("redirect"));
        HttpStatus status = redirect ? HttpStatus.SEE_OTHER : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).location(location).build();
    }

    @GetMapping(value = "/v1/artifacts/{artifactId}")
    public ResponseEntity<?> retrieveOrDownloadArtifact(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @PathVariable("artifactId") String artifactId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "ticket", required = false) String ticket) {
        if (properties.resolvedMockRateLimitEnabled()) {
            return tooManyRequests();
        }
        if (tokenStore.extractValidBearerUser(authorization).isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid access token");
        }
        Optional<MockArtifact> found = artifactStore.findArtifact(artifactId);
        if (found.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Artifact does not exist");
        }
        MockArtifact artifact = found.get();
        boolean download = action != null || ticket != null || (accept != null && accept.contains(MediaType.APPLICATION_OCTET_STREAM_VALUE));
        if (download) {
            if (action == null || ticket == null || !artifact.actionId().equals(action) || !artifact.ticket().equals(ticket)) {
                return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "action and ticket are required for artifact download");
            }
            if (!isCompleted(artifact)) {
                return error(HttpStatus.REQUEST_TIMEOUT, "REQUEST_TIMEOUT", "Artifact generation is still running");
            }
            String disposition = ContentDisposition.inline()
                    .filename(artifact.path(), StandardCharsets.UTF_8)
                    .build()
                    .toString();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .body(artifact.content());
        }
        return ResponseEntity.ok(Map.of(
                "expiration", DateTimeFormatter.ISO_INSTANT.format(artifact.createdAt().plusSeconds(86400)),
                "id", artifact.id(),
                "name", artifact.name(),
                "segment", "",
                "sourceType", artifact.sourceType(),
                "user", Map.of("id", artifact.userId(), "name", artifact.userName())));
    }

    @GetMapping(value = "/v1/actions/{actionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> retrievePrintStatus(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable("actionId") String actionId) {
        if (properties.resolvedMockRateLimitEnabled()) {
            return tooManyRequests();
        }
        if (tokenStore.extractValidBearerUser(authorization).isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid access token");
        }
        Optional<MockArtifact> found = artifactStore.findByAction(actionId);
        if (found.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Action does not exist");
        }
        MockArtifact artifact = found.get();
        boolean completed = isCompleted(artifact);
        int state = completed ? 2 : 1;
        int code = 0;
        int reason = 0;
        if (properties.resolvedMockForceError()) {
            state = 3;
            code = 1;
            reason = 1;
        }

        String createdAt = DateTimeFormatter.ISO_INSTANT.format(artifact.createdAt());
        String stateUpdated = DateTimeFormatter.ISO_INSTANT.format(completed ? completedAt(artifact) : Instant.now());
        Map<String, Object> artifactResponse = new LinkedHashMap<>();
        artifactResponse.put("id", artifact.id());
        artifactResponse.put("name", artifact.name());
        artifactResponse.put("pages", 1);
        artifactResponse.put("path", artifact.path());
        artifactResponse.put("sourceType", artifact.sourceType());
        artifactResponse.put("workflowId", artifact.actionId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("artifact", artifactResponse);
        response.put("billings", new Object[0]);
        response.put("code", code);
        response.put("executedTime", createdAt);
        response.put("id", artifact.actionId());
        response.put("profile", "");
        response.put("reason", reason);
        response.put("segment", "");
        response.put("state", state);
        response.put("stateUpdated", stateUpdated);
        response.put("type", 4);
        response.put("user", Map.of("id", artifact.userId(), "name", artifact.userName()));
        return ResponseEntity.ok(response);
    }

    private boolean isValidBasicAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        try {
            String credentials = new String(Base64.getDecoder().decode(authorization.substring("Basic ".length())), StandardCharsets.UTF_8);
            return (properties.clientId() + ":" + properties.secret()).equals(credentials);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Optional<SvfUserContext> parseJwtAssertion(String assertion) {
        if (assertion == null || assertion.isBlank()) {
            return Optional.empty();
        }
        String[] segments = assertion.split("\\.");
        if (segments.length != 3) {
            return Optional.empty();
        }
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
            if (!properties.clientId().equals(String.valueOf(payload.get("iss")))) {
                return Optional.empty();
            }
            Object sub = payload.get("sub");
            Object name = payload.get("userName");
            if (sub == null || name == null) {
                return Optional.empty();
            }
            String userId = String.valueOf(sub);
            String userName = String.valueOf(name);
            if (userId.isBlank() || userName.isBlank()) {
                return Optional.empty();
            }
            long exp = Long.parseLong(String.valueOf(payload.get("exp")));
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new SvfUserContext(userId, userName));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private boolean isCompleted(MockArtifact artifact) {
        return !Instant.now().isBefore(completedAt(artifact));
    }

    private Instant completedAt(MockArtifact artifact) {
        return artifact.createdAt().plus(Duration.ofMillis(properties.resolvedMockProcessingDelayMillis()));
    }

    private ResponseEntity<SvfApiError> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(properties.resolvedMockRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SvfApiError("TOO_MANY_REQUESTS", "Mock rate limit is enabled", ""));
    }

    private ResponseEntity<SvfApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SvfApiError(code, message, ""));
    }

    private String findCsvPart(MultiValueMap<String, String> form) {
        return form.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("data/"))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse("");
    }
}
