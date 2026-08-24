package com.example.svf.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class InMemoryUserStore {
    private final Map<String, UserAccount> users = Map.of(
            "zhangsan", new UserAccount("zhangsan", "password", "zhangsan@example.com", "张三", "USER"),
            "lisi", new UserAccount("lisi", "password", "lisi@example.com", "李四", "USER"),
            "wangwu", new UserAccount("wangwu", "password", "wangwu@example.com", "王五", "USER")
    );

    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        UserAccount account = users.get(username);
        if (account == null || !account.password().equals(password)) {
            return Optional.empty();
        }
        return Optional.of(account.toAuthenticatedUser());
    }

    public Optional<AuthenticatedUser> findByUsername(String username) {
        UserAccount account = users.get(username);
        return account == null ? Optional.empty() : Optional.of(account.toAuthenticatedUser());
    }

    private record UserAccount(String username, String password, String userId, String userName, String role) {
        AuthenticatedUser toAuthenticatedUser() {
            return new AuthenticatedUser(username, userId, userName, role);
        }
    }
}
