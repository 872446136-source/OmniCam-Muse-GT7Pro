package local.jiege.hook.xpan;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.util.Arrays;
import local.jiege.hook.common.Log;

/**
 * The 65:24 crop of an XPAN capture.
 *
 * <p><b>What the capture pipeline asks for.</b> {@code VendorConfigure.getXpanCropRegion} picks the
 * crop region of the capture that is being watermarked: portrait or landscape, main or wide, and
 * hands back four ints, {@code left, top, width, height}, in the picture's own pixels. The caller
 * ({@code WatermarkUtil.buildHold}) turns them into the {@code Size} and {@code Point} that reach the
 * watermark parameter as {@code mXpanCropSize} / {@code mXpanCropCoordinate}. A null region leaves
 * the coordinate null, and the capture is saved at the size the sensor handed over — the full 4:3
 * frame with the XPAN mode having only changed the picture size.
 *
 * <p><b>Why it is null here.</b> The table the camera reads,
 * {@code /odm/etc/camera/config/oplus_camera_config}, does list the six XPAN keys this build needs,
 * but the picture size it declares ({@code 8000x3636}) is the canvas the crop is cut from, not the
 * crop. {@code CameraHWConfiguration.config} then maps that canvas onto what the camera may deliver
 * — {@code 80ac,-1_35_3,8000X3636X4096X3072} — so a capture arrives 8000x3636 at most, while the
 * region the watermark layer would ask for is computed against a canvas it never receives. The
 * result is the 4:3 frame this mode used to save.
 *
 * <p><b>What this does.</b> Answers the region from the canvas the surface really is, so the crop
 * and the picture agree again: 7872x2912 inside 8000x3636, i.e. the 64/362 margins of the canvas
 * dropped on each side. It is the same region the stock table lists, only answered where the
 * pipeline reads it. The picture surface has to be that size in the first place — the custom-size
 * table in {@code CameraHWConfiguration.config} decides it — and both are needed: each one alone
 * leaves the mode as it was.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_xpancrop_off}.
 */
public final class XpanCropRegion {
    private static final String TAG = "XpanCrop";
    private static final String CONFIGURE =
        "com.oplus.ocs.camera.ipuapi.process.watermark.config.VendorConfigure";
    private static final String OFF = "/data/adb/omnicam_xpancrop_off";

    /** The canvas the picture surface is, and the region inside it that XPAN keeps. */
    private static final int LANDSCAPE_W = 7872;
    private static final int LANDSCAPE_H = 2912;
    private static final int PORTRAIT_W = 2912;
    private static final int PORTRAIT_H = 7872;

    private XpanCropRegion() {}

    public static void installCamera(ClassLoader loader) {
        Log.guard(TAG, "XPAN crop region", () -> install(loader));
    }

    private static void install(ClassLoader loader) throws Throwable {
        if (new File(OFF).exists()) {
            Log.i(TAG, "disabled by " + OFF);
            return;
        }
        Class<?> configure = XposedHelpers.findClass(CONFIGURE, loader);
        XposedBridge.hookAllMethods(configure, "getXpanLandScapeCropRegion",
            region(new int[] {64, 360, LANDSCAPE_W, LANDSCAPE_H}));
        XposedBridge.hookAllMethods(configure, "getXpanPortraitCropRegion",
            region(new int[] {360, 64, PORTRAIT_W, PORTRAIT_H}));
        Log.i(TAG, "installed: landscape " + LANDSCAPE_W + "x" + LANDSCAPE_H
            + ", portrait " + PORTRAIT_W + "x" + PORTRAIT_H);
    }

    /** Answers the region given, and says what the stock table had answered instead. */
    private static XC_MethodHook region(final int[] value) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object before = param.getResult();
                if (before instanceof int[] && Arrays.equals(value, (int[]) before)) return;
                Log.i(TAG, "region " + (before instanceof int[] ? Arrays.toString((int[]) before) : String.valueOf(before))
                    + " -> " + Arrays.toString(value));
                param.setResult(value.clone());
            }
        };
    }
}
