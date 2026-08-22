package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.zgram.ZgramLocalData;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

public class ZgramBookmarksActivity extends ZgramSearchableActivity {

    private static final int ID_MESSAGE = 1;
    private static final int ID_CLEAR = 2;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.ZgramLocalBookmarksTitle);
    }

    @Override
    protected CharSequence searchHint() {
        return getString(R.string.ZgramLocalBookmarksSearch);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final ZgramLocalData data = ZgramLocalData.get(currentAccount);
        final List<ZgramLocalData.Snapshot> snapshots = data.bookmarks(query());
        items.add(UItem.asTopViewStatic(getString(R.string.ZgramLocalBookmarksAbout), R.drawable.zgram_wings));
        items.add(UItem.asHeader(LocaleController.formatString(R.string.ZgramLocalBookmarksCount, snapshots.size())));
        if (snapshots.isEmpty()) {
            items.add(UItem.asShadow(getString(TextUtils.isEmpty(query())
                    ? R.string.ZgramLocalBookmarksEmpty
                    : R.string.ZgramCommandPaletteEmpty)));
        } else {
            for (ZgramLocalData.Snapshot snapshot : snapshots) {
                final UItem item = UItem.asSettingsCell(
                        ID_MESSAGE,
                        snapshot.deletedAt != 0 ? R.drawable.msg_delete : R.drawable.msg_fave,
                        title(snapshot),
                        description(snapshot));
                item.object = snapshot;
                items.add(item);
            }
            items.add(UItem.asShadow(getString(R.string.ZgramLocalBookmarksStoredLocally)));
            items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_clear, getString(R.string.ZgramLocalBookmarksClear)).red());
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MESSAGE && item.object instanceof ZgramLocalData.Snapshot) {
            final ZgramLocalData.Snapshot snapshot = (ZgramLocalData.Snapshot) item.object;
            presentFragment(ChatActivity.of(snapshot.dialogId, snapshot.messageId));
        } else if (item.id == ID_CLEAR) {
            confirmClear();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id != ID_MESSAGE || !(item.object instanceof ZgramLocalData.Snapshot)) {
            return false;
        }
        final ZgramLocalData.Snapshot snapshot = (ZgramLocalData.Snapshot) item.object;
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.ZgramLocalBookmarksRemove))
                .setMessage(getString(R.string.ZgramLocalBookmarksRemoveConfirm))
                .setPositiveButton(getString(R.string.Remove), (dialog, which) -> {
                    ZgramLocalData.get(currentAccount).removeBookmark(snapshot.dialogId, snapshot.messageId);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.ZgramLocalBookmarksClear))
                .setMessage(getString(R.string.ZgramLocalBookmarksClearConfirm))
                .setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
                    ZgramLocalData.get(currentAccount).clearBookmarks();
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
    }

    private static String title(ZgramLocalData.Snapshot snapshot) {
        String value = !TextUtils.isEmpty(snapshot.text) ? snapshot.text : snapshot.media;
        if (TextUtils.isEmpty(value)) {
            value = snapshot.peerName;
        }
        value = value.replace('\n', ' ').trim();
        return value.length() > 72 ? value.substring(0, 69) + "…" : value;
    }

    private static String description(ZgramLocalData.Snapshot snapshot) {
        final String date = LocaleController.formatDateTime(snapshot.date, false);
        final String sender = TextUtils.isEmpty(snapshot.senderName) ? snapshot.peerName : snapshot.senderName;
        final String state = snapshot.deletedAt != 0 ? " · " + LocaleController.getString(R.string.ZgramLocalBookmarksDeleted) : "";
        return sender + " · " + date + state;
    }
}
