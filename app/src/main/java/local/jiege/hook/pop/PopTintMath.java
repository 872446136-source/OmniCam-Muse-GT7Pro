package local.jiege.hook.pop;

/** Green/magenta balance for the GT7 Pro preview LUT, whose CWCM tables contain neutral curves. */
public final class PopTintMath {
    private PopTintMath() {}

    public static float[] apply(float[] input, int value) {
        if (input == null || value == 0 || input.length % 3 != 0) return input;
        float t = Math.max(-100, Math.min(100, value)) / 100f;
        // Opposed channel gains preserve the neutral's P3 luminance before gamut clipping.
        float rb = 1f + 0.16f * t;
        float green = 1f - 0.16f * t;
        float luminance = 0.308262f * rb + 0.691738f * green;
        rb /= luminance;
        green /= luminance;
        float[] output = input.clone();
        for (int i = 0; i < output.length; i += 3) {
            output[i] = clamp(output[i] * rb);
            output[i + 1] = clamp(output[i + 1] * green);
            output[i + 2] = clamp(output[i + 2] * rb);
        }
        return output;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
