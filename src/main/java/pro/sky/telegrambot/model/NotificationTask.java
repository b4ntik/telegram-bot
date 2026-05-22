package pro.sky.telegrambot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Entity
@Table(name = "notification_task")
public class NotificationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SCHEDULED"; // SCHEDULED, SENT, ERROR

    @Column(name = "time_zone", length = 50)
    // Добавляем поле для хранения часового пояса
    private String timeZone;
    @Column(name = "is_yearly")
    private boolean isYearly = false;

    @Column(name = "yearly_day")
    private String yearlyDay;  // день месяца (01-31)

    @Column(name = "yearly_month")
    private String yearlyMonth;  // месяц (01-12)

    @Column(name = "yearly_time")
    private String yearlyTime;  // время в формате HH:mm

    //конструкторы
    public NotificationTask() {}

    public NotificationTask(Long chatId, String messageText, LocalDateTime scheduledTime, String timeZone) {
        this.chatId = chatId;
        this.messageText = messageText;
        this.scheduledTime = scheduledTime;
        this.createdAt = LocalDateTime.now();
        this.status = "SCHEDULED";
        this.timeZone = timeZone;
    }

    //геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    // Геттер для получения времени с учетом часового пояса
    public ZonedDateTime getScheduledTimeWithTimeZone() {
        return scheduledTime.atZone(ZoneId.of(timeZone));
    }

    // Геттеры и сеттеры
    public boolean getIsYearly() { return isYearly; }
    public void setIsYearly(boolean isYearly) { this.isYearly = isYearly; }

    public String getYearlyDay() { return yearlyDay; }
    public void setYearlyDay(String yearlyDay) { this.yearlyDay = yearlyDay; }

    public String getYearlyMonth() { return yearlyMonth; }
    public void setYearlyMonth(String yearlyMonth) { this.yearlyMonth = yearlyMonth; }

    public String getYearlyTime() { return yearlyTime; }
    public void setYearlyTime(String yearlyTime) { this.yearlyTime = yearlyTime; }
}