package local.jiege.hook.modestate;

import android.text.TextUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.c3;
import defpackage.t2;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModeStateSplit {
    private static final String RETRO_CAMERA = "retroCamera";
    private static final String TAG = "ModeStateSplit";

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class State {
        final String factory;
        final Map<String, Object> modeKeys = new HashMap();
        final String name;
        final String preference;
        final String prefix;
        final String setting;
        Object sharedKey;
        final boolean stockRetroSwap;

        public State(String str, String str2, String str3, String str4, String str5, boolean z) {
            this.name = str;
            this.setting = str2;
            this.preference = str3;
            this.factory = str4;
            this.prefix = str5;
            this.stockRetroSwap = z;
        }
    }

    private ModeStateSplit() {
    }

    private static void install(final ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        final State[] stateArr = {new State("flash", "flash_per_mode", "pref_camera_flashmode_key", "DataKeys.cameraPrefs", "pref_camera_flashmode_key_jiege_", false), new State("live", "live_photo_per_mode", "pref_live_photo_key", "DataKeys.prefs", "pref_live_photo_key_jiege_", true)};
        symbols.method("DataKeys.cameraPrefs");
        symbols.method("DataKeys.prefs");
        XposedBridge.hookMethod(symbols.method("ModeSwitch.switch"), new XC_MethodHook() { // from class: local.jiege.hook.modestate.ModeStateSplit.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                AnonymousClass1 anonymousClass1 = this;
                Object[] objArr = methodHookParam.args;
                int i = 0;
                String str = (String) objArr[0];
                String str2 = (String) objArr[1];
                if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2) || str.equals(str2)) {
                    return;
                }
                State[] stateArr2 = stateArr;
                int length = stateArr2.length;
                while (i < length) {
                    State state = stateArr2[i];
                    if (Settings.get(state.setting) && (!state.stockRetroSwap || (!ModeStateSplit.RETRO_CAMERA.equals(str) && !ModeStateSplit.RETRO_CAMERA.equals(str2)))) {
                        try {
                            Object objSharedKey = state.sharedKey;
                            if (objSharedKey == null) {
                                objSharedKey = ModeStateSplit.sharedKey(symbols, state);
                                state.sharedKey = objSharedKey;
                            }
                            Object obj = DataStore.get(symbols, classLoader, objSharedKey, PresetSelection.STANDARD_KEY);
                            if (!PresetSelection.STANDARD_KEY.equals(obj)) {
                                Symbols symbols2 = symbols;
                                DataStore.put(symbols2, classLoader, ModeStateSplit.modeKey(symbols2, state, str), obj);
                            }
                            Symbols symbols3 = symbols;
                            Object obj2 = DataStore.get(symbols3, classLoader, ModeStateSplit.modeKey(symbols3, state, str2), PresetSelection.STANDARD_KEY);
                            boolean zEquals = PresetSelection.STANDARD_KEY.equals(obj2);
                            if (!zEquals) {
                                DataStore.put(symbols, classLoader, state.sharedKey, obj2);
                            }
                            StringBuilder sb = new StringBuilder();
                            sb.append(state.name);
                            sb.append(" ");
                            sb.append(str);
                            sb.append("=");
                            sb.append(obj);
                            sb.append(" -> ");
                            sb.append(str2);
                            sb.append("=");
                            if (zEquals) {
                                obj2 = obj + " (first visit)";
                            }
                            sb.append(obj2);
                            Log.i(ModeStateSplit.TAG, sb.toString());
                        } catch (Throwable th) {
                            StringBuilder sb2 = new StringBuilder();
                            c3.s(sb2, state.name, " swap failed ", str, " -> ");
                            sb2.append(str2);
                            Log.e(ModeStateSplit.TAG, sb2.toString(), th);
                        }
                    }
                    i++;
                    anonymousClass1 = this;
                }
            }
        });
        Log.i(TAG, "installed");
    }

    public static void installCamera(ClassLoader classLoader) {
        try {
            install(classLoader);
        } catch (Throwable th) {
            Log.e(TAG, "mode state split unavailable", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object modeKey(Symbols symbols, State state, String str) {
        Object objCall;
        synchronized (state.modeKeys) {
            try {
                objCall = state.modeKeys.get(str);
                if (objCall == null) {
                    objCall = symbols.call(state.factory, null, String.class, state.prefix + str);
                    state.modeKeys.put(str, objCall);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return objCall;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object sharedKey(Symbols symbols, State state) throws IllegalAccessException {
        if ("flash".equals(state.name)) {
            return DataStore.key(symbols, "Keys.flash", state.preference);
        }
        Iterator it = symbols.all("ModeSwitch.keys").iterator();
        while (it.hasNext()) {
            Object obj = ((Field) it.next()).get(null);
            if (obj != null && Reflection.hasStringField(obj, state.preference)) {
                return obj;
            }
        }
        t2.i(state.preference, "DataKey not found: ");
        return null;
    }
}
