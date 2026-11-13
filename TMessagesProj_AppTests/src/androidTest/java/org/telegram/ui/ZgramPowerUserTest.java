package org.telegram.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.zgram.ZgramLocalData;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ZgramDetailCell;
import org.telegram.ui.Components.UItem;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class ZgramPowerUserTest {
    @Test
    public void powerUserUsesReadableNativeSettingsRows() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ZgramPowerUserActivity screen = new ZgramPowerUserActivity();
            assertTrue(screen.useSimpleActionBar());
            assertTrue(screen.isSupportEdgeToEdge());
            ArrayList<UItem> items = new ArrayList<>();
            screen.fillItems(items, null);
            SparseIntArray zgram = Theme.getThemeFileValues(null, "zgram.attheme", null);
            for (Theme.ResourcesProvider palette : new Theme.ResourcesProvider[] {
                    key -> zgram.get(key, Theme.getDefaultColor(key)),
                    key -> Theme.getDefaultColor(key)
            }) {
                int rows = 0;
                for (UItem item : items) {
                    if (item.id < 1 || item.id > 8) {
                        continue;
                    }
                    UItem.UItemFactory<?> factory = UItem.findFactory(item.viewType);
                    assertNotNull(factory);
                    assertTrue(factory.isClickable());
                    View row = factory.createView(context(), null, 0, 0, palette);
                    assertTrue(row instanceof SettingsActivity.SettingCell);
                    factory.bindView(row, item, true, null, null);
                    measure(row);
                    assertTrue(row.getMeasuredHeight() >= AndroidUtilities.dp(48));
                    TextView title = findText(row, item.text.toString());
                    assertNotNull("Missing row title " + item.text, title);
                    assertTrue(title.getMeasuredWidth() > 0);
                    assertTrue("Unreadable row title " + item.text,
                            ColorUtils.calculateContrast(title.getCurrentTextColor(),
                                    palette.getColor(Theme.key_windowBackgroundWhite)) >= 4.5);
                    rows++;
                }
                assertEquals(8, rows);
            }
            screen.onFragmentDestroy();
        });
    }

    @Test
    public void savedRowsRefreshColorsWhenRebound() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final boolean[] dark = {false};
            Theme.ResourcesProvider palette = key -> {
                if (key == Theme.key_windowBackgroundWhiteBlackText) return dark[0] ? 0xffeeeeee : 0xff111111;
                if (key == Theme.key_windowBackgroundWhiteGrayText2) return dark[0] ? 0xffcccccc : 0xff444444;
                return Theme.getDefaultColor(key);
            };
            UItem item = ZgramDetailCell.Factory.of(6, 0, "Saved message", "Sender · Date · Deleted");
            UItem.UItemFactory<?> factory = UItem.findFactory(item.viewType);
            View row = factory.createView(context(), null, 0, 0, palette);
            for (boolean isDark : new boolean[] {false, true}) {
                dark[0] = isDark;
                factory.bindView(row, item, true, null, null);
                measure(row);
                assertEquals(palette.getColor(Theme.key_windowBackgroundWhiteBlackText),
                        findText(row, "Saved message").getCurrentTextColor());
                assertEquals(palette.getColor(Theme.key_windowBackgroundWhiteGrayText2),
                        findText(row, "Sender · Date · Deleted").getCurrentTextColor());
                assertTrue(factory.isClickable());
                assertTrue(row.getMeasuredHeight() >= AndroidUtilities.dp(64));
            }
        });
    }

    @Test
    public void archiveAndBookmarkTapsOpenLocalTextAndVersions() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ZgramLocalData.Snapshot saved = new ZgramLocalData.Snapshot();
            saved.dialogId = 880001;
            saved.messageId = 21;
            saved.date = 1700000000;
            saved.deletedAt = 1700000001;
            saved.text = "The complete locally saved message\nwith another line";
            saved.peerName = "Purged chat";
            ZgramLocalData.Version version = new ZgramLocalData.Version();
            version.text = "Earlier saved text";
            version.observedAt = 1699999999;
            saved.versions.add(version);
            final BaseFragment[] destination = {null};
            ZgramArchiveActivity archive = new ZgramArchiveActivity(saved.dialogId) {
                @Override public boolean presentFragment(BaseFragment fragment) {
                    destination[0] = fragment;
                    return true;
                }
            };
            UItem item = ZgramDetailCell.Factory.of(6, 0, saved.text, "");
            item.object = saved;
            archive.onClick(item, null, 0, 0, 0);
            assertTrue(destination[0] instanceof ZgramSnapshotActivity);
            destination[0].onFragmentDestroy();
            ZgramBookmarksActivity bookmarks = new ZgramBookmarksActivity() {
                @Override public boolean presentFragment(BaseFragment fragment) {
                    destination[0] = fragment;
                    return true;
                }
            };
            item.id = 1;
            bookmarks.onClick(item, null, 0, 0, 0);
            assertTrue(destination[0] instanceof ZgramSnapshotActivity);
            destination[0].onFragmentDestroy();

            ZgramSnapshotActivity reader = new ZgramSnapshotActivity(saved) {
                @Override public Context getContext() { return context(); }
            };
            ArrayList<UItem> contents = new ArrayList<>();
            reader.fillItems(contents, null);
            ArrayList<String> texts = new ArrayList<>();
            for (UItem content : contents) {
                if (content.view instanceof TextView) {
                    TextView text = (TextView) content.view;
                    assertTrue(text.isTextSelectable());
                    texts.add(text.getText().toString());
                }
            }
            assertTrue(texts.contains(saved.text));
            assertTrue(texts.contains(version.text));
            archive.onFragmentDestroy();
            bookmarks.onFragmentDestroy();
            reader.onFragmentDestroy();
        });
    }

    private static Context context() {
        return ApplicationLoader.applicationContext;
    }

    private static void measure(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(360), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
