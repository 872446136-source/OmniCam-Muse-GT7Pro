package local.jiege.hook.xpan;

import defpackage.c3;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class XpanLiveExtension {
    private static final int FREE = 1718773093;
    private static final int LPEX = 1819305336;
    private static final int MOOV = 1836019574;

    private XpanLiveExtension() {
    }

    private static byte[] box(String str) {
        byte[] bytes = c3.u("LivePhotoExtension", str).getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(bytes.length + 8).putInt(bytes.length + 8).putInt(LPEX).put(bytes).array();
    }

    private static JSONArray identity() {
        return new JSONArray().put(1.0d).put(0.0d).put(0.0d).put(0.0d).put(1.0d).put(0.0d).put(0.0d).put(0.0d).put(1.0d);
    }

    private static String json(XpanDevelopingVideo.Clip clip) throws IOException {
        int i = clip.areaWidth;
        int i2 = clip.areaHeight;
        int i3 = clip.photoWidth;
        int i4 = clip.photoHeight;
        try {
            return new JSONObject().put("coverFramePts", clip.coverUs).put("cropRect", new JSONArray().put(0).put(0).put(i).put(i2)).put("desc", "OppoMotionVideoExt").put("eisCropFactor", new JSONArray().put(1.0d).put(1.0d)).put("matrixCount", 0).put("originPhotoSize", new JSONArray().put(i3).put(i4)).put("photoCropFactor", 1.0d).put("photoCropMatrix", identity()).put("photoCropRect", new JSONArray().put(0).put(0).put(i3).put(i4)).put("photoEisCropFactor", new JSONArray().put(1.0d).put(1.0d)).put("photoEisMatrix", identity()).put("subVideoScaleFactor", 1.0d).put("version", 1).put("videoOrientation", clip.rotation).put("videoSize", new JSONArray().put(i).put(i2)).toString();
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public static void write(File file, XpanDevelopingVideo.Clip clip) throws IOException {
        byte[] bArr;
        long unsignedLong;
        byte[] bArrBox = box(json(clip));
        byte[] allBytes = Files.readAllBytes(file.toPath());
        ByteBuffer byteBufferWrap = ByteBuffer.wrap(allBytes);
        int i = -1;
        int i2 = -1;
        for (int i3 = 0; i3 < allBytes.length; i3 += (int) unsignedLong) {
            unsignedLong = Integer.toUnsignedLong(byteBufferWrap.getInt(i3));
            if (unsignedLong == 1) {
                unsignedLong = byteBufferWrap.getLong(i3 + 8);
            } else if (unsignedLong == 0) {
                unsignedLong = allBytes.length - i3;
            }
            if (unsignedLong < 8 || unsignedLong > allBytes.length - i3) {
                throw new IOException("bad MP4 box at " + i3 + ", size " + unsignedLong);
            }
            if (i >= 0 && i2 < 0) {
                i2 = i3;
            }
            if (byteBufferWrap.getInt(i3 + 4) == MOOV) {
                i = i3;
            }
        }
        if (i < 0) {
            throw new IOException("MP4 without moov");
        }
        int i4 = byteBufferWrap.getInt(i);
        int i5 = i + i4;
        if (i2 < 0) {
            bArr = new byte[allBytes.length + bArrBox.length];
            System.arraycopy(allBytes, 0, bArr, 0, allBytes.length);
            System.arraycopy(bArrBox, 0, bArr, allBytes.length, bArrBox.length);
        } else {
            if (byteBufferWrap.getInt(i2 + 4) != FREE || byteBufferWrap.getInt(i2) - bArrBox.length < 8) {
                throw new IOException("moov is neither last nor followed by free space");
            }
            byte[] bArr2 = (byte[]) allBytes.clone();
            int i6 = byteBufferWrap.getInt(i2);
            System.arraycopy(bArrBox, 0, bArr2, i5, bArrBox.length);
            ByteBuffer.wrap(bArr2).putInt(bArrBox.length + i5, i6 - bArrBox.length).putInt(i5 + bArrBox.length + 4, FREE);
            bArr = bArr2;
        }
        ByteBuffer.wrap(bArr).putInt(i, i4 + bArrBox.length);
        Files.write(file.toPath(), bArr, new OpenOption[0]);
    }
}
