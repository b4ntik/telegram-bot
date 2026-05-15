package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.model.User;
import pro.sky.telegrambot.repository.NotificationTaskRepository;


import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class NotificationProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessingService.class);
    private String userTimeZone;
    private LocalDateTime scheduledTimeUTC;
    @Autowired
    private NotificationTaskRepository notificationTaskRepository;

    @Autowired
    private NotificationTaskSchedulerService notificationTaskSchedulerService;

    @Autowired
    private TelegramBotService telegramBotService;

   // @Autowired
    private UserService userService;

    private final static Pattern PATTERN = Pattern.compile(
            "(\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2})(\\s+)(.+)"
    );

    public boolean checkMessageContainPattern(String s) {
        return PATTERN.matcher(s).matches();
    }

    public void handlePatternMessage(Long chatId, String messageText) {
        try {
            // Создаем или находим пользователя
            User user = userService.findOrCreateUser(chatId, userTimeZone);

            String[] parts = messageText.split("\\s+", 3);
            if (parts.length < 3) {
                telegramBotService.sendMessage(chatId, "❌ Неправильный формат. Используйте: ДД.ММ.ГГГГ ЧЧ:MM Текст");
                return;
            }

            String dateTimeStr = parts[0] + " " + parts[1];
            String text = parts[2];

            // Получаем часовой пояс пользователя
            String userTimeZone = user.getTimeZone();

            // Парсим время в часовом поясе пользователя
            LocalDateTime scheduledTimeInUserTZ = parseDateTime(dateTimeStr, userTimeZone);

            // Конвертируем в UTC для хранения в БД
            LocalDateTime scheduledTimeUTC = convertToUTC(scheduledTimeInUserTZ, userTimeZone);

            if (scheduledTimeUTC.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                telegramBotService.sendMessage(chatId, "❌ Ошибка: указанное время уже прошло.");
                return;
            }

            // Сохраняем в репозиторий с часовым поясом
            NotificationTask scheduledMessage = new NotificationTask(chatId, text, scheduledTimeUTC, userTimeZone);
            NotificationTask savedMessage = notificationTaskRepository.save(scheduledMessage);

            // Планируем отправку
            notificationTaskSchedulerService.scheduleMessage(savedMessage);

            // Отображаем время в часовом поясе пользователя
            String displayTime = formatDateTimeForUser(scheduledTimeInUserTZ, userTimeZone);

            telegramBotService.sendMessage(chatId, "✅ Уведомление запланировано на: " + displayTime +
                    "\nТекст: " + text +
                    "\nID: " + savedMessage.getId() +
                    "\nЧасовой пояс: " + userTimeZone);
        } catch (Exception e) {
            logger.error("Ошибка при обработке сообщения с паттерном", e);
            telegramBotService.sendMessage(chatId, "❌ Ошибка при обработке сообщения. Проверьте формат: ДД.ММ.ГГГГ ЧЧ:MM Текст");
        }
    }

    public void handleScheduleCommand(Long chatId, String messageText) {
        String[] parts = messageText.split(" ", 4);
        if (parts.length < 4) {
            telegramBotService.sendMessage(chatId, "❌ Неправильный формат команды. Используйте:\n/schedule ДД.ММ.ГГГГ ЧЧ:MM Текст_сообщения");
            return;
        }

        try {
            String dateTimeStr = parts[1] + " " + parts[2];
            String text = parts[3];

            //проверяем формат даты
            if (!dateTimeStr.matches("\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2}")) {
                telegramBotService.sendMessage(chatId, "❌ Неправильный формат даты. Используйте: ДД.ММ.ГГГГ ЧЧ:MM");
                return;
            }

            LocalDateTime scheduledTime = parseDateTime(dateTimeStr);

            if (scheduledTime.isBefore(LocalDateTime.now())) {
                telegramBotService.sendMessage(chatId, "❌ Ошибка: указанное время уже прошло.");
                return;
            }

            //сохраняем в репозиторий

            NotificationTask scheduledMessage = new NotificationTask(chatId, text, scheduledTimeUTC, userTimeZone);
            NotificationTask savedMessage = notificationTaskRepository.save(scheduledMessage);

            //планируем отправку
            notificationTaskSchedulerService.scheduleMessage(savedMessage);

            telegramBotService.sendMessage(chatId, "✅ Уведомление запланировано!\n" +
                    "⏰ Время: " + dateTimeStr +
                    "\n💬 Текст: " + text +
                    "\n🆔 ID: " + savedMessage.getId());

        } catch (Exception e) {
            logger.error("Ошибка при обработке команды /schedule", e);
            telegramBotService.sendMessage(chatId, "❌ Ошибка при планировании уведомления. Проверьте формат данных.");
        }
    }

    public void handleListCommand(Long chatId) {
        try {
            List<NotificationTask> scheduledMessages = notificationTaskRepository
                    .findByChatIdAndStatusOrderByScheduledTimeAsc(chatId, "SCHEDULED");

            if (scheduledMessages.isEmpty()) {
                telegramBotService.sendMessage(chatId, "📭 У вас нет запланированных уведомлений.");
                return;
            }

            StringBuilder message = new StringBuilder("📋 Ваши запланированные уведомления:\n\n");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            for (NotificationTask msg : scheduledMessages) {
                message.append("🆔 ").append(msg.getId())
                        .append("\n⏰ ").append(msg.getScheduledTime().format(formatter))
                        .append("\n💬 ").append(msg.getMessageText())
                        .append("\n\n");
            }

            telegramBotService.sendMessage(chatId, message.toString());
        } catch (Exception e) {
            logger.error("Ошибка при получении списка уведомлений", e);
            telegramBotService.sendMessage(chatId, "❌ Ошибка при получении списка уведомлений.");
        }
    }

    public void sendHelpMessage(Long chatId) {
        String helpText = """
            📋 Доступные команды:
            
            /start - Начать работу
            /help - Показать эту справку
            /list - Показать запланированные уведомления
            
            📝 Формат сообщений:
            Просто отправьте сообщение в формате:
            ДД.ММ.ГГГГ ЧЧ:MM Ваш_текст_уведомления
            
            Примеры:
            25.12.2024 14:30 Поздравить с Новым годом
            """;

        telegramBotService.sendMessage(chatId, helpText);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return LocalDateTime.parse(dateTimeStr, formatter);
    }
    private String getUserTimeZone(Long chatId) {
        // Здесь можно реализовать логику определения часового пояса
        // 1. Хранить в базе данных для каждого пользователя
        // 2. Запрашивать у пользователя
        // 3. Использовать геолокацию (если доступно)
        // 4. Использовать часовой пояс по умолчанию

        // Временное решение - Europe/Moscow как пример
        return "Europe/Moscow";
    }
    private LocalDateTime parseDateTime(String dateTimeStr, String timeZone) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            LocalDateTime localDateTime = LocalDateTime.parse(dateTimeStr, formatter);

            // Создаем ZonedDateTime в часовом поясе пользователя
            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.of(timeZone));

            return zonedDateTime.toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Неверный формат даты и времени");
        }
    }

    private LocalDateTime convertToUTC(LocalDateTime userTime, String userTimeZone) {
        ZonedDateTime zonedUserTime = userTime.atZone(ZoneId.of(userTimeZone));
        ZonedDateTime utcTime = zonedUserTime.withZoneSameInstant(ZoneOffset.UTC);
        return utcTime.toLocalDateTime();
    }

    private LocalDateTime convertFromUTC(LocalDateTime utcTime, String targetTimeZone) {
        ZonedDateTime zonedUtcTime = utcTime.atZone(ZoneOffset.UTC);
        ZonedDateTime targetTime = zonedUtcTime.withZoneSameInstant(ZoneId.of(targetTimeZone));
        return targetTime.toLocalDateTime();
    }

    private String formatDateTimeForUser(LocalDateTime dateTime, String timeZone) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        ZonedDateTime zonedDateTime = dateTime.atZone(ZoneId.of(timeZone));
        return zonedDateTime.format(formatter) + " (" + timeZone + ")";
    }
    public void handleTimeZoneCommand(Long chatId, String timeZoneStr) {
        try {
            // Валидируем часовой пояс
            ZoneId.of(timeZoneStr);

            // Сохраняем часовой пояс пользователя
            userService.saveUserTimeZone(chatId, timeZoneStr);

            telegramBotService.sendMessage(chatId,
                    "✅ Часовой пояс установлен: " + timeZoneStr +
                            "\nТеперь все уведомления будут учитывать ваш часовой пояс.");

        } catch (DateTimeException e) {
            telegramBotService.sendMessage(chatId,
                    "❌ Неверный часовой пояс. Примеры правильных форматов:\n" +
                            "• Europe/Moscow\n" +
                            "• Asia/Vladivostok\n" +
                            "• Europe/London\n" +
                            "• America/New_York");
        }
    }
}