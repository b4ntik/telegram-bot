package pro.sky.telegrambot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "username")
    private String username;

    @Column(name = "time_zone", length = 50)
    // Добавляем поле для хранения часового пояса
    private String timeZone;
    @Column(name = "registredAt", nullable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();

    public User() {

    }

    public User(Long chatId, String timeZone) {
        this.chatId = chatId;
        this.timeZone = timeZone;
        this.registeredAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

   public void saveUserTimeZone(Long chatId, String timeZoneStr) {
        this.chatId=chatId;
        this.timeZone = timeZoneStr;
   }

    public void setRegisteredAt(LocalDateTime now) {
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {  // ← этот метод нужен
        this.username = username;
    }
}
