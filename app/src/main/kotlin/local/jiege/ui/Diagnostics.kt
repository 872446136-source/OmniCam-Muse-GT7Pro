package local.jiege.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import local.jiege.hook.common.Capabilities
import local.jiege.settings.DiagnosticsContract
import local.jiege.settings.MasterFilterContract
import local.jiege.settings.ModuleSettings

/** The camera's injection state (DiagnosticsContract); null when the camera is not hooked or not reachable. */
internal object CameraReport {
    /**
     * Also stores the camera's master list when it came along. Blocks, and starts the camera
     * process when it is not running; call off the main thread.
     */
    fun fetch(context: Context): Bundle? = fetchState(context, DiagnosticsContract.CAMERA_AUTHORITY)
        ?.also { state -> state.getString(DiagnosticsContract.KEY_MASTER_STOCK)?.let { MasterFilters.store(context).saveCameraList(it) } }
}

/** The gallery's injection state; null when the gallery is not hooked. Blocks and starts the gallery process. */
internal object GalleryReport {
    fun fetch(context: Context): Bundle? = fetchState(context, DiagnosticsContract.GALLERY_AUTHORITY)
}

/**
 * The state the hooked process answers with, or null.
 *
 * <p>The call also *starts* the process it asks, so it blocks until that process is up. When the
 * process crash-loops (a broken hook, say) it never comes up and the call never returns, which used
 * to leave the page on "正在检查" forever and made the log export hang. So the call runs on its own
 * thread and is abandoned after [STATE_TIMEOUT_MS]: the page then says the app is not reachable,
 * which is the honest answer.
 */
private const val STATE_TIMEOUT_MS = 6000L

private fun fetchState(context: Context, authority: String): Bundle? {
    val task = java.util.concurrent.FutureTask(java.util.concurrent.Callable {
        context.contentResolver.call(Uri.parse("content://$authority"), DiagnosticsContract.METHOD_STATE, null, null)
            ?.takeIf { it.containsKey(DiagnosticsContract.KEY_PID) }
    })
    val thread = Thread(task, "diagnostics-$authority")
    thread.isDaemon = true
    thread.start()
    return try {
        task.get(STATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (_: Exception) {
        task.cancel(true)
        null
    }
}

/** One line of the injection check: passed, or the problem and what to do. */
internal data class Check(val title: String, val ok: Boolean, val detail: String)

internal enum class Health { OK, PENDING, FAILED }

internal class InjectionState(val checks: List<Check>, val health: Health) {
    val summary: String
        get() = when (health) {
            Health.OK -> "注入正常"
            Health.PENDING -> checks.first { !it.ok }.detail
            Health.FAILED -> checks.first { !it.ok }.title + "异常"
        }

    companion object {
        /** Reads the state; does file and package manager I/O, call off the main thread. */
        fun read(context: Context): InjectionState {
            val checks = ArrayList<Check>()
            val lsposed = MasterFilters.visibleToCamera(context)
            checks += Check("LSPosed", lsposed, if (lsposed) "模块已激活" else "模块未激活，在 LSPosed 中启用并勾选相机、相册、系统框架")
            // The module only mounts a data layer (LUTs, basictone, GR LMT, watermark artwork) and,
            // since 486, substitutes the Meishe renderer — the algorithm stack and the ODM configs
            // are never touched. Both are checked, one line each.
            val mounted = moduleMounted()
            checks += Check(
                "KSU 模块",
                mounted,
                if (mounted) "模块数据可读（/odm 模块层）" else "数据未挂载：确认 KernelSU 里本模块已启用、magic_mount_rs 元模块已安装，然后重启手机",
            )
            // 486: POP and GR's film styles are Meishe renderers, not BasicTone passes, and this
            // firmware's libmsnativefilter.so does not contain them. service.sh binds the module's
            // copy over it; this line says whether that happened, because "POP is black" and "the
            // renderer was never replaced" are the same sentence from the outside.
            val renderer = Capabilities.renderer()
            val rendererOk = Capabilities.rendererReplaced()
            checks += Check(
                "Meishe 渲染器",
                rendererOk,
                if (rendererOk) "已替换为模块版本，POP 与 GR 胶片滤镜可渲染"
                else "原厂版本（$renderer）：POP 与 GR 的四款胶片滤镜无法渲染。在 KernelSU 里点本模块的“执行”按钮开启替换，然后重启手机",
            )
            val updated = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
            // A process still running hooks from the previous module APK only needs a restart.
            var stale = 0
            val cameraReport = CameraReport.fetch(context)
            for ((label, report) in listOf("相机" to cameraReport, "相册" to GalleryReport.fetch(context))) {
                when {
                    report == null ->
                        checks += Check("${label}注入", false, if (lsposed) "${label}未被注入，在 LSPosed 中勾选${label}后点“重启$label”" else "${label}未被注入")
                    report.getString(DiagnosticsContract.KEY_MODULE_APK) != context.applicationInfo.sourceDir ||
                        report.getLong(DiagnosticsContract.KEY_START) < updated -> {
                        stale++
                        checks += Check("${label}注入", false, "${label}仍在使用旧版本，点“重启$label”")
                    }
                    !report.getBoolean(DiagnosticsContract.KEY_FILES_MOUNTED, true) ->
                        checks += Check("${label}注入", false, "${label}进程里模块数据既没挂载也没有私有目录副本，数据层功能已停用。先点“重启$label”（副本由开机后的 service.sh 补写）；仍不行再看 KernelSU → 超级用户 → 右上角勾选“显示系统应用” → $label → 选“自定义” → 关闭“卸载模块”。常见原因：10007 去广告等模块开启了 Zygisk Next 排除列表")
                    else -> {
                        val missing = report.getInt(DiagnosticsContract.KEY_SYMBOLS_MISSING)
                        val errors = report.getInt(DiagnosticsContract.KEY_ERRORS)
                        checks += when {
                            missing > 0 -> Check("${label}注入", false, "$missing 个符号未匹配，${label}版本可能不受支持")
                            errors > 0 -> Check("${label}注入", false, "$errors 条错误，请导出日志发给作者")
                            else -> Check("${label}注入", true, "已注入，" + report.getInt(DiagnosticsContract.KEY_SYMBOLS_RESOLVED) + " 个符号")
                        }
                    }
                }
            }
            // Which features the camera process actually installed, and why the rest did not. One build
            // runs everywhere; this is the list that says what it became on this phone.
            val featureLines = cameraReport?.getString(DiagnosticsContract.KEY_FEATURE_STATUS)
                .orEmpty().lines().filter { it.isNotBlank() }
            if (featureLines.isNotEmpty()) {
                val on = featureLines.count { it.startsWith("on\t") }
                val off = featureLines.filterNot { it.startsWith("on\t") }
                val detail = buildString {
                    append("$on 项已启用")
                    if (off.isEmpty()) {
                        append("，全部可用")
                    } else {
                        append("，${off.size} 项未启用：")
                        append(
                            off.joinToString("；") { line ->
                                val parts = line.split('\t')
                                val name = parts.getOrNull(1).orEmpty()
                                val reason = parts.getOrNull(2).orEmpty()
                                if (reason.isEmpty()) name else "$name（$reason）"
                            },
                        )
                    }
                }
                checks += Check("功能装配", true, detail)
            }
            val problems = checks.count { !it.ok }
            val health = when {
                problems == 0 -> Health.OK
                problems == stale -> Health.PENDING
                else -> Health.FAILED
            }
            return InjectionState(checks, health)
        }

        /**
         * Whether the module's payload is reachable from this process.
         *
         * <p>Not a device-id comparison: since 485 the payload is an overlay layer over /odm, so
         * stock files and module files share one anonymous device and {@code st_dev} says nothing.
         * What is checked instead is a directory only the module creates, and — for a phone that
         * has not rebooted since 484 — the old private copy.
         */
        private fun moduleMounted(): Boolean {
            val copied = java.io.File(Capabilities.CAMERA_BASICTONE, "lmt_gr").isDirectory()
            return copied || Capabilities.dataRoot() != null
        }
    }
}

/** Stops an app's process and opens it again. */
internal object AppRestart {
    /** Stops the camera in the background (root) so that camera-side settings apply when it opens next. */
    fun stopCamera() {
        Thread { rootCommand("am force-stop com.oplus.camera") }.start()
    }

    /**
     * Returns null on success, else what the user has to do. Needs root (Android 14 lets an app
     * stop only its own processes); without it the app's info page opens for 强行停止.
     * Blocks; call off the main thread.
     */
    fun restart(context: Context, packageName: String, label: String): String? {
        if (rootCommand("am force-stop $packageName") == null) {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "没有 Root 授权，请在应用信息页点“强行停止”后重开$label；或在 KernelSU 中给本应用授权 Root"
        }
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return "${label}已结束，找不到${label}入口"
        Thread.sleep(300)
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return null
    }
}

/**
 * The system log buffers hold about two minutes on this phone, so 开启日志 runs a root logcat into
 * the app's cache (two rotating 8 MiB files) until it is switched off; the export takes it along.
 */
internal object LogCapture {
    private fun file(context: Context) = context.cacheDir.absolutePath + "/logcat-capture.txt"

    private fun pattern(context: Context) = "'[l]ogcat -b main,system,crash -f ${file(context)}'"

    /** Starts a fresh recording; false without root. */
    fun start(context: Context): Boolean {
        val path = file(context)
        rootCommand("pkill -f ${pattern(context)}; rm -f $path $path.1")
        return rootCommand("nohup logcat -b main,system,crash -f $path -r 8192 -n 1 </dev/null >/dev/null 2>&1 &") != null
    }

    fun running(context: Context): Boolean = rootCommand("pgrep -f ${pattern(context)}")?.isNotBlank() == true

    /** The recording, oldest lines first; null when nothing was recorded. */
    fun read(context: Context): String? {
        val path = file(context)
        return rootCommand("cat $path.1 $path 2>/dev/null", 20)?.takeIf { it.isNotBlank() }
    }

    /** Stops the recording and keeps it for the export. */
    fun stop(context: Context) {
        rootCommand("pkill -f ${pattern(context)}")
    }
}

/** Runs a command as root; the output, or null when root is unavailable or the command failed. */
internal fun rootCommand(command: String, timeoutSeconds: Long = 8): String? = try {
    val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    val output = StringBuilder()
    val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroy()
        null
    } else {
        reader.join(1000)
        if (process.exitValue() == 0) output.toString() else null
    }
} catch (_: Exception) {
    null
}

/** The exported log: a zip with the state, the camera state and log lines, settings and, with root, system logs. */
internal object LogExport {
    fun fileName(): String = "OmniCam-Muse-日志-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".zip"

    /** Writes the zip; blocks for up to ~20 s when root logs are collected. */
    fun write(context: Context, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put("概要.txt", summary(context))
            val report = CameraReport.fetch(context)
            put("相机.txt", if (report == null) "相机未被注入或无法连接\n" else
                report.keySet().filter { it != DiagnosticsContract.KEY_LOG && it != DiagnosticsContract.KEY_MASTER_STOCK }.sorted().joinToString("\n") { "$it = ${report.get(it)}" } +
                    "\n\n" + report.getString(DiagnosticsContract.KEY_LOG).orEmpty() + "\n")
            // 488: the gallery has its own hooked process and its own log, and it was never in the
            // export — so "the Hasselblad styles are not in the gallery either" came back with no
            // gallery lines at all to check it against. Same layout as the camera one.
            val gallery = GalleryReport.fetch(context)
            put("相册.txt", if (gallery == null) "相册未被注入或无法连接\n" else
                gallery.keySet().filter { it != DiagnosticsContract.KEY_LOG && it != DiagnosticsContract.KEY_MASTER_STOCK }.sorted().joinToString("\n") { "$it = ${gallery.get(it)}" } +
                    "\n\n" + gallery.getString(DiagnosticsContract.KEY_LOG).orEmpty() + "\n")
            put("设置.txt", settings(context))
            runCatching { MasterFilters.store(context).configText() }.getOrNull()?.let { put("大师滤镜配置.txt", it) }
            put("应用日志.txt", ownLogcat())
            // 487: what the module's own scripts did at boot. 486 shipped a Meishe renderer bind whose
            // only record was bind.log inside the module directory, and the report came back without
            // it — so "the renderer was not replaced" could not be told apart from "the mount failed".
            // It is in the export now, from both places service.sh writes it to.
            val module = rootCommand(
                "cat /data/local/tmp/omnicam-bind.log 2>/dev/null; " +
                    "for d in /data/adb/modules/*; do " +
                    "if [ -f \$d/bind.log ] || [ -f \$d/action.log ]; then echo \"== \$d\"; " +
                    "tail -n 120 \$d/bind.log 2>/dev/null; tail -n 40 \$d/action.log 2>/dev/null; fi; done; " +
                    "echo '== module.prop'; cat /data/adb/modules/ricoh_pop_x9u/module.prop 2>/dev/null | head -5; " +
                    "echo '== renderer on disk'; ls -l /odm/lib64/libmsnativefilter.so 2>/dev/null; " +
                    "echo '== layer copy'; ls -l /data/adb/modules/ricoh_pop_x9u/system/odm/lib64/ 2>/dev/null",
                15)
            if (module != null) put("模块日志.txt", module)
            val lspd = rootCommand("for f in \$(ls -t /data/adb/lspd/log/*.log 2>/dev/null | head -4); do echo \"== \$f\"; tail -n 3000 \"\$f\"; done", 15)
            if (lspd != null) put("LSPosed.txt", lspd)
            val logcat = rootCommand("logcat -d -b main,system,crash -t 20000", 15)
            if (logcat != null) put("logcat.txt", logcat)
            LogCapture.read(context)?.let { put("logcat-记录.txt", it) }
        }
    }

    private fun summary(context: Context): String = buildString {
        val state = InjectionState.read(context)
        appendLine("导出时间  " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()))
        appendLine("模块版本  " + version(context, context.packageName))
        appendLine("相机版本  " + version(context, MasterFilterContract.CAMERA_PACKAGE))
        appendLine("相册版本  " + version(context, "com.coloros.gallery3d"))
        appendLine("设备      ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("系统      Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("构建      ${Build.DISPLAY}")
        appendLine("指纹      ${Build.FINGERPRINT}")
        appendLine("Root 日志 " + if (rootCommand("id") != null) "已收集" else "无 Root 授权，未收集")
        appendLine()
        appendLine("注入状态  " + state.summary)
        for (check in state.checks) appendLine((if (check.ok) "  [正常] " else "  [异常] ") + check.title + "：" + check.detail)
    }

    private fun version(context: Context, packageName: String): String = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    } catch (_: Exception) {
        "未安装"
    }

    /** All module settings; the licence itself stays out. */
    @Suppress("DEPRECATION")
    private fun settings(context: Context): String {
        val preferences = try {
            context.getSharedPreferences(ModuleSettings.FILE, Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
            context.getSharedPreferences(ModuleSettings.FILE, Context.MODE_PRIVATE)
        }
        return preferences.all.toSortedMap().entries.joinToString("\n") { (key, value) ->
            "$key = $value"
        } + "\n"
    }

    private fun ownLogcat(): String = try {
        val process = ProcessBuilder("logcat", "-d", "-t", "3000", "--pid", Process.myPid().toString()).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(5, TimeUnit.SECONDS)
        text
    } catch (error: Exception) {
        error.toString()
    }
}
