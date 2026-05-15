package pro.sky.telegrambot.service;


import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramBotService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private TelegramBot bot;

    @Value("${telegram.bot.token}")
    private String botToken;

    @PostConstruct
    public void init() {
        this.bot = new TelegramBot(botToken);
        logger.info("Telegram bot initialized with token: {}", botToken.substring(0, 10) + "...");
    }

    //отправка сообщения
    public boolean sendMessage(Long chatId, String text) {
        return sendMessage(chatId.toString(), text);
    }

    //
    public boolean sendMessage(String chatId, String text) {
        try {
            SendMessage request = new SendMessage(chatId, text);
            SendResponse response = bot.execute(request);

            if (response.isOk()) {
                logger.debug("Message sent successfully to chat: {}, message: {}", chatId, text);
                return true;
            } else {
                logger.error("Failed to send message to chat: {}. Error: {}", chatId, response.description());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error sending message to chat: {}", chatId, e);
            return false;
        }
    }

    public TelegramBot getBot() {
        return bot;
    }
}