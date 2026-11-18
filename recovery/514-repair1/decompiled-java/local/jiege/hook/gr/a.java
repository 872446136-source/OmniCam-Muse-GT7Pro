package local.jiege.hook.gr;

import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class a implements Log.Installer {
    public final /* synthetic */ byte e;
    public final /* synthetic */ Symbols f;
    public final /* synthetic */ ClassLoader g;

    public /* synthetic */ a(Symbols symbols, ClassLoader classLoader, byte b) {
        this.e = b;
        this.f = symbols;
        this.g = classLoader;
    }

    @Override // local.jiege.hook.common.Log.Installer
    public final void install() {
        switch (this.e) {
            case 0:
                GrCameraWatermark.hookStoredValues(this.f, this.g);
                break;
            case 1:
                GrCameraWatermark.hookSettingsSummary(this.f, this.g);
                break;
            case 2:
                GrEffectTransport.hookPreviewLut(this.f, this.g);
                break;
            case 3:
                GrEffectTransport.hookCaptureStage(this.f, this.g);
                break;
            case 4:
                GrEffectTransport.hookSdkRequest(this.f, this.g);
                break;
            case 5:
                GrModeHooks.lambda$install$1(this.f, this.g);
                break;
            default:
                GrSnapFocus.hookMenuClick(this.f, this.g);
                break;
        }
    }
}
