package com.plexus.personal.user.application;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String username) {
        super("User not found: " + username);
    }
}
