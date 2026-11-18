package local.jiege.hook.gr;

import android.os.Bundle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrEffectTransport {
    private static final String TAG = "RicohGrPort";
    private static volatile boolean warnedNoTables;

    private GrEffectTransport() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Bundle effectBundle(byte[] bArr) {
        Bundle bundle = new Bundle();
        bundle.putByteArray("gr.effect.params", bArr);
        return bundle;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookCaptureStage(final Symbols symbols, final ClassLoader classLoader) {
        final Method method = symbols.method("StageRequest.putBundle");
        XposedBridge.hookMethod(symbols.method("GrMode.captureStage"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrEffectTransport.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                GrState.currentMode = methodHookParam.thisObject;
                if (GrState.isGrMode()) {
                    try {
                        byte[] bArrLoadEffectBytes = GrEffectTransport.loadEffectBytes(symbols);
                        if (bArrLoadEffectBytes != null && bArrLoadEffectBytes.length != 0) {
                            Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.CameraParameter", classLoader), "GR_MODE_PARAM");
                            Object obj = symbols.get("Stage.request", methodHookParam.args[0]);
                            if (obj == null) {
                                Log.i(GrEffectTransport.TAG, "GR capture stage has no request builder");
                                return;
                            } else {
                                method.invoke(obj, staticObjectField, GrEffectTransport.effectBundle(bArrLoadEffectBytes));
                                return;
                            }
                        }
                        Log.i(GrEffectTransport.TAG, "GR effect encoder unavailable");
                    } catch (Throwable th) {
                        Log.e(GrEffectTransport.TAG, "GR effect stage attach failed", th);
                    }
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookPreviewLut(final Symbols symbols, ClassLoader classLoader) {
        final ThreadLocal threadLocal = new ThreadLocal();
        XposedBridge.hookMethod(symbols.method("Lut3d.load"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrEffectTransport.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                threadLocal.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                boolean zEquals = "gr".equals(methodHookParam.args[2]);
                if (zEquals != "gr".equals(symbols.get("Lut3d.mode", methodHookParam.thisObject))) {
                    symbols.set("Lut3d.index", methodHookParam.thisObject, -1);
                }
                threadLocal.set(Boolean.valueOf(zEquals));
            }
        });
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.ocs.camera.CombineLutHelper", classLoader), "init", new XC_MethodHook() { // from class: local.jiege.hook.gr.GrEffectTransport.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Boolean.TRUE.equals(threadLocal.get())) {
                    String strGrLmtDir = Capabilities.grLmtDir();
                    if (Capabilities.grLmt()) {
                        methodHookParam.args[0] = strGrLmtDir;
                        return;
                    }
                    if (GrEffectTransport.warnedNoTables) {
                        return;
                    }
                    Log.i(GrEffectTransport.TAG, "GR tables absent (" + strGrLmtDir + "), preview keeps the host LUT");
                    GrEffectTransport.warnedNoTables = true;
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookSdkRequest(final Symbols symbols, final ClassLoader classLoader) {
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.GRCapMode", classLoader), "updateCaptureRequestTag", new XC_MethodHook() { // from class: local.jiege.hook.gr.GrEffectTransport.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                byte[] bArrLoadEffectBytes;
                if (methodHookParam.args.length == 3 && GrState.isGrMode()) {
                    try {
                        Object obj = methodHookParam.args[1];
                        Object objectField = XposedHelpers.getObjectField(obj, "mGrEffectParam");
                        if ((objectField == null || XposedHelpers.callMethod(objectField, "getGrEffectParams", new Object[0]) == null) && (bArrLoadEffectBytes = GrEffectTransport.loadEffectBytes(symbols)) != null && bArrLoadEffectBytes.length != 0) {
                            Object objNewInstance = XposedHelpers.newInstance(XposedHelpers.findClass("com.oplus.ocs.camera.consumer.apsAdapter.adapter.GrEffectParam", classLoader), new Object[0]);
                            XposedHelpers.callMethod(objNewInstance, "setGrEffectParams", new Class[]{byte[].class}, new Object[]{bArrLoadEffectBytes});
                            XposedHelpers.setObjectField(obj, "mGrEffectParam", objNewInstance);
                        }
                    } catch (Throwable th) {
                        Log.e(GrEffectTransport.TAG, "GR effect request tag attach failed", th);
                    }
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                byte[] bArrLoadEffectBytes;
                if (methodHookParam.args.length == 3 && GrState.isGrMode()) {
                    try {
                        Object obj = methodHookParam.args[0];
                        Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.metadata.UPreviewKeys", classLoader), "KEY_GR_MODE_PARAM");
                        if (Boolean.TRUE.equals(XposedHelpers.callMethod(obj, "containCustomKey", new Object[]{staticObjectField})) || (bArrLoadEffectBytes = GrEffectTransport.loadEffectBytes(symbols)) == null || bArrLoadEffectBytes.length == 0) {
                            return;
                        }
                        XposedHelpers.callMethod(obj, "set", new Object[]{staticObjectField, GrEffectTransport.effectBundle(bArrLoadEffectBytes)});
                    } catch (Throwable th) {
                        Log.e(GrEffectTransport.TAG, "GR effect request injection failed", th);
                    }
                }
            }
        });
    }

    public static void install(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "GR preview LUT", new a(symbols, classLoader, (byte) 2));
        Log.guard(TAG, "GR capture stage", new a(symbols, classLoader, (byte) 3));
        Log.guard(TAG, "GR SDK request", new a(symbols, classLoader, (byte) 4));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static byte[] loadEffectBytes(Symbols symbols) {
        Object objCaptureParams = GrModeHooks.captureParams(symbols);
        if (objCaptureParams == null) {
            return null;
        }
        Object objFindFieldByType = Reflection.findFieldByType(objCaptureParams, symbols.cls("GrStylePanel").getName());
        if (objFindFieldByType != null) {
            return structBytes(symbols, symbols.call("GrStylePanel.effect", objFindFieldByType, new Object[0]));
        }
        Class<?> cls = symbols.cls("GrEffectTable");
        Object objFindFieldByType2 = Reflection.findFieldByType(objCaptureParams, cls.getName());
        if (objFindFieldByType2 == null) {
            objFindFieldByType2 = XposedHelpers.newInstance(cls, new Object[0]);
        }
        return structBytes(symbols, XposedHelpers.newInstance(symbols.cls("GrEffect"), new Object[]{objFindFieldByType2}));
    }

    private static byte[] structBytes(Symbols symbols, Object obj) {
        ByteBuffer byteBufferDuplicate = ((ByteBuffer) symbols.call("GrEffectTable.buffer", symbols.call("GrEffect.table", obj, new Object[0]), new Object[0])).duplicate();
        byteBufferDuplicate.clear();
        byte[] bArr = new byte[byteBufferDuplicate.remaining()];
        byteBufferDuplicate.get(bArr);
        return bArr;
    }
}
