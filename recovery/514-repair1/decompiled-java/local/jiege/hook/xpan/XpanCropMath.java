package local.jiege.hook.xpan;

import defpackage.t2;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class XpanCropMath {
    private XpanCropMath() {
    }

    public static int[] region(int i, int i2, boolean z) {
        if (i < 2 || i2 < 2) {
            t2.m("Invalid picture size");
            return null;
        }
        int i3 = i & (-2);
        int i4 = i2 & (-2);
        int i5 = z ? 24 : 65;
        long j = z ? 65 : 24;
        long j2 = ((long) i3) * j;
        long j3 = i5;
        long j4 = ((long) i4) * j3;
        if (j2 > j4) {
            i3 = ((int) (j4 / j)) & (-2);
        } else {
            i4 = ((int) (j2 / j3)) & (-2);
        }
        if (i3 >= 2 && i4 >= 2) {
            return new int[]{((i - i3) / 2) & (-2), ((i2 - i4) / 2) & (-2), i3, i4};
        }
        t2.m("Picture too small for XPAN");
        return null;
    }
}
