package pro.sky.telegrambot.controller;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.service.TelegramBotService;
import pro.sky.telegrambot.service.NotificationProcessingService;

import java.util.List;

@Service
public class TelegramBotListener implements UpdatesListener {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotListener.class);

    private final TelegramBotService telegramBotService;
    private final NotificationProcessingService notificationProcessingService;
    private final TelegramBot bot;

    @Autowired
    public TelegramBotListener(TelegramBotService telegramBotService,
                               NotificationProcessingService notificationProcessingService) {
        this.telegramBotService = telegramBotService;
        this.notificationProcessingService = notificationProcessingService;
        this.bot = telegramBotService.getBot();
    }

    @PostConstruct
    public void init() {
        bot.setUpdatesListener(this);
        logger.info("Telegram Bot UpdatesListener initialized successfully");
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(this::processUpdate);
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    public void onError(Throwable throwable) {
        logger.error("Updates listener error", throwable);
    }

    private void processUpdate(Update update) {
        logger.info("Processing update: {}", update);

        if (update.message() != null && update.message().text() != null) {
            String messageText = update.message().text();
            Long chatId = update.message().chat().id();

            logger.info("Received message: '{}' from chat: {}", messageText, chatId);

            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText);
            } else if (notificationProcessingService.checkMessageContainPattern(messageText)) {
                notificationProcessingService.handlePatternMessage(chatId, messageText);
            } else {
                telegramBotService.sendMessage(chatId,
                        "Я понимаю команды, начинающиеся с /, или сообщения в формате: ДД.ММ.ГГГГ ЧЧ:MM Текст_уведомления");
            }
        } else {
            logger.info("Update without text message: {}", update);
        }
    }

    private void handleCommand(Long chatId, String messageText) {
        String command = messageText.split(" ")[0];
        logger.info("Handling command: {} for chat: {}", command, chatId);

        switch (command) {
            case "/start":
                telegramBotService.sendMessage(chatId,
                        "Привет! Я бот для планирования уведомлений. Используйте /help для справки.");
                break;
            case "/help":
                notificationProcessingService.sendHelpMessage(chatId);
                break;
            case "/schedule":
                notificationProcessingService.handleScheduleCommand(chatId, messageText);
                break;
            case "/list":
                notificationProcessingService.handleListCommand(chatId);
                break;
            default:
                telegramBotService.sendMessage(chatId,
                        "Неизвестная команда. Используйте /help для списка команд.");
                break;
        }
    }
}