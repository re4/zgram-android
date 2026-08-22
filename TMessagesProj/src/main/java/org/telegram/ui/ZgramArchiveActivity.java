package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.zgram.ZgramLocalData;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ZgramArchiveActivity extends ZgramSearchableActivity {

    private static final int ID_DIALOG = 1;
    private static final int ID_RETENTION = 2;
    private static final int ID_EXPORT_JSON = 3;
    private static final int ID_EXPORT_HTML = 4;
    private static final int ID_CLEAR = 5;
    private static final int ID_MESSAGE = 6;

    private final long dialogId;

    public ZgramArchiveActivity() {
        this(0L);
    }

    public ZgramArchiveActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected CharSequence getTitle() {
        if (dialogId == 0) {
            return getString(R.string.ZgramLocalArchiveTitle);
        }
        final String name = DialogObject.getName(currentAccount, dialogId);
        return TextUtils.isEmpty(name) ? getString(R.string.ZgramLocalArchiveTitle) : name;
    }

    @Override
    protected CharSequence searchHint() {
        return getString(R.string.ZgramLocalArchiveSearch);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (dialogId == 0) {
            fillDialogs(items);
        } else {
            fillTimeline(items);
        }
    }

    private void fillDialogs(ArrayList<UItem> items) {
        final ZgramLocalData data = ZgramLocalData.get(currentAccount);
        final List<ZgramLocalData.ArchiveSummary> summaries = data.archiveDialogs(query());
        items.add(UItem.asTopViewStatic(getString(R.string.ZgramLocalArchiveAbout), R.drawable.zgram_wings));
        items.add(UItem.asHeader(LocaleController.formatString(R.string.ZgramLocalArchiveChatCount, summaries.size())));
        if (summaries.isEmpty()) {
            items.add(UItem.asShadow(getString(TextUtils.isEmpty(query())
                    ? R.string.ZgramLocalArchiveEmpty
                    : R.string.ZgramCommandPaletteEmpty)));
            return;
        }
        for (ZgramLocalData.ArchiveSummary summary : summaries) {
            final String name = TextUtils.isEmpty(summary.peerName)
                    ? Long.toString(summary.dialogId)
                    : summary.peerName;
            final String value = LocaleController.formatString(
                    R.string.ZgramLocalArchiveSummary,
                    summary.count,
                    AndroidUtilities.formatFileSize(summary.approximateBytes));
            final UItem item = UItem.asSettingsCell(ID_DIALOG, R.drawable.msg_archive, name, value);
            item.object = summary;
            items.add(item);
        }
        items.add(UItem.asShadow(getString(R.string.ZgramLocalArchiveEncryptedAbout)));
    }

    private void fillTimeline(ArrayList<UItem> items) {
        final ZgramLocalData data = ZgramLocalData.get(currentAccount);
        final List<ZgramLocalData.Snapshot> snapshots = data.archive(dialogId, query());
        items.add(UItem.asTopViewStatic(getString(R.string.ZgramLocalArchiveChatAbout), R.drawable.zgram_wings));
        items.add(UItem.asHeader(getString(R.string.ZgramLocalArchiveControls)));
        items.add(UItem.asSettingsCell(
                ID_RETENTION,
                R.drawable.msg_autodelete,
                getString(R.string.ZgramLocalArchiveRetention),
                retentionName(data.getRetention(dialogId))));
        items.add(UItem.asButton(ID_EXPORT_JSON, R.drawable.msg_share, getString(R.string.ZgramLocalArchiveExportJson)));
        items.add(UItem.asButton(ID_EXPORT_HTML, R.drawable.msg_share, getString(R.string.ZgramLocalArchiveExportHtml)));
        items.add(UItem.asShadow(getString(R.string.ZgramLocalArchivePrivacy)));
        items.add(UItem.asHeader(LocaleController.formatString(R.string.ZgramLocalArchiveMessageCount, snapshots.size())));
        if (snapshots.isEmpty()) {
            items.add(UItem.asShadow(getString(TextUtils.isEmpty(query())
                    ? R.string.ZgramLocalArchiveChatEmpty
                    : R.string.ZgramCommandPaletteEmpty)));
        } else {
            for (ZgramLocalData.Snapshot snapshot : snapshots) {
                final UItem item = UItem.asSettingsCell(
                        ID_MESSAGE,
                        snapshot.deletedAt != 0 ? R.drawable.msg_delete : R.drawable.msg_message,
                        title(snapshot),
                        description(snapshot));
                item.object = snapshot;
                items.add(item);
            }
            items.add(UItem.asShadow(getString(R.string.ZgramLocalArchiveSearchHelp)));
            items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_clear, getString(R.string.ZgramLocalArchiveClearChat)).red());
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_DIALOG && item.object instanceof ZgramLocalData.ArchiveSummary) {
            presentFragment(new ZgramArchiveActivity(((ZgramLocalData.ArchiveSummary) item.object).dialogId));
        } else if (item.id == ID_RETENTION) {
            chooseRetention();
        } else if (item.id == ID_EXPORT_JSON) {
            export(false);
        } else if (item.id == ID_EXPORT_HTML) {
            export(true);
        } else if (item.id == ID_CLEAR) {
            confirmClear();
        } else if (item.id == ID_MESSAGE && item.object instanceof ZgramLocalData.Snapshot) {
            final ZgramLocalData.Snapshot snapshot = (ZgramLocalData.Snapshot) item.object;
            presentFragment(ChatActivity.of(snapshot.dialogId, snapshot.messageId));
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

    private void chooseRetention() {
        final int[] values = {
                ZgramLocalData.RETENTION_OFF,
                ZgramLocalData.RETENTION_WEEK,
                ZgramLocalData.RETENTION_MONTH,
                ZgramLocalData.RETENTION_YEAR,
                ZgramLocalData.RETENTION_FOREVER
        };
        final CharSequence[] labels = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = retentionName(values[i]);
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.ZgramLocalArchiveRetention))
                .setItems(labels, (dialog, which) -> {
                    ZgramLocalData.get(currentAccount).setRetention(dialogId, values[which]);
                    listView.adapter.update(true);
                })
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.ZgramLocalArchiveClearChat))
                .setMessage(getString(R.string.ZgramLocalArchiveClearConfirm))
                .setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
                    ZgramLocalData.get(currentAccount).clearArchive(dialogId);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
    }

    private void export(boolean html) {
        if (getParentActivity() == null) {
            return;
        }
        try {
            final File directory = new File(getParentActivity().getFilesDir(), "cache");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Could not create export directory");
            }
            final String extension = html ? "html" : "json";
            final File file = new File(directory, String.format(Locale.US, "zgram-archive-%d.%s", Math.abs(dialogId), extension));
            final String content = html
                    ? ZgramLocalData.get(currentAccount).exportHtml(dialogId)
                    : ZgramLocalData.get(currentAccount).exportJson(dialogId);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(content.getBytes(StandardCharsets.UTF_8));
            }
            final Uri uri = FileProvider.getUriForFile(
                    getParentActivity(),
                    ApplicationLoader.getApplicationId() + ".provider",
                    file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(html ? "text/html" : "application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(Intent.createChooser(intent, getString(R.string.ShareFile)));
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.error, getString(R.string.ZgramLocalArchiveExportFailed))
                    .show();
        }
    }

    private static String retentionName(int value) {
        if (value == ZgramLocalData.RETENTION_WEEK) {
            return LocaleController.getString(R.string.ZgramLocalArchiveRetentionWeek);
        } else if (value == ZgramLocalData.RETENTION_MONTH) {
            return LocaleController.getString(R.string.ZgramLocalArchiveRetentionMonth);
        } else if (value == ZgramLocalData.RETENTION_YEAR) {
            return LocaleController.getString(R.string.ZgramLocalArchiveRetentionYear);
        } else if (value == ZgramLocalData.RETENTION_FOREVER) {
            return LocaleController.getString(R.string.ZgramLocalArchiveRetentionForever);
        }
        return LocaleController.getString(R.string.ZgramLocalArchiveRetentionOff);
    }

    private static String title(ZgramLocalData.Snapshot snapshot) {
        String value = !TextUtils.isEmpty(snapshot.text) ? snapshot.text : snapshot.media;
        if (TextUtils.isEmpty(value)) {
            value = LocaleController.getString(R.string.ZgramLocalArchiveMessage);
        }
        value = value.replace('\n', ' ').trim();
        return value.length() > 72 ? value.substring(0, 69) + "…" : value;
    }

    private static String description(ZgramLocalData.Snapshot snapshot) {
        final String sender = TextUtils.isEmpty(snapshot.senderName) ? snapshot.peerName : snapshot.senderName;
        final String date = LocaleController.formatDateTime(snapshot.date, false);
        final String state = snapshot.deletedAt != 0
                ? LocaleController.getString(R.string.ZgramLocalBookmarksDeleted)
                : snapshot.edited || !snapshot.versions.isEmpty()
                ? LocaleController.getString(R.string.ZgramLocalArchiveEdited)
                : !TextUtils.isEmpty(snapshot.media)
                ? snapshot.media
                : "";
        return sender + " · " + date + (TextUtils.isEmpty(state) ? "" : " · " + state);
    }
}
