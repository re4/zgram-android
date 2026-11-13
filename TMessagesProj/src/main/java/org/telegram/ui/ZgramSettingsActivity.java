package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UniversalFragment;

/** Shared Telegram settings layout for the local Zgram tools. */
abstract class ZgramSettingsActivity extends UniversalFragment {
    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        View view = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return view;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
}
