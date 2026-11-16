package local.jiege.settings;

/** Per-engine exposure settings. Mode 2 preserves the pre-514 offset plus alignment. */
public final class MasterExposure {
    public static final int OFFSET = 0, ALIGN = 1, LEGACY = 2;
    private MasterExposure() {}
    public static String offsetKey(int family) { return "master_look_exposure_" + family; }
    public static String alignKey(int family) { return "master_align_" + family; }
    public static String modeKey(int family) { return "master_exposure_mode_" + family; }
    public static MasterLooks.Param offsetParam(int family) {
        return new MasterLooks.Param(offsetKey(family), "偏移量", " EV", 100, -400, 400, 0);
    }
    private static boolean numbered(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) return false;
        String suffix = key.substring(prefix.length());
        if (suffix.isEmpty()) return false;
        for (int i = 0; i < suffix.length(); i++) if (!Character.isDigit(suffix.charAt(i))) return false;
        return true;
    }
    public static boolean isOffsetKey(String key) { return numbered(key, "master_look_exposure_"); }
    public static boolean isAlignKey(String key) { return numbered(key, "master_align_"); }
    public static boolean isModeKey(String key) { return numbered(key, "master_exposure_mode_"); }
    public static boolean isKey(String key) { return isOffsetKey(key) || isAlignKey(key) || isModeKey(key); }
    public static float ev(String look, int mode, int hundredths, int align) {
        float offset = Math.max(-400, Math.min(400, hundredths)) / 100f;
        float alignment = MasterAlign.offset(look, align);
        float value = mode == ALIGN ? alignment : mode == OFFSET ? offset : offset + alignment;
        return Math.max(-4f, Math.min(4f, Math.round(value * 100f) / 100f));
    }
    public static boolean isLiveInt(String key) {
        return isKey(key) || MasterLooks.paramDefault(key) != null
            || key.equals(ModuleSettings.MASTER_LOOK) || key.equals(ModuleSettings.MASTER_LOOK_OPPO)
            || key.equals(ModuleSettings.MASTER_LOOK_LUMIX) || key.equals(ModuleSettings.MASTER_LOOK_FILM)
            || key.equals(ModuleSettings.MASTER_LOOK_PRINT) || key.equals(ModuleSettings.MASTER_LOOK_CANON)
            || key.equals(ModuleSettings.MASTER_LOOK_DARKTABLE) || key.equals(ModuleSettings.MASTER_LOOK_BASIC)
            || key.equals(ModuleSettings.MASTER_HNCS_INTENT) || key.equals(ModuleSettings.MASTER_HNCS_CURVE)
            || key.equals(ModuleSettings.MASTER_FILM_DENSITY_C) || key.equals(ModuleSettings.MASTER_FILM_DENSITY_M)
            || key.equals(ModuleSettings.MASTER_FILM_DENSITY_Y) || key.equals(ModuleSettings.MASTER_LOOK_MODE)
            || key.equals(ModuleSettings.MASTER_BLEND_TONE) || key.equals(ModuleSettings.MASTER_BLEND_COLOUR)
            || key.equals(ModuleSettings.MASTER_LOOK_EXPOSURE) || key.equals(ModuleSettings.MASTER_ALIGN);
    }
}
