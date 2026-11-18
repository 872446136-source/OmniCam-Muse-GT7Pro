package local.jiege.hook.x10;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PaletteCaptureAlgorithms {
    static final String PALETTE = "aps_algo_color_palette";

    private PaletteCaptureAlgorithms() {
    }

    public static String[] include(String[] strArr) {
        int length = strArr.length;
        for (int i = 0; i < strArr.length; i++) {
            if (PALETTE.equals(strArr[i])) {
                return strArr;
            }
            if (length == strArr.length && ("aps_algo_face_info".equals(strArr[i]) || "aps_algo_post_proc".equals(strArr[i]))) {
                length = i;
            }
        }
        String[] strArr2 = new String[strArr.length + 1];
        System.arraycopy(strArr, 0, strArr2, 0, length);
        strArr2[length] = PALETTE;
        System.arraycopy(strArr, length, strArr2, length + 1, strArr.length - length);
        return strArr2;
    }
}
