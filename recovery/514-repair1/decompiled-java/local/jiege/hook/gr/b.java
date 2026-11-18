package local.jiege.hook.gr;

import defpackage.ll;
import defpackage.xe1;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class b implements Log.Installer {
    public final /* synthetic */ byte e;
    public final /* synthetic */ Object f;

    public /* synthetic */ b(Object obj, byte b) {
        this.e = b;
        this.f = obj;
    }

    @Override // local.jiege.hook.common.Log.Installer
    public final void install() {
        byte b = this.e;
        Object obj = this.f;
        switch (b) {
            case 0:
                GrCameraWatermark.lambda$install$1((Symbols) obj);
                break;
            case 1:
                GrCameraWatermark.lambda$install$2((Symbols) obj);
                break;
            case 2:
                GrFocalLength.lambda$install$0((Symbols) obj);
                break;
            case 3:
                GrFocalLength.lambda$install$8((Symbols) obj);
                break;
            case 4:
                GrFocalLength.lambda$install$9((Symbols) obj);
                break;
            case 5:
                GrFocalLength.hookZoomToMillimetres((Symbols) obj);
                break;
            case 6:
                GrFocalLength.lambda$install$4((Symbols) obj);
                break;
            case 7:
                GrFocalLength.lambda$install$5((Symbols) obj);
                break;
            case 8:
                GrFocalLength.lambda$install$6((Symbols) obj);
                break;
            case ll.a /* 9 */:
                GrModeHooks.hookTouchDownCapture((Symbols) obj);
                break;
            case ll.b /* 10 */:
                GrModeHooks.hookMasterEffectBranch((Symbols) obj);
                break;
            case 11:
                GrModeHooks.hookEffectPanelLifecycle((Symbols) obj);
                break;
            case ll.c /* 12 */:
                GrMasterEffectEntry.install((Symbols) obj);
                break;
            case 13:
                GrModeArrange.install((Symbols) obj);
                break;
            case 14:
                GrRatioGuard.lambda$install$0((Symbols) obj);
                break;
            case xe1.e /* 15 */:
                GrRatioGuard.lambda$install$1((Symbols) obj);
                break;
            case 16:
                GrRatioGuard.hookStoredRatio((Symbols) obj);
                break;
            case 17:
                GrSnapFocus.hookCaptureStage((Symbols) obj);
                break;
            case 18:
                GrSnapFocus.hookAutofocusMode((Symbols) obj);
                break;
            default:
                GrModeHooks.hookStyleFeature((ClassLoader) obj);
                break;
        }
    }
}
