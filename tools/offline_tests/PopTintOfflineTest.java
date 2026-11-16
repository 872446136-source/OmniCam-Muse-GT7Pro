import java.util.Arrays;
import local.jiege.hook.pop.PopTintMath;

public final class PopTintOfflineTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        float[] neutral = {0.4f, 0.4f, 0.4f};
        check(PopTintMath.apply(neutral, 0) == neutral, "zero must leave the native LUT untouched");
        float[] positive = PopTintMath.apply(neutral, 100);
        float[] negative = PopTintMath.apply(neutral, -100);
        check(positive[0] > positive[1] && positive[2] > positive[1], "positive must move toward magenta");
        check(negative[0] < negative[1] && negative[2] < negative[1], "negative must move toward green");
        check(Arrays.equals(neutral, new float[]{0.4f, 0.4f, 0.4f}), "native cached table must not be mutated");
        for (int value = -100; value <= 100; value++) {
            float[] out = PopTintMath.apply(neutral, value);
            double y = out[0] * 0.228974 + out[1] * 0.691738 + out[2] * 0.079288;
            check(Math.abs(y - 0.4) < 1e-6, "tint must not act as another brightness slider");
            for (int i = 0; i <= 255; i++) {
                float v = i / 255f;
                for (float f : PopTintMath.apply(new float[]{v, 1-v, v}, value)) {
                    check(Float.isFinite(f) && f >= 0 && f <= 1, "finite in-gamut result");
                }
            }
        }
        check(Arrays.equals(PopTintMath.apply(neutral, Integer.MAX_VALUE), positive), "upper clamp");
        check(Arrays.equals(PopTintMath.apply(neutral, Integer.MIN_VALUE), negative), "lower clamp");
        System.out.println("POP tint: neutral, endpoints, luminance, cached-input isolation and 51,456 range checks passed");
    }
}
