"""Exercise the actual export hook against both gallery interface shapes on the JVM."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {
'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;
import java.util.*;
public abstract class XC_MethodHook {
  public static class MethodHookParam {
    public Object thisObject; public Object[] args; public Object result; public boolean replaced;
    private final Map<String,Object> extras = new HashMap<>();
    public void setResult(Object r) { result=r; replaced=true; }
    public Object getResult() { return result; }
    public void setObjectExtra(String k,Object v) { extras.put(k,v); }
    public Object getObjectExtra(String k) { return extras.get(k); }
  }
  protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
  protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
}''',
'de/robv/android/xposed/XposedBridge.java': '''package de.robv.android.xposed;
import java.util.*; import java.lang.reflect.*;
public class XposedBridge {
  private static final Map<Method,XC_MethodHook> hooks = new HashMap<>();
  public static void hookMethod(Method m,XC_MethodHook h) { hooks.put(m,h); }
  public static void clear() { hooks.clear(); }
  public static Object invoke(Method m,Object o,Object... args) throws Throwable {
    XC_MethodHook h=hooks.get(m); XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();
    p.thisObject=o; p.args=args;
    if(h!=null) h.beforeHookedMethod(p);
    try { if(!p.replaced) p.result=m.invoke(o,args); }
    catch(InvocationTargetException e) { throw e.getCause(); }
    finally { if(h!=null) h.afterHookedMethod(p); }
    return p.result;
  }
}''',
'local/jiege/settings/ModuleSettings.java': '''package local.jiege.settings;
public class ModuleSettings { public static final String VIDEO_OLIVE_UNLIMITED="video_olive_unlimited"; }''',
'local/jiege/hook/common/Settings.java': '''package local.jiege.hook.common;
public class Settings { public static boolean enabled; public static boolean get(String k) { return enabled; } }''',
'local/jiege/hook/common/Symbols.java': '''package local.jiege.hook.common;
import java.util.*; import java.lang.reflect.*;
public class Symbols {
  public static final Symbols INSTANCE=new Symbols(); public final Map<String,Object> entries=new HashMap<>();
  public static Symbols get() { return INSTANCE; }
  public boolean has(String... keys) { for(String k:keys) if(!entries.containsKey(k)) return false; return true; }
  public Method method(String k) { if(!has(k)) throw new AssertionError("unavailable method "+k); return (Method)entries.get(k); }
  public Field field(String k) { return (Field)entries.get(k); }
}''',
'VideoOliveHarness.java': '''import java.lang.reflect.*;
import de.robv.android.xposed.XposedBridge;
import local.jiege.hook.common.*;
import local.jiege.hook.gallery.VideoOliveDuration;
public class VideoOliveHarness {
  public static class Config { public static int getInt(String key,int fallback) { return fallback; } }
  public static class Limits { public static long maxMs() { return 5000; } }
  public static class Task {
    public boolean isMenuExport; public boolean fail; public long observed;
    public void run() throws Throwable {
      observed=(Long)XposedBridge.invoke(Limits.class.getMethod("maxMs"),null);
      if(fail) throw new IllegalStateException("simulated export failure");
    }
  }
  private static void check(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
  public static void main(String[] args) throws Throwable {
    Method config=Config.class.getMethod("getInt",String.class,int.class);
    Symbols s=Symbols.get(); s.entries.put("Config.getInt",config);
    VideoOliveDuration.installGallery(VideoOliveHarness.class.getClassLoader());
    Settings.enabled=false;
    check((Integer)XposedBridge.invoke(config,null,"video_editor_olive_save_max_duration",12)==12,"disabled preserves stock");
    Settings.enabled=true;
    check((Integer)XposedBridge.invoke(config,null,"video_editor_olive_save_max_duration",12)==86400,"older gallery config-only path");
    check((Integer)XposedBridge.invoke(config,null,"editor_olive_to_gif_max_duration",12)==12,"unrelated export limit preserved");
    XposedBridge.clear();
    Method run=Task.class.getMethod("run"), max=Limits.class.getMethod("maxMs");
    s.entries.put("ExportOliveTask.run",run); s.entries.put("OliveExportLimit.maxMs",max);
    s.entries.put("ExportOliveTask.isMenuExport",Task.class.getField("isMenuExport"));
    VideoOliveDuration.installGallery(VideoOliveHarness.class.getClassLoader());
    Task editor=new Task(); XposedBridge.invoke(run,editor);
    check(editor.observed==86400000L,"newer gallery editor bound");
    check((Long)XposedBridge.invoke(max,null)==5000L,"context restored after success");
    Task menu=new Task(); menu.isMenuExport=true; XposedBridge.invoke(run,menu);
    check(menu.observed==5000L,"normal menu export preserved");
    editor.fail=true;
    try { XposedBridge.invoke(run,editor); throw new AssertionError("failure swallowed"); }
    catch(IllegalStateException expected) {}
    check((Long)XposedBridge.invoke(max,null)==5000L,"context restored after export failure");
    Settings.enabled=false; editor.fail=false; XposedBridge.invoke(run,editor);
    check(editor.observed==5000L,"disabled newer gallery preserves limit");
    System.out.println("Video olive: older gallery, config isolation, newer editor scope and failure cleanup passed");
  }
}''',
}
with tempfile.TemporaryDirectory(prefix='omnicam_olive_') as temp:
    folder = Path(temp)
    for name, text in SOURCES.items():
        path = folder / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
    sources = [str(p) for p in folder.rglob('*.java')]
    sources.append(str(ROOT / 'app/src/main/java/local/jiege/hook/gallery/VideoOliveDuration.java'))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, *sources], check=True)
    subprocess.run(['java', '-cp', temp, 'VideoOliveHarness'], check=True)
