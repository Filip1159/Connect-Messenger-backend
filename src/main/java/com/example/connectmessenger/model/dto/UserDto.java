package com.example.connectmessenger.model.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDto {
    private final Long id;
    private final String name;
    private final String surname;
    private final String username;
    /** Name of the uploaded avatar (changes with every upload); null = none, GET /users/{id}/avatar serves it. */
    private final String avatarKey;
}
