package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Locale;

public class ZgramCommandPaletteActivity extends ZgramSearchableActivity {

    private static final int SAVED_MESSAGES = 1;
    private static final int CONTACTS = 2;
    private static final int SETTINGS = 3;
    private static final int THEME = 4;
    private static final int FOLDERS = 5;
    private static final int BOOKMARKS = 6;
    private static final int ARCHIVE = 7;
    private static final int POWER_USER = 8;
    private static final int CHAT_SEARCH = 9;
    private static final int CHAT_APPEARANCE = 10;
    private static final int CHAT_PHOTOS = 11;
    private static final int CHAT_FILES = 12;
    private static final int CHAT_LINKS = 13;
    private static final int CHAT_VOICE = 14;
    private static final int CHAT_ARCHIVE = 15;
    private static final int CHAT_MUTE = 16;

    private final long dialogId;
    private final ChatActivity source;

    public ZgramCommandPaletteActivity() {
        this(0L, null);
    }

    public ZgramCommandPaletteActivity(long dialogId, ChatActivity source) {
        this.dialogId = dialogId;
        this.source = source;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.ZgramCommandPaletteTitle);
    }

    @Override
    protected CharSequence searchHint() {
        return getString(R.string.ZgramCommandPaletteSearch);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopViewStatic(getString(R.string.ZgramCommandPaletteAbout), R.drawable.zgram_wings));
        addGroup(items, getString(R.string.ZgramCommandPaletteNavigation), new Command[] {
                command(SAVED_MESSAGES, R.drawable.msg_saved, R.string.SavedMessages, R.string.ZgramCommandSavedMessages),
                command(CONTACTS, R.drawable.msg_contacts, R.string.Contacts, R.string.ZgramCommandContacts),
                command(SETTINGS, R.drawable.msg_settings, R.string.Settings, R.string.ZgramCommandSettings)
        });
        addGroup(items, getString(R.string.ZgramCommandPaletteInterface), new Command[] {
                command(THEME, R.drawable.msg_theme, R.string.Theme, R.string.ZgramAppearanceAbout),
                command(FOLDERS, R.drawable.settings_folders, R.string.Filters, R.string.ZgramFoldersAbout)
        });
        addGroup(items, getString(R.string.ZgramCommandPaletteLocalData), new Command[] {
                command(BOOKMARKS, R.drawable.msg_fave, R.string.ZgramLocalBookmarksTitle, R.string.ZgramLocalBookmarksShortAbout),
                command(ARCHIVE, R.drawable.msg_archive, R.string.ZgramLocalArchiveTitle, R.string.ZgramLocalArchiveShortAbout),
                command(POWER_USER, R.drawable.settings_power, R.string.ZgramPowerUserTitle, R.string.ZgramPowerUserAbout)
        });
        if (dialogId != 0 && source != null) {
            addGroup(items, getString(R.string.ZgramCommandPaletteCurrentChat), new Command[] {
                    command(CHAT_SEARCH, R.drawable.msg_search, R.string.Search, R.string.ZgramCommandChatSearch),
                    command(CHAT_APPEARANCE, R.drawable.msg_theme, R.string.ZgramChatAppearance, R.string.ZgramCommandChatAppearance),
                    command(CHAT_PHOTOS, R.drawable.msg_media, R.string.SharedMediaTab2, R.string.ZgramCommandChatPhotos),
                    command(CHAT_FILES, R.drawable.search_files_filled, R.string.SharedFilesTab2, R.string.ZgramCommandChatFiles),
                    command(CHAT_LINKS, R.drawable.msg_link, R.string.SharedLinksTab2, R.string.ZgramCommandChatLinks),
                    command(CHAT_VOICE, R.drawable.search_voice_filled, R.string.SharedVoiceTab2, R.string.ZgramCommandChatVoice),
                    command(CHAT_ARCHIVE, R.drawable.msg_archive, R.string.ZgramLocalArchiveTitle, R.string.ZgramCommandChatArchive),
                    command(CHAT_MUTE, R.drawable.msg_mute, R.string.MuteNotifications, R.string.ZgramCommandChatMute)
            });
        }
        if (items.size() == 1) {
            items.add(UItem.asShadow(getString(R.string.ZgramCommandPaletteEmpty)));
        } else {
            items.add(UItem.asShadow(getString(R.string.ZgramCommandPaletteHint)));
        }
    }

    private void addGroup(ArrayList<UItem> items, CharSequence title, Command[] commands) {
        final ArrayList<Command> visible = new ArrayList<>();
        for (Command command : commands) {
            if (command.matches(query())) {
                visible.add(command);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        items.add(UItem.asHeader(title));
        for (Command command : visible) {
            final UItem item = UItem.asSettingsCell(command.id, command.icon, command.title, command.about);
            item.object = command;
            items.add(item);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case SAVED_MESSAGES:
                presentFragment(ChatActivity.of(UserConfig.getInstance(currentAccount).getClientUserId()));
                break;
            case CONTACTS:
                final Bundle args = new Bundle();
                args.putBoolean("needPhonebook", true);
                presentFragment(new ContactsActivity(args));
                break;
            case SETTINGS:
                presentFragment(new SettingsActivity());
                break;
            case THEME:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                break;
            case FOLDERS:
                presentFragment(new FiltersSetupActivity());
                break;
            case BOOKMARKS:
                presentFragment(new ZgramBookmarksActivity());
                break;
            case ARCHIVE:
                presentFragment(new ZgramArchiveActivity());
                break;
            case POWER_USER:
                presentFragment(new ZgramPowerUserActivity());
                break;
            case CHAT_ARCHIVE:
                presentFragment(new ZgramArchiveActivity(dialogId));
                break;
            case CHAT_SEARCH:
                runInChat(() -> source.openSearchWithText(null));
                break;
            case CHAT_APPEARANCE:
                runInChat(source::openZgramChatAppearance);
                break;
            case CHAT_PHOTOS:
                runInChat(() -> source.openZgramMedia(SharedMediaLayout.TAB_PHOTOVIDEO));
                break;
            case CHAT_FILES:
                runInChat(() -> source.openZgramMedia(SharedMediaLayout.TAB_FILES));
                break;
            case CHAT_LINKS:
                runInChat(() -> source.openZgramMedia(SharedMediaLayout.TAB_LINKS));
                break;
            case CHAT_VOICE:
                runInChat(() -> source.openZgramMedia(SharedMediaLayout.TAB_VOICE));
                break;
            case CHAT_MUTE:
                runInChat(source::openZgramMute);
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void runInChat(Runnable action) {
        if (source == null) {
            return;
        }
        finishFragment();
        AndroidUtilities.runOnUIThread(action, 120L);
    }

    private static Command command(int id, int icon, int title, int about) {
        return new Command(
                id,
                icon,
                LocaleController.getString(title),
                LocaleController.getString(about));
    }

    private static final class Command {
        private final int id;
        private final int icon;
        private final String title;
        private final String about;

        private Command(int id, int icon, String title, String about) {
            this.id = id;
            this.icon = icon;
            this.title = title;
            this.about = about;
        }

        private boolean matches(String query) {
            if (TextUtils.isEmpty(query)) {
                return true;
            }
            final String normalized = query.trim().toLowerCase(Locale.ROOT);
            return title.toLowerCase(Locale.ROOT).contains(normalized)
                    || about.toLowerCase(Locale.ROOT).contains(normalized);
        }
    }
}
