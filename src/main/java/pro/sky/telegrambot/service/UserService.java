package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.repository.UserRepository;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public User findOrCreateUser(Long chatId, String timeZone) {
        return userRepository.findByChatId(chatId)
                .orElseGet(() -> {
                    User newUser = new User(chatId, timeZone);
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
}

