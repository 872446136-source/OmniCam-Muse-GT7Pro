package local.jiege.hook.common;

import de.robv.android.xposed.XSharedPreferences;
import defpackage.ll;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class Settings {
    private static final String MODULE_PACKAGE = "local.jiege.camera";
    private static XSharedPreferences preferences;

    private Settings() {
    }

    public static synchronized boolean get(String str) {
        XSharedPreferences xSharedPreferencesPreferences;
        boolean z;
        xSharedPreferencesPreferences = preferences();
        str.getClass();
        z = true;
        switch (str) {
            case "dump_hdrtransform":
            case "dump_raw":
            case "soft_light_weak_hdf":
            case "normal_follows_master":
            case "dump_ccm_vec":
            case "key_launch_mode_on":
            case "video_beauty_high_spec":
            case "soft_light_no_local_contrast":
            case "video_olive_unlimited":
            case "pop_sdr_style":
            case "video_filter_high_spec":
            case "xpan_clean_small_mask":
            case "master_portra_physical":
            case "shutter_always":
            case "model_spoof_exif":
            case "soft_light_medium_black_mist":
            case "natural_mode":
            case "xpan_clean_large_mask":
            case "xpan_developing_live":
            case "soft_light_strong_white_mist":
            case "high_pixel_200m_no_face":
            case "model_spoof_watermark":
            case "xpan_clean_animation":
            case "shutter_volume_custom":
                z = false;
                break;
            case "shutter_in_vibrate":
            case "xpan_watermark_isolation":
            case "flash_per_mode":
            case "unlock_25mp":
            case "pop_high_pixel":
            case "master_effect_neon":
            case "master_effect_portra":
            case "natural_no_lce":
            case "shutter_in_silent":
            case "natural_no_ai_detail":
            case "high_pixel_effects":
            case "live_photo_per_mode":
                break;
            default:
                throw new IllegalArgumentException("Unknown setting ".concat(str));
        }
        return xSharedPreferencesPreferences.getBoolean(str, z);
    }

    public static synchronized int getInt(String str) {
        return preferences().getInt(str, ll.t(str));
    }

    public static synchronized int getIntOr(String str, int i) {
        return preferences().getInt(str, i);
    }

    public static synchronized String getString(String str) {
        return preferences().getString(str, ll.u(str));
    }

    private static XSharedPreferences preferences() {
        XSharedPreferences xSharedPreferences = preferences;
        if (xSharedPreferences == null) {
            preferences = new XSharedPreferences(MODULE_PACKAGE, "module_settings");
        } else if (xSharedPreferences.hasFileChanged()) {
            preferences.reload();
        }
        return preferences;
    }
}
