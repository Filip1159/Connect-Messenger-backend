package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.RegisterRequest;
import com.example.connectmessenger.model.dto.UserDto;
import com.example.connectmessenger.model.dto.UserDtoMapper;
import com.example.connectmessenger.service.AccountService;
import com.example.connectmessenger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

/** Accounts: sign up (public), the profile of the signed-in user, avatars and blocking. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersController {
    public record ProfileRequest(String name, String surname) {
    }

    public record PasswordRequest(String currentPassword, String newPassword) {
    }

    private final AccountService accountService;
    private final UserService userService;

    /** People whose username or name contains `query` (at least 2 characters), never the caller. */
    @GetMapping(params = "query")
    public List<UserDto> search(@RequestParam String query, @AuthenticationPrincipal User principal) {
        return UserDtoMapper.mapToUserDtos(userService.getUsersByQuery(query, principal.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@RequestBody RegisterRequest request) {
        return UserDtoMapper.mapToUserDto(accountService.register(
                request.username(), request.name(), request.surname(), request.password()));
    }

    /** The signed-in user as stored now (the name in the login token is the one from sign-in time). */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal User principal) {
        return UserDtoMapper.mapToUserDto(accountService.getProfile(principal.getId()));
    }

    @PatchMapping("/me")
    public UserDto updateProfile(@RequestBody ProfileRequest request, @AuthenticationPrincipal User principal) {
        return UserDtoMapper.mapToUserDto(accountService.updateProfile(principal.getId(), request.name(), request.surname()));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody PasswordRequest request, @AuthenticationPrincipal User principal) {
        accountService.changePassword(principal.getId(), request.currentPassword(), request.newPassword());
    }

    @PutMapping(value = "/me/avatar", consumes = MULTIPART_FORM_DATA_VALUE)
    public UserDto setAvatar(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal User principal) {
        return UserDtoMapper.mapToUserDto(accountService.setAvatar(principal.getId(), file));
    }

    @DeleteMapping("/me/avatar")
    public UserDto removeAvatar(@AuthenticationPrincipal User principal) {
        return UserDtoMapper.mapToUserDto(accountService.removeAvatar(principal.getId()));
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long id) {
        var avatar = accountService.getAvatar(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(avatar.content());
    }

    @GetMapping("/blocked")
    public List<UserDto> blocked(@AuthenticationPrincipal User principal) {
        return UserDtoMapper.mapToUserDtos(accountService.blockedUsers(principal.getId()));
    }

    @PutMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable Long id, @AuthenticationPrincipal User principal) {
        accountService.block(principal.getId(), id);
    }

    @DeleteMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable Long id, @AuthenticationPrincipal User principal) {
        accountService.unblock(principal.getId(), id);
    }
}
