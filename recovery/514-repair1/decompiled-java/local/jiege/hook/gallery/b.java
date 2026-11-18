package local.jiege.hook.gallery;

import android.app.Activity;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class b implements Runnable {
    public final /* synthetic */ byte e;
    public final /* synthetic */ Activity f;

    public /* synthetic */ b(Activity activity, byte b) {
        this.e = b;
        this.f = activity;
    }

    @Override // java.lang.Runnable
    public final void run() {
        byte b = this.e;
        Activity activity = this.f;
        switch (b) {
            case 0:
                BatchWatermark.AnonymousClass11.lambda$afterHookedMethod$0(activity);
                break;
            default:
                BatchWatermark.F(activity);
                break;
        }
    }
}
