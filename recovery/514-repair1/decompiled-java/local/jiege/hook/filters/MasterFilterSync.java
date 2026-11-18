package local.jiege.hook.filters;

import android.app.AndroidAppHelper;
import android.content.Context;
import de.robv.android.xposed.XSharedPreferences;
import defpackage.c3;
import defpackage.u2;
import defpackage.vl0;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import local.jiege.hook.common.Diagnostics;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterFilterSync {
    private static final String KNOWN_IMPORTS = "jiege/imported_ids.txt";
    private static final String LUT_DIR = "jiege/lut";
    private static final String TAG = "MasterFilterSync";
    private static boolean loaded;
    private static String loadedStamp;
    private static final Set<String> removedImports = new LinkedHashSet();

    private MasterFilterSync() {
    }

    public static synchronized boolean changedSinceLoad() {
        return loaded && !(stamp(settingsDir()).equals(loadedStamp) && Settings.get("normal_follows_master") == FilterCatalog.mirrorsMaster());
    }

    private static Context context(Context context) {
        return context != null ? context : AndroidAppHelper.currentApplication();
    }

    public static synchronized void load(Context context) {
        File file;
        if (loaded) {
            return;
        }
        Context context2 = context(context);
        if (context2 == null) {
            return;
        }
        loaded = true;
        MasterConfig masterConfigEmpty = MasterConfig.empty();
        File file2 = settingsDir();
        loadedStamp = stamp(file2);
        if (file2 == null) {
            file = null;
        } else {
            try {
                file = new File(file2, "master_config.txt");
            } catch (Throwable th) {
                Log.e(TAG, "configuration unreadable, using defaults", th);
            }
        }
        if (file != null && file.isFile()) {
            masterConfigEmpty = MasterConfig.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        }
        FilterCatalog.configure(masterConfigEmpty, Settings.get("normal_follows_master"));
        MasterLayoutStore.set(masterConfigEmpty.layout());
        trackImports(context2, masterConfigEmpty);
        new Thread(new u2(context2, file2, masterConfigEmpty), "JiegeLutSync").start();
        Log.i(TAG, "configuration: " + masterConfigEmpty.order.size() + " ordered, " + masterConfigEmpty.visible.size() + " visibility, " + masterConfigEmpty.imported.size() + " imported");
    }

    public static void reportStock(List<String> list, List<String> list2) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).replace('\t', ' '));
            sb.append('\t');
            sb.append(list2.get(i) == null ? PresetSelection.STANDARD_KEY : list2.get(i).replace('\t', ' ').replace('\n', ' '));
            sb.append('\n');
        }
        Diagnostics.setMasterStock(sb.toString());
    }

    private static File settingsDir() {
        try {
            File file = new XSharedPreferences("local.jiege.camera", "module_settings").getFile();
            if (file == null) {
                return null;
            }
            return file.getParentFile();
        } catch (Throwable th) {
            Log.e(TAG, "settings directory unavailable", th);
            return null;
        }
    }

    private static String stamp(File file) {
        File file2 = file == null ? null : new File(file, "master_config.txt");
        if (file2 == null || !file2.isFile()) {
            return "none";
        }
        return file2.lastModified() + ":" + file2.length();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void syncLuts(Context context, File file, MasterConfig masterConfig) {
        File file2 = new File(context.getFilesDir(), LUT_DIR);
        file2.mkdirs();
        HashSet hashSet = new HashSet();
        Iterator<MasterConfig.Imported> it = masterConfig.imported.iterator();
        while (true) {
            if (!it.hasNext()) {
                File[] fileArrListFiles = file2.listFiles();
                if (fileArrListFiles == null) {
                    fileArrListFiles = new File[0];
                }
                for (File file3 : fileArrListFiles) {
                    if (!hashSet.contains(file3.getName())) {
                        file3.delete();
                    }
                }
                Log.i(TAG, "LUTs synced: " + hashSet.size());
                return;
            }
            MasterConfig.Imported next = it.next();
            hashSet.add(next.id);
            File file4 = new File(file2, next.id);
            if (!file4.isFile() || file4.length() != next.size) {
                File file5 = new File(file2, c3.l(new StringBuilder(), next.id, ".part"));
                if (file == null) {
                    continue;
                } else {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(vl0.d(file, next.id));
                        try {
                            FileOutputStream fileOutputStream = new FileOutputStream(file5);
                            try {
                                byte[] bArr = new byte[65536];
                                while (true) {
                                    int i = fileInputStream.read(bArr);
                                    if (i <= 0) {
                                        break;
                                    } else {
                                        fileOutputStream.write(bArr, 0, i);
                                    }
                                    try {
                                        fileInputStream.close();
                                    } catch (Throwable th) {
                                        th.addSuppressed(th);
                                    }
                                    throw th;
                                }
                                fileOutputStream.close();
                                fileInputStream.close();
                                if (!file5.renameTo(file4)) {
                                    Log.i(TAG, "LUT rename failed " + next.id);
                                }
                            } catch (Throwable th2) {
                                try {
                                    fileOutputStream.close();
                                } catch (Throwable th3) {
                                    th2.addSuppressed(th3);
                                }
                                throw th2;
                            }
                        } catch (Throwable th4) {
                            fileInputStream.close();
                            throw th4;
                        }
                    } catch (Throwable th5) {
                        file5.delete();
                        Log.e(TAG, "LUT copy failed " + next.id, th5);
                    }
                }
            }
        }
    }

    public static synchronized List<String> takeRemovedImports() {
        ArrayList arrayList;
        Set<String> set = removedImports;
        arrayList = new ArrayList(set);
        set.clear();
        return arrayList;
    }

    private static void trackImports(Context context, MasterConfig masterConfig) {
        File file = new File(context.getFilesDir(), KNOWN_IMPORTS);
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        Iterator<MasterConfig.Imported> it = masterConfig.imported.iterator();
        while (it.hasNext()) {
            linkedHashSet.add(it.next().id);
        }
        try {
            HashSet<String> hashSet = new HashSet();
            if (file.isFile()) {
                for (String str : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    if (!str.trim().isEmpty()) {
                        hashSet.add(str.trim());
                    }
                }
            }
            for (String str2 : hashSet) {
                if (!linkedHashSet.contains(str2)) {
                    removedImports.add(str2);
                }
            }
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), String.join("\n", linkedHashSet).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
            Set<String> set = removedImports;
            if (set.isEmpty()) {
                return;
            }
            Log.i(TAG, "imports removed: " + set);
        } catch (Throwable th) {
            Log.e(TAG, "import tracking failed", th);
        }
    }
}
