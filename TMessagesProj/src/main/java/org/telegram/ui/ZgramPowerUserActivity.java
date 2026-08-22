package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.zgram.ZgramLocalData;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class ZgramPowerUserActivity extends UniversalFragment {

    private static final int ID_PALETTE = 1;
    private static final int ID_BOOKMARKS = 2;
    private static final int ID_ARCHIVE = 3;
    private static final int ID_CHAT_TOOLS = 4;
    private static final int ID_APPEARANCE = 5;
    private static final int ID_FOLDERS = 6;
    private static final int ID_DATA = 7;
    private static final int ID_UPDATES = 8;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.ZgramPowerUserTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final ZgramLocalData data = ZgramLocalData.get(currentAccount);
        final String stats = LocaleController.formatString(
                R.string.ZgramPowerUserStats,
                data.bookmarkCount(),
                data.archiveCount(),
                AndroidUtilities.formatFileSize(data.storageSize()));
        items.add(UItem.asTopViewStatic(getString(R.string.ZgramPowerUserAbout), R.drawable.zgram_wings));
        items.add(UItem.asShadow(stats));
        items.add(UItem.asHeader(getString(R.string.ZgramPowerUserTools)));
        items.add(UItem.asSettingsCell(ID_PALETTE, R.drawable.msg_search, getString(R.string.ZgramCommandPaletteTitle), getString(R.string.ZgramCommandPaletteAbout)));
        items.add(UItem.asSettingsCell(ID_BOOKMARKS, R.drawable.msg_fave, getString(R.string.ZgramLocalBookmarksTitle), getString(R.string.ZgramLocalBookmarksShortAbout)));
        items.add(UItem.asSettingsCell(ID_ARCHIVE, R.drawable.msg_archive, getString(R.string.ZgramLocalArchiveTitle), getString(R.string.ZgramLocalArchiveShortAbout)));
        items.add(UItem.asSettingsCell(ID_CHAT_TOOLS, R.drawable.msg_settings, getString(R.string.ZgramChatTools), getString(R.string.ZgramChatToolsAbout)));
        items.add(UItem.asShadow(getString(R.string.ZgramLocalArchiveEncryptedAbout)));
        items.add(UItem.asHeader(getString(R.string.ZgramPowerUserShortcuts)));
        items.add(UItem.asSettingsCell(ID_APPEARANCE, R.drawable.msg_theme, getString(R.string.Theme), getString(R.string.ZgramAppearanceAbout)));
        items.add(UItem.asSettingsCell(ID_FOLDERS, R.drawable.settings_folders, getString(R.string.Filters), getString(R.string.ZgramFoldersAbout)));
        items.add(UItem.asSettingsCell(ID_DATA, R.drawable.settings_data, getString(R.string.DataSettings), getString(R.string.ZgramDataAbout)));
        items.add(UItem.asSettingsCell(ID_UPDATES, R.drawable.msg_channel, getString(R.string.ZgramOfficialUpdates), "@zgram_io"));
        items.add(UItem.asShadow(getString(R.string.ZgramPowerUserFooter)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_PALETTE) {
            presentFragment(new ZgramCommandPaletteActivity());
        } else if (item.id == ID_BOOKMARKS) {
            presentFragment(new ZgramBookmarksActivity());
        } else if (item.id == ID_ARCHIVE) {
            presentFragment(new ZgramArchiveActivity());
        } else if (item.id == ID_CHAT_TOOLS) {
            new AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.ZgramChatTools))
                    .setMessage(getString(R.string.ZgramChatToolsHowTo))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
        } else if (item.id == ID_APPEARANCE) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        } else if (item.id == ID_FOLDERS) {
            presentFragment(new FiltersSetupActivity());
        } else if (item.id == ID_DATA) {
            presentFragment(new DataSettingsActivity());
        } else if (item.id == ID_UPDATES) {
            Browser.openUrl(getContext(), "https://t.me/zgram_io");
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(true);
        }
    }
}
