package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.sky.telegrambot.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.repository.UserRepository;

import java.time.LocalDateTime;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    @Autowired
    private UserRepository userRepository;

    public User findOrCreateUser(Long chatId, String timeZone, String username) {
        return userRepository.findByChatId(chatId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setChatId(chatId);
                    newUser.setUsername(username != null ? username : "unknown");
                    newUser.setTimeZone(timeZone);  // ← ОБЯЗАТЕЛЬНО!
                    newUser.setRegisteredAt(LocalDateTime.now());
                    logger.info("Created new user with timezone: {} for chatId: {}", timeZone, chatId);
                    return userRepository.save(newUser);
                });
    }

    public void saveUserTimeZone(Long chatId, String timeZone) {
        User user = userRepository.findByChatId(chatId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setTimeZone(timeZone);
        userRepository.save(user);
    }

    public String getUserTimeZone(Long chatId) {
        return userRepository.findByChatId(chatId)
                .map(User::getTimeZone)
                .orElse("Europe/Moscow"); // часовой пояс по умолчанию
    }

    public boolean userExists(Long chatId) {
        return userRepository.existsByChatId(chatId);
    }
    public void debugUserTimeZone(Long chatId) {
        User user = userRepository.findByChatId(chatId).orElse(null);
        if (user != null) {
            logger.info("User {} has timezone: {}", chatId, user.getTimeZone());
        } else {
            logger.warn("User {} not found", chatId);
        }
    }
    public User getUserByChatId(Long chatId) {
        return userRepository.findByChatId(chatId).orElse(null);
    }
    public void resetTimeZone(Long chatId) {
        User user = userRepository.findByChatId(chatId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setTimeZone(null);
        userRepository.save(user);
        logger.info("Reset timezone for user chatId: {}", chatId);
    }
}

