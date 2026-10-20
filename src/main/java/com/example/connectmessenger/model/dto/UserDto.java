package com.example.connectmessenger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final String surname;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final String username;
    /** Name of the uploaded avatar (changes with every upload); null = none, GET /users/{id}/avatar serves it. */
    private final String avatarKey;
}
