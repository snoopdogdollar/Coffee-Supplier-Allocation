package com.coffeemongers.service;

import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.model.auth.AccountRole;
import java.util.Optional;

public final class InMemorySessionService implements SessionService {
    private DemoUser activeUser;
    private AccountRole activeWorkspace;
    @Override public synchronized Optional<DemoUser> currentUser() { return Optional.ofNullable(activeUser); }
    @Override public synchronized void start(DemoUser user) { activeUser = user; activeWorkspace = user.role(); }
    @Override public synchronized void end() { activeUser = null; activeWorkspace = null; }
    @Override public synchronized AccountRole activeWorkspace() { return activeWorkspace != null ? activeWorkspace : activeUser == null ? null : activeUser.role(); }
    @Override public synchronized void switchWorkspace(AccountRole role) {
        if (activeUser == null || activeUser.role() != AccountRole.ADMIN) throw new SecurityException("Only Admin can switch workspaces.");
        if (role == null) throw new IllegalArgumentException("Choose a workspace.");
        activeWorkspace = role;
    }
}
