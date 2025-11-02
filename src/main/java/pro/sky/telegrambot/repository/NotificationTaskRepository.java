package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.sky.telegrambot.model.NotificationTask;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {
    List<NotificationTask> findByChatIdAndStatusOrderByScheduledTimeAsc(Long chatId, String status);
    List<NotificationTask> findByStatusAndScheduledTimeAfter(String status, LocalDateTime time);
    List<NotificationTask> findByStatusAndScheduledTimeBefore(String status, LocalDateTime time);
    List<NotificationTask> findByStatus(String status);
}
