package org.telegram.messenger.zgram;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ZgramLocalDataTest {
    private long ownerId;
    private File file;
    private ZgramLocalData store;

    @Before
    public void setUp() throws Exception {
        // Isolate files and Keystore aliases from every real account and every other test.
        ownerId = System.currentTimeMillis() * 1000 + Math.floorMod(System.nanoTime(), 1000);
        file = new File(ApplicationLoader.applicationContext.getFilesDir(), "zgram/local_0_" + ownerId + ".zgd");
        store = reload();
    }

    @After
    public void tearDown() throws Exception {
        awaitWrites();
        File[] files = file.getParentFile().listFiles((dir, name) -> name.startsWith(file.getName()));
        if (files != null) {
            for (File ownedFile : files) {
                assertTrue("Could not remove test file " + ownedFile, ownedFile.delete());
            }
        }
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        keys.deleteEntry("zgram_local_0_" + ownerId);
    }

    @Test
    public void encryptedCopiesAndEditsSurvivePurgeAndRestart() throws Exception {
        store.setRetention(880001, ZgramLocalData.RETENTION_FOREVER);
        MessageObject original = message(880001, 21, "Private saved text", false);
        store.capture(original);
        assertTrue(store.toggleBookmark(original));
        awaitWrites();
        assertTrue("The Android Keystore write must reach disk", file.isFile());
        byte[] firstWrite = Files.readAllBytes(file.toPath());
        assertFalse(new String(firstWrite, StandardCharsets.UTF_8).contains("Private saved text"));

        store.capture(message(880001, 21, "Edited saved text", true));
        store.markHistoryDeleted(880001, Integer.MAX_VALUE);
        awaitWrites();
        byte[] secondWrite = Files.readAllBytes(file.toPath());
        assertFalse("Each GCM write needs a fresh IV", Arrays.equals(
                Arrays.copyOfRange(firstWrite, 12, 24), Arrays.copyOfRange(secondWrite, 12, 24)));

        store = reload();
        ZgramLocalData.Snapshot saved = store.archive(880001, "is:deleted").get(0);
        assertEquals("Edited saved text", saved.text);
        assertEquals("Private saved text", saved.versions.get(0).text);
        assertTrue(saved.deletedAt > 0);
        assertEquals(1, store.bookmarkCount());
        assertEquals(saved.text, store.bookmarks(null).get(0).text);
        assertEquals(ZgramLocalData.RETENTION_FOREVER, store.getRetention(880001));
        assertTrue(store.exportJson(880001).contains("Edited saved text"));
        assertTrue(store.exportHtml(880001).contains("Private saved text"));

        store.clearArchive(880001);
        awaitWrites();
        store = reload();
        assertEquals(0, store.archiveCount());
        assertEquals("Clearing the archive must not clear bookmarks", 1, store.bookmarkCount());
    }

    @Test
    public void deletionIdsRespectChannelNamespaceAndHistoryBoundary() throws Exception {
        for (long dialog : new long[] {880001, -880002, -880003}) {
            store.setRetention(dialog, ZgramLocalData.RETENTION_FOREVER);
            store.capture(message(dialog, 21, "Keep this copy", false));
            store.capture(message(dialog, 22, "Newer message", false));
        }
        store.markServerDeleted(880002, Collections.singletonList(21));
        assertEquals(1, store.archive(-880002, "is:deleted").size());
        assertEquals(0, store.archive(880001, "is:deleted").size());
        assertEquals(0, store.archive(-880003, "is:deleted").size());
        store.markServerDeleted(0, Collections.singletonList(21));
        assertEquals(1, store.archive(880001, "is:deleted").size());
        assertEquals(0, store.archive(-880003, "is:deleted").size());
        store.markHistoryDeleted(-880003, 21);
        assertEquals(1, store.archive(-880003, "is:deleted").size());
        assertEquals(6, store.archiveCount());
        awaitWrites();
        store = reload();
        assertEquals(6, store.archiveCount());
        assertEquals("Keep this copy", store.archive(-880003, "is:deleted").get(0).text);
    }

    @Test
    public void intactBackupRecoversFromInterruptedPrimaryWrite() throws Exception {
        store.setRetention(880001, ZgramLocalData.RETENTION_FOREVER);
        store.capture(message(880001, 21, "Recover from backup", false));
        awaitWrites();
        Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath());
        Files.write(file.toPath(), new byte[] {0, 1, 2});
        store = reload();
        assertEquals("Recover from backup", store.archive(880001, null).get(0).text);
        awaitWrites();
        assertEquals(1, reload().archiveCount());
    }

    @Test
    public void protectedMessagesStayExcludedAndRetentionStillApplies() throws Exception {
        store.setRetention(880001, ZgramLocalData.RETENTION_WEEK);
        MessageObject protectedMessage = message(880001, 21, "Protected", false);
        protectedMessage.messageOwner.noforwards = true;
        store.capture(protectedMessage);
        assertFalse(store.toggleBookmark(protectedMessage));
        MessageObject expired = message(880001, 22, "Older than retention", false);
        expired.messageOwner.date -= 8 * 86400;
        store.capture(expired);
        store.capture(message(880001, 23, "Within retention", false));
        store.markHistoryDeleted(880001, Integer.MAX_VALUE);
        awaitWrites();
        store = reload();
        assertEquals(1, store.archiveCount());
        assertEquals("Within retention", store.archive(880001, null).get(0).text);
        assertEquals(0, store.bookmarkCount());
    }

    private ZgramLocalData reload() throws Exception {
        Constructor<ZgramLocalData> constructor = ZgramLocalData.class.getDeclaredConstructor(int.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, ownerId);
    }

    private static MessageObject message(long dialog, int id, String text, boolean edited) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = id;
        message.dialog_id = dialog;
        message.date = (int) (System.currentTimeMillis() / 1000);
        message.message = text;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = 880001;
        message.peer_id = dialog > 0 ? new TLRPC.TL_peerUser() : new TLRPC.TL_peerChannel();
        if (dialog > 0) {
            message.peer_id.user_id = dialog;
        } else {
            message.peer_id.channel_id = -dialog;
        }
        if (edited) {
            message.edit_date = message.date + 1;
            message.flags |= 32768;
        }
        return new MessageObject(0, message, false, false);
    }

    private static void awaitWrites() throws Exception {
        CountDownLatch saved = new CountDownLatch(1);
        Utilities.globalQueue.postRunnable(saved::countDown, 1000);
        assertTrue("Timed out waiting for local storage", saved.await(15, TimeUnit.SECONDS));
    }
}
