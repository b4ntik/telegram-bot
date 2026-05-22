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
                "1️⃣ Однократное напоминание:\n" +
                "   20.05.2026 14:30 Купить молоко\n\n" +
                "2️⃣ Ежегодное напоминание (в 09:00):\n" +
                "   20.05 День рождения мамы\n\n" +
                "3️⃣ Ежегодное в указанное время:\n" +
                "   20.05 14:30 Позвонить маме\n\n" +
                "📌 Команды:\n" +
                "/start - начать работу\n" +
                "/help - эта справка\n" +
                "/timezone - узнать часовой пояс\n" +
                "/settimezone - изменить часовой пояс\n" +
                "/myprofile - информация о профиле";
    }

    /**
     * Парсит сообщение и создает напоминание
     */
    private String parseAndCreateNotification(String messageText, Long chatId, String timeZone) {
        try {
            logger.info("=== CREATING NOTIFICATION ===");
            logger.info("Message: {}, TimeZone: {}", messageText, timeZone);

            String[] parts = messageText.split(" ", 3);

            if (parts.length < 2) {
                return "❌ Неверный формат!\n" + getHelpMessage();
            }

            String datePart = parts[0];
            String timePart = null;
            String notificationText;
            boolean isYearly = false;
            String[] dayMonth = null;

            // Проверяем, что это не полная дата с годом (ДД.ММ.ГГГГ)
            boolean hasYear = datePart.matches("\\d{2}\\.\\d{2}\\.\\d{4}");
            boolean isDayMonth = datePart.matches("\\d{2}\\.\\d{2}") && !hasYear;

            if (isDayMonth) {
                // Формат: ДД.ММ ТЕКСТ или ДД.ММ ЧЧ:MM ТЕКСТ
                isYearly = true;

                if (parts.length >= 2 && parts[1].contains(":")) {
                    // Есть время: ДД.ММ ЧЧ:MM ТЕКСТ
                    timePart = parts[1];
                    notificationText = parts[2];
                } else {
                    // Без времени: ДД.ММ ТЕКСТ
                    timePart = "09:00";  // По умолчанию 09:00
                    notificationText = parts[1];
                }
            } else if (hasYear) {
                // Формат с годом: ДД.ММ.ГГГГ (обработка существующая)
                if (parts.length >= 2 && parts[1].contains(":")) {
                    timePart = parts[1];
                    notificationText = parts[2];
                } else {
                    timePart = "00:00";
                    notificationText = parts[1];
                }
            } else {
                return "❌ Неверный формат даты.\n\n" +
                        "Используйте:\n" +
                        "• 20.05.2026 14:30 Текст (однократное)\n" +
                        "• 20.05 Текст (ежегодное в 09:00)\n" +
                        "• 20.05 14:30 Текст (ежегодное в указанное время)";
            }

            // Текущая дата в часовом поясе пользователя
            ZonedDateTime nowInUserZone = ZonedDateTime.now(ZoneId.of(timeZone));
            int currentYear = nowInUserZone.getYear();

            LocalDateTime userLocalTime;

            if (isYearly) {
                // Парсим только день и месяц, год берем текущий
                dayMonth = datePart.split("\\.");
                int day = Integer.parseInt(dayMonth[0]);
                int month = Integer.parseInt(dayMonth[1]);

                userLocalTime = LocalDateTime.of(
                        currentYear, month, day,
                        Integer.parseInt(timePart.split(":")[0]),
                        Integer.parseInt(timePart.split(":")[1])
                );

                // Если дата уже прошла в этом году, переносим на следующий год
                if (userLocalTime.isBefore(nowInUserZone.toLocalDateTime())) {
                    userLocalTime = userLocalTime.plusYears(1);
                    logger.info("Yearly reminder moved to next year: {}", userLocalTime);
                }
            } else {
                // Существующая логика для полной даты
                userLocalTime = parseUserDateTime(datePart, timePart);
            }

            if (userLocalTime == null) {
                return "❌ Не удалось распознать дату и время.\n\n" +
                        "Используйте формат: ДД.ММ.ГГГГ ЧЧ:MM Текст\n" +
                        "или: ДД.ММ Текст (ежегодно в 09:00)";
            }

            logger.info("User local time: {} (yearly={})", userLocalTime, isYearly);

            // Конвертируем в UTC
            LocalDateTime scheduledTimeUTC = convertToUTC(userLocalTime, timeZone);

            // Проверка на прошедшее время (только для не-yearly или если в этом году)
            if (!isYearly && scheduledTimeUTC.isBefore(LocalDateTime.now())) {
                return "❌ Нельзя создать напоминание на прошедшее время!";
            }

            // Сохраняем задачу
            NotificationTask task = createAndSaveTask(chatId, notificationText, scheduledTimeUTC, timeZone);

            // Дополнительно сохраняем признак ежегодности (нужно добавить поле в NotificationTask)
            if (isYearly) {
                task.setIsYearly(true);
                task.setYearlyDay(dayMonth[0]);
                task.setYearlyMonth(dayMonth[1]);
                task.setYearlyTime(timePart);
                repository.save(task);
            }

            String formattedTime = userLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            String yearlyInfo = isYearly ? "\n🔄 Повтор: ежегодно" : "";

            return String.format("✅ Напоминание сохранено!%s\n📅 Когда: %s (по вашему часовому поясу)\n📝 Текст: %s\n🆔 ID задачи: %d",
                    yearlyInfo, formattedTime, notificationText, task.getId());

        } catch (Exception e) {
            logger.error("Error creating notification: {}", messageText, e);
            return "❌ Ошибка при создании напоминания. Проверьте формат:\n" +
                    "• 20.05.2026 14:30 Текст\n" +
                    "• 20.05 Текст (ежегодно в 09:00)\n" +
                    "• 20.05 14:30 Текст (ежегодно)";
        }
    }
    /**
     * Конвертирует локальное время пользователя в UTC
         private LocalDateTime convertToUTC(LocalDateTime userLocalTime, String timeZone) {
        ZonedDateTime userZonedTime = userLocalTime.atZone(ZoneId.of(timeZone));
        ZonedDateTime utcTime = userZonedTime.withZoneSameInstant(ZoneId.of("UTC"));
        return utcTime.toLocalDateTime();
    }
*/
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
    LocalDateTime convertToUTC(LocalDateTime userLocalTime, String timeZone) {
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