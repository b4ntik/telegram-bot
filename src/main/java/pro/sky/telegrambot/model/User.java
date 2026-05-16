package pro.sky.telegrambot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "time_zone", length = 50)
    // Добавляем поле для хранения часового пояса
    private String timeZone;

    public User(Long chatId, String timeZone) {
        this.chatId = chatId;
        this.timeZone = timeZone;
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
}
