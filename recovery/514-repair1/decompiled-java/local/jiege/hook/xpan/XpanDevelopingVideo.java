package local.jiege.hook.xpan;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class XpanDevelopingVideo {
    private static final float BITS_PER_PIXEL = 0.4f;
    private static final int EGL_RECORDABLE_ANDROID = 12610;
    private static final int FPS = 30;
    private static final String FRAGMENT = "precision highp float; varying vec2 vStored;uniform sampler2D uStill; uniform sampler2D uPhoto; uniform vec4 uArea; uniform float uNegative;void main(){ vec2 uv=vec2(1.0-vStored.y,vStored.x); vec2 p=(uv-uArea.xy)/(uArea.zw-uArea.xy); if(p.x<0.0||p.y<0.0||p.x>1.0||p.y>1.0){gl_FragColor=vec4(texture2D(uStill,uv).rgb,1.0);return;} vec3 photo=texture2D(uPhoto,p).rgb; gl_FragColor=vec4(mix(photo,vec3(1.0)-photo,uNegative),1.0);}";
    private static final int HOLD_FRAMES = 9;
    private static final int MAX_BIT_RATE = 80000000;
    private static final int MAX_LONG_SIDE = 2160;
    private static final String[] MIMES = {"video/hevc", "video/avc"};
    private static final float[] QUAD = {-1.0f, -1.0f, 0.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};
    private static final int REVEAL_FRAMES = 75;
    private static final String VERTEX = "attribute vec2 aPosition; attribute vec2 aTexCoord; varying vec2 vStored;void main(){gl_Position=vec4(aPosition,0.0,1.0);vStored=aTexCoord;}";

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Clip {
        final int areaHeight;
        final int areaWidth;
        final long coverUs;
        final Rect display;
        final int height;
        final String mime;
        final int photoHeight;
        final int photoWidth;
        final int rotation;
        final int width;

        public Clip(int i, int i2, int i3, String str, long j, int i4, int i5, int i6, int i7, Rect rect) {
            this.width = i;
            this.height = i2;
            this.rotation = i3;
            this.mime = str;
            this.coverUs = j;
            this.areaWidth = i4;
            this.areaHeight = i5;
            this.photoWidth = i6;
            this.photoHeight = i7;
            this.display = rect;
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Renderer {
        private final EGLContext context;
        private final EGLDisplay display;
        private final int height;
        private final int program;
        private final EGLSurface surface;
        private final int[] textures;
        private final FloatBuffer vertices;
        private final int width;

        public Renderer(Surface surface, Bitmap bitmap, Bitmap bitmap2, float[] fArr, int i, int i2) throws IOException {
            int[] iArr = new int[2];
            this.textures = iArr;
            this.width = i;
            this.height = i2;
            EGLDisplay eGLDisplayEglGetDisplay = EGL14.eglGetDisplay(0);
            this.display = eGLDisplayEglGetDisplay;
            if (eGLDisplayEglGetDisplay == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(eGLDisplayEglGetDisplay, new int[2], 0, new int[2], 0)) {
                throw new IOException("no EGL display");
            }
            EGLConfig[] eGLConfigArr = new EGLConfig[1];
            int[] iArr2 = new int[1];
            if (!EGL14.eglChooseConfig(eGLDisplayEglGetDisplay, new int[]{12324, 8, 12323, 8, 12322, 8, 12352, 4, XpanDevelopingVideo.EGL_RECORDABLE_ANDROID, 1, 12344}, 0, eGLConfigArr, 0, 1, iArr2, 0) || iArr2[0] == 0) {
                throw new IOException("no recordable EGL config");
            }
            EGLContext eGLContextEglCreateContext = EGL14.eglCreateContext(eGLDisplayEglGetDisplay, eGLConfigArr[0], EGL14.EGL_NO_CONTEXT, new int[]{12440, 2, 12344}, 0);
            this.context = eGLContextEglCreateContext;
            EGLSurface eGLSurfaceEglCreateWindowSurface = EGL14.eglCreateWindowSurface(eGLDisplayEglGetDisplay, eGLConfigArr[0], surface, new int[]{12344}, 0);
            this.surface = eGLSurfaceEglCreateWindowSurface;
            if (eGLContextEglCreateContext == EGL14.EGL_NO_CONTEXT || eGLSurfaceEglCreateWindowSurface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(eGLDisplayEglGetDisplay, eGLSurfaceEglCreateWindowSurface, eGLSurfaceEglCreateWindowSurface, eGLContextEglCreateContext)) {
                throw new IOException("no EGL recording surface");
            }
            int iGlCreateProgram = GLES20.glCreateProgram();
            this.program = iGlCreateProgram;
            GLES20.glAttachShader(iGlCreateProgram, shader(35633, XpanDevelopingVideo.VERTEX));
            GLES20.glAttachShader(iGlCreateProgram, shader(35632, XpanDevelopingVideo.FRAGMENT));
            GLES20.glLinkProgram(iGlCreateProgram);
            int[] iArr3 = new int[1];
            GLES20.glGetProgramiv(iGlCreateProgram, 35714, iArr3, 0);
            if (iArr3[0] == 0) {
                throw new IOException("shader link: " + GLES20.glGetProgramInfoLog(iGlCreateProgram));
            }
            int[] iArr4 = new int[1];
            GLES20.glGetIntegerv(3379, iArr4, 0);
            if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > iArr4[0]) {
                throw new IOException("photo " + bitmap.getWidth() + "x" + bitmap.getHeight() + " above GL texture limit " + iArr4[0]);
            }
            GLES20.glUseProgram(iGlCreateProgram);
            GLES20.glGenTextures(2, iArr, 0);
            Bitmap[] bitmapArr = {bitmap, bitmap2};
            String[] strArr = {"uStill", "uPhoto"};
            for (int i3 = 0; i3 < 2; i3++) {
                GLES20.glActiveTexture(33984 + i3);
                GLES20.glBindTexture(3553, this.textures[i3]);
                GLES20.glTexParameteri(3553, 10241, 9729);
                GLES20.glTexParameteri(3553, 10240, 9729);
                GLES20.glTexParameteri(3553, 10242, 33071);
                GLES20.glTexParameteri(3553, 10243, 33071);
                GLUtils.texImage2D(3553, 0, bitmapArr[i3], 0);
                GLES20.glUniform1i(GLES20.glGetUniformLocation(this.program, strArr[i3]), i3);
            }
            GLES20.glUniform4f(GLES20.glGetUniformLocation(this.program, "uArea"), fArr[0], fArr[1], fArr[2], fArr[3]);
            FloatBuffer floatBufferAsFloatBuffer = ByteBuffer.allocateDirect(XpanDevelopingVideo.QUAD.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            this.vertices = floatBufferAsFloatBuffer;
            floatBufferAsFloatBuffer.put(XpanDevelopingVideo.QUAD).position(0);
        }

        private static int shader(int i, String str) throws IOException {
            int iGlCreateShader = GLES20.glCreateShader(i);
            GLES20.glShaderSource(iGlCreateShader, str);
            GLES20.glCompileShader(iGlCreateShader);
            int[] iArr = new int[1];
            GLES20.glGetShaderiv(iGlCreateShader, 35713, iArr, 0);
            if (iArr[0] != 0) {
                return iGlCreateShader;
            }
            throw new IOException("shader: " + GLES20.glGetShaderInfoLog(iGlCreateShader));
        }

        public void close() {
            GLES20.glDeleteTextures(2, this.textures, 0);
            EGLDisplay eGLDisplay = this.display;
            EGLSurface eGLSurface = EGL14.EGL_NO_SURFACE;
            EGL14.eglMakeCurrent(eGLDisplay, eGLSurface, eGLSurface, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(this.display, this.surface);
            EGL14.eglDestroyContext(this.display, this.context);
            EGL14.eglReleaseThread();
        }

        public void draw(float f, long j) throws IOException {
            GLES20.glViewport(0, 0, this.width, this.height);
            GLES20.glUseProgram(this.program);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(this.program, "uNegative"), f);
            int iGlGetAttribLocation = GLES20.glGetAttribLocation(this.program, "aPosition");
            int iGlGetAttribLocation2 = GLES20.glGetAttribLocation(this.program, "aTexCoord");
            this.vertices.position(0);
            GLES20.glEnableVertexAttribArray(iGlGetAttribLocation);
            GLES20.glVertexAttribPointer(iGlGetAttribLocation, 2, 5126, false, 16, (Buffer) this.vertices);
            this.vertices.position(2);
            GLES20.glEnableVertexAttribArray(iGlGetAttribLocation2);
            GLES20.glVertexAttribPointer(iGlGetAttribLocation2, 2, 5126, false, 16, (Buffer) this.vertices);
            GLES20.glDrawArrays(5, 0, 4);
            if (GLES20.glGetError() != 0) {
                throw new IOException("GL draw failed");
            }
            EGLExt.eglPresentationTimeANDROID(this.display, this.surface, j);
            if (!EGL14.eglSwapBuffers(this.display, this.surface)) {
                throw new IOException("EGL swap failed");
            }
        }
    }

    private XpanDevelopingVideo() {
    }

    private static Bitmap decode(Bitmap bitmap, int i, int i2) throws IOException {
        if (bitmap == null) {
            throw new IOException("cannot decode a photo of the clip");
        }
        if (bitmap.getWidth() == i && bitmap.getHeight() == i2) {
            return bitmap;
        }
        Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(bitmap, i, i2, true);
        bitmap.recycle();
        return bitmapCreateScaledBitmap;
    }

    /* JADX WARN: Incorrect type for immutable var: ssa=boolean, code=int, for r12v0, types: [boolean] */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r12v2 */
    /* JADX WARN: Type inference failed for: r12v3 */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private static int[] drain(android.media.MediaCodec r8, android.media.MediaMuxer r9, android.media.MediaCodec.BufferInfo r10, int r11, int r12, boolean r13) throws java.io.IOException {
        /*
            long r0 = android.os.SystemClock.elapsedRealtime()
            r2 = 20000(0x4e20, double:9.8813E-320)
            long r0 = r0 + r2
        L7:
            if (r13 == 0) goto Lc
            r2 = 10000(0x2710, double:4.9407E-320)
            goto Le
        Lc:
            r2 = 0
        Le:
            int r2 = r8.dequeueOutputBuffer(r10, r2)
            r3 = -1
            if (r2 != r3) goto L2d
            if (r13 != 0) goto L1c
            int[] r8 = new int[]{r11, r12}
            return r8
        L1c:
            long r2 = android.os.SystemClock.elapsedRealtime()
            int r2 = (r2 > r0 ? 1 : (r2 == r0 ? 0 : -1))
            if (r2 > 0) goto L25
            goto L7
        L25:
            java.io.IOException r8 = new java.io.IOException
            java.lang.String r9 = "XPAN encoder did not finish"
            r8.<init>(r9)
            throw r8
        L2d:
            r3 = -2
            r4 = 1
            if (r2 != r3) goto L48
            if (r12 != 0) goto L40
            android.media.MediaFormat r11 = r8.getOutputFormat()
            int r11 = r9.addTrack(r11)
            r9.start()
            r12 = r4
            goto L7
        L40:
            java.io.IOException r8 = new java.io.IOException
            java.lang.String r9 = "XPAN encoder changed format twice"
            r8.<init>(r9)
            throw r8
        L48:
            if (r2 >= 0) goto L4b
            goto L7
        L4b:
            int r3 = r10.flags
            r5 = r3 & 4
            r6 = 0
            if (r5 == 0) goto L53
            goto L54
        L53:
            r4 = r6
        L54:
            r3 = r3 & 2
            if (r3 != 0) goto L7b
            int r3 = r10.size
            if (r3 <= 0) goto L7b
            if (r12 == 0) goto L73
            java.nio.ByteBuffer r3 = r8.getOutputBuffer(r2)
            int r5 = r10.offset
            r3.position(r5)
            int r5 = r10.offset
            int r7 = r10.size
            int r5 = r5 + r7
            r3.limit(r5)
            r9.writeSampleData(r11, r3, r10)
            goto L7b
        L73:
            java.io.IOException r8 = new java.io.IOException
            java.lang.String r9 = "XPAN encoder output before format"
            r8.<init>(r9)
            throw r8
        L7b:
            r8.releaseOutputBuffer(r2, r6)
            if (r4 == 0) goto L7
            int[] r8 = new int[]{r11, r12}
            return r8
        */
        throw new UnsupportedOperationException("Method not decompiled: local.jiege.hook.xpan.XpanDevelopingVideo.drain(android.media.MediaCodec, android.media.MediaMuxer, android.media.MediaCodec$BufferInfo, int, boolean, boolean):int[]");
    }

    /*  JADX ERROR: Type inference failed
        jadx.core.utils.exceptions.JadxOverflowException: Type inference error: updates count limit reached with updateSeq = 8481. Try increasing type updates limit count.
        	at jadx.core.utils.ErrorsCounter.addError(ErrorsCounter.java:59)
        	at jadx.core.utils.ErrorsCounter.error(ErrorsCounter.java:31)
        	at jadx.core.dex.attributes.nodes.NotificationAttrNode.addError(NotificationAttrNode.java:19)
        	at jadx.core.dex.visitors.typeinference.TypeInferenceVisitor.visit(TypeInferenceVisitor.java:79)
        */
    public static local.jiege.hook.xpan.XpanDevelopingVideo.Clip encode(java.io.File r34, int r35, int r36, byte[] r37, android.graphics.Rect r38, int r39, java.io.File r40) {
        /*
            Method dump skipped, instruction units count: 848
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: local.jiege.hook.xpan.XpanDevelopingVideo.encode(java.io.File, int, int, byte[], android.graphics.Rect, int, java.io.File):local.jiege.hook.xpan.XpanDevelopingVideo$Clip");
    }

    private static int[] encoderSize(String str, int i, int i2) {
        for (MediaCodecInfo mediaCodecInfo : new MediaCodecList(0).getCodecInfos()) {
            if (mediaCodecInfo.isEncoder()) {
                for (String str2 : mediaCodecInfo.getSupportedTypes()) {
                    if (str2.equalsIgnoreCase(str)) {
                        MediaCodecInfo.VideoCapabilities videoCapabilities = mediaCodecInfo.getCapabilitiesForType(str2).getVideoCapabilities();
                        for (float f = 1.0f; f > 0.2f; f *= 0.9f) {
                            int iMax = Math.max(16, (((int) (i * f)) / 16) * 16);
                            int iMax2 = Math.max(16, (((int) (i2 * f)) / 16) * 16);
                            if (videoCapabilities.isSizeSupported(iMax, iMax2)) {
                                return new int[]{iMax, iMax2};
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static int orientation(File file) {
        int attributeInt = new ExifInterface(file.getAbsolutePath()).getAttributeInt("Orientation", 1);
        if (attributeInt == 3) {
            return 180;
        }
        if (attributeInt != 6) {
            return attributeInt != 8 ? 0 : 270;
        }
        return 90;
    }

    private static BitmapFactory.Options sampled(int i, int i2, int i3, int i4) {
        int i5 = 1;
        while (true) {
            int i6 = i5 * 2;
            if (i / i6 < i3 || i2 / i6 < i4) {
                break;
            }
            i5 = i6;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = i5;
        return options;
    }
}
