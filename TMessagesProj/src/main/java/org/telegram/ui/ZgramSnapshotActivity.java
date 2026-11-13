package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.zgram.ZgramLocalData;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/** Reads the saved snapshot without depending on the original chat or message. */
public class ZgramSnapshotActivity extends ZgramSettingsActivity {
    private static final int ID_OPEN_ORIGINAL = 1;
    private static final int ID_COPY = 2;
    private final ZgramLocalData.Snapshot snapshot;

    public ZgramSnapshotActivity(ZgramLocalData.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.ZgramLocalSnapshotTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(TextUtils.isEmpty(snapshot.peerName)
                ? getString(R.string.ZgramLocalArchiveMessage) : snapshot.peerName));
        addText(items, snapshot.text, snapshot.media);
        final String sender = TextUtils.isEmpty(snapshot.senderName) ? snapshot.peerName : snapshot.senderName;
        items.add(UItem.asShadow(sender + " · " + LocaleController.formatDateTime(snapshot.date, false)
                + (snapshot.deletedAt == 0 ? "" : "\n" + getString(R.string.ZgramLocalBookmarksDeleted))));
        if (!snapshot.versions.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.ZgramLocalSnapshotVersions)));
            for (int i = snapshot.versions.size() - 1; i >= 0; i--) {
                ZgramLocalData.Version version = snapshot.versions.get(i);
                addText(items, version.text, version.media);
                items.add(UItem.asShadow(LocaleController.formatDateTime(version.observedAt, false)));
            }
        }
        if (!TextUtils.isEmpty(snapshot.text)) {
            items.add(UItem.asButton(ID_COPY, R.drawable.msg_copy, getString(R.string.Copy)));
        }
        items.add(UItem.asButton(ID_OPEN_ORIGINAL, R.drawable.msg_message, getString(R.string.ZgramLocalSnapshotOpenOriginal)));
        items.add(UItem.asShadow(getString(R.string.ZgramLocalSnapshotAbout)));
    }

    private void addText(ArrayList<UItem> items, String text, String media) {
        TextView content = new TextView(getContext());
        content.setTextSize(16);
        content.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        content.setText(TextUtils.isEmpty(text) ? media : text + (TextUtils.isEmpty(media) ? "" : "\n\n" + media));
        content.setTextIsSelectable(true);
        ThemeBind.textColor(content, Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
        items.add(UItem.asCustom(content));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_OPEN_ORIGINAL) {
            presentFragment(ChatActivity.of(snapshot.dialogId, snapshot.messageId));
        } else if (item.id == ID_COPY) {
            AndroidUtilities.addToClipboard(snapshot.text);
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
