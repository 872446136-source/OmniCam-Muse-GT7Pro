import local.jiege.settings.MasterExposure;
import local.jiege.settings.MasterLooks;
import local.jiege.settings.ModuleSettings;

public final class MasterExposureOfflineTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(!MasterExposure.offsetKey(MasterLooks.FAMILY_FILM).equals(MasterExposure.offsetKey(MasterLooks.FAMILY_HNCS)), "engines must have independent keys");
        check(MasterExposure.ev("not_in_table", MasterExposure.OFFSET, -50, 0) == -.5f, "HNCS negative offset");
        check(MasterExposure.ev("not_in_table", MasterExposure.OFFSET, 700, 0) == 4f, "positive clamp");
        check(MasterExposure.ev("not_in_table", MasterExposure.ALIGN, 50, 0) == 0f, "alignment must not add manual offset");
        check(MasterExposure.ev("not_in_table", MasterExposure.LEGACY, 50, -1) == .5f, "legacy offset and invalid alignment");
        check(MasterExposure.isLiveInt(ModuleSettings.MASTER_LOOK_FILM), "film switches must remain live");
        check(MasterExposure.isLiveInt(ModuleSettings.MASTER_SIGMOID_CONTRAST), "look parameters must remain live");
        check(!MasterExposure.isLiveInt(ModuleSettings.FILTER_PANEL_TRIGGER), "unrelated settings still restart");
        check(!MasterExposure.isKey("master_align_bad") && !MasterExposure.isKey("master_align_"), "strict numbered keys");
        check(ModuleSettings.defaultInt(MasterExposure.modeKey(3)) == MasterExposure.LEGACY, "migration fallback");
        System.out.println("Master exposure: independent engines, mode semantics, migration, clamps and live settings passed");
    }
}
