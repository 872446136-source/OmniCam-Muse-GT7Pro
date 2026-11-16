package local.jiege.hook.xpan;

/** Centre crop in delivered image coordinates, aligned for YUV 4:2:0. */
public final class XpanCropMath {
    private XpanCropMath() {}

    public static int[] region(int width, int height, boolean portrait) {
        if (width < 2 || height < 2) throw new IllegalArgumentException("Invalid picture size");
        int w = width & ~1, h = height & ~1;
        int numerator = portrait ? 24 : 65;
        int denominator = portrait ? 65 : 24;
        if ((long) w * denominator > (long) h * numerator) {
            w = (int) ((long) h * numerator / denominator) & ~1;
        } else {
            h = (int) ((long) w * denominator / numerator) & ~1;
        }
        if (w < 2 || h < 2) throw new IllegalArgumentException("Picture too small for XPAN");
        return new int[] {((width - w) / 2) & ~1, ((height - h) / 2) & ~1, w, h};
    }
}
