package pro.sky.telegrambot.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationTaskSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationTaskSchedulerService.class);

    @Autowired
    private NotificationTaskRepository notificationTaskRepository;

    @Autowired
    private TelegramBotService telegramBotService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    @PostConstruct
    public void init() {
        restoreScheduledMessages();
    }

    public void scheduleMessage(NotificationTask message) {
        LocalDateTime scheduledTime = message.getScheduledTime();
        LocalDateTime now = LocalDateTime.now();

        long delay = Duration.between(now, scheduledTime).toMillis();

        if (delay > 0) {
            scheduler.schedule(() -> {
                try {
                    //отправляем сообщение через TelegramService
                    boolean sent = telegramBotService.sendMessage(
                            message.getChatId(),
                            "⏰ Напоминание:\n" + message.getMessageText()
                    );

                    //статус в репозитории
                    if (sent) {
                        message.setStatus("SENT");
                        message.setSentAt(LocalDateTime.now());
                        logger.info("Scheduled message sent successfully: {}", message.getId());
                    } else {
                        message.setStatus("ERROR");
                        logger.error("Failed to send scheduled message: {}", message.getId());
                    }
                    notificationTaskRepository.save(message);

                } catch (Exception e) {
                    logger.error("Error sending scheduled message: {}", message.getId(), e);
                    message.setStatus("ERROR");
                    notificationTaskRepository.save(message);
                }
            }, delay, TimeUnit.MILLISECONDS);

            logger.info("Message scheduled: {} for time: {}", message.getId(), scheduledTime);
        } else {
            logger.warn("Message {} scheduled for past time: {}", message.getId(), scheduledTime);
            message.setStatus("ERROR");
            notificationTaskRepository.save(message);
        }
    }

    private void restoreScheduledMessages() {
        try {
            List<NotificationTask> pendingMessages = notificationTaskRepository.findByStatus("SCHEDULED");
            int restoredCount = 0;

            for (NotificationTask message : pendingMessages) {
                if (message.getScheduledTime().isAfter(LocalDateTime.now())) {
                    scheduleMessage(message);
                    restoredCount++;
                    logger.info("Restored scheduled message: {}", message.getId());
                } else {
                    message.setStatus("EXPIRED");
                    notificationTaskRepository.save(message);
                    logger.info("Marked expired message: {}", message.getId());
                }
            }

            logger.info("Restored {} scheduled messages", restoredCount);
        } catch (Exception e) {
            logger.error("Error restoring scheduled messages", e);
        }
    }


}
