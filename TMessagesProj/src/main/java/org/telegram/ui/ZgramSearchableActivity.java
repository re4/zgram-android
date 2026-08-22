package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UniversalFragment;

abstract class ZgramSearchableActivity extends UniversalFragment {

    private String query = "";

    @Override
    public View createView(Context context) {
        final View result = super.createView(context);
        final ActionBarMenuItem search = actionBar.createMenu()
                .addItem(1, R.drawable.outline_header_search, getResourceProvider())
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                    }

                    @Override
                    public void onSearchCollapse() {
                        setQuery("");
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        setQuery(editText.getText().toString());
                    }
                });
        search.setSearchFieldHint(searchHint());
        search.setContentDescription(searchHint());
        final EditTextBoldCursor field = search.getSearchField();
        field.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        field.setHintTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        field.setCursorColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        return result;
    }

    protected CharSequence searchHint() {
        return getString(R.string.Search);
    }

    protected final String query() {
        return query;
    }

    protected void onQueryChanged() {
        if (listView != null) {
            listView.adapter.update(true);
            listView.scrollToPosition(0);
        }
    }

    private void setQuery(String value) {
        final String next = value == null ? "" : value;
        if (!TextUtils.equals(query, next)) {
            query = next;
            onQueryChanged();
        }
    }
}
