package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.sky.telegrambot.model.NotificationTask;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {
    List<NotificationTask> findByChatIdAndStatusOrderByScheduledTimeAsc(Long chatId, String status);
    List<NotificationTask> findByStatusAndScheduledTimeAfter(String status, LocalDateTime time);
    List<NotificationTask> findByStatus(String status);
    // Поиск задач по статусу и времени (для основного шедулера)
    List<NotificationTask> findByStatusAndScheduledTimeBefore(String status, LocalDateTime time);

    // Поиск задач для конкретного пользователя по точному времени
    List<NotificationTask> findByChatIdAndStatusAndScheduledTime(Long chatId, String status, LocalDateTime time);

    // Поиск задач для конкретного пользователя по диапазону времени
    List<NotificationTask> findByChatIdAndStatusAndScheduledTimeBetween(Long chatId, String status,
                                                                        LocalDateTime start, LocalDateTime end);

    // Поиск всех задач, которые нужно отправить в ближайшие N минут
    @Query("SELECT t FROM NotificationTask t WHERE t.status = 'SCHEDULED' AND t.scheduledTime <= :now")
   List<NotificationTask> findByStatusAndScheduledTimeBefore(@Param("now") LocalDateTime now);

    List<NotificationTask> findByChatIdAndStatus(Long chatId, String status);
}
