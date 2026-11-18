package local.jiege.hook.watermark;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class JpegModelPatcher {
    private static final int TAG_MODEL = 272;
    private static final int TYPE_ASCII = 2;

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Result {
        public int appended;
        public boolean exif;
        public String original;
        public boolean tooLong;

        public boolean changed() {
            return this.exif || this.appended > 0;
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Tiff {
        final ByteBuffer buffer;
        final int length;
        final int start;

        private Tiff(ByteBuffer byteBuffer, int i, int i2) {
            this.buffer = byteBuffer;
            this.start = i;
            this.length = i2;
        }

        public static Tiff find(byte[] bArr, int i) {
            ByteOrder byteOrder;
            if (i < 0 || bArr.length < i + 20 || (bArr[i] & 255) != 255 || (bArr[i + 1] & 255) != 216 || (bArr[i + JpegModelPatcher.TYPE_ASCII] & 255) != 255 || (bArr[i + 3] & 255) != 225) {
                return null;
            }
            int i2 = ((bArr[i + 4] & 255) << 8) | (255 & bArr[i + 5]);
            if (bArr[i + 6] != 69 || bArr[i + 7] != 120 || bArr[i + 8] != 105 || bArr[i + 9] != 102 || bArr[i + 10] != 0 || bArr[i + 11] != 0) {
                return null;
            }
            int i3 = i + 12;
            int i4 = i2 - 8;
            if (i4 < 8 || i3 + i4 > bArr.length) {
                return null;
            }
            byte b = bArr[i3];
            if (b == 77 && bArr[i + 13] == 77) {
                byteOrder = ByteOrder.BIG_ENDIAN;
            } else {
                byteOrder = (b == 73 && bArr[i + 13] == 73) ? ByteOrder.LITTLE_ENDIAN : null;
            }
            if (byteOrder == null) {
                return null;
            }
            return new Tiff(ByteBuffer.wrap(bArr).order(byteOrder), i3, i4);
        }

        public int modelEntry() {
            int i;
            int i2 = this.start;
            int i3 = this.buffer.getInt(i2 + 4) + i2;
            int i4 = this.start;
            if (i3 >= i4 && (i = i3 + JpegModelPatcher.TYPE_ASCII) <= i4 + this.length) {
                int i5 = this.buffer.getShort(i3) & 65535;
                for (int i6 = 0; i6 < i5; i6++) {
                    int i7 = (i6 * 12) + i;
                    if (i7 + 12 > this.start + this.length) {
                        return -1;
                    }
                    if ((this.buffer.getShort(i7) & 65535) == JpegModelPatcher.TAG_MODEL && (this.buffer.getShort(i7 + JpegModelPatcher.TYPE_ASCII) & 65535) == JpegModelPatcher.TYPE_ASCII) {
                        return i7;
                    }
                }
            }
            return -1;
        }
    }

    private JpegModelPatcher() {
    }

    public static Result patch(byte[] bArr, int i, int i2, String str, String str2) {
        Result result = new Result();
        Charset charset = StandardCharsets.UTF_8;
        byte[] bytes = str2.getBytes(charset);
        String model = readModel(bArr, i);
        if (model != null && !model.isEmpty()) {
            str = model;
        }
        result.original = str;
        if (str != null && !str.isEmpty() && !str.equals(str2)) {
            if (model != null && !model.isEmpty()) {
                int iPatchModelTag = patchModelTag(bArr, i, bytes);
                if (iPatchModelTag == -1) {
                    result.tooLong = true;
                } else if (iPatchModelTag == 1) {
                    result.exif = true;
                }
            }
            result.appended = patchAppended(bArr, i, i2, str.getBytes(charset), bytes);
        }
        return result;
    }

    private static int patchAppended(byte[] bArr, int i, int i2, byte[] bArr2, byte[] bArr3) {
        int iMin = Math.min(bArr.length, i2 + i);
        int i3 = 0;
        while (bArr2.length + i < iMin) {
            if (bArr[i] == bArr2[0] && bArr[bArr2.length + i] == 0 && regionEquals(bArr, i, bArr2)) {
                int i4 = 0;
                while (bArr2.length + i + i4 < iMin && bArr[bArr2.length + i + i4] == 0) {
                    i4++;
                }
                if (bArr3.length + 1 <= bArr2.length + i4) {
                    Arrays.fill(bArr, i, bArr2.length + i, (byte) 0);
                    System.arraycopy(bArr3, 0, bArr, i, bArr3.length);
                    i3++;
                    i += bArr2.length;
                }
            }
            i++;
        }
        return i3;
    }

    private static int patchModelTag(byte[] bArr, int i, byte[] bArr2) {
        int iModelEntry;
        int i2;
        int length;
        Tiff tiffFind = Tiff.find(bArr, i);
        if (tiffFind == null || (iModelEntry = tiffFind.modelEntry()) < 0) {
            return 0;
        }
        ByteBuffer byteBuffer = tiffFind.buffer;
        int i3 = iModelEntry + 4;
        int i4 = byteBuffer.getInt(i3);
        int i5 = byteBuffer.getInt(iModelEntry + 8) + tiffFind.start;
        if (i4 <= 4 || i5 < 0 || (i2 = i5 + i4) > bArr.length) {
            return 0;
        }
        if (bArr2.length + 1 <= i4) {
            length = bArr2.length + 1;
        } else {
            length = bArr2.length <= i4 ? bArr2.length : -1;
        }
        if (length < 0) {
            return -1;
        }
        Arrays.fill(bArr, i5, i2, (byte) 0);
        System.arraycopy(bArr2, 0, bArr, i5, bArr2.length);
        byteBuffer.putInt(i3, length);
        return 1;
    }

    public static String readModel(byte[] bArr, int i) {
        int iModelEntry;
        int i2;
        Tiff tiffFind = Tiff.find(bArr, i);
        if (tiffFind == null || (iModelEntry = tiffFind.modelEntry()) < 0) {
            return null;
        }
        int i3 = tiffFind.buffer.getInt(iModelEntry + 4);
        int i4 = i3 <= 4 ? iModelEntry + 8 : tiffFind.buffer.getInt(iModelEntry + 8) + tiffFind.start;
        if (i3 < 1 || i4 < 0 || (i2 = i3 + i4) > bArr.length) {
            return null;
        }
        int i5 = i4;
        while (i5 < i2 && bArr[i5] != 0) {
            i5++;
        }
        return new String(bArr, i4, i5 - i4, StandardCharsets.UTF_8);
    }

    private static boolean regionEquals(byte[] bArr, int i, byte[] bArr2) {
        for (int i2 = 1; i2 < bArr2.length; i2++) {
            if (bArr[i + i2] != bArr2[i2]) {
                return false;
            }
        }
        return true;
    }
}
