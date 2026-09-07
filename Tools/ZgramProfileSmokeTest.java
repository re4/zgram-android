import org.telegram.messenger.zgram.ZgramSettings;
import org.telegram.tgnet.TLRPC;

public class ZgramProfileSmokeTest {
    public static void main(String[] args) {
        TLRPC.User user = new TLRPC.TL_user();
        user.id = 5343239210L;
        user.photo = new TLRPC.TL_userProfilePhoto();
        String[] locations = {"", "Miami, US", "Amsterdam, NL", "Miami, US", "Amsterdam, NL", "Singapore, SG"};
        for (int dc = 1; dc <= 5; dc++) {
            user.photo.dc_id = dc;
            expect("5343239210\nDC " + dc + " · " + locations[dc], ZgramSettings.profileInfo(user, null));
        }
        TLRPC.UserFull full = new TLRPC.TL_userFull();
        full.profile_photo = new TLRPC.TL_photo();
        full.profile_photo.dc_id = 4;
        user.photo = null;
        expect("5343239210\nDC 4 · Amsterdam, NL", ZgramSettings.profileInfo(user, full));
        user.id = Long.MAX_VALUE;
        user.photo = new TLRPC.TL_userProfilePhoto();
        user.photo.dc_id = 5;
        expect("9223372036854775807\nDC 5 · Singapore, SG", ZgramSettings.profileInfo(user, full));
        System.out.println("Profile smoke tests passed: raw 64-bit IDs, five DC mappings, photo fallback and precedence.");
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
