package local.jiege.hook.gallery;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import defpackage.s2;
import defpackage.t2;
import defpackage.u2;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class AddressNavigation {
    private static final String AUTO_LAUNCH_SINGLE_CHOICE = "android.intent.extra.AUTO_LAUNCH_SINGLE_CHOICE";
    private static final String TAG = "AddressNavigation";
    private static int addressId;

    /* JADX INFO: renamed from: local.jiege.hook.gallery.AddressNavigation$1, reason: invalid class name */
    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public class AnonymousClass1 extends XC_MethodHook {
        public static final /* synthetic */ int a = 0;

        public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            TextView textView = (TextView) methodHookParam.thisObject;
            if (textView.getId() == -1 || textView.isClickable()) {
                return;
            }
            if (AddressNavigation.addressId == 0) {
                AddressNavigation.addressId = AddressNavigation.f(textView);
            }
            if (textView.getId() != AddressNavigation.addressId) {
                return;
            }
            textView.setClickable(true);
            textView.setOnClickListener(new a());
        }
    }

    private AddressNavigation() {
    }

    public static /* bridge */ /* synthetic */ int f(TextView textView) {
        return id(textView, "tv_address");
    }

    private static String fileName(View view) {
        View viewFindViewById;
        int iId = id(view, "tv_full_name");
        do {
            ViewParent parent = view.getParent();
            if (!(parent instanceof ViewGroup)) {
                return null;
            }
            view = (ViewGroup) parent;
            viewFindViewById = view.findViewById(iId);
        } while (!(viewFindViewById instanceof TextView));
        return ((TextView) viewFindViewById).getText().toString();
    }

    private static int id(View view, String str) {
        return view.getResources().getIdentifier(str, "id", view.getContext().getPackageName());
    }

    public static void installGallery(ClassLoader classLoader) {
        Log.guard(TAG, "address opens the map", new t2((byte) 0));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installGallery$0() {
        XposedHelpers.findAndHookMethod(TextView.class, "setText", new Object[]{CharSequence.class, TextView.BufferType.class, Boolean.TYPE, Integer.TYPE, new AnonymousClass1()});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$open$1(Context context, Intent intent) {
        try {
            context.startActivity(intent.addFlags(268435456));
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "map app did not open", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$open$2(String str, Context context, String str2) {
        double[] dArrLocation = str == null ? null : location(context, str);
        if (dArrLocation != null) {
            List<Intent> listMapIntents = mapIntents(dArrLocation[0], dArrLocation[1], str2);
            new Handler(Looper.getMainLooper()).post(new s2(context, Intent.createChooser(listMapIntents.remove(0), "打开地图").putExtra("android.intent.extra.INITIAL_INTENTS", (Parcelable[]) listMapIntents.toArray(new Intent[0])).putExtra(AUTO_LAUNCH_SINGLE_CHOICE, true), (byte) 0));
        } else {
            Log.i(TAG, "no coordinates for " + str);
        }
    }

    private static double[] location(Context context, String str) {
        Uri contentUri = MediaStore.Files.getContentUri("external");
        try {
            Cursor cursorQuery = context.getContentResolver().query(contentUri, new String[]{"_id"}, "_display_name = ? COLLATE NOCASE", new String[]{str}, "date_modified DESC");
            while (cursorQuery != null) {
                try {
                    if (!cursorQuery.moveToNext()) {
                        break;
                    }
                    try {
                        InputStream inputStreamOpenInputStream = context.getContentResolver().openInputStream(MediaStore.setRequireOriginal(Uri.withAppendedPath(contentUri, String.valueOf(cursorQuery.getLong(0)))));
                        if (inputStreamOpenInputStream != null) {
                            try {
                                float[] fArr = new float[2];
                                if (new ExifInterface(inputStreamOpenInputStream).getLatLong(fArr)) {
                                    double[] dArr = {fArr[0], fArr[1]};
                                    inputStreamOpenInputStream.close();
                                    cursorQuery.close();
                                    return dArr;
                                }
                            } catch (Throwable th) {
                                try {
                                    inputStreamOpenInputStream.close();
                                    throw th;
                                } catch (Throwable th2) {
                                    th.addSuppressed(th2);
                                    throw th;
                                }
                            }
                        } else if (inputStreamOpenInputStream != null) {
                        }
                        inputStreamOpenInputStream.close();
                    } catch (Exception e) {
                        Log.e(TAG, "EXIF of " + str + " unreadable", e);
                    }
                } catch (Throwable th3) {
                    try {
                        cursorQuery.close();
                        throw th3;
                    } catch (Throwable th4) {
                        th3.addSuppressed(th4);
                        throw th3;
                    }
                }
                Log.e(TAG, "media lookup of " + str + " failed", e);
                return null;
            }
            if (cursorQuery == null) {
                return null;
            }
            cursorQuery.close();
            return null;
        } catch (Exception e2) {
            Log.e(TAG, "media lookup of " + str + " failed", e2);
            return null;
        }
    }

    private static List<Intent> mapIntents(double d, double d2, String str) {
        Locale locale = Locale.ROOT;
        String str2 = String.format(locale, "%.7f", Double.valueOf(d));
        String str3 = String.format(locale, "%.7f", Double.valueOf(d2));
        String strEncode = Uri.encode(str);
        String[] strArr = {"com.autonavi.minimap", c3.l(c3.n("androidamap://viewMap?sourceApplication=OmniCam&poiname=", strEncode, "&lat=", str2, "&lon="), str3, "&dev=1")};
        StringBuilder sbN = c3.n("baidumap://map/marker?location=", str2, ",", str3, "&title=");
        sbN.append(strEncode);
        sbN.append("&content=");
        sbN.append(strEncode);
        sbN.append("&coord_type=wgs84&src=local.jiege.camera");
        String[][] strArr2 = {strArr, new String[]{"com.baidu.BaiduMap", sbN.toString()}, new String[]{"com.tencent.map", c3.l(c3.n("qqmap://map/marker?marker=coord:", str2, ",", str3, ";title:"), strEncode, "&coord_type=1&referer=OmniCam")}};
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < 3; i++) {
            String[] strArr3 = strArr2[i];
            arrayList.add(new Intent("android.intent.action.VIEW", Uri.parse(strArr3[1])).setPackage(strArr3[0]));
        }
        return arrayList;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void open(View view) {
        new Thread(new u2(fileName(view), view.getContext(), ((TextView) view).getText().toString()), TAG).start();
    }
}
