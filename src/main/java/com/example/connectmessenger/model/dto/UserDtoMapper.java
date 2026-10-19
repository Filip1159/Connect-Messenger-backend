package com.example.connectmessenger.model.dto;


import com.example.connectmessenger.model.User;

import java.util.List;
import java.util.stream.Collectors;

public class UserDtoMapper {
    private UserDtoMapper() {}
    public static List<UserDto> mapToUserDtos(List<User> users) {
        return users.stream()
                .map(UserDtoMapper::mapToUserDto)
                .collect(Collectors.toList());
    }

    public static UserDto mapToUserDto(User u) {
        return UserDto.builder()
                .id(u.getId())
                .name(u.getName())
                .surname(u.getSurname())
                .username(u.getUsername())
                .avatarKey(u.getAvatarKey())
                .build();
    }
}
