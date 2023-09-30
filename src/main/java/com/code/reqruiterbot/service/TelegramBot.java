package com.code.reqruiterbot.service;

import com.code.reqruiterbot.model.*;
import com.code.reqruiterbot.config.BotConfig;
import com.code.reqruiterbot.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.*;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final UserRepository userRepository;
    private final VacancyRepository vacancyRepository;
    private final BlackListRepository blackListRepository;
    final BotConfig config;
    static final String INFO_TEXT = "Этот бот создан для помощи работникам в сфере IT в трудоустройстве " +
    "а так же небольшого облегчения работы рекрутеру. Пожалуйста, ознакомьтесь с меню " +
    "и выберите нужный вам пункт.\n";
    static final String ABOUT_ME = "Приветствую! Я — телеграм-бот от команды qazdev, созданный, чтобы стать твоим надежным " +
            "помощником в поиске интересной работы. С моей помощью ты можешь:\n" +
            "\n" +
            "Ознакомиться с актуальными вакансиями нашей компании.\n" +
            "Подписаться на рассылку, чтобы первым узнавать о новых открытых позициях.\n" +
            "Отправить свое резюме прямиком в руки наших HR-специалистов.\n" +
            "Давай вместе найдем для тебя идеальное место в нашей команде! \uD83D\uDE80";
    private final Map<Long, Long> lastMessageTimes = new ConcurrentHashMap<>();
    private final Map<Long, Integer> messageCountPerMinute = new ConcurrentHashMap<>();
    private final int maxMessagesPerMinute = 10;

    @Autowired
    public TelegramBot(BotConfig config, BlackListRepository blackListRepository, VacancyRepository vacancyRepository, UserRepository userRepository) {
        this.config = config;
        this.blackListRepository = blackListRepository;
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "начало"));
        listofCommands.add(new BotCommand("/info", "информация о боте"));
        listofCommands.add(new BotCommand("/register", "записать мои данные и подписаться на бота"));
        listofCommands.add(new BotCommand("/forgetme", "удалить мои данные и отписаться от бота"));
        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            long currentTimeMillis = System.currentTimeMillis();
            long lastMessageTime = lastMessageTimes.getOrDefault(chatId, 0L);
            if (currentTimeMillis - lastMessageTime < 60000) {
                int messageCount = messageCountPerMinute.getOrDefault(chatId, 0);
                if (messageCount >= maxMessagesPerMinute) {
                    sendMessage(chatId, "Превышено максимальное количество сообщений в минуту.");
                    sendMessage(1631579869, "полундра, меня пытаются заспамить " + chatId);
                    return;
                }
            } else {
                messageCountPerMinute.put(chatId, 0);
            }
            Optional<BlackList> existingUser = blackListRepository.findByChatId(chatId);
            String messageText = update.getMessage().getText();
            if (existingUser.isPresent()) {
                sendMessage(chatId, "   ");
                return;
            } if (messageText.contains("/send") && config.getOwnerId() == chatId) {
                var textToSend =messageText.substring(messageText.indexOf(" "));
                var users = userRepository.findAll();
                for (User user : users) {
                    sendMessage(user.getChatId(), textToSend);
                }
                log.info(chatId + "sended message to all by ADMIN");
            } else if (messageText.contains("/adddb") && config.getOwnerId() == chatId) {
                addNewVacancy(update);
            } else if (messageText.contains("/removedb") && config.getOwnerId() == chatId) {
                removeVacancy(update);
            } else if (messageText.startsWith("/addbl") && config.getOwnerId() == chatId) {
                processAddToBlackListCommand(update.getMessage());
            } else if (messageText.startsWith("/removebl") && config.getOwnerId() == chatId) {
                processRemoveFromBlackListCommand(update.getMessage());
            } else {
                switch (messageText) {
                    case "/start":
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;
                    case "/info":
                        sendMessage(chatId, INFO_TEXT);
                        log.info(chatId + "requested info");
                        break;
                    case "/register":
                        if (userRepository.findById(chatId).isEmpty()) {
                            register(chatId);
                        } else {
                            sendMessage(chatId, "Вы уже зарегистрированы.");
                        }
                        break;
                    case "/forgetme":
                        deleteUser(update.getMessage());
                        break;
                    case "Доступные вакансии":
                        handleVacanciesButton(chatId);
                        break;
                    case "Обо мне":
                        sendMessage(chatId, ABOUT_ME);
                        break;
                    default:
                        sendMessage(chatId, "Данной команды не существует");
                        break;
                }
            }
            lastMessageTimes.put(chatId, currentTimeMillis);
            messageCountPerMinute.put(chatId, messageCountPerMinute.getOrDefault(chatId, 0) + 1);
        } else if (update.hasMessage() && update.getMessage().hasDocument()) {
            long chatId = update.getMessage().getChatId();
            handleDocumentMessage(update.getMessage(), chatId);
        } else if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            if (update.getCallbackQuery().getMessage().hasText()) {
                String callBackData = update.getCallbackQuery().getData();
                long messageId = update.getCallbackQuery().getMessage().getMessageId();

                // Обработка callback'ов для выбора вакансии
                if (callBackData.startsWith("select_vacancy_")) {//ОБРАБАТЫВАЕТСЯ КОЛЛБЭК ----------------------2
                    String[] parts = callBackData.split("_");
                    if (parts.length == 3) {
                        int vacancyId = Integer.parseInt(parts[2]);
                        log.info("Callback for vacancyId: " + vacancyId); // Добавьте эту строку для логирования

                        handleResumeSubmission(chatId, vacancyId);
                    } else {
                        log.error("Invalid parts length: " + parts.length);
                    }
                }
                // Обработка callback'ов для регистрации пользователя
                else if (callBackData.equals("YES_BUTTON")) {
                    registerUser(update.getCallbackQuery().getMessage());
                    String text = "Вы успешно зарегистрировались. Теперь вы будете получать все обновления по доступным вакансиям ✉";
                    EditMessageText message = new EditMessageText();
                    message.setChatId(String.valueOf(chatId));
                    message.setText(text);
                    message.setMessageId((int) messageId);
                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.error("Error occured: Exception thrown in YES button" + e);
                    }
                } else if (callBackData.equals("NO_BUTTON")) {
                    String text = "Вы не будете получать обновления по вакансиям ✘";
                    EditMessageText message = new EditMessageText();
                    message.setChatId(String.valueOf(chatId));
                    message.setText(text);
                    message.setMessageId((int) messageId);
                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.error("Error occured: Exception thrown in NO button " + e);
                    }
                }
                else {
                    log.error("Invalid callbackData: " + callBackData);
                }
            }
        }
    }

    private void handleDocumentMessage(Message message, long chatId) {
        Document document = message.getDocument();
        String fileId = document.getFileId();
        String fileName = document.getFileName();
        long maxFileSize = 10 * 1024 * 1024; // 10 МБ
        Integer fileSize = document.getFileSize();

        if (fileSize != null && fileSize <= maxFileSize) {
            String fileExtension = getFileExtension(fileName);
            List<String> supportedExtensions = Arrays.asList("pdf", "doc", "docx", "jpeg", "jpg");

            if (supportedExtensions.contains(fileExtension.toLowerCase())) {
                try {
                    java.io.File localFile = getFileInfoAndDownload(fileId);
                    String savePath = "D:/CV from reqbot/" + fileName;
                    saveFileLocally(localFile, savePath, chatId);

                    // Отправьте файл рекрутеру
                    sendFileToRecruiter(chatId, fileId, fileName);
                } catch (TelegramApiException | IOException e) {
                    log.error("Exception thrown while sending file to recruiter: " + e);
                    sendMessage(chatId, "Произошла ошибка при отправке файла.");
                }
            } else {
                sendMessage(chatId, "Формат файла не поддерживается. Поддерживаемые форматы: PDF, DOC, DOCX, JPEG, JPG");
                log.error("User " + chatId + " tried to upload an unsupported file format");
            }
        } else {
            sendMessage(chatId, "Файл слишком большой. Максимальный размер файла: 10 МБ");
            log.error("User " + chatId + " tried to upload a file > 10 Mb");
        }
    }
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
    private java.io.File getFileInfoAndDownload(String fileId) throws TelegramApiException, IOException {
        GetFile getFileRequest = new GetFile();
        getFileRequest.setFileId(fileId);
        try {
            org.telegram.telegrambots.meta.api.objects.File fileObj = execute(getFileRequest);
            String filePath = fileObj.getFilePath();
            java.io.File tempFile = java.io.File.createTempFile("telegram_file_", ".tmp");
            downloadTelegramFile(filePath, tempFile);
            return tempFile;
        } catch (TelegramApiException | IOException e) {
            log.error("Exception throwed in method getFileInfoAndDownload" + e);
            throw e;
        }
    }

    public void downloadTelegramFile(String filePath, java.io.File destinationFile) throws TelegramApiException, IOException {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(destinationFile);
            String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream fileStream = connection.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            fileOutputStream.close();
            fileStream.close();
        } catch (IOException e) {
            log.error("Exception throwed in method downloadTelegramFile" + e);
            throw e;
        }
    }
    private void saveFileLocally(File file, String localPath, long chatId) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        java.nio.file.Path localFilePath = java.nio.file.Paths.get(localPath);
        java.nio.file.Files.copy(fileInputStream, localFilePath);
        fileInputStream.close();
        log.info("Resume sended by user and downloaded sucsessfully" + chatId);
    }
    private void sendFileToRecruiter(long recruiterChatId, String fileId, String fileName) {
        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(Long.toString(recruiterChatId)); //тут не верно-----------------------------------------------------------------------------------
        InputFile inputFile = new InputFile(fileId);
        sendDocument.setDocument(inputFile);
        sendDocument.setCaption(fileName);
        try {
            execute(sendDocument);
            log.info("Resume sent to recruiter successfully");
        } catch (TelegramApiException e) {
            log.error("Exception thrown while sending to recruiter: " + e);
        }
    }
    private void handleVacanciesButton(long chatId) { //ТЫКАЕМ КНОПКУ показать ВАКАНСИЮ------------------------------1
        List<Vacancy> vacancies = vacancyRepository.findAll();
        if (!vacancies.isEmpty()) {
            for (Vacancy vacancy : vacancies) {
                String callbackData = "select_vacancy_" + vacancy.getVacancyId();
                InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                InlineKeyboardButton button = new InlineKeyboardButton("Выбрать вакансию");
                button.setCallbackData(callbackData);
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(button);
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                keyboard.add(row);
                keyboardMarkup.setKeyboard(keyboard);

                StringBuilder vacancyText = new StringBuilder();
                vacancyText.append("ID: ").append(vacancy.getVacancyId()).append("\n")
                        .append("Название вакансии: ").append(vacancy.getJobTitle()).append("\n")
                        .append("Описание: ").append(vacancy.getProjectDescription()).append("\n")
                        .append("Обязанности: ").append(vacancy.getResponsibilities()).append("\n")
                        .append("Требования: ").append(vacancy.getRequirements()).append("\n\n\n");
                sendMessageWithInlineKeyboard(chatId, vacancyText.toString(), keyboardMarkup);
            }
            log.info("The user requested vacancies list: " + chatId);
        } else {
            sendMessage(chatId, "На данный момент вакансий нет, пожалуйста, оставайтесь на связи и проверяйте список, они обязательно появятся!");
            log.info("The user requested vacancies list, no available vacancies now: " + chatId);
        }
    }
    public void sendMessageWithInlineKeyboard(long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred while sending message with inline keyboard: " + e);
        }
    }
    private void handleResumeSubmission(long chatId, int vacancyId) { //РЕЗУЛЬТАТ НАЖАТИЯ КНОПКИ-------------------3
        //информация о вакансии и рекрутере на основе vacancyId
        Vacancy vacancy = vacancyRepository.findByVacancyId(vacancyId);

        if (vacancy != null) {
            //chatId рекрутера из vacancy
            long recruiterChatId = vacancy.getRecruiterChatId();
            log.info("Recruiter's chatId got " + recruiterChatId);
            if (recruiterChatId > 0) {
                String responseMessage = "Пожалуйста, отправьте свое резюме, и я передам его рекрутеру.";
                sendMessage(chatId, responseMessage);
                log.info("pushed button by user " + chatId);
            } else {
                String errorMessage = "Извините, не удалось найти рекрутера для этой вакансии.";
                sendMessage(chatId, errorMessage);
                log.info("cant find recruiter " + chatId);
            }
        } else {
            String errorMessage = "Извините, не удалось найти вакансию с выбранным ID.";
            sendMessage(chatId, errorMessage);
            log.info("cant find vacancie " + chatId);
        }
    }
    private void register(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Желаете зарегестрироваться и получать рассылку вакансий?");
        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();
        yesButton.setText("Да");
        yesButton.setCallbackData("YES_BUTTON");
        var noButton = new InlineKeyboardButton();
        noButton.setText("Нет");
        noButton.setCallbackData("NO_BUTTON");
        rowInLine.add(yesButton);
        rowInLine.add(noButton);
        rowsInLine.add(rowInLine);
        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occured while pushing buttons YES or NO: " + e);
        }
    }
    private void registerUser(Message msg) {//РЕГИСТРИРУЕМ ПОЛЬЗОВАТЕЛЯ-------------------------------------------------------------------------------!
        var chatId = msg.getChatId();
        var chat = msg.getChat();
        String text = "Вы уже зарегистрированы";
        User user = new User();
        user.setChatId(chatId);
        user.setFirstName(chat.getFirstName());
        user.setLastName(chat.getLastName());
        user.setUserName(chat.getUserName());
        user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
        userRepository.save(user);
        log.info("new user saved: " + user);
    }
    private void deleteUser(Message message) {//УДАЛЯЕМ ПОЛЬЗОВАТЕЛЯ-------------------------------------------------------------------------------!
        String text = "Вы отписались от бота ☹";
        Long chatId = message.getChatId();
        userRepository.deleteById(chatId);
        sendMessage(chatId, text);
        log.info("The user delited: " + chatId);
    }
    public void processAddToBlackListCommand(Message message) {
        Long chatId = message.getChatId();
        String[] commandParts = message.getText().split(" ");
        if (commandParts.length == 2) {
            try {
                long targetChatId = Long.parseLong(commandParts[1]);
                String firstName = message.getChat().getFirstName();
                String userName = message.getChat().getUserName();
                addToBlackList(targetChatId, firstName, userName);
            } catch (NumberFormatException e) {
                sendMessage(config.getOwnerId(), "Неверный формат ID пользователя.");
                log.error("wrong format ID " + e);
            }
        } else {
            sendMessage(config.getOwnerId(), "Неправильное количество параметров команды.");
            log.error("wrong format of command ");
        }
    }
    public void addToBlackList(Long chatId, String firstName, String userName) {
        Optional<BlackList> existingUser = blackListRepository.findByChatId(chatId);
        if (!existingUser.isPresent()) {
            BlackList blackListUser = new BlackList();
            blackListUser.setChatId(chatId);
            blackListUser.setBannedAt(new Timestamp(System.currentTimeMillis()));
            blackListUser.setFirstName(firstName);
            blackListUser.setUserName(userName);
            blackListRepository.save(blackListUser);
            sendMessage(config.getOwnerId(), "пользоавтель " + chatId+ " добавлен в black list");
            log.info("The user added to Black list: " + chatId);
        }
        else {
            sendMessage(config.getOwnerId(), "Пользователь с ID " + chatId + " уже находится в черном списке.");
            log.info("trying to doubleadd to blacklist " + chatId);
        }
    }
    public void removeFromBlackList(long chatId) {
        Optional<BlackList> existingUser = blackListRepository.findByChatId(chatId);
        if (existingUser.isPresent()) {
            blackListRepository.delete(existingUser.get());
            sendMessage(config.getOwnerId(), "Пользователь с ID " + chatId + " был удален из черного списка.");
        } else {
            sendMessage(config.getOwnerId(), "Пользователь с ID " + chatId + " не находится в черном списке.");
        }
    }
    public void processRemoveFromBlackListCommand(Message message) {
        String[] commandParts = message.getText().split(" ");
        if (commandParts.length == 2) {
            try {
                long targetChatId = Long.parseLong(commandParts[1]);
                removeFromBlackList(targetChatId);
                log.info("The user removed from Black list: " + targetChatId);
            } catch (NumberFormatException e) {
                sendMessage(config.getOwnerId(), "Неверный формат ID пользователя.");
                log.error("wrong format ID " + e);
            }
        } else {
            sendMessage(config.getOwnerId(), "Неправильное количество параметров команды.");
            log.error("wrong format of command ");
        }
    }
    private void addNewVacancy(Update update) {
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();
        String[] parts = messageText.split("\\|");
        if (parts.length == 4) {
            String jobTitle = parts[0].trim();
            String command = "/adddb";
            if (jobTitle.startsWith(command)) {
                jobTitle = jobTitle.substring(command.length()).trim();
            }
            String projectDescription = parts[1].trim();
            String responsibilities = parts[2].trim();
            String requirements = parts[3].trim();
            long recruiterChatId = chatId;
            Vacancy newVacancy = new Vacancy();
            newVacancy.setJobTitle(jobTitle);
            newVacancy.setProjectDescription(projectDescription);
            newVacancy.setResponsibilities(responsibilities);
            newVacancy.setRequirements(requirements);
            newVacancy.setRecruiterChatId(recruiterChatId);
            vacancyRepository.save(newVacancy);
            sendMessage(chatId, "Новая вакансия успешно добавлена ✅");
            log.info("Vacancy uploaded by user " + chatId);
            } else {
                sendMessage(chatId, "Пожалуйста, убедитесь, что ввод содержит название вакансии, описание проекта, обязанности и требования, разделенные символом '|'.");
                log.error("Vacancy not uploaded, reqruiter's mistake " + chatId);
            }
    }
    private void removeVacancy(Update update) {
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();
        if (messageText.startsWith("/removedb ")) {
            String vacancyIdStr = messageText.substring("/removedb ".length()).trim();
            if (!vacancyIdStr.isEmpty() && vacancyIdStr.matches("\\d+")) {
                long vacancyId = Long.parseLong(vacancyIdStr);
                Optional<Vacancy> optionalVacancy = vacancyRepository.findById((int) vacancyId);
                if (optionalVacancy.isPresent()) {
                    Vacancy vacancyToRemove = optionalVacancy.get();
                    vacancyRepository.delete(vacancyToRemove);
                    sendMessage(chatId, "Вакансия с ID '" + vacancyId + "' удалена ✅");
                    log.info("Vacancy with ID '" + vacancyId + "' was removed by " + chatId);
                } else {
                    sendMessage(chatId, "Вакансия с ID '" + vacancyId + "' не найдена.");
                    log.info("Vacancy with ID '" + vacancyId + "' was not found by " + chatId);
                }
            } else {
                sendMessage(chatId, "Пожалуйста, укажите корректный ID вакансии после команды /removedb.");
            }
        }
    }

    private void startCommandReceived(long chatId, String name) {
        String answer = "Привет, " + name + ", пожалуйста ознакомьтесь с меню и выберите интересующий пункт. " + "\nДля того чтобы " +
                "подписаться на бота и получать рассылку актуальных вакансий пожалуйста зарегистрируйтесь." +
                "\nВот краткое описание команд и кнопок, доступных в боте: \n /start - список команд \n /register - подписаться на бота и рассылку" +
                "\n /forgetMe - отписаться от бота \n /info - информация о боте";
        log.info("Replied START command to user " + name);
        sendMessage(chatId, answer);
    }
    private void sendMessage(long chatId, String textToSend)  {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Доступные вакансии");
        row.add("Обо мне");
        keyboardRows.add(row);
        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occured while sending message " + e);
        }
    }
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }
    @Override
    public String getBotToken() {
        return config.getToken();
    }
}