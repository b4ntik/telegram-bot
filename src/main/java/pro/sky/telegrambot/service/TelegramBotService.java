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

    @PreDestroy
    public void destroy() {
        if (bot != null) {
            bot.shutdown();
            logger.info("Telegram bot shutdown");
        }
    }

    /**
     * Отправка текстового сообщения
     */
    public boolean sendMessage(Long chatId, String text) {
        return sendMessage(chatId.toString(), text);
    }

    /**
     * Отправка текстового сообщения с указанием chatId как String
     */
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

    /**
     * Отправка сообщения с форматированием (Markdown)
     */
    public boolean sendMessageWithMarkdown(Long chatId, String text) {
        try {
            SendMessage request = new SendMessage(chatId.toString(), text)
                    .parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown);
            SendResponse response = bot.execute(request);

            return response.isOk();
        } catch (Exception e) {
            logger.error("Error sending markdown message to chat: {}", chatId, e);
            return false;
        }
    }

    /**
     * Отправка сообщения с HTML форматированием
     */
    public boolean sendMessageWithHtml(Long chatId, String text) {
        try {
            SendMessage request = new SendMessage(chatId.toString(), text)
                    .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML);
            SendResponse response = bot.execute(request);

            return response.isOk();
        } catch (Exception e) {
            logger.error("Error sending HTML message to chat: {}", chatId, e);
            return false;
        }
    }

    /**
     * Отправка сообщения с отключением уведомления (silent)
     */
    public boolean sendSilentMessage(Long chatId, String text) {
        try {
            SendMessage request = new SendMessage(chatId.toString(), text)
                    .disableNotification(true);
            SendResponse response = bot.execute(request);

            return response.isOk();
        } catch (Exception e) {
            logger.error("Error sending silent message to chat: {}", chatId, e);
            return false;
        }
    }

    /**
     * Получение экземпляра бота для расширенного использования
     */
    public TelegramBot getBot() {
        return bot;
    }
}