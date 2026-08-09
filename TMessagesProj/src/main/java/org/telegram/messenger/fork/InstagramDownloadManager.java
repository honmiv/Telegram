package org.telegram.messenger.fork;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class InstagramDownloadManager implements NotificationCenter.NotificationCenterDelegate {

    private static volatile InstagramDownloadManager[] Instance = new InstagramDownloadManager[3];
    private final int currentAccount;
    
    private final String BOT_USERNAME = "SaveAsBot";
    private long botPeerId = 0;
    private boolean isResolving = false;
    
    // State of the current interception
    private boolean isWaitingForBot = false;
    private String currentText = null;
    private ArrayList<Long> targetDialogIds = new ArrayList<>();
    private Runnable timeoutRunnable;
    private Runnable forwardRunnable;
    private ArrayList<MessageObject> collectedMediaMessages = new ArrayList<>();

    public static InstagramDownloadManager getInstance(int num) {
        InstagramDownloadManager localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (InstagramDownloadManager.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new InstagramDownloadManager(num);
                }
            }
        }
        return localInstance;
    }

    private InstagramDownloadManager(int account) {
        this.currentAccount = account;
    }

    public boolean interceptShare(String text, ArrayList<Long> dialogIds) {
        if (text == null || !text.contains("instagram.com")) {
            return false;
        }

        // Do not intercept if we are already sending this directly to the bot
        if (botPeerId != 0 && dialogIds != null && dialogIds.size() == 1 && dialogIds.get(0) == botPeerId) {
            return false;
        }

        if (isWaitingForBot) {
            if (text.equals(currentText)) {
                targetDialogIds.addAll(dialogIds);
                return true;
            }
            Toast.makeText(ApplicationLoader.applicationContext, "Уже скачивается другое видео...", Toast.LENGTH_SHORT).show();
            return true;
        }

        currentText = text;
        Toast.makeText(ApplicationLoader.applicationContext, "Скачивание видео через бота...", Toast.LENGTH_SHORT).show();

        isWaitingForBot = true;
        targetDialogIds.clear();
        targetDialogIds.addAll(dialogIds);
        collectedMediaMessages.clear();

        if (botPeerId != 0) {
            startDownloadProcess(text);
        } else {
            resolveBotAndStart(text);
        }

        return true;
    }

    private void resolveBotAndStart(String text) {
        if (isResolving) {
            return;
        }
        isResolving = true;
        MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(BOT_USERNAME, peerId -> {
            isResolving = false;
            if (peerId != null && peerId != 0 && peerId != Long.MAX_VALUE) {
                botPeerId = peerId;
                
                SendMessagesHelper.getInstance(currentAccount).sendMessage(
                        SendMessagesHelper.SendMessageParams.of("/start", botPeerId, null, null, null, true, null, null, null, true, 0, 0, null, false)
                );
                
                startDownloadProcess(text);
            } else {
                handleFailure("Не удалось найти бота " + BOT_USERNAME);
            }
        });
    }

    private void startDownloadProcess(String text) {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);

        SendMessagesHelper.getInstance(currentAccount).sendMessage(
                SendMessagesHelper.SendMessageParams.of(text, botPeerId, null, null, null, true, null, null, null, true, 0, 0, null, false)
        );

        timeoutRunnable = () -> handleFailure("Бот не ответил вовремя. Попробуйте позже.");
        AndroidUtilities.runOnUIThread(timeoutRunnable, 30000);
    }

    private void handleFailure(String errorMsg) {
        cleanup();
        Toast.makeText(ApplicationLoader.applicationContext, errorMsg, Toast.LENGTH_LONG).show();
    }

    private void cleanup() {
        isWaitingForBot = false;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        if (timeoutRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (forwardRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(forwardRunnable);
            forwardRunnable = null;
        }
        collectedMediaMessages.clear();
        targetDialogIds.clear();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            long dialogId = (Long) args[0];
            if (dialogId != botPeerId) {
                return;
            }

            ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
            boolean hasMedia = false;
            
            for (MessageObject msg : messages) {
                if (msg.isOutOwner()) continue;
                
                if (msg.messageOwner.media != null && (msg.messageOwner.media.photo != null || msg.messageOwner.media.document != null)) {
                    collectedMediaMessages.add(msg);
                    hasMedia = true;
                } else if (msg.messageOwner.message != null && msg.messageOwner.message.toLowerCase().contains("ошибка")) {
                    handleFailure("Бот вернул ошибку: " + msg.messageOwner.message);
                    return;
                }
            }

            if (hasMedia) {
                if (forwardRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(forwardRunnable);
                }
                
                forwardRunnable = this::forwardCollectedMedia;
                AndroidUtilities.runOnUIThread(forwardRunnable, 2000);
            }
        }
    }

    private void forwardCollectedMedia() {
        if (collectedMediaMessages.isEmpty() || targetDialogIds.isEmpty()) {
            cleanup();
            return;
        }

        int maxId = 0;
        int maxDate = 0;

        for (long targetId : targetDialogIds) {
            SendMessagesHelper.getInstance(currentAccount).sendMessage(
                    collectedMediaMessages, targetId, true, false, true, 0, 0, null, 0, 0, 0, null
            );
        }

        for (MessageObject msg : collectedMediaMessages) {
            if (msg.getId() > maxId) {
                maxId = msg.getId();
                maxDate = msg.messageOwner.date;
            }
        }
        if (maxId > 0) {
            MessagesController.getInstance(currentAccount).markDialogAsRead(botPeerId, maxId, 0, maxDate, false, 0, 0, true, 0);
        }

        Toast.makeText(ApplicationLoader.applicationContext, "Видео из Instagram успешно отправлено!", Toast.LENGTH_SHORT).show();
        cleanup();
    }
}
