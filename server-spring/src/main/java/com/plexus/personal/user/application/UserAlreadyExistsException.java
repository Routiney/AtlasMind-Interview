package com.plexus.personal.user.application;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String username) {
        super("Username already exists: " + username);
    }
}
