package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.controller.TelegramBotUpdatesListener;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.model.User;
import pro.sky.telegrambot.repository.NotificationTaskRepository;
import pro.sky.telegrambot.repository.UserRepository;


import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
public class NotificationTaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(NotificationTaskScheduler.class);

    @Autowired
    private NotificationTaskRepository notificationRepository;

    @Autowired
    private NotificationProcessingService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TelegramBotUpdatesListener bot;

    @Scheduled(fixedDelay = 30000) // Проверяем каждые 30 секунд
    @Scheduled(fixedDelay = 30000)  // Проверяем каждые 30 секунд
    public void checkAndSendNotifications() {
        logger.debug("Checking for notifications...");

        LocalDateTime nowUTC = LocalDateTime.now();
        List<NotificationTask> tasks = notificationRepository.findByStatusAndScheduledTimeBefore("SCHEDULED", nowUTC);

        if (tasks.isEmpty()) {
            return;
        }

        logger.info("Found {} tasks to process", tasks.size());

        for (NotificationTask task : tasks) {
            try {
                // Проверяем, существует ли пользователь (для личных чатов)
                // Для групп пользователя может не быть, это нормально
                User user = userRepository.findByChatId(task.getChatId()).orElse(null);

                String timeZone;
                boolean isGroup = task.getChatId() < 0;  // Отрицательный ID = группа

                if (isGroup) {
                    // Для групп используем Europe/Moscow (или можно хранить настройки группы)
                    timeZone = "Europe/Moscow";
                    logger.info("Processing group task ID: {}, chatId: {}", task.getId(), task.getChatId());
                } else {
                    // Для личных чатов — часовой пояс пользователя
                    if (user == null) {
                        logger.warn("User not found for chatId: {}", task.getChatId());
                        markTaskAsError(task);
                        continue;
                    }
                    timeZone = user.getTimeZone();
                    if (timeZone == null || timeZone.isEmpty()) {
                        timeZone = "Europe/Moscow";
                    }
                    logger.info("Processing personal task ID: {}, chatId: {}", task.getId(), task.getChatId());
                }

                // Конвертируем время задачи из UTC в часовой пояс
                ZonedDateTime taskTimeUTC = task.getScheduledTime().atZone(ZoneId.of("UTC"));
                ZonedDateTime taskTimeUserZone = taskTimeUTC.withZoneSameInstant(ZoneId.of(timeZone));
                ZonedDateTime nowUserZone = ZonedDateTime.now(ZoneId.of(timeZone));

                // Логируем для отладки
                logger.info("=== Task ID: {} (group: {}) ===", task.getId(), isGroup);
                logger.info("Timezone: {}", timeZone);
                logger.info("Task time (UTC): {}", task.getScheduledTime());
                logger.info("Task time ({}): {}", timeZone,
                        taskTimeUserZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                logger.info("Current time ({}): {}", timeZone,
                        nowUserZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                // Проверяем, наступило ли время отправки
                if (nowUserZone.isAfter(taskTimeUserZone) || nowUserZone.equals(taskTimeUserZone)) {
                    logger.info("Sending notification for task {}", task.getId());

                    // Формируем сообщение
                    String message;
                    if (isGroup) {
                        // Для группы — простое напоминание
                        message = "🔔 НАПОМИНАНИЕ 🔔\n\n📝 " + task.getMessageText();
                    } else {
                        // Для личных — с приветствием
                        message = "🔔 НАПОМИНАНИЕ 🔔\n\n📝 " + task.getMessageText();
                    }

                    // Отправляем сообщение (и для группы, и для личного чата работает одинаково)
                    bot.sendMessage(task.getChatId(), message);

                    // Отмечаем как отправленное
                    task.setStatus("SENT");
                    task.setSentAt(LocalDateTime.now());
                    notificationRepository.save(task);

                    logger.info("Notification sent for task {} to chat {}", task.getId(), task.getChatId());

                    // Если ежегодное — создаём на следующий год
                    if (task.getIsYearly()) {
                        createNextYearReminder(task);
                    }

                } else {
                    logger.debug("Task {} not due yet. Wait until: {}",
                            task.getId(), taskTimeUserZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }

            } catch (Exception e) {
                logger.error("Failed to process task: {}", task.getId(), e);
                markTaskAsError(task);
            }
        }
    }
    private void createNextYearReminder(NotificationTask oldTask) {
        try {
            if (!oldTask.getIsYearly()) {
                return;
            }

            if (oldTask.getYearlyDay() == null || oldTask.getYearlyMonth() == null || oldTask.getYearlyTime() == null) {
                logger.warn("Task {} marked as yearly but missing yearly data", oldTask.getId());
                return;
            }

            // Определяем часовой пояс
            String timeZone;
            boolean isGroup = oldTask.getChatId() < 0;

            if (isGroup) {
                timeZone = "Europe/Moscow";  // Часовой пояс группы
            } else {
                timeZone = oldTask.getTimeZone();
                if (timeZone == null || timeZone.isEmpty()) {
                    timeZone = "Europe/Moscow";
                }
            }

            // Текущее время в часовом поясе
            ZonedDateTime nowUserZone = ZonedDateTime.now(ZoneId.of(timeZone));
            int nextYear = nowUserZone.getYear() + 1;

            // Парсим день, месяц и время
            int day = Integer.parseInt(oldTask.getYearlyDay());
            int month = Integer.parseInt(oldTask.getYearlyMonth());
            String[] hoursMinutes = oldTask.getYearlyTime().split(":");
            int hour = Integer.parseInt(hoursMinutes[0]);
            int minute = Integer.parseInt(hoursMinutes[1]);

            // Создаём дату на следующий год
            LocalDateTime nextYearLocalTime = LocalDateTime.of(nextYear, month, day, hour, minute);

            // Конвертируем в UTC
            ZonedDateTime nextYearUserZoned = nextYearLocalTime.atZone(ZoneId.of(timeZone));
            ZonedDateTime nextYearUTC = nextYearUserZoned.withZoneSameInstant(ZoneId.of("UTC"));
            LocalDateTime nextYearScheduledUTC = nextYearUTC.toLocalDateTime();

            // Создаём новую задачу
            NotificationTask newTask = new NotificationTask();
            newTask.setChatId(oldTask.getChatId());
            newTask.setMessageText(oldTask.getMessageText());
            newTask.setScheduledTime(nextYearScheduledUTC);
            newTask.setTimeZone(timeZone);
            newTask.setIsYearly(true);
            newTask.setYearlyDay(oldTask.getYearlyDay());
            newTask.setYearlyMonth(oldTask.getYearlyMonth());
            newTask.setYearlyTime(oldTask.getYearlyTime());
            newTask.setStatus("SCHEDULED");
            newTask.setCreatedAt(LocalDateTime.now());

            notificationRepository.save(newTask);

            String formattedDate = nextYearLocalTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            logger.info("✅ Created next year reminder for task {} -> {} ({})",
                    oldTask.getId(), newTask.getId(), formattedDate);

        } catch (Exception e) {
            logger.error("❌ Failed to create next year reminder for task {}", oldTask.getId(), e);
        }
    }

    private void markTaskAsError(NotificationTask task) {
        task.setStatus("ERROR");
        task.setSentAt(LocalDateTime.now());
        notificationRepository.save(task);
    }
}