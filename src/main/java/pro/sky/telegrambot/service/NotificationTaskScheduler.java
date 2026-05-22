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
    public void checkAndSendNotifications() {
        logger.debug("Checking for notifications...");

        // Получаем текущее время в UTC
        LocalDateTime nowUTC = LocalDateTime.now();

        // Находим все задачи, которые должны быть отправлены (по UTC)
        List<NotificationTask> tasks = notificationRepository.findByStatusAndScheduledTimeBefore("SCHEDULED", nowUTC);

        if (tasks.isEmpty()) {
            return;
        }

        logger.info("Found {} tasks to process", tasks.size());

        for (NotificationTask task : tasks) {
            try {
                // Получаем пользователя
                User user = userRepository.findByChatId(task.getChatId()).orElse(null);
                if (user == null) {
                    logger.warn("User not found for chatId: {}", task.getChatId());
                    markTaskAsError(task);
                    continue;
                }

                String userTimeZone = user.getTimeZone();
                if (userTimeZone == null || userTimeZone.isEmpty()) {
                    userTimeZone = "Europe/Moscow";
                }

                // Конвертируем время задачи из UTC в часовой пояс пользователя
                ZonedDateTime taskTimeUTC = task.getScheduledTime().atZone(ZoneId.of("UTC"));
                ZonedDateTime taskTimeUserZone = taskTimeUTC.withZoneSameInstant(ZoneId.of(userTimeZone));

                // Текущее время в часовом поясе пользователя
                ZonedDateTime nowUserZone = ZonedDateTime.now(ZoneId.of(userTimeZone));

                // Логируем для отладки
                logger.info("=== Task ID: {} ===", task.getId());
                logger.info("User timezone: {}", userTimeZone);
                logger.info("Task time (UTC): {}", task.getScheduledTime());
                logger.info("Task time ({}): {}", userTimeZone,
                        taskTimeUserZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                logger.info("Current time ({}): {}", userTimeZone,
                        nowUserZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                // Проверяем, наступило ли время отправки (с учётом часового пояса пользователя)
                if (nowUserZone.isAfter(taskTimeUserZone) || nowUserZone.equals(taskTimeUserZone)) {
                    logger.info("Sending notification for task {}", task.getId());

                    // Отправляем сообщение
                    String message = String.format("🔔 НАПОМИНАНИЕ 🔔\n\n📝 %s", task.getMessageText());
                    bot.sendMessage(task.getChatId(), message);

                    // Отмечаем как отправленное
                    task.setStatus("SENT");
                    task.setSentAt(LocalDateTime.now());
                    notificationRepository.save(task);

                    logger.info("Notification sent for task {}", task.getId());
                } else {
                    logger.debug("Task {} not due yet. Wait until: {}",
                            task.getId(), taskTimeUserZone.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }
                if (task.getIsYearly()) {
                    createNextYearReminder(task);
                }

            } catch (Exception e) {
                logger.error("Failed to process task: {}", task.getId(), e);
                markTaskAsError(task);
            }
        }
    }
    private void createNextYearReminder(NotificationTask oldTask) {
        try {
            // Получаем часовой пояс пользователя
            User user = userRepository.findByChatId(oldTask.getChatId()).orElse(null);
            if (user == null) return;

            String timeZone = user.getTimeZone();
            ZonedDateTime nowUserZone = ZonedDateTime.now(ZoneId.of(timeZone));
            int nextYear = nowUserZone.getYear() + 1;

            // Создаём дату на следующий год
            LocalDateTime nextYearTime = LocalDateTime.of(
                    nextYear,
                    Integer.parseInt(oldTask.getYearlyMonth()),
                    Integer.parseInt(oldTask.getYearlyDay()),
                    Integer.parseInt(oldTask.getYearlyTime().split(":")[0]),
                    Integer.parseInt(oldTask.getYearlyTime().split(":")[1])
            );

            // Конвертируем в UTC
            LocalDateTime nextYearUTC = notificationService.convertToUTC(nextYearTime, timeZone);

            // Создаём новую задачу
            NotificationTask newTask = new NotificationTask();
            newTask.setChatId(oldTask.getChatId());
            newTask.setMessageText(oldTask.getMessageText());
            newTask.setScheduledTime(nextYearUTC);
            newTask.setTimeZone(timeZone);
            newTask.setIsYearly(true);
            newTask.setYearlyDay(oldTask.getYearlyDay());
            newTask.setYearlyMonth(oldTask.getYearlyMonth());
            newTask.setYearlyTime(oldTask.getYearlyTime());
            newTask.setStatus("SCHEDULED");
            newTask.setCreatedAt(LocalDateTime.now());

            notificationRepository.save(newTask);
            logger.info("Created next year reminder for task {}", oldTask.getId());

        } catch (Exception e) {
            logger.error("Failed to create next year reminder", e);
        }
    }

    private void markTaskAsError(NotificationTask task) {
        task.setStatus("ERROR");
        task.setSentAt(LocalDateTime.now());
        notificationRepository.save(task);
    }
}