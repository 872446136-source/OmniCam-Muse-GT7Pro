import java.util.Arrays;
import java.util.Random;
import local.jiege.hook.xpan.XpanCropMath;

public final class XpanCropOfflineTest {
    private static void expect(int w, int h, boolean portrait, int... expected) {
        int[] actual = XpanCropMath.region(w, h, portrait);
        if (!Arrays.equals(actual, expected)) throw new AssertionError(Arrays.toString(actual));
    }

    public static void main(String[] args) {
        // Actual full photo and quick-preview sizes recorded in the 511 device session.
        expect(3072, 4096, true, 780, 0, 1512, 4096);
        expect(1368, 3696, true, 2, 0, 1364, 3696);
        expect(410, 1108, true, 0, 0, 408, 1108);
        expect(4096, 3072, false, 0, 780, 4096, 1512);
        expect(8000, 3636, false, 0, 342, 8000, 2952);
        // Orientation comes from the SDK's chosen getter, not from width > height.
        expect(3072, 4096, false, 0, 1480, 3072, 1134);
        expect(4097, 3073, false, 0, 780, 4096, 1512);
        for (int[] bad : new int[][] {{0, 10}, {-1, 10}, {2, 2}, {1, 8000}}) {
            try {
                XpanCropMath.region(bad[0], bad[1], false);
                throw new AssertionError("Accepted invalid input");
            } catch (IllegalArgumentException expected) { }
        }
        Random random = new Random(512);
        for (int i = 0; i < 20000; i++) {
            int w = 8 + random.nextInt(Integer.MAX_VALUE - 8);
            int h = 8 + random.nextInt(Integer.MAX_VALUE - 8);
            boolean portrait = random.nextBoolean();
            int[] crop = XpanCropMath.region(w, h, portrait);
            for (int value : crop) if (value < 0 || (value & 1) != 0) throw new AssertionError("YUV alignment");
            if ((long) crop[0] + crop[2] > w || (long) crop[1] + crop[3] > h)
                throw new AssertionError("Crop outside delivered buffer");
            if (Math.abs((long) w - crop[2] - 2L * crop[0]) > 3
                    || Math.abs((long) h - crop[3] - 2L * crop[1]) > 3)
                throw new AssertionError("Off-centre crop");
            long error = Math.abs((long) crop[2] * (portrait ? 65 : 24)
                    - (long) crop[3] * (portrait ? 24 : 65));
            if (error >= 130) throw new AssertionError("Incorrect XPAN aspect ratio");
        }
        System.out.println("XPAN: recorded frames, orientation, odd sizes and 20000 bounded/aligned crop checks passed");
    }
}
