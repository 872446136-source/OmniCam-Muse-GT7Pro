package local.jiege.hook.common;

import android.hardware.camera2.CaptureRequest;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.k2;
import java.io.File;
import java.io.IOException;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MarkerSwitch {
    private static final long CHECK_INTERVAL_MS = 500;
    private Boolean applied;
    private long checkedAt;
    private final File marker;
    private final File root;
    private final String setting;
    private final String tag;

    private MarkerSwitch(String str, String str2, File file) {
        this.tag = str;
        this.setting = str2;
        this.root = file;
        this.marker = new File(file, "enabled");
    }

    public static void install(String str, String str2, File file) {
        MarkerSwitch markerSwitch = new MarkerSwitch(str, str2, file);
        markerSwitch.sync();
        Log.guard(str, "settings sync", new k2(markerSwitch));
        Log.i(str, "installed");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$0(MarkerSwitch markerSwitch) {
        XposedBridge.hookAllMethods(CaptureRequest.Builder.class, "build", new XC_MethodHook() { // from class: local.jiege.hook.common.MarkerSwitch.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MarkerSwitch.this.sync();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void sync() {
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (this.applied == null || jCurrentTimeMillis - this.checkedAt >= CHECK_INTERVAL_MS) {
            this.checkedAt = jCurrentTimeMillis;
            boolean z = Settings.get(this.setting);
            Boolean bool = this.applied;
            if (bool == null || bool.booleanValue() != z) {
                try {
                    if (z) {
                        this.root.mkdirs();
                        if (!this.marker.exists() && !this.marker.createNewFile()) {
                            throw new IOException("create " + this.marker);
                        }
                    } else if (this.marker.exists() && !this.marker.delete()) {
                        throw new IOException("delete " + this.marker);
                    }
                    this.applied = Boolean.valueOf(z);
                    Log.i(this.tag, z ? Install.ON : "off (stock configs)");
                } catch (IOException e) {
                    Log.e(this.tag, "marker", e);
                }
            }
        }
    }
}
