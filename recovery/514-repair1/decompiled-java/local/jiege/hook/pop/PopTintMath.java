package local.jiege.hook.pop;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class PopTintMath {
    private PopTintMath() {
    }

    public static float[] apply(float[] fArr, int i) {
        if (fArr != null && i != 0 && fArr.length % 3 == 0) {
            float fMax = (Math.max(-100, Math.min(100, i)) / 100.0f) * 0.16f;
            float f = fMax + 1.0f;
            float f2 = 1.0f - fMax;
            float f3 = (0.691738f * f2) + (0.308262f * f);
            float f4 = f / f3;
            float f5 = f2 / f3;
            fArr = (float[]) fArr.clone();
            for (int i2 = 0; i2 < fArr.length; i2 += 3) {
                fArr[i2] = clamp(fArr[i2] * f4);
                int i3 = i2 + 1;
                fArr[i3] = clamp(fArr[i3] * f5);
                int i4 = i2 + 2;
                fArr[i4] = clamp(fArr[i4] * f4);
            }
        }
        return fArr;
    }

    private static float clamp(float f) {
        return Math.max(0.0f, Math.min(1.0f, f));
    }
}
