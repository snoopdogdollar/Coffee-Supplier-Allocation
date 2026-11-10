package com.coffeemongers.service;

import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;

public interface AuthenticationService {
    DemoUser login(String email, char[] password);
    DemoUser register(String fullName, String email, char[] password, AccountRole role);
}
