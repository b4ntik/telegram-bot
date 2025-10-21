package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class NotificationProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessingService.class);

    @Autowired
    private NotificationTaskRepository notificationTaskRepository;

    @Autowired
    private NotificationTaskSchedulerService notificationTaskSchedulerService;

    @Autowired
    private TelegramBotService telegramBotService;

    private final static Pattern PATTERN = Pattern.compile(
            "(\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2})(\\s+)(.+)"
    );

    public boolean checkMessageContainPattern(String s) {
        return PATTERN.matcher(s).matches();
    }

    public void handlePatternMessage(Long chatId, String messageText) {
        try {
            String[] parts = messageText.split("\\s+", 3);
            if (parts.length < 3) {
                telegramBotService.sendMessage(chatId, "❌ Неправильный формат. Используйте: ДД.ММ.ГГГГ ЧЧ:MM Текст");
                return;
            }

            String dateTimeStr = parts[0] + " " + parts[1];
            String text = parts[2];

            LocalDateTime scheduledTime = parseDateTime(dateTimeStr);

            if (scheduledTime.isBefore(LocalDateTime.now())) {
                telegramBotService.sendMessage(chatId, "❌ Ошибка: указанное время уже прошло.");
                return;
            }

            //сохраняем в репозиторий
            NotificationTask scheduledMessage = new NotificationTask(chatId, text, scheduledTime);
            NotificationTask savedMessage = notificationTaskRepository.save(scheduledMessage);

            //планируем отправку
            notificationTaskSchedulerService.scheduleMessage(savedMessage);

            telegramBotService.sendMessage(chatId, "✅ Уведомление запланировано на: " + dateTimeStr +
                    "\nТекст: " + text +
                    "\nID: " + savedMessage.getId());
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
            NotificationTask scheduledMessage = new NotificationTask(chatId, text, scheduledTime);
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
}