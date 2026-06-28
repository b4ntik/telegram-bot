package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.model.User;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Удаляет напоминание по ID
     * @param taskId ID задачи
     * @param chatId ID чата (для проверки прав)
     * @return результат операции
     */
    public String deleteNotification(Long taskId, Long chatId) {
        try {
            Optional<NotificationTask> taskOpt = repository.findById(taskId);

            if (taskOpt.isEmpty()) {
                return "❌ Напоминание с ID " + taskId + " не найдено.";
            }

            NotificationTask task = taskOpt.get();

            // Проверяем, что задача принадлежит этому пользователю
            if (!task.getChatId().equals(chatId)) {
                return "❌ У вас нет прав для удаления этого напоминания.";
            }

            // Проверяем статус (нельзя удалить уже отправленное)
            if ("SENT".equals(task.getStatus())) {
                return "❌ Это напоминание уже было отправлено и не может быть удалено.";
            }

            // Сохраняем информацию для ответа
            String taskInfo = formatTaskInfo(task);

            // Удаляем
            repository.delete(task);

            logger.info("User {} deleted notification task {}", chatId, taskId);
            return "✅ Напоминание удалено:\n" + taskInfo;

        } catch (Exception e) {
            logger.error("Error deleting notification {}", taskId, e);
            return "❌ Ошибка при удалении напоминания. Попробуйте позже.";
        }
    }

    /**
     * Форматирует информацию о задаче для вывода
     */
    private String formatTaskInfo(NotificationTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 Текст: ").append(task.getMessageText()).append("\n");

        // Конвертируем время в часовой пояс пользователя
        String userTimeZone = "Europe/Moscow"; // по умолчанию
        try {
            User user = userService.getUserByChatId(task.getChatId());
            if (user != null && user.getTimeZone() != null) {
                userTimeZone = user.getTimeZone();
            }
        } catch (Exception e) {
            // игнорируем
        }

        LocalDateTime userTime = convertToUserTime(task.getScheduledTime(), userTimeZone);
        sb.append("📅 Время: ").append(userTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        if (task.getIsYearly()) {
            sb.append(" (ежегодно)");
        }

        return sb.toString();
    }

    /**
     * Конвертирует UTC время в локальное время пользователя
     */
    private LocalDateTime convertToUserTime(LocalDateTime utcTime, String timeZone) {
        ZonedDateTime utcZoned = utcTime.atZone(ZoneId.of("UTC"));
        ZonedDateTime userZoned = utcZoned.withZoneSameInstant(ZoneId.of(timeZone));
        return userZoned.toLocalDateTime();
    }

    /**
     * Получает список активных напоминаний пользователя
     */
    public String listNotifications(Long chatId) {
        List<NotificationTask> tasks = repository.findByChatIdAndStatus(chatId, "SCHEDULED");

        if (tasks.isEmpty()) {
            return "📭 У вас нет активных напоминаний.";
        }

        StringBuilder sb = new StringBuilder("📋 Ваши активные напоминания:\n\n");
        String userTimeZone = userService.getUserTimeZone(chatId);

        for (NotificationTask task : tasks) {
            LocalDateTime userTime = convertToUserTime(task.getScheduledTime(), userTimeZone);
            sb.append("🆔 ID: ").append(task.getId()).append("\n");
            sb.append("📝 ").append(task.getMessageText()).append("\n");
            sb.append("📅 ").append(userTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

            if (task.getIsYearly()) {
                sb.append(" 🔄 ежегодно");
            }

            sb.append("\n\n");
        }

        sb.append("Для удаления отправьте: /delete ID");
        return sb.toString();
    }
    public int deleteAllNotifications(Long chatId) {
        List<NotificationTask> tasks = repository.findByChatIdAndStatus(chatId, "SCHEDULED");
        int count = tasks.size();
        repository.deleteAll(tasks);
        logger.info("User {} deleted all {} notifications", chatId, count);
        return count;
    }

    /**
     * Обрабатывает сообщения из группы
     * Формат: ДД.ММ ЧЧ:MM Текст напоминания
     * Пример: 29.06 15:00 Идем на день рождения бабушки
     */
    public String processGroupMessage(String messageText, Long chatId, String senderUsername, String userTimeZone) {
        logger.info("Processing group message: '{}' from chat: {}, user timezone: {}", messageText, chatId, userTimeZone);

        // Валидация часового пояса
        String timeZone = userTimeZone;
        if (timeZone == null || timeZone.isEmpty()) {
            timeZone = "Europe/Moscow";
            logger.warn("User timezone is null or empty, using default: Europe/Moscow");
        }

        // Проверяем, что пояс валидный
        try {
            ZoneId.of(timeZone);
        } catch (Exception e) {
            timeZone = "Europe/Moscow";
            logger.warn("Invalid timezone '{}', using Europe/Moscow", userTimeZone);
        }

        if (messageText == null || messageText.trim().isEmpty()) {
            return null;
        }

        if (messageText.startsWith("/")) {
            return null;
        }

        Pattern pattern = Pattern.compile("(\\d{2}\\.\\d{2})(?:\\s+(\\d{2}:\\d{2}))?\\s+(.+)");
        Matcher matcher = pattern.matcher(messageText.trim());

        if (!matcher.matches()) {
            return null;
        }

        String datePart = matcher.group(1);
        String timePart = matcher.group(2);
        String reminderText = matcher.group(3);

        if (timePart == null) {
            timePart = "09:00";
        }

        try {
            // ✅ ИСПРАВЛЕНО: используем timeZone вместо жесткого Europe/Moscow
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
            int currentYear = now.getYear();

            String[] dayMonth = datePart.split("\\.");
            int day = Integer.parseInt(dayMonth[0]);
            int month = Integer.parseInt(dayMonth[1]);
            String[] hoursMinutes = timePart.split(":");
            int hour = Integer.parseInt(hoursMinutes[0]);
            int minute = Integer.parseInt(hoursMinutes[1]);

            // ✅ ИСПРАВЛЕНО: используем timeZone
            LocalDateTime groupLocalTime = LocalDateTime.of(currentYear, month, day, hour, minute);

            if (groupLocalTime.isBefore(now.toLocalDateTime())) {
                groupLocalTime = groupLocalTime.plusYears(1);
            }

            // ✅ ИСПРАВЛЕНО: используем timeZone
            ZonedDateTime groupZonedTime = groupLocalTime.atZone(ZoneId.of(timeZone));
            ZonedDateTime utcTime = groupZonedTime.withZoneSameInstant(ZoneId.of("UTC"));
            LocalDateTime scheduledTimeUTC = utcTime.toLocalDateTime();

            // ✅ ИСПРАВЛЕНО: используем timeZone
            NotificationTask task = new NotificationTask();
            task.setChatId(chatId);
            task.setMessageText(reminderText);
            task.setScheduledTime(scheduledTimeUTC);
            task.setTimeZone(timeZone);  // ← сохранён в БД
            task.setStatus("SCHEDULED");
            task.setCreatedAt(LocalDateTime.now());
            task.setIsYearly(true);
            task.setYearlyDay(String.valueOf(day));
            task.setYearlyMonth(String.valueOf(month));
            task.setYearlyTime(timePart);

            repository.save(task);

            String formattedTime = groupLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            return String.format("✅ Напоминание для группы сохранено!\n" +
                            "📅 Когда: %s (%s)\n" +  // ← добавили часовой пояс в ответ
                            "📝 Текст: %s\n" +
                            "🔄 Повтор: ежегодно\n" +
                            "Автор: @%s",
                    formattedTime, timeZone, reminderText, senderUsername);

        } catch (Exception e) {
            logger.error("Failed to parse group message: {}", messageText, e);
            return "❌ Не удалось распознать формат.\n" +
                    "Используйте: ДД.ММ ЧЧ:MM Текст напоминания\n" +
                    "Пример: 29.06 15:00 Идем на день рождения бабушки";
        }

    }

}