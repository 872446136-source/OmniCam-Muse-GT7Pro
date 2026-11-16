"""Run the real resource-hook installer with a key reader that rejects early access."""
import argparse
from pathlib import Path
import subprocess
import tempfile

FIXTURES = {
    'de/robv/android/xposed/XC_MethodHook.java': '''
package de.robv.android.xposed;
public class XC_MethodHook {
  protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
  public static class MethodHookParam {
    public Object[] args; public Object result;
    public boolean hasThrowable() { return false; }
    public Object getResult() { return result; }
    public void setResult(Object v) { result = v; }
  }
}''',
    'de/robv/android/xposed/XposedBridge.java': '''
package de.robv.android.xposed;
public class XposedBridge {
  public static XC_MethodHook installed;
  public static void hookMethod(java.lang.reflect.Method m, XC_MethodHook h) { installed = h; }
  public static void replay(Object... args) throws Throwable {
    XC_MethodHook.MethodHookParam p = new XC_MethodHook.MethodHookParam(); p.args = args;
    installed.afterHookedMethod(p);
  }
}''',
    'android/app/AndroidAppHelper.java': '''
package android.app;
public class AndroidAppHelper {
  public static Application currentApplication() { return null; }
}''',
    'local/jiege/hook/common/Symbols.java': '''
package local.jiege.hook.common;
public class Symbols {
  public static Symbols get() { return new Symbols(); }
  public void target() {}
  public java.lang.reflect.Method method(String key) {
    try { return getClass().getMethod("target"); } catch (Exception e) { throw new AssertionError(e); }
  }
}''',
    'local/jiege/hook/common/DataStore.java': '''
package local.jiege.hook.common;
public class DataStore {
  public static int reads;
  public static Object key(Symbols s, String k, String name) {
    reads++; throw new AssertionError("PrefKeyGlobal initialized before camera startup");
  }
  public static Object get(Symbols s, ClassLoader c, Object key, Object fallback) { return fallback; }
}''',
    'local/jiege/hook/common/Log.java': '''
package local.jiege.hook.common;
public class Log {
  public interface Action { void run() throws Throwable; }
  public static void guard(String t, String label, Action a) {
    try { a.run(); } catch (Throwable e) { throw new AssertionError(label, e); }
  }
  public static void i(String t, String m) {}
  public static void e(String t, String m, Throwable e) { throw new AssertionError(m, e); }
}''',
    'HasselStartupTest.java': '''
import local.jiege.hook.common.DataStore;
import local.jiege.hook.watermark.HasselResources;
import de.robv.android.xposed.XposedBridge;
public class HasselStartupTest {
  public static void main(String[] args) throws Throwable {
    HasselResources.installCamera(HasselStartupTest.class.getClassLoader());
    if (XposedBridge.installed == null || DataStore.reads != 0) throw new AssertionError("early key lookup");
    XposedBridge.replay("common", "", null, true);
    XposedBridge.replay("gr", "", null, false);
    XposedBridge.replay("retroCamera", "", null, false);
    XposedBridge.replay("common", "", null, false); // no Application yet
    if (DataStore.reads != 0) throw new AssertionError("unrelated mode accessed key");
    System.out.println("Hassel startup: no settings initialization during install, excluded modes or before Application");
  }
}''',
}


def run(android_jar):
    root = Path(__file__).resolve().parents[2]
    with tempfile.TemporaryDirectory(prefix='hassel-startup-') as temp:
        folder = Path(temp)
        for name, content in FIXTURES.items():
            file = folder / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content, encoding='utf-8')
        sources = [str(p) for p in folder.rglob('*.java')]
        sources.append(str(root / 'app/src/main/java/local/jiege/hook/watermark/HasselResources.java'))
        subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(android_jar), '-d', temp, *sources], check=True)
        import os
        subprocess.run(['java', '-cp', temp + os.pathsep + str(android_jar), 'HasselStartupTest'], check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('android_jar', type=Path)
    run(parser.parse_args().android_jar)
