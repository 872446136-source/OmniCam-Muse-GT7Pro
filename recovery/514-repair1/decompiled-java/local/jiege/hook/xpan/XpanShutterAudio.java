package local.jiege.hook.xpan;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.view.Surface;
import defpackage.c3;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class XpanShutterAudio {
    private static final int AAC_BIT_RATE = 128000;
    private static final String SOUND = "camera_xpan_shutter_sound";

    private XpanShutterAudio() {
    }

    private static byte[] decode(AssetFileDescriptor assetFileDescriptor, int[] iArr) throws Throwable {
        char c;
        long j;
        MediaExtractor mediaExtractor = new MediaExtractor();
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());
            int iTrack = track(mediaExtractor, "audio/");
            mediaExtractor.selectTrack(iTrack);
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(iTrack);
            iArr[0] = trackFormat.getInteger("sample-rate");
            char c2 = 1;
            iArr[1] = trackFormat.getInteger("channel-count");
            MediaCodec mediaCodecCreateDecoderByType = MediaCodec.createDecoderByType(trackFormat.getString("mime"));
            try {
                mediaCodecCreateDecoderByType.configure(trackFormat, (Surface) null, (MediaCrypto) null, 0);
                mediaCodecCreateDecoderByType.start();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                long j2 = 10000;
                long jElapsedRealtime = SystemClock.elapsedRealtime() + 10000;
                char c3 = 0;
                while (SystemClock.elapsedRealtime() <= jElapsedRealtime) {
                    if (c3 == 0) {
                        long j3 = j2;
                        int iDequeueInputBuffer = mediaCodecCreateDecoderByType.dequeueInputBuffer(j3);
                        if (iDequeueInputBuffer >= 0) {
                            int sampleData = mediaExtractor.readSampleData(mediaCodecCreateDecoderByType.getInputBuffer(iDequeueInputBuffer), 0);
                            if (sampleData < 0) {
                                c = c2;
                                j = j3;
                                mediaCodecCreateDecoderByType.queueInputBuffer(iDequeueInputBuffer, 0, 0, 0L, 4);
                                c3 = c;
                            } else {
                                c = c2;
                                j = j3;
                                mediaCodecCreateDecoderByType.queueInputBuffer(iDequeueInputBuffer, 0, sampleData, mediaExtractor.getSampleTime(), 0);
                                mediaExtractor.advance();
                            }
                        } else {
                            c = c2;
                            j = j3;
                        }
                    } else {
                        c = c2;
                        j = j2;
                    }
                    int iDequeueOutputBuffer = mediaCodecCreateDecoderByType.dequeueOutputBuffer(bufferInfo, j);
                    if (iDequeueOutputBuffer == -2) {
                        MediaFormat outputFormat = mediaCodecCreateDecoderByType.getOutputFormat();
                        iArr[0] = outputFormat.getInteger("sample-rate");
                        iArr[c] = outputFormat.getInteger("channel-count");
                    } else if (iDequeueOutputBuffer >= 0) {
                        ByteBuffer outputBuffer = mediaCodecCreateDecoderByType.getOutputBuffer(iDequeueOutputBuffer);
                        int i = bufferInfo.size;
                        byte[] bArr = new byte[i];
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.get(bArr);
                        byteArrayOutputStream.write(bArr, 0, i);
                        mediaCodecCreateDecoderByType.releaseOutputBuffer(iDequeueOutputBuffer, false);
                        if ((bufferInfo.flags & 4) != 0) {
                            byte[] byteArray = byteArrayOutputStream.toByteArray();
                            try {
                                mediaCodecCreateDecoderByType.stop();
                            } catch (IllegalStateException unused) {
                            }
                            mediaCodecCreateDecoderByType.release();
                            mediaExtractor.release();
                            return byteArray;
                        }
                    } else {
                        continue;
                    }
                    j2 = j;
                    jElapsedRealtime = jElapsedRealtime;
                    c2 = c;
                }
                throw new IOException("shutter sound decoder stalled");
            } catch (Throwable th) {
                th = th;
                mediaCodec = mediaCodecCreateDecoderByType;
                if (mediaCodec != null) {
                    try {
                        mediaCodec.stop();
                    } catch (IllegalStateException unused2) {
                    }
                    mediaCodec.release();
                }
                mediaExtractor.release();
                throw th;
            }
        } catch (Throwable th2) {
            th = th2;
        }
    }

    /* JADX WARN: Code duplicated, block: B:27:0x00ac A[Catch: all -> 0x007f, TryCatch #3 {all -> 0x007f, blocks: (B:3:0x0026, B:4:0x003d, B:7:0x0047, B:9:0x004e, B:14:0x0076, B:25:0x00a5, B:27:0x00ac, B:29:0x00b5, B:31:0x00bb, B:33:0x00bf, B:34:0x00e5, B:17:0x0082, B:46:0x0110, B:47:0x0117), top: B:59:0x0026 }] */
    /* JADX WARN: Code duplicated, block: B:28:0x00b3  */
    /* JADX WARN: Code duplicated, block: B:29:0x00b5 A[Catch: all -> 0x007f, TryCatch #3 {all -> 0x007f, blocks: (B:3:0x0026, B:4:0x003d, B:7:0x0047, B:9:0x004e, B:14:0x0076, B:25:0x00a5, B:27:0x00ac, B:29:0x00b5, B:31:0x00bb, B:33:0x00bf, B:34:0x00e5, B:17:0x0082, B:46:0x0110, B:47:0x0117), top: B:59:0x0026 }] */
    /* JADX WARN: Code duplicated, block: B:53:0x00ee A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:63:0x0109 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:64:0x0109 A[SYNTHETIC] */
    private static Encoded encode(byte[] bArr, int i, int i2) throws Throwable {
        int i3;
        char c;
        long j;
        int iDequeueOutputBuffer;
        MediaFormat mediaFormatCreateAudioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", i, i2);
        char c2 = 2;
        mediaFormatCreateAudioFormat.setInteger("aac-profile", 2);
        mediaFormatCreateAudioFormat.setInteger("bitrate", AAC_BIT_RATE);
        MediaCodec mediaCodecCreateEncoderByType = MediaCodec.createEncoderByType("audio/mp4a-latm");
        Encoded encoded = new Encoded(0);
        try {
            mediaCodecCreateEncoderByType.configure(mediaFormatCreateAudioFormat, (Surface) null, (MediaCrypto) null, 1);
            mediaCodecCreateEncoderByType.start();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int i4 = i2 * 2;
            long j2 = 10000;
            long jElapsedRealtime = SystemClock.elapsedRealtime() + 10000;
            int i5 = 0;
            boolean z = false;
            while (SystemClock.elapsedRealtime() <= jElapsedRealtime) {
                if (z) {
                    i3 = i5;
                } else {
                    int i6 = i5;
                    int iDequeueInputBuffer = mediaCodecCreateEncoderByType.dequeueInputBuffer(j2);
                    if (iDequeueInputBuffer >= 0) {
                        ByteBuffer inputBuffer = mediaCodecCreateEncoderByType.getInputBuffer(iDequeueInputBuffer);
                        c = c2;
                        int iMin = Math.min((inputBuffer.remaining() / i4) * i4, bArr.length - i6);
                        MediaCodec mediaCodec = mediaCodecCreateEncoderByType;
                        try {
                            long j3 = (((long) (i6 / i4)) * 1000000) / ((long) i);
                            if (iMin <= 0) {
                                mediaCodecCreateEncoderByType = mediaCodec;
                                mediaCodecCreateEncoderByType.queueInputBuffer(iDequeueInputBuffer, 0, 0, j3, 4);
                                i5 = i6;
                                j = 10000;
                                z = true;
                            } else {
                                mediaCodecCreateEncoderByType = mediaCodec;
                                inputBuffer.put(bArr, i6, iMin);
                                mediaCodecCreateEncoderByType.queueInputBuffer(iDequeueInputBuffer, 0, iMin, j3, 0);
                                i5 = i6 + iMin;
                                j = 10000;
                            }
                        } catch (Throwable th) {
                            th = th;
                            mediaCodecCreateEncoderByType = mediaCodec;
                            try {
                                mediaCodecCreateEncoderByType.stop();
                            } catch (IllegalStateException unused) {
                            }
                            mediaCodecCreateEncoderByType.release();
                            throw th;
                        }
                    } else {
                        i3 = i6;
                    }
                    iDequeueOutputBuffer = mediaCodecCreateEncoderByType.dequeueOutputBuffer(bufferInfo, j);
                    if (iDequeueOutputBuffer == -2) {
                        encoded.format = mediaCodecCreateEncoderByType.getOutputFormat();
                    } else if (iDequeueOutputBuffer < 0) {
                        continue;
                    } else {
                        if ((bufferInfo.flags & 2) == 0 && bufferInfo.size > 0) {
                            ByteBuffer outputBuffer = mediaCodecCreateEncoderByType.getOutputBuffer(iDequeueOutputBuffer);
                            byte[] bArr2 = new byte[bufferInfo.size];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.get(bArr2);
                            encoded.samples.add(bArr2);
                            encoded.times.add(Long.valueOf(Math.max(0L, bufferInfo.presentationTimeUs)));
                        }
                        mediaCodecCreateEncoderByType.releaseOutputBuffer(iDequeueOutputBuffer, false);
                        if ((bufferInfo.flags & 4) != 0) {
                            try {
                                mediaCodecCreateEncoderByType.stop();
                            } catch (IllegalStateException unused2) {
                            }
                            mediaCodecCreateEncoderByType.release();
                            if (encoded.format != null || encoded.samples.isEmpty()) {
                                throw new IOException("AAC encoder produced nothing");
                            }
                            return encoded;
                        }
                    }
                    c2 = c;
                    j2 = 10000;
                }
                c = c2;
                i5 = i3;
                j = 10000;
                iDequeueOutputBuffer = mediaCodecCreateEncoderByType.dequeueOutputBuffer(bufferInfo, j);
                if (iDequeueOutputBuffer == -2) {
                    encoded.format = mediaCodecCreateEncoderByType.getOutputFormat();
                } else if (iDequeueOutputBuffer < 0) {
                    continue;
                } else {
                    if ((bufferInfo.flags & 2) == 0) {
                        ByteBuffer outputBuffer2 = mediaCodecCreateEncoderByType.getOutputBuffer(iDequeueOutputBuffer);
                        byte[] bArr3 = new byte[bufferInfo.size];
                        outputBuffer2.position(bufferInfo.offset);
                        outputBuffer2.get(bArr3);
                        encoded.samples.add(bArr3);
                        encoded.times.add(Long.valueOf(Math.max(0L, bufferInfo.presentationTimeUs)));
                    }
                    mediaCodecCreateEncoderByType.releaseOutputBuffer(iDequeueOutputBuffer, false);
                    if ((bufferInfo.flags & 4) != 0) {
                        mediaCodecCreateEncoderByType.stop();
                        mediaCodecCreateEncoderByType.release();
                        if (encoded.format != null) {
                        }
                        throw new IOException("AAC encoder produced nothing");
                    }
                }
                c2 = c;
                j2 = 10000;
            }
            throw new IOException("AAC encoder stalled");
        } catch (Throwable th2) {
            th = th2;
        }
    }

    private static Encoded encodeShutter(Context context) throws IOException {
        int identifier = context.getResources().getIdentifier(SOUND, "raw", context.getPackageName());
        if (identifier == 0) {
            throw new IOException("camera has no raw/camera_xpan_shutter_sound");
        }
        int[] iArr = new int[2];
        AssetFileDescriptor assetFileDescriptorOpenRawResourceFd = context.getResources().openRawResourceFd(identifier);
        try {
            if (assetFileDescriptorOpenRawResourceFd == null) {
                throw new IOException("raw/camera_xpan_shutter_sound is compressed in the APK");
            }
            byte[] bArrDecode = decode(assetFileDescriptorOpenRawResourceFd, iArr);
            assetFileDescriptorOpenRawResourceFd.close();
            return encode(bArrDecode, iArr[0], iArr[1]);
        } catch (Throwable th) {
            if (assetFileDescriptorOpenRawResourceFd != null) {
                try {
                    assetFileDescriptorOpenRawResourceFd.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public static void mux(Context context, File file, File file2, int i) throws Throwable {
        Throwable th;
        Encoded encodedEncodeShutter = encodeShutter(context);
        MediaExtractor mediaExtractor = new MediaExtractor();
        MediaMuxer mediaMuxer = null;
        boolean z = false;
        try {
            mediaExtractor.setDataSource(file.getAbsolutePath());
            int iTrack = track(mediaExtractor, "video/");
            mediaExtractor.selectTrack(iTrack);
            MediaMuxer mediaMuxer2 = new MediaMuxer(file2.getAbsolutePath(), 0);
            try {
                mediaMuxer2.setOrientationHint(i);
                int iAddTrack = mediaMuxer2.addTrack(mediaExtractor.getTrackFormat(iTrack));
                int iAddTrack2 = mediaMuxer2.addTrack(encodedEncodeShutter.format);
                mediaMuxer2.start();
                try {
                    ByteBuffer byteBufferAllocateDirect = ByteBuffer.allocateDirect(4194304);
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int i2 = 0;
                    while (mediaExtractor.getSampleTrackIndex() >= 0) {
                        long sampleTime = mediaExtractor.getSampleTime();
                        while (i2 < encodedEncodeShutter.samples.size() && encodedEncodeShutter.times.get(i2).longValue() <= sampleTime) {
                            writeAudio(mediaMuxer2, iAddTrack2, encodedEncodeShutter, i2, bufferInfo);
                            i2++;
                        }
                        byteBufferAllocateDirect.clear();
                        int sampleData = mediaExtractor.readSampleData(byteBufferAllocateDirect, 0);
                        if (sampleData < 0) {
                            break;
                        }
                        bufferInfo.set(0, sampleData, sampleTime, (mediaExtractor.getSampleFlags() & 1) != 0 ? 1 : 0);
                        mediaMuxer2.writeSampleData(iAddTrack, byteBufferAllocateDirect, bufferInfo);
                        mediaExtractor.advance();
                    }
                    while (i2 < encodedEncodeShutter.samples.size()) {
                        writeAudio(mediaMuxer2, iAddTrack2, encodedEncodeShutter, i2, bufferInfo);
                        i2++;
                    }
                    mediaExtractor.release();
                    try {
                        mediaMuxer2.stop();
                    } finally {
                        mediaMuxer2.release();
                    }
                } catch (Throwable th2) {
                    th = th2;
                    z = true;
                    mediaMuxer = mediaMuxer2;
                    mediaExtractor.release();
                    if (mediaMuxer == null) {
                        throw th;
                    }
                    if (z) {
                        try {
                            mediaMuxer.stop();
                        } finally {
                            mediaMuxer.release();
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
            }
        } catch (Throwable th4) {
            th = th4;
        }
    }

    private static int track(MediaExtractor mediaExtractor, String str) throws IOException {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            if (mediaExtractor.getTrackFormat(i).getString("mime").startsWith(str)) {
                return i;
            }
        }
        throw new IOException(c3.k("no ", str, " track"));
    }

    private static void writeAudio(MediaMuxer mediaMuxer, int i, Encoded encoded, int i2, MediaCodec.BufferInfo bufferInfo) {
        byte[] bArr = encoded.samples.get(i2);
        bufferInfo.set(0, bArr.length, encoded.times.get(i2).longValue(), 0);
        mediaMuxer.writeSampleData(i, ByteBuffer.wrap(bArr), bufferInfo);
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Encoded {
        MediaFormat format;
        final List<byte[]> samples;
        final List<Long> times;

        private Encoded() {
            this.samples = new ArrayList();
            this.times = new ArrayList();
        }

        public /* synthetic */ Encoded(int i) {
            this();
        }
    }
}
