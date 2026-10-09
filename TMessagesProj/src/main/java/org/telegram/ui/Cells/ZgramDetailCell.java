package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class ZgramDetailCell extends LinearLayout implements Theme.Colorable {
    private final Theme.ResourcesProvider resourcesProvider;
    private final ImageView iconView;
    private final TextView titleView;
    private final TextView detailView;
    private boolean needDivider;

    public ZgramDetailCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(AndroidUtilities.dp(64));
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LayoutParams iconParams = new LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        iconParams.gravity = Gravity.CENTER_VERTICAL;
        iconParams.setMarginEnd(AndroidUtilities.dp(16));
        addView(iconView, iconParams);

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(VERTICAL);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        addView(textLayout, textParams);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textLayout.addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        detailView = new TextView(context);
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        detailView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        LayoutParams detailParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = AndroidUtilities.dp(4);
        textLayout.addView(detailView, detailParams);

        updateColors();
    }

    public void set(CharSequence title, CharSequence detail, int icon, boolean divider) {
        titleView.setText(title);
        detailView.setText(detail);
        detailView.setVisibility(TextUtils.isEmpty(detail) ? GONE : VISIBLE);
        iconView.setVisibility(icon == 0 ? GONE : VISIBLE);
        if (icon != 0) {
            iconView.setImageResource(icon);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
        invalidate();
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        detailView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        iconView.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && Theme.dividerPaint != null) {
            int inset = AndroidUtilities.dp(64);
            canvas.drawLine(LocaleController.isRTL ? 0 : inset, getHeight() - 1,
                    getWidth() - (LocaleController.isRTL ? inset : 0), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public static final class Factory extends UItem.UItemFactory<ZgramDetailCell> {
        static { setup(new Factory()); }

        @Override
        public ZgramDetailCell createView(Context context, RecyclerListView listView, int currentAccount,
                                          int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new ZgramDetailCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            ((ZgramDetailCell) view).set(item.text, item.subtext, item.iconResId, divider);
        }

        public static UItem of(int id, int icon, CharSequence title, CharSequence detail) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.iconResId = icon;
            item.text = title;
            item.subtext = detail;
            return item;
        }
    }
}
