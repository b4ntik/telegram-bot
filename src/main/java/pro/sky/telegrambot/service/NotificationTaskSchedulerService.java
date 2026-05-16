package pro.sky.telegrambot.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class NotificationTaskSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationTaskSchedulerService.class);
    @Autowired
    private TelegramBotService telegramBotService;

    @Autowired
    private NotificationTaskRepository notificationTaskRepository;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public void scheduleMessage(NotificationTask task) {
        LocalDateTime nowUTC = LocalDateTime.now(ZoneOffset.UTC);
        Duration delay = Duration.between(nowUTC, task.getScheduledTime());

        if (delay.isNegative()) {
            return; // Время уже прошло
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                // Конвертируем время обратно в часовой пояс пользователя для логирования
                LocalDateTime userTime = convertFromUTC(task.getScheduledTime(), task.getTimeZone());

                // Отправляем сообщение
                telegramBotService.sendMessage(task.getChatId(),
                        "⏰ Напоминание:\n" + task.getMessageText() +
                                "\n\nЗапланировано на: " +
                                formatDateTimeForUser(userTime, task.getTimeZone()));

                // Удаляем задачу из базы после отправки
                notificationTaskRepository.delete(task);
                scheduledTasks.remove(task.getId());

            } catch (Exception e) {
                logger.error("Ошибка при отправке запланированного сообщения", e);
            }
        }, delay.toSeconds(), TimeUnit.SECONDS);

        scheduledTasks.put(task.getId(), future);
    }

    // Вспомогательные методы для конвертации времени
    private LocalDateTime convertFromUTC(LocalDateTime utcTime, String targetTimeZone) {
        ZonedDateTime zonedUtcTime = utcTime.atZone(ZoneOffset.UTC);
        ZonedDateTime targetTime = zonedUtcTime.withZoneSameInstant(ZoneId.of(targetTimeZone));
        return targetTime.toLocalDateTime();
    }

    private String formatDateTimeForUser(LocalDateTime dateTime, String timeZone) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        ZonedDateTime zonedDateTime = dateTime.atZone(ZoneId.of(timeZone));
        return zonedDateTime.format(formatter);
    }
}