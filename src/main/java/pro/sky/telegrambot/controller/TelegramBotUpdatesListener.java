package pro.sky.telegrambot.controller;

import com.pengrad.telegrambot.model.Chat;
import pro.sky.telegrambot.model.User;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pro.sky.telegrambot.service.NotificationProcessingService;
import pro.sky.telegrambot.service.UserService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Component
public class TelegramBotUpdatesListener {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private final TelegramBot telegramBot;
    private final UserService userService;
    private final NotificationProcessingService notificationProcessingService;

    // Кэш для отслеживания ожидания ответа о часовом поясе
    // В реальном проекте лучше использовать Redis или базу данных
    private final java.util.Map<Long, Boolean> waitingForTimeZone = new java.util.concurrent.ConcurrentHashMap<>();

    // Популярные часовые пояса
    private static final List<String> COMMON_TIMEZONES = Arrays.asList(
            "Europe/Moscow",      // Москва (UTC+3)
            "Europe/Kaliningrad", // Калининград (UTC+2)
            "Europe/Samara",      // Самара (UTC+4)
            "Asia/Yekaterinburg", // Екатеринбург (UTC+5)
            "Asia/Novosibirsk",   // Новосибирск (UTC+7)
            "Asia/Irkutsk",       // Иркутск (UTC+8)
            "Asia/Vladivostok",   // Владивосток (UTC+10)
            "Europe/London",      // Лондон (UTC+0)
            "Europe/Paris",       // Париж (UTC+1)
            "America/New_York",   // Нью-Йорк (UTC-5)
            "Asia/Tokyo"          // Токио (UTC+9)
    );

    public TelegramBotUpdatesListener(UserService userService,
                                      NotificationProcessingService notificationProcessingService,
                                      @Value("${telegram.bot.token}") String botToken) {
        this.userService = userService;
        this.notificationProcessingService = notificationProcessingService;
        this.telegramBot = new TelegramBot(botToken);
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                handleUpdate(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        logger.info("Telegram bot started listening for updates");
    }

    private void handleUpdate(Update update) {
        try {
            if (update.message() != null && update.message().text() != null) {
                Long chatId = update.message().chat().id();
                String username = update.message().from().username();
                String messageText = update.message().text();

                logger.info("Received message: '{}' from chat: {}", messageText, chatId);

                // Проверяем, группа это или личный чат
                boolean isGroup = update.message().chat().type() == Chat.Type.group ||
                        update.message().chat().type() == Chat.Type.supergroup;

                logger.info("Received message: '{}' from chat: {} (isGroup: {})",
                        messageText, chatId, isGroup);

                // Если это группа — обрабатываем как групповое напоминание
                if (isGroup) {
                    // Игнорируем команды типа /start в группе
                    if (messageText.startsWith("/")) {
                        return;
                    }

                    // Обрабатываем групповое сообщение
                    String response = notificationProcessingService.processGroupMessage(messageText, chatId, username);
                    if (response != null && !response.isEmpty()) {
                        sendMessage(chatId, response);
                    }
                    return;
                }

                // Проверяем, ожидаем ли мы ответ о часовом поясе
                if (waitingForTimeZone.containsKey(chatId) && waitingForTimeZone.get(chatId)) {
                    handleTimeZoneResponse(chatId, messageText, username);
                    return;
                }

                // Проверяем, существует ли пользователь и есть ли у него часовой пояс
                if (!userService.userExists(chatId)) {
                    // Новый пользователь - запрашиваем часовой пояс
                    requestTimeZone(chatId);
                    return;
                }

                String userTimeZone = userService.getUserTimeZone(chatId);
                if (userTimeZone == null || userTimeZone.isEmpty()) {
                    // У пользователя нет часового пояса - запрашиваем
                    requestTimeZone(chatId);
                    return;
                }

                // Обработка команд
                if (messageText.startsWith("/")) {
                    handleCommand(chatId, messageText);
                    return;
                }

                // Обработка напоминания
                String response = notificationProcessingService.processMessage(messageText, chatId, userTimeZone);
                if (response != null && !response.isEmpty()) {
                    sendMessage(chatId, response);
                }
            }
        } catch (Exception e) {
            logger.error("Error handling update: {}", update, e);
        }
    }

    private void requestTimeZone(Long chatId) {
        waitingForTimeZone.put(chatId, true);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                new KeyboardButton("Europe/Moscow"),
                new KeyboardButton("Europe/Kaliningrad"),
                new KeyboardButton("Europe/Samara"),
                new KeyboardButton("Asia/Yekaterinburg"),
                new KeyboardButton("Другой часовой пояс")
        );
        keyboard.resizeKeyboard(true);
        keyboard.oneTimeKeyboard(true);

        String message = "🌍 Добро пожаловать! Пожалуйста, выберите ваш часовой пояс.\n\n" +
                "Это нужно, чтобы напоминания приходили в правильное время.\n\n" +
                "Самые популярные пояса:\n" +
                "• Europe/Moscow (Москва, UTC+3)\n" +
                "• Europe/Kaliningrad (Калининград, UTC+2)\n" +
                "• Europe/Samara (Самара, UTC+4)\n" +
                "• Europe/Yekaterinburg (Екатеринбург, UTC+5)\n\n" +
                "Если вашего пояса нет в списке, нажмите 'Другой часовой пояс' и введите его в формате:\n" +
                "Например: Asia/Novosibirsk\n\n" +
                "Список всех поясов: https://en.wikipedia.org/wiki/List_of_tz_database_time_zones";

        SendMessage request = new SendMessage(chatId, message);
        request.replyMarkup(keyboard);
        telegramBot.execute(request);
        logger.info("Requested timezone from chat: {}", chatId);
    }

    private void handleTimeZoneResponse(Long chatId, String response, String username) {
        waitingForTimeZone.remove(chatId);

        String timeZone = null;

        if (response.equals("Другой часовой пояс")) {
            // Запрашиваем ручной ввод
            sendMessage(chatId, "Введите ваш часовой пояс в формате: Europe/Moscow\n\n" +
                    "Примеры: Asia/Novosibirsk, America/New_York, Europe/London");

            // Снова ждем ответ, но уже для ручного ввода
            waitingForTimeZone.put(chatId, true);
            return;
        }

        // Проверяем, что ответ - корректный часовой пояс
        if (isValidTimeZone(response)) {
            timeZone = response;
        } else {
            // Пробуем найти ближайший подходящий пояс
            String suggested = suggestTimeZone(response);
            if (suggested != null) {
                sendMessage(chatId, "Возможно, вы имели в виду: " + suggested + "?\n" +
                        "Если да, введите его точно, или выберите другой.");
                waitingForTimeZone.put(chatId, true);
                return;
            } else {
                sendMessage(chatId, "❌ Неверный формат часового пояса.\n\n" +
                        "Используйте формат: Region/City\n" +
                        "Например: Europe/Moscow, Asia/Novosibirsk\n\n" +
                        "Попробуйте еще раз или выберите из списка.");
                requestTimeZone(chatId);
                return;
            }
        }

        // Сохраняем часовой пояс
        userService.findOrCreateUser(chatId, timeZone, username);

        // Проверяем, что сохранилось
        userService.debugUserTimeZone(chatId);

        sendMessage(chatId, "✅ Часовой пояс установлен: " + timeZone + "\n\n" +
                "Теперь вы можете создавать напоминания в формате:\n" +
                "📅 ДД.ММ.ГГГГ ЧЧ:MM Текст напоминания\n\n" +
                "Пример: 20.05.2026 14:30 Позвонить маме\n\n" +
                "Используйте /help для справки.");

        logger.info("User {} set timezone to: {}", chatId, timeZone);
    }

    private void handleCommand(Long chatId, String command) {
        switch (command.toLowerCase()) {
            case "/start":
                String timeZone = userService.getUserTimeZone(chatId);
                sendMessage(chatId, "👋 Привет! Я бот-напоминатор.\n\n" +
                        "Ваш часовой пояс: " + timeZone + "\n\n" +
                        "Отправь мне сообщение в формате:\n" +
                        "📅 ДД.ММ.ГГГГ ЧЧ:MM Текст напоминания\n\n" +
                        "Пример: 20.05.2026 14:30 Позвонить маме");
                break;

            case "/help":
                sendMessage(chatId, "📝 Команды бота:\n\n" +
                        "/start - начать работу\n" +
                        "/help - эта справка\n" +
                        "/timezone - узнать часовой пояс\n" +
                        "/settimezone - изменить часовой пояс\n" +
                        "/mytasks - список моих напоминаний\n" +
                        "/delete ID - удалить напоминание\n" +
                        "/myprofile - информация о профиле\n\n" +
                        "📅 Создание напоминаний:\n" +
                        "• 20.05.2026 14:30 Текст (однократное)\n" +
                        "• 20.05 Текст (ежегодно в 09:00)\n" +
                        "• 20.05 14:30 Текст (ежегодно)");
                break;

            case "/timezone":
                String currentTz = userService.getUserTimeZone(chatId);
                sendMessage(chatId, "🌍 Ваш текущий часовой пояс: " + currentTz);
                break;

            case "/settimezone":
                requestTimeZone(chatId);
                break;
            case "/resettimezone":
                userService.resetTimeZone(chatId);
                requestTimeZone(chatId);
                break;

            case "/myzones":
                sendMessage(chatId, getTimeZonesDisplay());
                break;
            case "/myprofile":
                User user = userService.getUserByChatId(chatId);
                if (user != null) {
                    sendMessage(chatId, "📋 Ваш профиль:\n" +
                            "🆔 Chat ID: " + user.getChatId() + "\n" +
                            "👤 Username: " + user.getUsername() + "\n" +
                            "🌍 Timezone: " + user.getTimeZone() + "\n" +
                            "📅 Registered: " + user.getRegisteredAt());
                } else {
                    sendMessage(chatId, "❌ Профиль не найден");
                }
                break;
            // В методе handleCommand добавьте новые case'ы:
            case "/delete":
                handleDeleteCommand(chatId, command);
                break;

            case "/mytasks":
                String tasksList = notificationProcessingService.listNotifications(chatId);
                sendMessage(chatId, tasksList);
                break;
            case "/deleteall":
                int deleted = notificationProcessingService.deleteAllNotifications(chatId);
                sendMessage(chatId, "✅ Удалено " + deleted + " напоминаний.");
                break;

            default:
                sendMessage(chatId, "Неизвестная команда. Используйте /help для списка команд.");
        }
    }

    private String getTimeZonesDisplay() {
        LocalDateTime now = LocalDateTime.now();
        StringBuilder sb = new StringBuilder("🕐 Текущее время в разных часовых поясах:\n\n");

        for (String tz : COMMON_TIMEZONES) {
            try {
                ZonedDateTime time = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of(tz));
                sb.append("• ").append(tz).append(": ");
                sb.append(time.format(DateTimeFormatter.ofPattern("HH:mm")));
                sb.append("\n");
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }

        return sb.toString();
    }

    private boolean isValidTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String suggestTimeZone(String input) {
        // Простое предположение на основе ключевых слов
        String lowerInput = input.toLowerCase();

        if (lowerInput.contains("мос") || lowerInput.contains("moscow")) return "Europe/Moscow";
        if (lowerInput.contains("спб") || lowerInput.contains("peter")) return "Europe/Moscow";
        if (lowerInput.contains("новос") || lowerInput.contains("novos")) return "Asia/Novosibirsk";
        if (lowerInput.contains("екат") || lowerInput.contains("ekat")) return "Europe/Yekaterinburg";
        if (lowerInput.contains("самар") || lowerInput.contains("samara")) return "Europe/Samara";
        if (lowerInput.contains("калин") || lowerInput.contains("kaliningrad")) return "Europe/Kaliningrad";
        if (lowerInput.contains("влад") || lowerInput.contains("vlad")) return "Asia/Vladivostok";
        if (lowerInput.contains("иркут") || lowerInput.contains("irkutsk")) return "Asia/Irkutsk";

        return null;
    }

    public void sendMessage(long chatId, String text) {
        try {
            SendMessage request = new SendMessage(chatId, text);
            telegramBot.execute(request);
            logger.info("Sent message to chat {}: {}", chatId, text);
        } catch (Exception e) {
            logger.error("Failed to send message to chat {}: {}", chatId, e.getMessage());
        }
    }

    public void sendNotification(long chatId, String messageText) {
        sendMessage(chatId, "🔔 НАПОМИНАНИЕ 🔔\n\n" + messageText);
    }
    /**
     * Обрабатывает команду удаления /delete <id>
     */
    private void handleDeleteCommand(Long chatId, String command) {
        String[] parts = command.split(" ");

        if (parts.length != 2) {
            sendMessage(chatId, "❌ Используйте: /delete ID_напоминания\n\n" +
                    "Чтобы узнать ID, отправьте /mytasks");
            return;
        }

        try {
            Long taskId = Long.parseLong(parts[1]);
            String result = notificationProcessingService.deleteNotification(taskId, chatId);
            sendMessage(chatId, result);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ ID должен быть числом.\nПример: /delete 42");
        }
    }
}