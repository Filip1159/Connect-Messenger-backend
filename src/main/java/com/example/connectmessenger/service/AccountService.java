package com.example.connectmessenger.service;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.UserBlock;
import com.example.connectmessenger.repo.UserBlockRepo;
import com.example.connectmessenger.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Everything a person does with their own account: sign up, profile, password, avatar, blocking. */
@Service
@RequiredArgsConstructor
public class AccountService {
    public static final int MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 72;  // bcrypt ignores the rest
    static final int MAX_NAME_LENGTH = 50;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,30}");

    public record Avatar(byte[] content, String contentType) {
    }

    private final UserRepo userRepo;
    private final UserBlockRepo userBlockRepo;
    private final AwsS3Client s3Client;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Transactional
    public User register(String username, String name, String surname, String password) {
        String login = username == null ? "" : username.strip();
        if (!USERNAME.matcher(login).matches()) {
            throw badRequest("The username must have 3-30 characters: letters, digits, dot, dash or underscore");
        }
        validatePassword(password);
        if (userRepo.existsByUsernameIgnoreCase(login)) {
            throw taken();
        }
        var user = new User();
        user.setUsername(login);
        user.setName(requiredName(name, "name"));
        user.setSurname(requiredName(surname, "surname"));
        user.setPassword(passwordEncoder.encode(password));
        try {
            return userRepo.save(user);
        } catch (DataIntegrityViolationException e) {  // someone registered the same name a moment ago
            throw taken();
        }
    }

    @Transactional(readOnly = true)
    public User getProfile(Long userId) {
        return getUser(userId);
    }

    @Transactional
    public User updateProfile(Long userId, String name, String surname) {
        User user = getUser(userId);
        user.setName(requiredName(name, "name"));
        user.setSurname(requiredName(surname, "surname"));
        return userRepo.save(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getUser(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The current password is wrong");
        }
        validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    @Transactional
    public User setAvatar(Long userId, MultipartFile file) {
        if (file != null && file.getSize() > MAX_AVATAR_SIZE) {
            throw badRequest("The avatar can have at most 2 MB");
        }
        User user = getUser(userId);
        var image = s3Client.detectImage(file);
        String key = "avatar_" + userId + "_" + UUID.randomUUID() + "." + image.extension();
        s3Client.putFile(key, file, image);
        user.setAvatarKey(key);
        return userRepo.save(user);
    }

    @Transactional
    public User removeAvatar(Long userId) {
        User user = getUser(userId);
        user.setAvatarKey(null);
        return userRepo.save(user);
    }

    @Transactional(readOnly = true)
    public Avatar getAvatar(Long userId) {
        String key = getUser(userId).getAvatarKey();
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No avatar");
        }
        String type = key.endsWith(".png") ? "image/png" : key.endsWith(".gif") ? "image/gif" : "image/jpeg";
        return new Avatar(s3Client.getFile(key), type);
    }

    @Transactional
    public void block(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw badRequest("You can't block yourself");
        }
        getUser(targetId);
        if (userBlockRepo.findByBlockerIdAndBlockedId(userId, targetId).isEmpty()) {
            userBlockRepo.save(UserBlock.builder().blockerId(userId).blockedId(targetId).build());
        }
    }

    @Transactional
    public void unblock(Long userId, Long targetId) {
        userBlockRepo.findByBlockerIdAndBlockedId(userId, targetId).ifPresent(userBlockRepo::delete);
    }

    @Transactional(readOnly = true)
    public List<User> blockedUsers(Long userId) {
        return userBlockRepo.findByBlockerId(userId).stream()
                .map(b -> userRepo.findById(b.getBlockedId()).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
    }

    private User getUser(Long id) {
        return userRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH
                || password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw badRequest("The password must have %d-%d characters, with a letter and a digit"
                    .formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH));
        }
    }

    private static String requiredName(String value, String field) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > MAX_NAME_LENGTH) {
            throw badRequest("The " + field + " must have 1-" + MAX_NAME_LENGTH + " characters");
        }
        return text;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException taken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "This username is taken");
    }
}
