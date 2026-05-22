package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class NotificationProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessingService.class);

    private final NotificationTaskRepository repository;
    private final UserService userService;

    public NotificationProcessingService(NotificationTaskRepository repository,
                                         UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    /**
     * Обрабатывает сообщение от пользователя
     * @param messageText текст сообщения
     * @param chatId идентификатор чата
     * @param timeZone часовой пояс пользователя
     * @return текст ответа для отправки пользователю
     */
    public String processMessage(String messageText, Long chatId, String timeZone) {
        logger.info("Processing message: '{}' from chat: {} with timezone: {}",
                messageText, chatId, timeZone);

        if (messageText == null || messageText.trim().isEmpty()) {
            return getHelpMessage();
        }

        // Проверяем, что это команда
        if (messageText.startsWith("/")) {
            return handleCommand(messageText, chatId, timeZone);
        }

        // Пробуем распарсить как напоминание
        return parseAndCreateNotification(messageText, chatId, timeZone);
    }

    private String handleCommand(String command, Long chatId, String timeZone) {
        switch (command.toLowerCase()) {
            case "/start":
                return "👋 Привет! Я бот-напоминатор.\n\n" +
                        "Ваш часовой пояс: " + timeZone + "\n\n" +
                        "Отправь мне сообщение в формате:\n" +
                        "📅 ДД.ММ.ГГГГ ЧЧ:MM Текст напоминания\n\n" +
                        "Пример: 20.05.2026 14:30 Позвонить маме\n\n" +
                        "Или просто:\n" +
                        "20.05.2026 Позвонить маме (напомню в 00:00)";
            case "/help":
                return getHelpMessage();
            default:
                return "Неизвестная команда. Используйте /help для списка команд.";
        }
    }

    private String getHelpMessage() {
        return "📝 Как пользоваться ботом:\n\n" +
                "1️⃣ С датой и временем:\n" +
                "   20.05.2026 14:30 Купить молоко\n\n" +
                "2️⃣ Только с датой (напомню в 00:00):\n" +
                "   20.05.2026 Купить молоко\n\n" +
                "3️⃣ Команды:\n" +
                "   /start - начать работу\n" +
                "   /help - эта справка\n" +
                "   /timezone - узнать текущий часовой пояс\n" +
                "   /settimezone - изменить часовой пояс";
    }

    /**
     * Парсит сообщение и создает напоминание
     */
    private String parseAndCreateNotification(String messageText, Long chatId, String timeZone) {
        try {
            logger.info("=== DEBUG: parseAndCreateNotification ===");
            logger.info("Message: {}, ChatId: {}, TimeZone: {}", messageText, chatId, timeZone);
            // Разбиваем сообщение на части
            String[] parts = messageText.split(" ", 3);

            if (parts.length < 2) {
                return "❌ Неверный формат!\n" + getHelpMessage();
            }

            String datePart = parts[0]; // ДД.ММ.ГГГГ
            String timePart = null;
            String notificationText;

            // Проверяем, указано ли время
            if (parts.length >= 2 && parts[1].contains(":")) {
                timePart = parts[1];
                notificationText = parts[2];
            } else {
                // Время не указано, используем 00:00
                timePart = "00:00";
                notificationText = parts[1];
            }

            // Парсим дату и время, которые ввел пользователь (в его часовом поясе)
            LocalDateTime userLocalTime = parseUserDateTime(datePart, timePart);
            logger.info("User entered time: {} ({})", userLocalTime, timeZone);
            if (userLocalTime == null) {
                return "❌ Не удалось распознать дату и время.\n\n" +
                        "Используйте формат: ДД.ММ.ГГГГ ЧЧ:MM Текст\n" +
                        "Например: 20.05.2026 14:30 Позвонить маме";
            }
            logger.info("User local time: {}", userLocalTime);
            logger.info("User time zone: {}", timeZone);

            ZonedDateTime nowInUserZone = ZonedDateTime.now(ZoneId.of(timeZone));
            logger.info("Current time in user zone: {}",
                    nowInUserZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            LocalDateTime currentUserTime = nowInUserZone.toLocalDateTime();

            logger.info("User local time (scheduled): {}", userLocalTime);
            logger.info("Current time (user zone): {}", currentUserTime);



            logger.info("Scheduled time UTC: {}", userLocalTime);
            logger.info("Current time UTC: {}", currentUserTime);
            logger.info("Time difference (seconds): {}",
                    java.time.Duration.between(currentUserTime, userLocalTime).getSeconds());

            logger.info("User time: {} ({}) -> UTC time: {}", userLocalTime, timeZone, userLocalTime);

            // Проверяем, что время не в прошлом
            if (userLocalTime.isBefore(currentUserTime)) {
                logger.warn("Time is in the past! User time: {} ({}) -> UTC: {}, Now UTC: {}",
                        userLocalTime, timeZone, userLocalTime, currentUserTime);
                return "❌ Нельзя создать напоминание на прошедшее время!\n" +
                        "Укажите будущую дату и время.\n\n" +
                        "Ваше время: " + userLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + " (" + timeZone + ")\n" +
                        "Текущее время: " + currentUserTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + " (" + timeZone + ")";
            }
            if (Math.abs(java.time.Duration.between(currentUserTime, userLocalTime).getSeconds()) < 60) {
                logger.info("Time is now or within next minute, allowing");
            }
            // Конвертируем в UTC для хранения в БД
            LocalDateTime scheduledTimeUTC = convertToUTC(userLocalTime, timeZone);
            LocalDateTime nowUTC = LocalDateTime.now();

            // Создаем и сохраняем задачу
            NotificationTask task = createAndSaveTask(chatId, notificationText, scheduledTimeUTC, timeZone);

            // Форматируем ответ для пользователя (показываем в его часовом поясе)
            String formattedTime = userLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            return String.format("✅ Напоминание сохранено!\n" +
                            "📅 Когда: %s (по вашему часовому поясу)\n" +
                            "📝 Текст: %s\n" +
                            "🆔 ID задачи: %d",
                    formattedTime, notificationText, task.getId());

        } catch (DateTimeParseException e) {
            logger.error("Failed to parse date/time from message: {}", messageText, e);
            return "❌ Не удалось распознать дату и время.\n\n" +
                    "Используйте формат: ДД.ММ.ГГГГ ЧЧ:MM Текст\n" +
                    "Например: 20.05.2026 14:30 Позвонить маме";
        } catch (Exception e) {
            logger.error("Error processing message: {}", messageText, e);
            return "❌ Произошла ошибка при сохранении напоминания. Попробуйте еще раз.";
        }
    }

    /**
     * Парсит строки даты и времени в LocalDateTime
     */
    private LocalDateTime parseUserDateTime(String datePart, String timePart) {
        try {
            return LocalDateTime.parse(
                    datePart + " " + timePart,
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            );
        } catch (DateTimeParseException e) {
            logger.error("Failed to parse: {} {}", datePart, timePart, e);
            return null;
        }
    }

    /**
     * Конвертирует локальное время пользователя в UTC
     */
    private LocalDateTime convertToUTC(LocalDateTime userLocalTime, String timeZone) {
        // Важно: userLocalTime - это время, которое указал пользователь в своем часовом поясе
        // Но без привязки к конкретной дате, LocalDateTime не знает о часовом поясе
        // Поэтому мы создаем ZonedDateTime с указанием, что это время в часовом поясе пользователя
        ZonedDateTime userZonedTime = ZonedDateTime.of(userLocalTime, ZoneId.of(timeZone));
        ZonedDateTime utcTime = userZonedTime.withZoneSameInstant(ZoneId.of("UTC"));
        return utcTime.toLocalDateTime();
    }

    /**
     * Создает и сохраняет задачу в БД
     */
    private NotificationTask createAndSaveTask(Long chatId, String messageText,
                                               LocalDateTime scheduledTimeUTC, String timeZone) {
        NotificationTask task = new NotificationTask(chatId, messageText, scheduledTimeUTC, timeZone);
        return repository.save(task);
    }

    /**
     * Конвертирует UTC время в локальное время пользователя (для отображения)
     */
    public LocalDateTime convertToUserTime(LocalDateTime utcTime, String timeZone) {
        ZonedDateTime utcZoned = utcTime.atZone(ZoneId.of("UTC"));
        ZonedDateTime userZoned = utcZoned.withZoneSameInstant(ZoneId.of(timeZone));
        return userZoned.toLocalDateTime();
    }
}