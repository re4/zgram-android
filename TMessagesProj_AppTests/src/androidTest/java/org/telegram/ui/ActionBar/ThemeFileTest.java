package org.telegram.ui.ActionBar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class ThemeFileTest {
    @Test
    public void decimalAndHexColorsMatchForLfAndCrlf() throws Exception {
        for (String newline : new String[]{"\n", "\r\n"}) {
            String header = "glass_defaultIcon=-1" + newline
                    + "glass_defaultText=-592138" + newline
                    + "glass_tabUnselected=#ffb8afa3" + newline
                    + "dialogTextBlack=#fff3ecdf" + newline
                    + "chat_emojiPanelBackground=-15987182" + newline;
            SparseIntArray colors = parse(header.getBytes(StandardCharsets.UTF_8), null);
            assertEquals(Color.WHITE, colors.get(Theme.key_glass_defaultIcon));
            assertEquals(-592138, colors.get(Theme.key_glass_defaultText));
            assertEquals(0xffb8afa3, colors.get(Theme.key_glass_tabUnselected));
            assertEquals(0xfff3ecdf, colors.get(Theme.key_dialogTextBlack));
            assertEquals(0xff0c0e12, colors.get(Theme.key_chat_emojiPanelBackground));
        }
    }

    @Test
    public void crlfPreservesWallpaperLinkAndBinaryOffset() throws Exception {
        String prefix = "WLS=https://example.com/wallpaper\r\n"
                + "glass_defaultIcon=-1\r\nWPS\r\n";
        byte[] header = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] wallpaper = new byte[]{(byte) 0xff, (byte) 0xd8, 0, 13, 10, (byte) 0xff, (byte) 0xd9};
        byte[] file = new byte[header.length + wallpaper.length];
        System.arraycopy(header, 0, file, 0, header.length);
        System.arraycopy(wallpaper, 0, file, header.length, wallpaper.length);
        String[] link = new String[1];
        SparseIntArray colors = parse(file, link);
        assertEquals("https://example.com/wallpaper", link[0]);
        assertEquals(header.length, colors.get(Theme.key_wallpaperFileOffset));
        assertEquals(Color.WHITE, colors.get(Theme.key_glass_defaultIcon));
    }

    @Test
    public void zgramDrawerTextAndControlsRemainReadable() {
        SparseIntArray colors = Theme.getThemeFileValues(null, "zgram.attheme", null);
        int emojiBackground = color(colors, Theme.key_chat_emojiPanelBackground);
        int attachmentBackground = color(colors, Theme.key_dialogBackground);
        int glassIcon = color(colors, Theme.key_glass_defaultIcon);

        // EmojiView uses 60% icon opacity for its search hint.
        int hint = ColorUtils.setAlphaComponent(glassIcon, (int) (255 * .6f));
        assertContrast(hint, emojiBackground);
        assertContrast(color(colors, Theme.key_chat_emojiPanelTrendingTitle), emojiBackground);
        assertContrast(color(colors, Theme.key_chat_emojiPanelStickerSetName), emojiBackground);
        assertContrast(color(colors, Theme.key_glass_tabUnselected), attachmentBackground);
        assertContrast(color(colors, Theme.key_glass_tabSelectedText), attachmentBackground);
        assertContrast(color(colors, Theme.key_dialogTextBlack), attachmentBackground);
        assertContrast(color(colors, Theme.key_windowBackgroundWhiteBlackText), attachmentBackground);
        assertTrue("The bundled wallpaper is still present", colors.get(Theme.key_wallpaperFileOffset) > 0);
    }

    private static int color(SparseIntArray colors, int key) {
        assertTrue("Missing theme color " + ThemeColors.getStringName(key), colors.indexOfKey(key) >= 0);
        return colors.get(key);
    }

    private static void assertContrast(int foreground, int background) {
        double contrast = ColorUtils.calculateContrast(foreground, background);
        assertTrue("Drawer text contrast was " + contrast, contrast >= 4.5);
    }

    private static SparseIntArray parse(byte[] data, String[] wallpaperLink) throws Exception {
        File file = File.createTempFile("zgram-theme-", ".attheme", ApplicationLoader.applicationContext.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(data);
            }
            return Theme.getThemeFileValues(file, null, wallpaperLink);
        } finally {
            file.delete();
        }
    }
}
