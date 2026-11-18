package local.jiege.hook.palette;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GalleryPaletteScope {
    private static final String CONTAINER = "com.oplus.gallery.foundation.fileaccess.extension.FileExtendedContainer";
    private static final String EDITOR = "com.oplus.gallery.pictureeditorpage.PhotoEditorActivity";
    private static final String FILTER_INFO = "filter.info";
    private static final int IS_PALETTE = 140;
    private static final String OPEN_MODE = "com.oplus.gallery.foundation.fileaccess.extension.OpenFileMode";
    private static final int PALETTE_ALGO_USED = 144;
    private static final String TAG = "GalleryPaletteScope";
    private static volatile boolean photoPalette = true;

    private GalleryPaletteScope() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Uri editedImage(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (!intent.getBooleanExtra("editor_from_photo", false)) {
            return intent.getData();
        }
        String stringExtra = intent.getStringExtra("editor_image_uri");
        if (stringExtra == null) {
            return null;
        }
        return Uri.parse(stringExtra);
    }

    public static void install(final ClassLoader classLoader) {
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("ColorPaletteMode.lazy")) {
                Log.i(TAG, "palette mode symbol missing");
                return;
            }
            Field field = symbols.field("ColorPaletteMode.lazy");
            final Object obj = field.get(null);
            Class<?> type = field.getType();
            field.set(null, Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new InvocationHandler() { // from class: local.jiege.hook.palette.a
                @Override // java.lang.reflect.InvocationHandler
                public final Object invoke(Object obj2, Method method, Object[] objArr) {
                    return GalleryPaletteScope.lambda$install$0(obj, obj2, method, objArr);
                }
            }));
            XposedHelpers.findAndHookMethod(EDITOR, classLoader, "onCreate", new Object[]{Bundle.class, new XC_MethodHook() { // from class: local.jiege.hook.palette.GalleryPaletteScope.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Activity activity = (Activity) methodHookParam.thisObject;
                    GalleryPaletteScope.photoPalette = GalleryPaletteScope.paletteCapture(activity, GalleryPaletteScope.editedImage(activity.getIntent()), classLoader);
                }
            }});
            Log.i(TAG, "installed");
        } catch (Throwable th) {
            Log.e(TAG, "palette scope hook failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ Object lambda$install$0(Object obj, Object obj2, Method method, Object[] objArr) throws IllegalAccessException, InvocationTargetException {
        Object objInvoke = method.invoke(obj, objArr);
        if ("getValue".equals(method.getName())) {
            return Boolean.valueOf(Boolean.TRUE.equals(objInvoke) && photoPalette);
        }
        return objInvoke;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean paletteCapture(Activity activity, Uri uri, ClassLoader classLoader) {
        if (uri == null) {
            return true;
        }
        Object objNewInstance = null;
        try {
            objNewInstance = XposedHelpers.newInstance(XposedHelpers.findClass(CONTAINER, classLoader), new Object[0]);
            if (!((Boolean) XposedHelpers.callMethod(objNewInstance, "setDataSource", new Object[]{activity, uri, XposedHelpers.getStaticObjectField(XposedHelpers.findClass(OPEN_MODE, classLoader), "MODE_READ")})).booleanValue()) {
                if (objNewInstance != null) {
                    try {
                        XposedHelpers.callMethod(objNewInstance, "close", new Object[0]);
                    } catch (Throwable unused) {
                    }
                }
                return true;
            }
            byte[] bArr = (byte[]) XposedHelpers.callMethod(objNewInstance, "getExtensionData", new Object[]{FILTER_INFO});
            boolean z = bArr == null || paletteSemantics(bArr);
            StringBuilder sb = new StringBuilder("editing ");
            sb.append(uri);
            sb.append(": ");
            sb.append(z ? "palette" : "filter");
            sb.append(" mode");
            Log.i(TAG, sb.toString());
            if (objNewInstance != null) {
                try {
                    XposedHelpers.callMethod(objNewInstance, "close", new Object[0]);
                } catch (Throwable unused2) {
                }
            }
            return z;
        } catch (Throwable th) {
            try {
                Log.e(TAG, "filter.info read failed for " + uri, th);
                if (objNewInstance != null) {
                    try {
                        XposedHelpers.callMethod(objNewInstance, "close", new Object[0]);
                    } catch (Throwable unused3) {
                    }
                }
                return true;
            } catch (Throwable th2) {
                if (objNewInstance != null) {
                    try {
                        XposedHelpers.callMethod(objNewInstance, "close", new Object[0]);
                    } catch (Throwable unused4) {
                    }
                }
                throw th2;
            }
        }
    }

    private static boolean paletteSemantics(byte[] bArr) {
        ByteBuffer byteBufferOrder = ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN);
        if (bArr.length < 148 || byteBufferOrder.getFloat(0) < 5.0f) {
            return false;
        }
        return byteBufferOrder.getInt(IS_PALETTE) == 1 || byteBufferOrder.getInt(PALETTE_ALGO_USED) == 1;
    }
}
