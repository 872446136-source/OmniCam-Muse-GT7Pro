package local.jiege.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import defpackage.c3;
import defpackage.ib;
import defpackage.v2;
import defpackage.vl0;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterFilterProvider extends ContentProvider {
    @Override // android.content.ContentProvider
    public final Bundle call(String str, String str2, Bundle bundle) {
        String strWrite;
        str.getClass();
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.myUid()) {
            Context context = getContext();
            context.getClass();
            String[] packagesForUid = context.getPackageManager().getPackagesForUid(callingUid);
            if (packagesForUid == null) {
                packagesForUid = new String[0];
            }
            if (ib.l0(packagesForUid, "com.oplus.camera") < 0) {
                throw new SecurityException(c3.f(callingUid, "Not allowed: uid "));
            }
        }
        v2 v2Var = v2.U;
        Context context2 = getContext();
        context2.getClass();
        vl0 vl0VarN = v2Var.n(context2);
        if (!str.equals("config")) {
            return null;
        }
        Bundle bundle2 = new Bundle();
        synchronized (vl0VarN) {
            strWrite = vl0VarN.b().write();
        }
        bundle2.putString("text", strWrite);
        return bundle2;
    }

    @Override // android.content.ContentProvider
    public final int delete(Uri uri, String str, String[] strArr) {
        uri.getClass();
        return 0;
    }

    @Override // android.content.ContentProvider
    public final String getType(Uri uri) {
        uri.getClass();
        return null;
    }

    @Override // android.content.ContentProvider
    public final Uri insert(Uri uri, ContentValues contentValues) {
        uri.getClass();
        return null;
    }

    @Override // android.content.ContentProvider
    public final boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public final Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        uri.getClass();
        return null;
    }

    @Override // android.content.ContentProvider
    public final int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        uri.getClass();
        return 0;
    }
}
