package local.jiege.hook.palette;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Build;
import dalvik.system.BaseDexClassLoader;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class GalleryColorPalette {
    private static final String FEATURE = "feature_is_support_color_palette";
    private static final String LIBRARY_PATH = "/product/lib64:/system/lib64";
    private static final String SDK_JAR = "com.oplus.camera.unit.sdk.jar";
    private static final String STAGED_SDK = "/data/user/0/com.coloros.gallery3d/files/jiege_ipu/com.oplus.camera.unit.sdk.jar";
    private static final String TAG = "GalleryColorPalette";

    private GalleryColorPalette() {
    }

    public static void installGallery(ClassLoader classLoader) {
        if (replaceSdkLoader(classLoader)) {
            try {
                XposedBridge.hookMethod(Symbols.get().method("Config.getBoolean"), new XC_MethodHook() { // from class: local.jiege.hook.palette.GalleryColorPalette.1
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        if (GalleryColorPalette.FEATURE.equals(methodHookParam.args[0])) {
                            methodHookParam.setResult(Boolean.TRUE);
                        }
                    }
                });
                GalleryPaletteScope.install(classLoader);
                Log.i(TAG, "gallery palette editor enabled");
            } catch (Throwable th) {
                Log.e(TAG, "gallery palette switch hook failed", th);
            }
        }
    }

    private static void p010PathOnly(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.ipusdk.processunit.palette.PaletteProcessUnitSDK", classLoader, "isTextureRequest", new Object[]{XC_MethodReplacement.returnConstant(Boolean.FALSE)});
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.ipusdk.processunit.palette.PaletteProcessUnitSDK", classLoader, "processImage", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.palette.GalleryColorPalette.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                EGLContext eGLContextEglGetCurrentContext = EGL14.eglGetCurrentContext();
                if (eGLContextEglGetCurrentContext == null || eGLContextEglGetCurrentContext == EGL14.EGL_NO_CONTEXT || XposedHelpers.getObjectField(methodHookParam.thisObject, "mShareGlContext") != null) {
                    return;
                }
                XposedHelpers.callMethod(methodHookParam.thisObject, "setShareGlContext", new Object[]{EGL14.eglGetCurrentDisplay(), eGLContextEglGetCurrentContext});
            }
        }});
    }

    private static boolean replaceSdkLoader(ClassLoader classLoader) {
        if (!new File(STAGED_SDK).canRead()) {
            Log.i(TAG, "staged camera unit SDK missing: /data/user/0/com.coloros.gallery3d/files/jiege_ipu/com.oplus.camera.unit.sdk.jar");
            return false;
        }
        try {
            ClassLoader[] classLoaderArr = (ClassLoader[]) XposedHelpers.getObjectField(classLoader, "sharedLibraryLoaders");
            if (classLoaderArr == null) {
                return false;
            }
            for (int i = 0; i < classLoaderArr.length; i++) {
                ClassLoader classLoader2 = classLoaderArr[i];
                if ((classLoader2 instanceof BaseDexClassLoader) && classLoader2.toString().contains(SDK_JAR)) {
                    ClassLoader classLoaderSharedLibraryLoader = sharedLibraryLoader(classLoader2.getParent());
                    classLoaderArr[i] = classLoaderSharedLibraryLoader;
                    p010PathOnly(classLoaderSharedLibraryLoader);
                    GalleryFilters.install(classLoaderArr[i]);
                    return true;
                }
            }
            Log.i(TAG, "camera unit SDK loader not found");
            return false;
        } catch (Throwable th) {
            Log.e(TAG, "camera unit SDK loader replacement failed", th);
        }
    }

    private static ClassLoader sharedLibraryLoader(ClassLoader classLoader) {
        return (ClassLoader) XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.android.internal.os.ClassLoaderFactory", (ClassLoader) null), "createClassLoader", new Class[]{String.class, String.class, String.class, ClassLoader.class, Integer.TYPE, Boolean.TYPE, String.class}, new Object[]{STAGED_SDK, LIBRARY_PATH, LIBRARY_PATH, classLoader, Integer.valueOf(Build.VERSION.SDK_INT), Boolean.TRUE, null});
    }
}
