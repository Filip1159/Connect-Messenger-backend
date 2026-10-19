package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.UserDto;
import com.example.connectmessenger.model.dto.UserDtoMapper;
import com.example.connectmessenger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{text}")
    public List<UserDto> getUsersByQuery(@PathVariable String text, @AuthenticationPrincipal User principal) {
        List<User> users = userService.getUsersByQuery(text, principal.getId());
        return UserDtoMapper.mapToUserDtos(users);

    }
}
