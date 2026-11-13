package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.zgram.ZgramLocalData;
import org.telegram.messenger.zgram.ZgramSettings;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

public class ZgramPowerUserActivity extends ZgramSettingsActivity {

    private static final int ID_PALETTE = 1;
    private static final int ID_BOOKMARKS = 2;
    private static final int ID_ARCHIVE = 3;
    private static final int ID_CHAT_TOOLS = 4;
    private static final int ID_APPEARANCE = 5;
    private static final int ID_FOLDERS = 6;
    private static final int ID_DATA = 7;
    private static final int ID_UPDATES = 8;
    private static final int ID_PROFILE_INFO = 9;

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
        items.add(UItem.asHeader(getString(R.string.ZgramPowerUserTools)));
        items.add(setting(ID_PALETTE, IconBackgroundColors.BLUE, R.drawable.msg_search, getString(R.string.ZgramCommandPaletteTitle), null));
        items.add(setting(ID_BOOKMARKS, IconBackgroundColors.ORANGE, R.drawable.msg_fave, getString(R.string.ZgramLocalBookmarksTitle), Integer.toString(data.bookmarkCount())));
        items.add(setting(ID_ARCHIVE, IconBackgroundColors.GREEN, R.drawable.msg_archive, getString(R.string.ZgramLocalArchiveTitle), Integer.toString(data.archiveCount())));
        items.add(setting(ID_CHAT_TOOLS, IconBackgroundColors.PURPLE, R.drawable.msg_settings, getString(R.string.ZgramChatTools), null));
        items.add(UItem.asShadow(stats + "\n\n" + getString(R.string.ZgramLocalArchiveEncryptedAbout)));
        items.add(UItem.asCheck(ID_PROFILE_INFO, getString(R.string.ZgramShowProfileInfo)).setChecked(ZgramSettings.showProfileInfo()));
        items.add(UItem.asShadow(getString(R.string.ZgramProfileInfoAbout)));
        items.add(UItem.asHeader(getString(R.string.ZgramPowerUserShortcuts)));
        items.add(setting(ID_APPEARANCE, IconBackgroundColors.PURPLE, R.drawable.msg_theme, getString(R.string.Theme), null));
        items.add(setting(ID_FOLDERS, IconBackgroundColors.BLUE_ALT, R.drawable.settings_folders, getString(R.string.Filters), null));
        items.add(setting(ID_DATA, IconBackgroundColors.BLUE_DEEP, R.drawable.settings_data, getString(R.string.DataSettings), null));
        items.add(setting(ID_UPDATES, IconBackgroundColors.CYAN, R.drawable.msg_channel, getString(R.string.ZgramOfficialUpdates), "@zgram_io"));
        items.add(UItem.asShadow(getString(R.string.ZgramPowerUserFooter)));
    }

    private static UItem setting(int id, IconBackgroundColors color, int icon, CharSequence title, CharSequence value) {
        return SettingsActivity.SettingCell.Factory.of(id, color.top, color.bottom, icon, title, null, value);
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
        } else if (item.id == ID_PROFILE_INFO) {
            ZgramSettings.setShowProfileInfo(!ZgramSettings.showProfileInfo());
            listView.adapter.update(true);
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
