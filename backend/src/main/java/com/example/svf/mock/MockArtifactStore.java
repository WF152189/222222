package com.example.svf.mock;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MockArtifactStore {
    private final ConcurrentMap<String, MockArtifact> artifacts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> actionToArtifact = new ConcurrentHashMap<>();

    public void save(MockArtifact artifact) {
        artifacts.put(artifact.id(), artifact);
        actionToArtifact.put(artifact.actionId(), artifact.id());
    }

    public Optional<MockArtifact> findArtifact(String artifactId) {
        return Optional.ofNullable(artifacts.get(artifactId));
    }

    public Optional<MockArtifact> findByAction(String actionId) {
        String artifactId = actionToArtifact.get(actionId);
        if (artifactId == null) {
            return Optional.empty();
        }
        return findArtifact(artifactId);
    }

    public String newArtifactId() {
        return UUID.randomUUID().toString();
    }

    public String newActionId() {
        return UUID.randomUUID().toString();
    }
}
