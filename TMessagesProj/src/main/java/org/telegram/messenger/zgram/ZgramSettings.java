package org.telegram.messenger.zgram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

public final class ZgramSettings {

    private ZgramSettings() {
    }

    public static boolean showProfileInfo() {
        return MessagesController.getGlobalMainSettings().getBoolean("zgramShowProfileInfo", true);
    }

    public static void setShowProfileInfo(boolean value) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("zgramShowProfileInfo", value).apply();
    }

    public static String profileInfo(TLRPC.User user, TLRPC.UserFull full) {
        int dc = user.photo != null ? user.photo.dc_id : 0;
        if (dc == 0 && full != null && full.profile_photo != null) {
            dc = full.profile_photo.dc_id;
        }
        String location;
        switch (dc) {
            case 1:
            case 3:
                location = "Miami, US";
                break;
            case 2:
            case 4:
                location = "Amsterdam, NL";
                break;
            case 5:
                location = "Singapore, SG";
                break;
            default:
                location = LocaleController.getString(R.string.ZgramDcUnavailable);
        }
        String dataCenter = dc > 0 ? "DC " + Integer.toString(dc) + " · " + location : location;
        return Long.toString(user.id) + "\n" + dataCenter;
    }
}
