package com.coffeemongers.service;

import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.model.auth.AccountRole;
import java.util.Optional;

public interface SessionService {
    Optional<DemoUser> currentUser();
    void start(DemoUser user);
    void end();
    AccountRole activeWorkspace();
    void switchWorkspace(AccountRole role);
}
