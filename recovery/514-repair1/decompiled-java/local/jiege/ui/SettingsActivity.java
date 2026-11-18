package local.jiege.ui;

import android.R;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import defpackage.dw;
import defpackage.ew;
import defpackage.fw;
import defpackage.gw;
import defpackage.hi1;
import defpackage.hw;
import defpackage.if1;
import defpackage.ig1;
import defpackage.iw;
import defpackage.jw;
import defpackage.kb1;
import defpackage.kw;
import defpackage.lj;
import defpackage.mj;
import defpackage.rb1;
import defpackage.sk;
import defpackage.we1;
import defpackage.wj;
import defpackage.xe1;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class SettingsActivity extends lj {
    public static final /* synthetic */ int x = 0;

    @Override // defpackage.lj, defpackage.kj, android.app.Activity
    public final void onCreate(Bundle bundle) {
        byte b = 3;
        byte b2 = 0;
        hi1 hi1Var = new hi1(0, 0, new if1(b));
        hi1 hi1Var2 = new hi1(fw.a, fw.b, new if1(b));
        View decorView = getWindow().getDecorView();
        decorView.getClass();
        hw iwVar = fw.c;
        if (iwVar == null) {
            int i = Build.VERSION.SDK_INT;
            if (i >= 35) {
                iwVar = new kw();
            } else if (i >= 30) {
                iwVar = new jw();
            } else {
                iwVar = i >= 29 ? new iw() : new hw();
            }
            fw.c = iwVar;
        }
        hw hwVar = iwVar;
        dw dwVar = new dw(hwVar, hi1Var, hi1Var2, this, decorView);
        ViewGroup viewGroup = (ViewGroup) decorView;
        int i2 = 0;
        while (true) {
            if (i2 >= viewGroup.getChildCount()) {
                ew ewVar = new ew(dwVar, viewGroup.getContext());
                ewVar.setTag(hwVar);
                ewVar.setVisibility(8);
                ewVar.setWillNotDraw(true);
                viewGroup.addView(ewVar);
                break;
            }
            int i3 = i2 + 1;
            View childAt = viewGroup.getChildAt(i2);
            if (childAt == null) {
                throw new IndexOutOfBoundsException();
            }
            if (childAt.getTag() instanceof gw) {
                break;
            } else {
                i2 = i3;
            }
        }
        dwVar.run();
        Window window = getWindow();
        window.getClass();
        hwVar.a(window);
        super.onCreate(bundle);
        wj wjVar = new wj(-2116686207, true, new kb1(new rb1(this), b2));
        ViewGroup.LayoutParams layoutParams = mj.a;
        View childAt2 = ((ViewGroup) getWindow().getDecorView().findViewById(R.id.content)).getChildAt(0);
        sk skVar = childAt2 instanceof sk ? (sk) childAt2 : null;
        if (skVar != null) {
            skVar.setParentCompositionContext(null);
            skVar.setContent(wjVar);
            return;
        }
        sk skVar2 = new sk(this);
        skVar2.setParentCompositionContext(null);
        skVar2.setContent(wjVar);
        View decorView2 = getWindow().getDecorView();
        if (ig1.f(decorView2) == null) {
            decorView2.setTag(local.jiege.camera.R.id.view_tree_lifecycle_owner, this);
        }
        if (xe1.g(decorView2) == null) {
            decorView2.setTag(local.jiege.camera.R.id.view_tree_view_model_store_owner, this);
        }
        if (we1.f(decorView2) == null) {
            decorView2.setTag(local.jiege.camera.R.id.view_tree_saved_state_registry_owner, this);
        }
        setContentView(skVar2, mj.a);
    }
}
