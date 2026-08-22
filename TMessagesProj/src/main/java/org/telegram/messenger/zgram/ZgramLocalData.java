package org.telegram.messenger.zgram;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

public final class ZgramLocalData {

    public static final int RETENTION_OFF = 0;
    public static final int RETENTION_WEEK = 7;
    public static final int RETENTION_MONTH = 30;
    public static final int RETENTION_YEAR = 365;
    public static final int RETENTION_FOREVER = -1;

    private static final int MAGIC = 0x5A474C31;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_VERSIONS = 20;
    private static final long MAX_FILE_SIZE = 128L * 1024L * 1024L;
    private static final ZgramLocalData[] instances = new ZgramLocalData[UserConfig.MAX_ACCOUNT_COUNT];

    private final int account;
    private final long ownerId;
    private final LinkedHashMap<String, Snapshot> bookmarks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Snapshot> archive = new LinkedHashMap<>();
    private final HashMap<Long, Integer> retention = new HashMap<>();

    private boolean loaded;
    private boolean dirty;
    private boolean saveScheduled;

    public static ZgramLocalData get(int account) {
        synchronized (instances) {
            final long ownerId = UserConfig.getInstance(account).getClientUserId();
            final ZgramLocalData current = instances[account];
            if (current == null || current.ownerId != ownerId) {
                instances[account] = new ZgramLocalData(account, ownerId);
            }
            return instances[account];
        }
    }

    private ZgramLocalData(int account, long ownerId) {
        this.account = account;
        this.ownerId = ownerId;
    }

    public synchronized boolean isBookmarked(long dialogId, int messageId) {
        ensureLoadedLocked();
        return bookmarks.containsKey(key(dialogId, messageId));
    }

    public static boolean canStore(MessageObject message) {
        return eligible(message);
    }

    public synchronized boolean toggleBookmark(MessageObject message) {
        ensureLoadedLocked();
        if (!eligible(message)) {
            return false;
        }
        final String key = key(message.getDialogId(), message.getId());
        if (bookmarks.remove(key) != null) {
            scheduleSaveLocked();
            return false;
        }
        bookmarks.put(key, snapshot(message));
        scheduleSaveLocked();
        return true;
    }

    public synchronized void removeBookmark(long dialogId, int messageId) {
        ensureLoadedLocked();
        if (bookmarks.remove(key(dialogId, messageId)) != null) {
            scheduleSaveLocked();
        }
    }

    public synchronized void clearBookmarks() {
        ensureLoadedLocked();
        if (!bookmarks.isEmpty()) {
            bookmarks.clear();
            scheduleSaveLocked();
        }
    }

    public synchronized List<Snapshot> bookmarks(String query) {
        ensureLoadedLocked();
        final ArrayList<Snapshot> result = new ArrayList<>();
        for (Snapshot snapshot : bookmarks.values()) {
            if (snapshot.matches(query)) {
                result.add(snapshot.copy());
            }
        }
        result.sort(Comparator.comparingLong((Snapshot value) -> value.observedAt).reversed());
        return result;
    }

    public synchronized int bookmarkCount() {
        ensureLoadedLocked();
        return bookmarks.size();
    }

    public synchronized int getRetention(long dialogId) {
        ensureLoadedLocked();
        final Integer value = retention.get(dialogId);
        return value == null ? RETENTION_OFF : value;
    }

    public synchronized boolean isArchiveEnabled(long dialogId) {
        return getRetention(dialogId) != RETENTION_OFF;
    }

    public synchronized void setRetention(long dialogId, int days) {
        ensureLoadedLocked();
        if (days == RETENTION_OFF) {
            if (retention.remove(dialogId) != null) {
                scheduleSaveLocked();
            }
            return;
        }
        if (days != RETENTION_WEEK && days != RETENTION_MONTH && days != RETENTION_YEAR && days != RETENTION_FOREVER) {
            return;
        }
        if (!Integer.valueOf(days).equals(retention.put(dialogId, days))) {
            pruneLocked();
            scheduleSaveLocked();
        }
    }

    public synchronized void capture(MessageObject message) {
        ensureLoadedLocked();
        if (captureLocked(message)) {
            pruneLocked();
            scheduleSaveLocked();
        }
    }

    public synchronized void capture(List<MessageObject> messages) {
        ensureLoadedLocked();
        boolean changed = false;
        if (messages != null) {
            for (MessageObject message : messages) {
                changed |= captureLocked(message);
            }
        }
        if (changed) {
            pruneLocked();
            scheduleSaveLocked();
        }
    }

    public synchronized void markDeleted(long dialogId, List<Integer> messageIds) {
        ensureLoadedLocked();
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        final long deletedAt = System.currentTimeMillis() / 1000L;
        boolean changed = false;
        for (Integer messageId : messageIds) {
            if (messageId == null) {
                continue;
            }
            final String key = key(dialogId, messageId);
            changed |= markDeletedLocked(bookmarks.get(key), deletedAt);
            changed |= markDeletedLocked(archive.get(key), deletedAt);
        }
        if (changed) {
            scheduleSaveLocked();
        }
    }

    public synchronized List<ArchiveSummary> archiveDialogs(String query) {
        ensureLoadedLocked();
        pruneLocked();
        final HashMap<Long, ArchiveSummary> summaries = new HashMap<>();
        for (Snapshot snapshot : archive.values()) {
            ArchiveSummary summary = summaries.get(snapshot.dialogId);
            if (summary == null) {
                summary = new ArchiveSummary(snapshot.dialogId, snapshot.peerName, getRetention(snapshot.dialogId));
                summaries.put(snapshot.dialogId, summary);
            }
            summary.count++;
            summary.lastDate = Math.max(summary.lastDate, snapshot.date);
            summary.deletedCount += snapshot.deletedAt != 0 ? 1 : 0;
            summary.approximateBytes += snapshot.estimateSize();
            if (TextUtils.isEmpty(summary.peerName) && !TextUtils.isEmpty(snapshot.peerName)) {
                summary.peerName = snapshot.peerName;
            }
        }
        for (Long dialogId : retention.keySet()) {
            if (!summaries.containsKey(dialogId)) {
                summaries.put(dialogId, new ArchiveSummary(dialogId, DialogObject.getName(account, dialogId), getRetention(dialogId)));
            }
        }
        final ArrayList<ArchiveSummary> result = new ArrayList<>();
        for (ArchiveSummary summary : summaries.values()) {
            if (summary.matches(query)) {
                result.add(summary.copy());
            }
        }
        result.sort(Comparator.comparingInt((ArchiveSummary value) -> value.lastDate).reversed());
        return result;
    }

    public synchronized List<Snapshot> archive(long dialogId, String query) {
        ensureLoadedLocked();
        pruneLocked();
        final ArrayList<Snapshot> result = new ArrayList<>();
        for (Snapshot snapshot : archive.values()) {
            if (snapshot.dialogId == dialogId && snapshot.matches(query)) {
                result.add(snapshot.copy());
            }
        }
        result.sort(Comparator.comparingInt((Snapshot value) -> value.date).reversed());
        return result;
    }

    public synchronized int archiveCount() {
        ensureLoadedLocked();
        pruneLocked();
        return archive.size();
    }

    public synchronized void clearArchive(long dialogId) {
        ensureLoadedLocked();
        boolean changed = false;
        final Iterator<Snapshot> iterator = archive.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().dialogId == dialogId) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            scheduleSaveLocked();
        }
    }

    public synchronized void clearAllArchives() {
        ensureLoadedLocked();
        if (!archive.isEmpty() || !retention.isEmpty()) {
            archive.clear();
            retention.clear();
            scheduleSaveLocked();
        }
    }

    public synchronized long storageSize() {
        ensureLoadedLocked();
        final File file = storageFile();
        return file.exists() ? file.length() : 0L;
    }

    public synchronized String exportJson(long dialogId) {
        ensureLoadedLocked();
        final JSONObject root = new JSONObject();
        try {
            root.put("format", "zgram-local-archive");
            root.put("version", FORMAT_VERSION);
            root.put("dialog_id", dialogId);
            root.put("chat", DialogObject.getName(account, dialogId));
            root.put("retention_days", getRetention(dialogId));
            final JSONArray messages = new JSONArray();
            for (Snapshot snapshot : archive(dialogId, null)) {
                messages.put(snapshot.toJson());
            }
            root.put("messages", messages);
            return root.toString(2);
        } catch (Exception e) {
            FileLog.e(e);
            return root.toString();
        }
    }

    public synchronized String exportHtml(long dialogId) {
        ensureLoadedLocked();
        final String chat = DialogObject.getName(account, dialogId);
        final StringBuilder result = new StringBuilder();
        result.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        result.append("<title>").append(html(chat)).append(" — Zgram Local Archive</title>");
        result.append("<style>body{margin:0;background:#090a0d;color:#eee7dc;font:15px system-ui}main{max-width:860px;margin:auto;padding:32px 18px}h1{color:#e0ad50}.item{background:#13161b;border:1px solid #2a1e33;border-radius:14px;padding:14px;margin:12px 0}.meta{color:#bda978;font-size:12px}.badge{color:#090a0d;background:#e0ad50;border-radius:8px;padding:2px 7px;font-size:11px}.deleted{background:#7446a3;color:white}.text{white-space:pre-wrap;margin-top:9px}.version{border-left:2px solid #7446a3;padding-left:10px;margin-top:10px;color:#c8becf}</style></head><body><main>");
        result.append("<h1>").append(html(chat)).append("</h1><p>Encrypted local archive exported by Zgram.</p>");
        for (Snapshot snapshot : archive(dialogId, null)) {
            result.append("<section class=\"item\"><div class=\"meta\"><span class=\"badge");
            if (snapshot.deletedAt != 0) {
                result.append(" deleted");
            }
            result.append("\">").append(snapshot.deletedAt != 0 ? "DELETED" : snapshot.edited ? "EDITED" : !TextUtils.isEmpty(snapshot.media) ? "MEDIA" : "MESSAGE").append("</span> ");
            result.append(html(snapshot.senderName)).append(" · ").append(html(new Date(snapshot.date * 1000L).toString())).append("</div>");
            if (!TextUtils.isEmpty(snapshot.text)) {
                result.append("<div class=\"text\">").append(html(snapshot.text)).append("</div>");
            }
            if (!TextUtils.isEmpty(snapshot.media)) {
                result.append("<div class=\"meta\">Media: ").append(html(snapshot.media)).append("</div>");
            }
            for (Version version : snapshot.versions) {
                result.append("<div class=\"version\"><div class=\"meta\">Earlier version · ").append(html(new Date(version.observedAt * 1000L).toString())).append("</div>");
                result.append("<div class=\"text\">").append(html(version.text)).append("</div></div>");
            }
            result.append("</section>");
        }
        result.append("</main></body></html>");
        return result.toString();
    }

    private boolean captureLocked(MessageObject message) {
        if (!eligible(message)) {
            return false;
        }
        final String key = key(message.getDialogId(), message.getId());
        final Snapshot incoming = snapshot(message);
        boolean changed = false;
        final Snapshot bookmark = bookmarks.get(key);
        if (bookmark != null) {
            changed |= bookmark.updateFrom(incoming);
        }
        if (getRetention(message.getDialogId()) != RETENTION_OFF) {
            final Snapshot archived = archive.get(key);
            if (archived == null) {
                archive.put(key, incoming);
                changed = true;
            } else {
                changed |= archived.updateFrom(incoming);
            }
        }
        return changed;
    }

    private static boolean markDeletedLocked(Snapshot snapshot, long deletedAt) {
        if (snapshot == null || snapshot.deletedAt != 0) {
            return false;
        }
        snapshot.deletedAt = deletedAt;
        snapshot.observedAt = deletedAt;
        return true;
    }

    private Snapshot snapshot(MessageObject message) {
        final Snapshot result = new Snapshot();
        result.dialogId = message.getDialogId();
        result.messageId = message.getId();
        result.date = message.messageOwner.date;
        result.editDate = message.messageOwner.edit_date;
        result.observedAt = System.currentTimeMillis() / 1000L;
        result.senderId = message.getFromChatId();
        result.peerName = DialogObject.getName(account, result.dialogId);
        result.senderName = DialogObject.getName(account, result.senderId);
        result.text = text(message);
        result.media = media(message);
        result.replyTo = message.getReplyMsgId();
        result.outgoing = message.isOutOwner();
        result.edited = message.isEdited();
        return result;
    }

    private static boolean eligible(MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0 || message.getDialogId() == 0) {
            return false;
        }
        if (DialogObject.isEncryptedDialog(message.getDialogId()) || message.isSecret() || message.isSecretMedia() || message.isEphemeral() || message.isExpiredStory() || message.isSponsored() || message.isVoiceOnce() || message.isRoundOnce()) {
            return false;
        }
        if (message.messageOwner.noforwards || MessagesController.getInstance(message.currentAccount).isPeerNoForwards(message.getDialogId()) || message.messageOwner.ttl_period != 0 || message.messageOwner.destroyTime != 0 || message.type == MessageObject.TYPE_PAID_MEDIA) {
            return false;
        }
        if (message.messageOwner.media != null && message.messageOwner.media.ttl_seconds != 0) {
            return false;
        }
        return message.messageOwner.action == null || message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty;
    }

    private static String text(MessageObject message) {
        if (message.messageText != null && !TextUtils.isEmpty(message.messageText)) {
            return message.messageText.toString();
        }
        if (message.caption != null && !TextUtils.isEmpty(message.caption)) {
            return message.caption.toString();
        }
        return message.messageOwner.message == null ? "" : message.messageOwner.message;
    }

    private static String media(MessageObject message) {
        if (message.isPhoto()) {
            return "Photo";
        } else if (message.isVideo()) {
            return "Video";
        } else if (message.isRoundVideo()) {
            return "Video message";
        } else if (message.isVoice()) {
            return "Voice message";
        } else if (message.isMusic()) {
            return "Music";
        } else if (message.isSticker()) {
            return "Sticker";
        } else if (message.isPoll()) {
            return "Poll";
        } else if (message.getDocument() != null) {
            return "File";
        }
        return "";
    }

    private void pruneLocked() {
        final long now = System.currentTimeMillis() / 1000L;
        boolean changed = false;
        final Iterator<Snapshot> iterator = archive.values().iterator();
        while (iterator.hasNext()) {
            final Snapshot snapshot = iterator.next();
            final Integer days = retention.get(snapshot.dialogId);
            if (days != null && days > 0 && snapshot.date < now - days * 86400L) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            dirty = true;
        }
    }

    private void ensureLoadedLocked() {
        if (loaded) {
            return;
        }
        loaded = true;
        final File file = storageFile();
        final File backup = backupFile();
        final File source = file.exists() ? file : backup;
        if (!source.exists()) {
            return;
        }
        try {
            if (source.length() > MAX_FILE_SIZE) {
                throw new IllegalStateException("Zgram local data file is too large");
            }
            final byte[] plaintext = decrypt(read(source));
            readJsonLocked(new JSONObject(new String(plaintext, StandardCharsets.UTF_8)));
            pruneLocked();
            if (dirty) {
                scheduleSaveLocked();
            }
        } catch (Exception e) {
            FileLog.e(e);
            final File unreadable = new File(source.getParentFile(), source.getName() + ".unreadable-" + System.currentTimeMillis());
            source.renameTo(unreadable);
            bookmarks.clear();
            archive.clear();
            retention.clear();
        }
    }

    private void readJsonLocked(JSONObject root) throws Exception {
        if (root.optInt("version", 0) != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported Zgram local data version");
        }
        final JSONArray savedBookmarks = root.optJSONArray("bookmarks");
        if (savedBookmarks != null) {
            for (int i = 0; i < savedBookmarks.length(); i++) {
                final Snapshot snapshot = Snapshot.fromJson(savedBookmarks.getJSONObject(i));
                bookmarks.put(snapshot.key(), snapshot);
            }
        }
        final JSONArray savedArchive = root.optJSONArray("archive");
        if (savedArchive != null) {
            for (int i = 0; i < savedArchive.length(); i++) {
                final Snapshot snapshot = Snapshot.fromJson(savedArchive.getJSONObject(i));
                archive.put(snapshot.key(), snapshot);
            }
        }
        final JSONObject savedRetention = root.optJSONObject("retention");
        if (savedRetention != null) {
            final Iterator<String> keys = savedRetention.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                retention.put(Long.parseLong(key), savedRetention.getInt(key));
            }
        }
    }

    private JSONObject toJsonLocked() throws Exception {
        final JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("owner", ownerId);
        final JSONArray savedBookmarks = new JSONArray();
        for (Snapshot snapshot : bookmarks.values()) {
            savedBookmarks.put(snapshot.toJson());
        }
        root.put("bookmarks", savedBookmarks);
        final JSONArray savedArchive = new JSONArray();
        for (Snapshot snapshot : archive.values()) {
            savedArchive.put(snapshot.toJson());
        }
        root.put("archive", savedArchive);
        final JSONObject savedRetention = new JSONObject();
        for (Long dialogId : retention.keySet()) {
            savedRetention.put(Long.toString(dialogId), retention.get(dialogId));
        }
        root.put("retention", savedRetention);
        return root;
    }

    private void scheduleSaveLocked() {
        dirty = true;
        if (saveScheduled) {
            return;
        }
        saveScheduled = true;
        Utilities.globalQueue.postRunnable(() -> {
            final byte[] payload;
            synchronized (ZgramLocalData.this) {
                try {
                    payload = toJsonLocked().toString().getBytes(StandardCharsets.UTF_8);
                    dirty = false;
                } catch (Exception e) {
                    saveScheduled = false;
                    FileLog.e(e);
                    return;
                }
            }
            boolean failed = false;
            try {
                write(encrypt(payload));
            } catch (Exception e) {
                synchronized (ZgramLocalData.this) {
                    dirty = true;
                }
                failed = true;
                FileLog.e(e);
            }
            synchronized (ZgramLocalData.this) {
                saveScheduled = false;
                if (dirty && !failed) {
                    scheduleSaveLocked();
                }
            }
        }, 300L);
    }

    private byte[] encrypt(byte[] plaintext) throws Exception {
        final byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad());
        final byte[] encrypted = cipher.doFinal(plaintext);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(iv.length);
            output.write(iv);
            output.writeInt(encrypted.length);
            output.write(encrypted);
        }
        return bytes.toByteArray();
    }

    private byte[] decrypt(byte[] data) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                throw new IllegalStateException("Invalid Zgram local data header");
            }
            final int ivLength = input.readInt();
            if (ivLength < 12 || ivLength > 32) {
                throw new IllegalStateException("Invalid Zgram local data IV");
            }
            final byte[] iv = new byte[ivLength];
            input.readFully(iv);
            final int encryptedLength = input.readInt();
            if (encryptedLength < 16 || encryptedLength > data.length) {
                throw new IllegalStateException("Invalid Zgram local data payload");
            }
            final byte[] encrypted = new byte[encryptedLength];
            input.readFully(encrypted);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad());
            return cipher.doFinal(encrypted);
        }
    }

    private SecretKey encryptionKey() throws Exception {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? modernEncryptionKey() : legacyEncryptionKey();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private SecretKey modernEncryptionKey() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        final String alias = keyAlias();
        if (!keyStore.containsAlias(alias)) {
            final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(alias, null);
    }

    @SuppressWarnings("deprecation")
    private SecretKey legacyEncryptionKey() throws Exception {
        final Context context = ApplicationLoader.applicationContext;
        final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        final String alias = keyAlias() + "_wrap";
        if (!keyStore.containsAlias(alias)) {
            final Calendar start = Calendar.getInstance();
            final Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 30);
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(alias)
                    .setSubject(new X500Principal("CN=Zgram Local Data"))
                    .setSerialNumber(BigInteger.valueOf(ownerId == 0 ? account + 1L : Math.abs(ownerId)))
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build());
            generator.generateKeyPair();
        }
        final SharedPreferences preferences = context.getSharedPreferences("zgram_local_keys", Context.MODE_PRIVATE);
        final String preferenceKey = alias + "_aes";
        final String encoded = preferences.getString(preferenceKey, null);
        final Cipher wrapper = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        if (encoded == null) {
            final KeyGenerator generator = KeyGenerator.getInstance("AES");
            try {
                generator.init(256);
            } catch (Exception e) {
                generator.init(128);
            }
            final SecretKey key = generator.generateKey();
            final Certificate certificate = keyStore.getCertificate(alias);
            wrapper.init(Cipher.ENCRYPT_MODE, certificate.getPublicKey());
            preferences.edit().putString(preferenceKey, Base64.encodeToString(wrapper.doFinal(key.getEncoded()), Base64.NO_WRAP)).commit();
            return key;
        }
        wrapper.init(Cipher.DECRYPT_MODE, keyStore.getKey(alias, null));
        return new SecretKeySpec(wrapper.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), "AES");
    }

    private byte[] aad() {
        return String.format(Locale.US, "zgram:%d:%d:%d", account, ownerId, FORMAT_VERSION).getBytes(StandardCharsets.UTF_8);
    }

    private String keyAlias() {
        return "zgram_local_" + account + "_" + Math.abs(ownerId);
    }

    private File storageDirectory() {
        final File directory = new File(ApplicationLoader.applicationContext.getFilesDir(), "zgram");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    private File storageFile() {
        return new File(storageDirectory(), "local_" + account + "_" + Math.abs(ownerId) + ".zgd");
    }

    private File backupFile() {
        return new File(storageFile().getAbsolutePath() + ".bak");
    }

    private void write(byte[] data) throws Exception {
        final File file = storageFile();
        final File temporary = new File(file.getAbsolutePath() + ".tmp");
        final File backup = backupFile();
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(data);
            output.getFD().sync();
        }
        if (backup.exists() && !backup.delete()) {
            throw new IllegalStateException("Could not replace Zgram local data backup");
        }
        if (file.exists() && !file.renameTo(backup)) {
            throw new IllegalStateException("Could not back up Zgram local data");
        }
        if (!temporary.renameTo(file)) {
            if (backup.exists()) {
                backup.renameTo(file);
            }
            throw new IllegalStateException("Could not save Zgram local data");
        }
        if (backup.exists()) {
            backup.delete();
        }
    }

    private static byte[] read(File file) throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length());
        try (FileInputStream input = new FileInputStream(file)) {
            final byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }

    private static String key(long dialogId, int messageId) {
        return dialogId + ":" + messageId;
    }

    private static String html(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static final class Version {
        public long observedAt;
        public String text = "";
        public String media = "";

        private Version copy() {
            final Version result = new Version();
            result.observedAt = observedAt;
            result.text = text;
            result.media = media;
            return result;
        }

        private JSONObject toJson() throws Exception {
            final JSONObject result = new JSONObject();
            result.put("observed_at", observedAt);
            result.put("text", text);
            result.put("media", media);
            return result;
        }

        private static Version fromJson(JSONObject object) {
            final Version result = new Version();
            result.observedAt = object.optLong("observed_at", 0L);
            result.text = object.optString("text", "");
            result.media = object.optString("media", "");
            return result;
        }
    }

    public static final class Snapshot {
        public long dialogId;
        public int messageId;
        public int date;
        public int editDate;
        public long observedAt;
        public long deletedAt;
        public long senderId;
        public int replyTo;
        public boolean outgoing;
        public boolean edited;
        public String peerName = "";
        public String senderName = "";
        public String text = "";
        public String media = "";
        public final ArrayList<Version> versions = new ArrayList<>();

        public String key() {
            return ZgramLocalData.key(dialogId, messageId);
        }

        public boolean matches(String query) {
            if (TextUtils.isEmpty(query)) {
                return true;
            }
            final String normalized = query.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("is:deleted")) {
                return deletedAt != 0;
            } else if (normalized.equals("is:edited") || normalized.equals("has:edits")) {
                return edited || !versions.isEmpty();
            } else if (normalized.equals("has:media")) {
                return !TextUtils.isEmpty(media);
            } else if (normalized.startsWith("from:")) {
                return lower(senderName).contains(normalized.substring(5).trim());
            }
            return lower(peerName).contains(normalized)
                    || lower(senderName).contains(normalized)
                    || lower(text).contains(normalized)
                    || lower(media).contains(normalized);
        }

        private boolean updateFrom(Snapshot source) {
            boolean changed = false;
            if (!TextUtils.equals(text, source.text) || !TextUtils.equals(media, source.media)) {
                final Version version = new Version();
                version.observedAt = observedAt;
                version.text = text;
                version.media = media;
                if (!TextUtils.isEmpty(version.text) || !TextUtils.isEmpty(version.media)) {
                    versions.add(version);
                    while (versions.size() > MAX_VERSIONS) {
                        versions.remove(0);
                    }
                }
                text = source.text;
                media = source.media;
                edited = true;
                changed = true;
            }
            if (editDate != source.editDate || replyTo != source.replyTo || outgoing != source.outgoing
                    || senderId != source.senderId || !TextUtils.equals(peerName, source.peerName)
                    || !TextUtils.equals(senderName, source.senderName)) {
                editDate = source.editDate;
                replyTo = source.replyTo;
                outgoing = source.outgoing;
                senderId = source.senderId;
                if (!TextUtils.isEmpty(source.peerName)) {
                    peerName = source.peerName;
                }
                if (!TextUtils.isEmpty(source.senderName)) {
                    senderName = source.senderName;
                }
                changed = true;
            }
            if (changed) {
                observedAt = source.observedAt;
                edited |= source.edited;
            }
            return changed;
        }

        private Snapshot copy() {
            final Snapshot result = new Snapshot();
            result.dialogId = dialogId;
            result.messageId = messageId;
            result.date = date;
            result.editDate = editDate;
            result.observedAt = observedAt;
            result.deletedAt = deletedAt;
            result.senderId = senderId;
            result.replyTo = replyTo;
            result.outgoing = outgoing;
            result.edited = edited;
            result.peerName = peerName;
            result.senderName = senderName;
            result.text = text;
            result.media = media;
            for (Version version : versions) {
                result.versions.add(version.copy());
            }
            return result;
        }

        private long estimateSize() {
            long result = 96L + text.length() * 2L + media.length() * 2L + peerName.length() * 2L + senderName.length() * 2L;
            for (Version version : versions) {
                result += 24L + version.text.length() * 2L + version.media.length() * 2L;
            }
            return result;
        }

        private JSONObject toJson() throws Exception {
            final JSONObject result = new JSONObject();
            result.put("dialog_id", dialogId);
            result.put("message_id", messageId);
            result.put("date", date);
            result.put("edit_date", editDate);
            result.put("observed_at", observedAt);
            result.put("deleted_at", deletedAt);
            result.put("sender_id", senderId);
            result.put("reply_to", replyTo);
            result.put("outgoing", outgoing);
            result.put("edited", edited);
            result.put("peer_name", peerName);
            result.put("sender_name", senderName);
            result.put("text", text);
            result.put("media", media);
            final JSONArray savedVersions = new JSONArray();
            for (Version version : versions) {
                savedVersions.put(version.toJson());
            }
            result.put("versions", savedVersions);
            return result;
        }

        private static Snapshot fromJson(JSONObject object) throws Exception {
            final Snapshot result = new Snapshot();
            result.dialogId = object.getLong("dialog_id");
            result.messageId = object.getInt("message_id");
            result.date = object.optInt("date", 0);
            result.editDate = object.optInt("edit_date", 0);
            result.observedAt = object.optLong("observed_at", 0L);
            result.deletedAt = object.optLong("deleted_at", 0L);
            result.senderId = object.optLong("sender_id", 0L);
            result.replyTo = object.optInt("reply_to", 0);
            result.outgoing = object.optBoolean("outgoing", false);
            result.edited = object.optBoolean("edited", false);
            result.peerName = object.optString("peer_name", "");
            result.senderName = object.optString("sender_name", "");
            result.text = object.optString("text", "");
            result.media = object.optString("media", "");
            final JSONArray savedVersions = object.optJSONArray("versions");
            if (savedVersions != null) {
                for (int i = 0; i < savedVersions.length(); i++) {
                    result.versions.add(Version.fromJson(savedVersions.getJSONObject(i)));
                }
            }
            return result;
        }

        private static String lower(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }
    }

    public static final class ArchiveSummary {
        public final long dialogId;
        public String peerName;
        public int retentionDays;
        public int count;
        public int deletedCount;
        public int lastDate;
        public long approximateBytes;

        private ArchiveSummary(long dialogId, String peerName, int retentionDays) {
            this.dialogId = dialogId;
            this.peerName = peerName;
            this.retentionDays = retentionDays;
        }

        private boolean matches(String query) {
            return TextUtils.isEmpty(query) || (peerName != null && peerName.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT)));
        }

        private ArchiveSummary copy() {
            final ArchiveSummary result = new ArchiveSummary(dialogId, peerName, retentionDays);
            result.count = count;
            result.deletedCount = deletedCount;
            result.lastDate = lastDate;
            result.approximateBytes = approximateBytes;
            return result;
        }
    }
}
