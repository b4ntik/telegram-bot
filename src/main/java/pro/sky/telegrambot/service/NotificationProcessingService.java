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
                "1️⃣ Сегодня в указанное время:\n" +
                "   10:00 Позвонить клиенту\n\n" +
                "2️⃣ Ежегодное напоминание (в 09:00):\n" +
                "   20.05 День рождения мамы\n\n" +
                "3️⃣ Однократное (в 00:00):\n" +
                "   20.05.2026 Позвонить маме\n\n" +
                "4️⃣ Однократное в указанное время:\n" +
                "   20.05.2026 14:30 Позвонить маме\n\n" +
                "📌 Команды:\n" +
                "/start - начать работу\n" +
                "/help - эта справка\n" +
                "/timezone - узнать часовой пояс\n" +
                "/settimezone - изменить часовой пояс\n" +
                "/mytasks - список моих напоминаний\n" +
                "/delete ID - удалить напоминание";
    }

    /**
     * Парсит сообщение и создает напоминание
     */
    private String parseAndCreateNotification(String messageText, Long chatId, String timeZone) {
        try {
            logger.info("=== CREATING NOTIFICATION ===");
            logger.info("Message: {}, TimeZone: {}", messageText, timeZone);

            // === ФОРМАТ 1: только время (сегодня) ===
            // Пример: 10:00 Позвонить клиенту
            Pattern timeOnlyPattern = Pattern.compile("^(\\d{2}:\\d{2})\\s+(.+)$");
            Matcher timeOnlyMatcher = timeOnlyPattern.matcher(messageText.trim());

            if (timeOnlyMatcher.matches()) {
                String timePart = timeOnlyMatcher.group(1);
                String notificationText = timeOnlyMatcher.group(2);

                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
                LocalDateTime today = now.toLocalDateTime();

                String[] hoursMinutes = timePart.split(":");
                int hour = Integer.parseInt(hoursMinutes[0]);
                int minute = Integer.parseInt(hoursMinutes[1]);

                LocalDateTime userLocalTime = LocalDateTime.of(
                        today.getYear(), today.getMonth(), today.getDayOfMonth(),
                        hour, minute
                );

                if (userLocalTime.isBefore(now.toLocalDateTime())) {
                    userLocalTime = userLocalTime.plusDays(1);
                    logger.info("Time already passed today, moved to tomorrow: {}", userLocalTime);
                }

                LocalDateTime scheduledTimeUTC = convertToUTC(userLocalTime, timeZone);

                NotificationTask task = createAndSaveTask(chatId, notificationText, scheduledTimeUTC, timeZone);
                task.setIsYearly(false);
                repository.save(task);

                String formattedTime = userLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                return String.format("✅ Напоминание сохранено!\n" +
                                "📅 Когда: %s (по вашему часовому поясу)\n" +
                                "📝 Текст: %s\n" +
                                "🆔 ID задачи: %d",
                        formattedTime, notificationText, task.getId());
            }

            // === ФОРМАТ 2: ежегодное (ДД.ММ ТЕКСТ) ===
            // Пример: 20.05 День рождения мамы
            Pattern yearlyPattern = Pattern.compile("^(\\d{2}\\.\\d{2})\\s+(.+)$");
            Matcher yearlyMatcher = yearlyPattern.matcher(messageText.trim());

            if (yearlyMatcher.matches()) {
                String datePart = yearlyMatcher.group(1);
                String notificationText = yearlyMatcher.group(2);

                String[] dayMonth = datePart.split("\\.");
                int day = Integer.parseInt(dayMonth[0]);
                int month = Integer.parseInt(dayMonth[1]);

                // Всегда 09:00 для ежегодных
                String timePart = "09:00";
                String[] hoursMinutes = timePart.split(":");
                int hour = Integer.parseInt(hoursMinutes[0]);
                int minute = Integer.parseInt(hoursMinutes[1]);

                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
                int currentYear = now.getYear();

                LocalDateTime userLocalTime = LocalDateTime.of(currentYear, month, day, hour, minute);

                if (userLocalTime.isBefore(now.toLocalDateTime())) {
                    userLocalTime = userLocalTime.plusYears(1);
                    logger.info("Yearly reminder moved to next year: {}", userLocalTime);
                }

                LocalDateTime scheduledTimeUTC = convertToUTC(userLocalTime, timeZone);

                NotificationTask task = createAndSaveTask(chatId, notificationText, scheduledTimeUTC, timeZone);
                task.setIsYearly(true);
                task.setYearlyDay(String.valueOf(day));
                task.setYearlyMonth(String.valueOf(month));
                task.setYearlyTime("09:00");
                repository.save(task);

                String formattedTime = userLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                return String.format("✅ Ежегодное напоминание сохранено!\n" +
                                "📅 Когда: %s (по вашему часовому поясу)\n" +
                                "📝 Текст: %s\n" +
                                "🔄 Повтор: ежегодно в 09:00\n" +
                                "🆔 ID задачи: %d",
                        formattedTime, notificationText, task.getId());
            }

            // === ФОРМАТ 3: однократное с годом и без времени ===
            // Пример: 20.05.2026 Позвонить маме
            Pattern singleDatePattern = Pattern.compile("^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(.+)$");
            Matcher singleDateMatcher = singleDatePattern.matcher(messageText.trim());

            if (singleDateMatcher.matches()) {
                String datePart = singleDateMatcher.group(1);
                String notificationText = singleDateMatcher.group(2);

                // Парсим дату, время = 00:00
                String[] dayMonthYear = datePart.split("\\.");
                int day = Integer.parseInt(dayMonthYear[0]);
                int month = Integer.parseInt(dayMonthYear[1]);
                int year = Integer.parseInt(dayMonthYear[2]);

                LocalDateTime userLocalTime = LocalDateTime.of(year, month, day, 0, 0);

                LocalDateTime scheduledTimeUTC = convertToUTC(userLocalTime, timeZone);

                if (scheduledTimeUTC.isBefore(LocalDateTime.now())) {
                    return "❌ Нельзя создать напоминание на прошедшее время!";
                }

                NotificationTask task = createAndSaveTask(chatId, notificationText, scheduledTimeUTC, timeZone);
                task.setIsYearly(false);
                repository.save(task);

                String formattedTime = userLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                return String.format("✅ Напоминание сохранено!\n" +
                                "📅 Когда: %s (по вашему часовому поясу)\n" +
                                "📝 Текст: %s\n" +
                                "🆔 ID задачи: %d",
                        formattedTime, notificationText, task.getId());
            }

            // === ФОРМАТ 4: однократное с годом и временем ===
            // Пример: 20.05.2026 14:30 Позвонить маме
            Pattern singleFullPattern = Pattern.compile("^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}:\\d{2})\\s+(.+)$");
            Matcher singleFullMatcher = singleFullPattern.matcher(messageText.trim());

            if (singleFullMatcher.matches()) {
                String datePart = singleFullMatcher.group(1);
                String timePart = singleFullMatcher.group(2);
                String notificationText = singleFullMatcher.group(3);

                LocalDateTime userLocalTime = parseUserDateTime(datePart, timePart);
                if (userLocalTime == null) {
                    return "❌ Не удалось распознать дату и время.";
                }

                LocalDateTime scheduledTimeUTC = convertToUTC(userLocalTime, timeZone);

                if (scheduledTimeUTC.isBefore(LocalDateTime.now())) {
                    return "❌ Нельзя создать напоминание на прошедшее время!";
                }

                NotificationTask task = createAndSaveTask(chatId, notificationText, scheduledTimeUTC, timeZone);
                task.setIsYearly(false);
                repository.save(task);

                String formattedTime = userLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                return String.format("✅ Напоминание сохранено!\n" +
                                "📅 Когда: %s (по вашему часовому поясу)\n" +
                                "📝 Текст: %s\n" +
                                "🆔 ID задачи: %d",
                        formattedTime, notificationText, task.getId());
            }

            // Если ни один формат не подошёл
            return "❌ Неверный формат!\n\n" +
                    "Используйте один из форматов:\n" +
                    "• 10:00 Текст (сегодня)\n" +
                    "• 20.05 Текст (ежегодно в 09:00)\n" +
                    "• 20.05.2026 Текст (однократно в 00:00)\n" +
                    "• 20.05.2026 14:30 Текст (однократно)";

        } catch (Exception e) {
            logger.error("Error creating notification: {}", messageText, e);
            return "❌ Ошибка при создании напоминания. Проверьте формат:\n" +
                    "• 10:00 Текст (сегодня)\n" +
                    "• 20.05 Текст (ежегодно в 09:00)\n" +
                    "• 20.05.2026 Текст (однократно)\n" +
                    "• 20.05.2026 14:30 Текст (однократно)";
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
        logger.info("Processing group message: '{}' from chat: {}, user timezone: {}",
                messageText, chatId, userTimeZone);

        String timeZone = userTimeZone;
        if (timeZone == null || timeZone.isEmpty()) {
            timeZone = "Europe/Moscow";
        }

        try {
            ZoneId.of(timeZone);
        } catch (Exception e) {
            timeZone = "Europe/Moscow";
            logger.warn("Invalid timezone, using Europe/Moscow");
        }

        if (messageText == null || messageText.trim().isEmpty()) {
            return null;
        }

        if (messageText.startsWith("/")) {
            return null;
        }

        // === ФОРМАТ 1: только время (сегодня) ===
        Pattern timeOnlyPattern = Pattern.compile("^(\\d{2}:\\d{2})\\s+(.+)$");
        Matcher timeOnlyMatcher = timeOnlyPattern.matcher(messageText.trim());

        if (timeOnlyMatcher.matches()) {
            String timePart = timeOnlyMatcher.group(1);
            String reminderText = timeOnlyMatcher.group(2);

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
            LocalDateTime today = now.toLocalDateTime();

            String[] hoursMinutes = timePart.split(":");
            int hour = Integer.parseInt(hoursMinutes[0]);
            int minute = Integer.parseInt(hoursMinutes[1]);

            LocalDateTime groupLocalTime = LocalDateTime.of(
                    today.getYear(), today.getMonth(), today.getDayOfMonth(),
                    hour, minute
            );

            if (groupLocalTime.isBefore(now.toLocalDateTime())) {
                groupLocalTime = groupLocalTime.plusDays(1);
            }

            LocalDateTime scheduledTimeUTC = convertToUTC(groupLocalTime, timeZone);

            NotificationTask task = new NotificationTask();
            task.setChatId(chatId);
            task.setMessageText(reminderText);
            task.setScheduledTime(scheduledTimeUTC);
            task.setTimeZone(timeZone);
            task.setStatus("SCHEDULED");
            task.setCreatedAt(LocalDateTime.now());
            task.setIsYearly(false);
            repository.save(task);

            String formattedTime = groupLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            return String.format("✅ Напоминание для группы сохранено!\n" +
                            "📅 Когда: %s (%s)\n" +
                            "📝 Текст: %s\n" +
                            "Автор: @%s",
                    formattedTime, timeZone, reminderText, senderUsername);
        }

        // === ФОРМАТ 2: ежегодное (ДД.ММ ТЕКСТ) ===
        Pattern yearlyPattern = Pattern.compile("^(\\d{2}\\.\\d{2})\\s+(.+)$");
        Matcher yearlyMatcher = yearlyPattern.matcher(messageText.trim());

        if (yearlyMatcher.matches()) {
            String datePart = yearlyMatcher.group(1);
            String reminderText = yearlyMatcher.group(2);

            String[] dayMonth = datePart.split("\\.");
            int day = Integer.parseInt(dayMonth[0]);
            int month = Integer.parseInt(dayMonth[1]);

            String timePart = "09:00";
            String[] hoursMinutes = timePart.split(":");
            int hour = Integer.parseInt(hoursMinutes[0]);
            int minute = Integer.parseInt(hoursMinutes[1]);

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
            int currentYear = now.getYear();

            LocalDateTime groupLocalTime = LocalDateTime.of(currentYear, month, day, hour, minute);

            if (groupLocalTime.isBefore(now.toLocalDateTime())) {
                groupLocalTime = groupLocalTime.plusYears(1);
            }

            LocalDateTime scheduledTimeUTC = convertToUTC(groupLocalTime, timeZone);

            NotificationTask task = new NotificationTask();
            task.setChatId(chatId);
            task.setMessageText(reminderText);
            task.setScheduledTime(scheduledTimeUTC);
            task.setTimeZone(timeZone);
            task.setStatus("SCHEDULED");
            task.setCreatedAt(LocalDateTime.now());
            task.setIsYearly(true);
            task.setYearlyDay(String.valueOf(day));
            task.setYearlyMonth(String.valueOf(month));
            task.setYearlyTime("09:00");
            repository.save(task);

            String formattedTime = groupLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            return String.format("✅ Ежегодное напоминание для группы сохранено!\n" +
                            "📅 Когда: %s (%s)\n" +
                            "📝 Текст: %s\n" +
                            "🔄 Повтор: ежегодно в 09:00\n" +
                            "Автор: @%s",
                    formattedTime, timeZone, reminderText, senderUsername);
        }

        // === ФОРМАТ 3: однократное с годом без времени ===
        Pattern singleDatePattern = Pattern.compile("^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(.+)$");
        Matcher singleDateMatcher = singleDatePattern.matcher(messageText.trim());

        if (singleDateMatcher.matches()) {
            String datePart = singleDateMatcher.group(1);
            String reminderText = singleDateMatcher.group(2);

            String[] dayMonthYear = datePart.split("\\.");
            int day = Integer.parseInt(dayMonthYear[0]);
            int month = Integer.parseInt(dayMonthYear[1]);
            int year = Integer.parseInt(dayMonthYear[2]);

            LocalDateTime groupLocalTime = LocalDateTime.of(year, month, day, 0, 0);
            LocalDateTime scheduledTimeUTC = convertToUTC(groupLocalTime, timeZone);

            if (scheduledTimeUTC.isBefore(LocalDateTime.now())) {
                return "❌ Нельзя создать напоминание на прошедшее время!";
            }

            NotificationTask task = new NotificationTask();
            task.setChatId(chatId);
            task.setMessageText(reminderText);
            task.setScheduledTime(scheduledTimeUTC);
            task.setTimeZone(timeZone);
            task.setStatus("SCHEDULED");
            task.setCreatedAt(LocalDateTime.now());
            task.setIsYearly(false);
            repository.save(task);

            String formattedTime = groupLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            return String.format("✅ Напоминание для группы сохранено!\n" +
                            "📅 Когда: %s (%s)\n" +
                            "📝 Текст: %s\n" +
                            "Автор: @%s",
                    formattedTime, timeZone, reminderText, senderUsername);
        }

        // === ФОРМАТ 4: однократное с годом и временем ===
        Pattern singleFullPattern = Pattern.compile("^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}:\\d{2})\\s+(.+)$");
        Matcher singleFullMatcher = singleFullPattern.matcher(messageText.trim());

        if (singleFullMatcher.matches()) {
            String datePart = singleFullMatcher.group(1);
            String timePart = singleFullMatcher.group(2);
            String reminderText = singleFullMatcher.group(3);

            LocalDateTime groupLocalTime = parseUserDateTime(datePart, timePart);
            if (groupLocalTime == null) {
                return "❌ Не удалось распознать дату и время.";
            }

            LocalDateTime scheduledTimeUTC = convertToUTC(groupLocalTime, timeZone);

            if (scheduledTimeUTC.isBefore(LocalDateTime.now())) {
                return "❌ Нельзя создать напоминание на прошедшее время!";
            }

            NotificationTask task = new NotificationTask();
            task.setChatId(chatId);
            task.setMessageText(reminderText);
            task.setScheduledTime(scheduledTimeUTC);
            task.setTimeZone(timeZone);
            task.setStatus("SCHEDULED");
            task.setCreatedAt(LocalDateTime.now());
            task.setIsYearly(false);
            repository.save(task);

            String formattedTime = groupLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            return String.format("✅ Напоминание для группы сохранено!\n" +
                            "📅 Когда: %s (%s)\n" +
                            "📝 Текст: %s\n" +
                            "Автор: @%s",
                    formattedTime, timeZone, reminderText, senderUsername);
        }

        return null;
    }

}