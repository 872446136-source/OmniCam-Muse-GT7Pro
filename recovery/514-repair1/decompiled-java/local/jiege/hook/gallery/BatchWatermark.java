package local.jiege.hook.gallery;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.ed;
import defpackage.fd;
import defpackage.gd;
import defpackage.hd;
import defpackage.id;
import defpackage.na;
import defpackage.t2;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class BatchWatermark {
    private static final String CHANNEL = "jiege_batch_watermark";
    private static final String DISPLAY_NAME = "jiege-batch-watermark";
    private static final String EDITOR = "com.oplus.gallery.pictureeditorpage.PhotoEditorActivity";
    private static final String EXTRA_STEP = "jiege_batch_watermark_step";
    private static final int GALLERY_BLUE = -13529862;
    private static final String MENU_BAR = "com.oplus.gallery.standard_lib.ui.bottomnavigation.BottomFloatingMenuBar";
    private static final String MENU_BAR_VIEW = "main_menu_container";
    private static final String NONE = "picture3d_editor_id_watermark_master_none";
    private static final int NOTIFICATION = 27166;
    private static final long SAVE_TIMEOUT_MS = 20000;
    private static final String SECONDARY_PANEL = "secondary_menu_container";
    private static final String TAG = "BatchWatermark";
    private static final String TITLE = "批量编辑水印";
    private static final int TITLE_ID = 2134838017;
    private static final String WATERMARK_MENU = "strategy_watermark_master";
    private static Object chosenOperator;
    private static Object chosenStyle;
    private static int confirmId;
    private static boolean confirmed;
    private static VirtualDisplay display;
    private static ImageReader frames;
    private static boolean noneChosen;
    private static int noneId;
    private static boolean running;
    private static int saved;
    private static String styleId;
    private static final Set<Activity> OPENED = Collections.newSetFromMap(new WeakHashMap());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Uri> queue = new ArrayList();
    private static WeakReference<Activity> current = new WeakReference<>(null);
    private static final Set<Activity> PANEL = Collections.newSetFromMap(new WeakHashMap());

    /* JADX INFO: renamed from: local.jiege.hook.gallery.BatchWatermark$11, reason: invalid class name */
    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public class AnonymousClass11 extends XC_MethodHook {
        final /* synthetic */ Method val$applyStyle;
        final /* synthetic */ Class val$loaderClass;
        final /* synthetic */ Method val$styleSource;

        public AnonymousClass11(Method method, Method method2, Class cls) {
            this.val$applyStyle = method;
            this.val$styleSource = method2;
            this.val$loaderClass = cls;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static /* synthetic */ void lambda$afterHookedMethod$0(Activity activity) {
            BatchWatermark.pressSave(activity, System.currentTimeMillis());
        }

        public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            int iStep;
            Activity activity = (Activity) BatchWatermark.current.get();
            if (activity == null || !BatchWatermark.running || (iStep = BatchWatermark.step(activity)) < 0 || BatchWatermark.interactive(iStep)) {
                return;
            }
            Object obj = methodHookParam.thisObject;
            try {
                if (BatchWatermark.noneChosen) {
                    BatchWatermark.clickNone(activity, System.currentTimeMillis());
                } else {
                    BatchWatermark.apply(obj, activity, this.val$applyStyle, this.val$styleSource, this.val$loaderClass);
                }
            } catch (Throwable th) {
                Log.e(BatchWatermark.TAG, "batch style failed", th);
            }
            BatchWatermark.MAIN.postDelayed(new b(activity, (byte) 0), 1200L);
        }
    }

    /* JADX INFO: renamed from: local.jiege.hook.gallery.BatchWatermark$2, reason: invalid class name */
    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public class AnonymousClass2 extends XC_MethodHook {
        final /* synthetic */ ClassLoader val$classLoader;
        final /* synthetic */ Class val$function1;
        final /* synthetic */ Class val$function2;
        final /* synthetic */ Object val$unit;

        public AnonymousClass2(ClassLoader classLoader, Class cls, Class cls2, Object obj) {
            this.val$classLoader = classLoader;
            this.val$function1 = cls;
            this.val$function2 = cls2;
            this.val$unit = obj;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static /* synthetic */ Object lambda$beforeHookedMethod$0(Object obj, View view, Object obj2, Method method, Object[] objArr) throws NoSuchMethodException {
            if (!"invoke".equals(method.getName())) {
                return BatchWatermark.objectMethod(obj2, method, objArr);
            }
            Object objCallMethod = XposedHelpers.callMethod(obj, "invoke", new Object[]{objArr[0]});
            BatchWatermark.addItem(view.getContext(), (List) objArr[0]);
            return objCallMethod;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static /* synthetic */ Object lambda$beforeHookedMethod$1(Object obj, View view, Object obj2, Object obj3, Method method, Object[] objArr) {
            if (!"invoke".equals(method.getName())) {
                return BatchWatermark.objectMethod(obj3, method, objArr);
            }
            if (BatchWatermark.titleOf(objArr[1]) != BatchWatermark.TITLE_ID) {
                return XposedHelpers.callMethod(obj, "invoke", new Object[]{objArr[0], objArr[1]});
            }
            BatchWatermark.choose(BatchWatermark.activity(view.getContext()), obj);
            return obj2;
        }

        public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Object objNewProxyInstance;
            final View view = (View) methodHookParam.thisObject;
            Object[] objArr = methodHookParam.args;
            final Object obj = objArr[1];
            final Object obj2 = objArr[2];
            objArr[1] = Proxy.newProxyInstance(this.val$classLoader, new Class[]{this.val$function1}, new InvocationHandler() { // from class: local.jiege.hook.gallery.c
                @Override // java.lang.reflect.InvocationHandler
                public final Object invoke(Object obj3, Method method, Object[] objArr2) {
                    return BatchWatermark.AnonymousClass2.lambda$beforeHookedMethod$0(obj, view, obj3, method, objArr2);
                }
            });
            Object[] objArr2 = methodHookParam.args;
            if (obj2 == null) {
                objNewProxyInstance = null;
            } else {
                ClassLoader classLoader = this.val$classLoader;
                Class[] clsArr = {this.val$function2};
                final Object obj3 = this.val$unit;
                objNewProxyInstance = Proxy.newProxyInstance(classLoader, clsArr, new InvocationHandler() { // from class: local.jiege.hook.gallery.d
                    @Override // java.lang.reflect.InvocationHandler
                    public final Object invoke(Object obj4, Method method, Object[] objArr3) {
                        return BatchWatermark.AnonymousClass2.lambda$beforeHookedMethod$1(obj2, view, obj3, obj4, method, objArr3);
                    }
                });
            }
            objArr2[2] = objNewProxyInstance;
        }
    }

    /* JADX INFO: renamed from: local.jiege.hook.gallery.BatchWatermark$4, reason: invalid class name */
    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public class AnonymousClass4 extends XC_MethodHook {
        /* JADX INFO: Access modifiers changed from: private */
        public static /* synthetic */ boolean lambda$afterHookedMethod$0(Activity activity) {
            BatchWatermark.keepWatermarkOnly(activity);
            return true;
        }

        public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            final Activity activity = (Activity) methodHookParam.thisObject;
            int iStep = BatchWatermark.step(activity);
            if (iStep < 0) {
                return;
            }
            BatchWatermark.current = new WeakReference(activity);
            BatchWatermark.confirmed = false;
            activity.getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() { // from class: local.jiege.hook.gallery.e
                @Override // android.view.ViewTreeObserver.OnPreDrawListener
                public final boolean onPreDraw() {
                    return BatchWatermark.AnonymousClass4.lambda$afterHookedMethod$0(activity);
                }
            });
            if (BatchWatermark.interactive(iStep)) {
                return;
            }
            BatchWatermark.MAIN.postDelayed(new b(activity, (byte) 1), BatchWatermark.SAVE_TIMEOUT_MS);
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Observed implements View.OnClickListener {
        final View.OnClickListener listener;

        public Observed(View.OnClickListener onClickListener) {
            this.listener = onClickListener;
        }

        @Override // android.view.View.OnClickListener
        public void onClick(View view) {
            BatchWatermark.observe(view);
            this.listener.onClick(view);
        }
    }

    private BatchWatermark() {
    }

    public static /* bridge */ /* synthetic */ void F(Activity activity) {
        giveUp(activity);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Activity activity(Context context) {
        while ((context instanceof ContextWrapper) && !(context instanceof Activity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof Activity) {
            return (Activity) context;
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void addItem(Context context, List<Object> list) throws NoSuchMethodException {
        int identifier = context.getResources().getIdentifier("base_adjust_sheet_title", "string", context.getPackageName());
        int i = -1;
        for (int i2 = 0; i2 < list.size(); i2++) {
            int iTitleOf = titleOf(list.get(i2));
            if (iTitleOf == TITLE_ID) {
                return;
            }
            if (iTitleOf == identifier) {
                i = i2 + 1;
            }
        }
        if (i < 0) {
            return;
        }
        Class<?> cls = list.get(0).getClass();
        Class cls2 = Integer.TYPE;
        Class cls3 = Boolean.TYPE;
        Constructor<?> declaredConstructor = cls.getDeclaredConstructor(cls2, cls3, cls3, cls3);
        declaredConstructor.setAccessible(true);
        Integer numValueOf = Integer.valueOf(TITLE_ID);
        Boolean bool = Boolean.FALSE;
        Boolean bool2 = Boolean.TRUE;
        list.add(i, declaredConstructor.newInstance(numValueOf, bool, bool2, bool2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void apply(Object obj, Activity activity, Method method, Method method2, Class<?> cls) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Object objInvoke;
        Object obj2;
        method.setAccessible(true);
        Object obj3 = chosenStyle;
        if (obj3 != null && (obj2 = chosenOperator) != null) {
            method.invoke(null, obj, styleId, obj3, obj2, null, 16);
            return;
        }
        Object objInvoke2 = method2.invoke(null, styleId);
        Constructor<?> constructor = cls.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object objNewInstance = constructor.newInstance(activity.getApplicationContext());
        Method[] declaredMethods = cls.getDeclaredMethods();
        int length = declaredMethods.length;
        int i = 0;
        while (true) {
            if (i >= length) {
                objInvoke = null;
                break;
            }
            Method method3 = declaredMethods[i];
            Class<?>[] parameterTypes = method3.getParameterTypes();
            if (parameterTypes.length == 3 && objInvoke2 != null && parameterTypes[0].isInstance(objInvoke2)) {
                Class<?> cls2 = parameterTypes[1];
                Class<?> cls3 = Boolean.TYPE;
                if (cls2 == cls3 && parameterTypes[2] == cls3 && method3.getReturnType().getName().equals("hx0")) {
                    method3.setAccessible(true);
                    objInvoke = method3.invoke(objNewInstance, objInvoke2, Boolean.FALSE, Boolean.TRUE);
                    break;
                }
            }
            i++;
        }
        if (objInvoke != null) {
            Object objCallMethod = XposedHelpers.callMethod(objInvoke, "getFirst", new Object[0]);
            Object objCallMethod2 = XposedHelpers.callMethod(objInvoke, "getSecond", new Object[0]);
            method.setAccessible(true);
            method.invoke(null, obj, styleId, objCallMethod, objCallMethod2, null, 16);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Method method4 : cls.getDeclaredMethods()) {
            sb.append(method4);
            sb.append("; ");
        }
        StringBuilder sb2 = new StringBuilder("style ");
        sb2.append(styleId);
        sb2.append(" did not load from ");
        sb2.append(objInvoke2);
        sb2.append(" (");
        sb2.append(objInvoke2 != null ? objInvoke2.getClass() : null);
        sb2.append("), loader ");
        sb2.append((Object) sb);
        throw new IllegalStateException(sb2.toString());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void choose(Activity activity, Object obj) {
        if (activity == null) {
            return;
        }
        if (running) {
            Toast.makeText(activity, "上一批水印还在处理", 0).show();
            return;
        }
        Object objSelection = selection(obj);
        List arrayList = objSelection == null ? new ArrayList() : imageUris(activity, XposedHelpers.callMethod(objSelection, "getSelectedItems", new Object[0]));
        if (arrayList.isEmpty()) {
            Toast.makeText(activity, "没有可编辑水印的照片", 0).show();
        } else {
            XposedHelpers.callMethod(objSelection, "exitSelectionMode", new Object[0]);
            start(activity, arrayList);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void clickNone(Activity activity, long j) {
        if (activity != current.get() || activity.isFinishing()) {
            return;
        }
        View viewFindViewById = activity.findViewById(id(activity, NONE));
        if (viewFindViewById != null && viewFindViewById.getWidth() > 0) {
            viewFindViewById.performClick();
        } else {
            if (System.currentTimeMillis() - j < SAVE_TIMEOUT_MS) {
                MAIN.postDelayed(new fd(activity, j, (byte) 1), 200L);
                return;
            }
            Log.i(TAG, "no 无 item: " + activity.getIntent().getData());
        }
    }

    private static Uri contentUri(Object obj) {
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (field.getType() == WeakReference.class) {
                try {
                    field.setAccessible(true);
                    Object obj2 = ((WeakReference) field.get(obj)).get();
                    if (obj2 != null) {
                        for (Class<?> superclass = obj2.getClass(); superclass != null; superclass = superclass.getSuperclass()) {
                            if (superclass.getName().endsWith(".MediaObject")) {
                                for (Method method : superclass.getDeclaredMethods()) {
                                    if (method.getReturnType() == Uri.class && method.getParameterTypes().length == 0) {
                                        method.setAccessible(true);
                                        return (Uri) method.invoke(obj2, null);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable th) {
                    Log.e(TAG, "content uri of " + obj + " failed", th);
                }
            }
        }
        return null;
    }

    private static int display(Context context) {
        VirtualDisplay virtualDisplayCreateVirtualDisplay = display;
        if (virtualDisplayCreateVirtualDisplay == null) {
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            frames = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, 1, 2);
            HandlerThread handlerThread = new HandlerThread("jiege-batch-frames");
            handlerThread.start();
            frames.setOnImageAvailableListener(new gd(), new Handler(handlerThread.getLooper()));
            virtualDisplayCreateVirtualDisplay = ((DisplayManager) context.getSystemService(DisplayManager.class)).createVirtualDisplay(DISPLAY_NAME, displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi, frames.getSurface(), 8);
            display = virtualDisplayCreateVirtualDisplay;
        }
        return virtualDisplayCreateVirtualDisplay.getDisplay().getDisplayId();
    }

    private static Object findSelection(Object obj, int i) {
        if (obj != null && i <= 2) {
            if (obj instanceof WeakReference) {
                return findSelection(((WeakReference) obj).get(), i);
            }
            if (hasSelectedItems(obj)) {
                return obj;
            }
            if (!obj.getClass().getName().startsWith("java.") && !obj.getClass().getName().startsWith("android.")) {
                for (Class<?> superclass = obj.getClass(); superclass != null && superclass != Object.class && !superclass.getName().startsWith("android.") && !superclass.getName().startsWith("androidx."); superclass = superclass.getSuperclass()) {
                    for (Field field : superclass.getDeclaredFields()) {
                        if (!field.getType().isPrimitive() && !Modifier.isStatic(field.getModifiers())) {
                            try {
                                field.setAccessible(true);
                                Object objFindSelection = findSelection(field.get(obj), 1 + i);
                                if (objFindSelection != null) {
                                    return objFindSelection;
                                }
                            } catch (Throwable unused) {
                                continue;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void giveUp(Activity activity) {
        if (activity != current.get() || activity.isFinishing()) {
            return;
        }
        Log.i(TAG, "no save within 20000 ms: " + activity.getIntent().getData());
        activity.finish();
    }

    private static boolean hasSelectedItems(Object obj) {
        for (Method method : obj.getClass().getMethods()) {
            if ("getSelectedItems".equals(method.getName()) && method.getParameterTypes().length == 0) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookEditor(ClassLoader classLoader) {
        final Class clsFindClass = XposedHelpers.findClass(EDITOR, classLoader);
        XposedHelpers.findAndHookMethod(clsFindClass, "onCreate", new Object[]{Bundle.class, new AnonymousClass4()});
        XposedHelpers.findAndHookMethod(clsFindClass, "onResume", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.5
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Activity activity = (Activity) methodHookParam.thisObject;
                if (BatchWatermark.step(activity) >= 0 && activity == BatchWatermark.current.get() && BatchWatermark.OPENED.add(activity)) {
                    BatchWatermark.watermarkOnly(activity, 0);
                }
            }
        }});
        XposedHelpers.findAndHookMethod(View.class, "performClick", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.6
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                BatchWatermark.observe((View) methodHookParam.thisObject);
            }
        }});
        XposedHelpers.findAndHookMethod(View.class, "setOnClickListener", new Object[]{View.OnClickListener.class, new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.7
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                View.OnClickListener onClickListener = (View.OnClickListener) methodHookParam.args[0];
                View view = (View) methodHookParam.thisObject;
                if (onClickListener == null || (onClickListener instanceof Observed) || !BatchWatermark.watched(view)) {
                    return;
                }
                methodHookParam.args[0] = new Observed(onClickListener);
            }
        }});
        XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", new Object[]{Intent.class, Integer.TYPE, Bundle.class, new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.8
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Activity activity = (Activity) methodHookParam.thisObject;
                Intent intent = (Intent) methodHookParam.args[0];
                if (!clsFindClass.isInstance(activity) || activity.getIntent() == null || activity.getIntent().getIntExtra(BatchWatermark.EXTRA_STEP, 0) <= 0) {
                    return;
                }
                if (intent == null || intent.getComponent() == null || !BatchWatermark.EDITOR.equals(intent.getComponent().getClassName())) {
                    Log.i(BatchWatermark.TAG, "batch editor kept from opening " + intent);
                    methodHookParam.setResult((Object) null);
                }
            }
        }});
        XposedHelpers.findAndHookMethod(Activity.class, "finish", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.9
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Activity activity = (Activity) methodHookParam.thisObject;
                if (activity == BatchWatermark.current.get() && clsFindClass.isInstance(activity)) {
                    BatchWatermark.current = new WeakReference(null);
                    int iStep = BatchWatermark.step(activity);
                    if (iStep < 0) {
                        return;
                    }
                    if (BatchWatermark.interactive(iStep) && (!BatchWatermark.confirmed || (BatchWatermark.styleId == null && !BatchWatermark.noneChosen))) {
                        Log.i(BatchWatermark.TAG, "batch left at the first photo, style " + BatchWatermark.styleId);
                        BatchWatermark.running = false;
                        return;
                    }
                    if (BatchWatermark.confirmed) {
                        BatchWatermark.saved++;
                    }
                    int i = iStep + 1;
                    if (i < BatchWatermark.queue.size()) {
                        BatchWatermark.notifyProgress(activity, i);
                    }
                    BatchWatermark.next(activity, i);
                }
            }
        }});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookMenu(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass(MENU_BAR, classLoader);
        Class<?> clsFindClass2 = XposedHelpers.findClass("kotlin.jvm.functions.Function1", classLoader);
        Class<?> clsFindClass3 = XposedHelpers.findClass("kotlin.jvm.functions.Function2", classLoader);
        Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("kotlin.Unit", classLoader), "INSTANCE");
        int i = 0;
        for (Method method : clsFindClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 3 && parameterTypes[0] == Integer.TYPE && parameterTypes[1] == clsFindClass2 && parameterTypes[2] == clsFindClass3) {
                XposedBridge.hookMethod(method, new AnonymousClass2(classLoader, clsFindClass2, clsFindClass3, staticObjectField));
                i++;
            }
        }
        if (i != 0) {
            return;
        }
        t2.j("no popup method on com.oplus.gallery.standard_lib.ui.bottomnavigation.BottomFloatingMenuBar");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookStyle(Symbols symbols) {
        Method method = symbols.method("MasterVM.applyStyle");
        Method method2 = symbols.method("StyleData.source");
        Class<?> declaringClass = symbols.method("StyleLoader.resolve").getDeclaringClass();
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.10
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Activity activity = (Activity) BatchWatermark.current.get();
                if (activity == null || !BatchWatermark.interactive(BatchWatermark.step(activity))) {
                    return;
                }
                BatchWatermark.styleId = (String) methodHookParam.args[1];
                BatchWatermark.chosenStyle = methodHookParam.args[2];
                BatchWatermark.chosenOperator = methodHookParam.args[3];
                BatchWatermark.noneChosen = false;
            }
        });
        XposedBridge.hookMethod(symbols.method("MasterVM.onImagePack"), new AnonymousClass11(method, method2, declaringClass));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookTitle() {
        XposedHelpers.findAndHookMethod(Resources.class, "getText", new Object[]{Integer.TYPE, new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (((Integer) methodHookParam.args[0]).intValue() == BatchWatermark.TITLE_ID) {
                    methodHookParam.setResult(BatchWatermark.TITLE);
                }
            }
        }});
    }

    private static int id(Activity activity, String str) {
        return activity.getResources().getIdentifier(str, "id", activity.getPackageName());
    }

    private static List<Uri> imageUris(Context context, Object obj) {
        ArrayList arrayList = new ArrayList();
        if (obj instanceof Collection) {
            for (Object obj2 : (Collection) obj) {
                String strValueOf = String.valueOf(obj2);
                if (!strValueOf.contains("video")) {
                    Uri uriContentUri = contentUri(obj2);
                    Log.i(TAG, "selected " + strValueOf + " -> " + uriContentUri);
                    if (uriContentUri != null && readable(context, uriContentUri)) {
                        arrayList.add(uriContentUri);
                    }
                }
            }
        }
        return arrayList;
    }

    public static void installGallery(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "menu title", new t2((byte) 3));
        Log.guard(TAG, "more menu item", new hd(classLoader, (byte) 1));
        Log.guard(TAG, "batch editors", new hd(classLoader, (byte) 2));
        Log.guard(TAG, "style capture and reapply", new id(symbols, (byte) 0));
    }

    public static void installSystem(ClassLoader classLoader) {
        Log.guard(TAG, "editor on the batch display", new hd(classLoader, (byte) 0));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean interactive(int i) {
        return i == 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void keepWatermarkOnly(Activity activity) {
        View viewFindViewById = activity.findViewById(id(activity, MENU_BAR_VIEW));
        if (viewFindViewById != null && viewFindViewById.getVisibility() != 4) {
            viewFindViewById.setVisibility(4);
        }
        View viewFindViewById2 = activity.findViewById(id(activity, SECONDARY_PANEL));
        int i = PANEL.contains(activity) ? 0 : 4;
        if (viewFindViewById2 == null || viewFindViewById2.getVisibility() == i) {
            return;
        }
        viewFindViewById2.setVisibility(i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$display$4(ImageReader imageReader) {
        Image imageAcquireLatestImage = imageReader.acquireLatestImage();
        if (imageAcquireLatestImage != null) {
            imageAcquireLatestImage.close();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installSystem$3(ClassLoader classLoader) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gallery.BatchWatermark.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                ApplicationInfo applicationInfo;
                Object objCallMethod;
                Boolean bool = Boolean.TRUE;
                if (bool.equals(methodHookParam.getResult())) {
                    return;
                }
                Object[] objArr = methodHookParam.args;
                ActivityInfo activityInfo = (ActivityInfo) objArr[3];
                int iIntValue = ((Integer) objArr[1]).intValue();
                if (activityInfo != null && BatchWatermark.EDITOR.equals(activityInfo.name) && (applicationInfo = activityInfo.applicationInfo) != null && applicationInfo.uid == iIntValue && (objCallMethod = XposedHelpers.callMethod(XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.hardware.display.DisplayManagerGlobal", (ClassLoader) null), "getInstance", new Object[0]), "getDisplayInfo", new Object[]{methodHookParam.args[2]})) != null && BatchWatermark.DISPLAY_NAME.equals(XposedHelpers.getObjectField(objCallMethod, "name")) && XposedHelpers.getIntField(objCallMethod, "ownerUid") == iIntValue) {
                    methodHookParam.setResult(bool);
                }
            }
        };
        Class cls = Integer.TYPE;
        XposedHelpers.findAndHookMethod("com.android.server.wm.ActivityTaskSupervisor", classLoader, "isCallerAllowedToLaunchOnDisplay", new Object[]{cls, cls, cls, ActivityInfo.class, xC_MethodHook});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$watermarkOnly$5(Activity activity, int i) {
        watermarkOnly(activity, i + 1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void next(Activity activity, int i) {
        if (i < queue.size()) {
            open(activity, i);
            return;
        }
        notifyDone(activity.getApplicationContext());
        running = false;
        releaseDisplay();
    }

    private static Notification.Builder notification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NotificationManager.class);
        if (notificationManager.getNotificationChannel(CHANNEL) == null) {
            notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL, TITLE, 2));
        }
        return new Notification.Builder(context, CHANNEL).setSmallIcon(context.getApplicationInfo().icon).setContentTitle(TITLE).setColor(GALLERY_BLUE).setOnlyAlertOnce(true);
    }

    private static void notifyDone(Context context) {
        String str;
        int size = queue.size() - saved;
        Notification.Builder builderNotification = notification(context);
        if (size == 0) {
            str = "已完成 " + saved + " 张";
        } else {
            str = "已完成 " + saved + " 张，" + size + " 张未能保存";
        }
        post(context, builderNotification.setContentText(str).setAutoCancel(true));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void notifyProgress(Context context, int i) {
        Notification.Builder style = notification(context).setStyle(new Notification.ProgressStyle().setStyledByProgress(false).setProgressTrackerIcon(Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))).setProgressSegments(segments(i)).setProgress(i));
        StringBuilder sb = new StringBuilder("已完成 ");
        sb.append(i);
        sb.append(" 张，共 ");
        List<Uri> list = queue;
        sb.append(list.size());
        sb.append(" 张");
        post(context, style.setContentText(sb.toString()).setShortCriticalText(i + "/" + list.size()).setRequestPromotedOngoing(true).setOngoing(true));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object objectMethod(Object obj, Method method, Object[] objArr) {
        String name = method.getName();
        name.getClass();
        if (name.equals("equals")) {
            return Boolean.valueOf(obj == objArr[0]);
        }
        return !name.equals("hashCode") ? TAG : Integer.valueOf(System.identityHashCode(obj));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void observe(View view) {
        Activity activity = current.get();
        if (activity == null || view.getId() == -1 || activity(view.getContext()) != activity) {
            return;
        }
        if (view.getId() == id(activity, "photoeditor_action_confirm")) {
            confirmed = true;
        }
        if (view.getId() == id(activity, NONE) && interactive(step(activity))) {
            noneChosen = true;
            styleId = null;
        }
    }

    private static void open(Activity activity, int i) {
        Intent intentPutExtra = new Intent("android.intent.action.EDIT").setDataAndType(queue.get(i), "image/*").setClassName(activity.getPackageName(), EDITOR).putExtra(EXTRA_STEP, i);
        if (interactive(i)) {
            activity.startActivity(intentPutExtra);
            return;
        }
        try {
            ActivityOptions launchDisplayId = ActivityOptions.makeBasic().setLaunchDisplayId(display(activity));
            intentPutExtra.addFlags(402653184);
            activity.startActivity(intentPutExtra, launchDisplayId.toBundle());
        } catch (Throwable th) {
            Log.e(TAG, "editor did not open on the hidden display", th);
            running = false;
            releaseDisplay();
            post(activity, notification(activity).setContentText("批量编辑水印中断：" + saved + " / " + queue.size() + " 张已完成").setAutoCancel(true));
        }
    }

    private static void post(Context context, Notification.Builder builder) {
        try {
            ((NotificationManager) context.getSystemService(NotificationManager.class)).notify(NOTIFICATION, builder.build());
        } catch (Throwable th) {
            Log.e(TAG, "progress notification failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void pressSave(Activity activity, long j) {
        if (activity != current.get() || activity.isFinishing() || confirmed) {
            return;
        }
        View viewFindViewById = activity.findViewById(id(activity, "photoeditor_action_confirm"));
        if (viewFindViewById != null && viewFindViewById.isEnabled() && viewFindViewById.isShown()) {
            viewFindViewById.performClick();
        } else if (System.currentTimeMillis() - j < SAVE_TIMEOUT_MS) {
            MAIN.postDelayed(new fd(activity, j, (byte) 0), 300L);
        }
    }

    private static boolean readable(Context context, Uri uri) {
        try {
            ParcelFileDescriptor parcelFileDescriptorOpenFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            boolean z = parcelFileDescriptorOpenFileDescriptor != null;
            if (parcelFileDescriptorOpenFileDescriptor != null) {
                parcelFileDescriptorOpenFileDescriptor.close();
            }
            return z;
        } catch (Throwable unused) {
            Log.i(TAG, "skipped, not readable: " + uri);
            return false;
        }
    }

    private static void releaseDisplay() {
        VirtualDisplay virtualDisplay = display;
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        ImageReader imageReader = frames;
        if (imageReader != null) {
            imageReader.close();
        }
        display = null;
        frames = null;
    }

    private static List<Notification.ProgressStyle.Segment> segments(int i) {
        ArrayList arrayList = new ArrayList();
        if (i > 0) {
            arrayList.add(ed.c(i).setColor(GALLERY_BLUE));
        }
        List<Uri> list = queue;
        if (i < list.size()) {
            arrayList.add(ed.c(list.size() - i));
        }
        return arrayList;
    }

    private static Object selection(Object obj) {
        Object objFindSelection = findSelection(obj, 0);
        if (objFindSelection == null) {
            StringBuilder sb = new StringBuilder();
            for (Field field : obj.getClass().getDeclaredFields()) {
                sb.append(field.getType().getName());
                sb.append(' ');
            }
            Log.i(TAG, "no selection in " + obj.getClass().getName() + ": " + ((Object) sb));
        }
        return objFindSelection;
    }

    private static void start(Activity activity, List<Uri> list) {
        List<Uri> list2 = queue;
        list2.clear();
        list2.addAll(list);
        running = true;
        styleId = null;
        chosenStyle = null;
        chosenOperator = null;
        noneChosen = false;
        saved = 0;
        open(activity, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int step(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !running) {
            return -1;
        }
        return intent.getIntExtra(EXTRA_STEP, -1);
    }

    private static void tap(View view) {
        long jUptimeMillis = SystemClock.uptimeMillis();
        float width = view.getWidth() / 2.0f;
        float height = view.getHeight() / 2.0f;
        MotionEvent motionEventObtain = MotionEvent.obtain(jUptimeMillis, jUptimeMillis, 0, width, height, 0);
        MotionEvent motionEventObtain2 = MotionEvent.obtain(jUptimeMillis, 50 + jUptimeMillis, 1, width, height, 0);
        view.dispatchTouchEvent(motionEventObtain);
        view.dispatchTouchEvent(motionEventObtain2);
        motionEventObtain.recycle();
        motionEventObtain2.recycle();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int titleOf(Object obj) {
        if (obj == null) {
            return 0;
        }
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (field.getType() == Integer.TYPE && !Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                    return field.getInt(obj);
                } catch (IllegalAccessException unused) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean watched(View view) {
        int id = view.getId();
        if (id == -1) {
            return false;
        }
        if (confirmId == 0) {
            Resources resources = view.getResources();
            String packageName = view.getContext().getPackageName();
            confirmId = resources.getIdentifier("photoeditor_action_confirm", "id", packageName);
            noneId = resources.getIdentifier(NONE, "id", packageName);
        }
        return id == confirmId || id == noneId;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void watermarkOnly(Activity activity, int i) {
        if (activity != current.get() || activity.isFinishing()) {
            return;
        }
        View viewFindViewById = activity.findViewById(id(activity, NONE));
        if (viewFindViewById != null && viewFindViewById.getWidth() > 0) {
            PANEL.add(activity);
            keepWatermarkOnly(activity);
            return;
        }
        if (i >= 100) {
            Log.i(TAG, "watermark panel did not open");
            PANEL.add(activity);
            keepWatermarkOnly(activity);
        } else {
            View viewFindViewById2 = activity.findViewById(id(activity, WATERMARK_MENU));
            if (viewFindViewById2 != null && viewFindViewById2.getWidth() > 0 && i % 3 == 0) {
                tap(viewFindViewById2);
            }
            MAIN.postDelayed(new na(activity, i, (byte) 1), 100L);
        }
    }
}
