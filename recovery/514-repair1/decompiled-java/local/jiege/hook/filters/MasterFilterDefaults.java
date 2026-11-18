package local.jiege.hook.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterFilterDefaults {
    private static final String CUBE_LUT_ID = "Radiance.cube.rgb.bin";
    private static final String NAME_RESOURCE = "camera_filter_oplus_master_radiance_new";

    private MasterFilterDefaults() {
    }

    private static void classic(List<FilterEntry> list, String str, String str2, boolean z) {
        list.add(new FilterEntry(str, NAME_RESOURCE, str2, FilterEntry.Category.CLASSIC, FilterEntry.Source.STOCK_RESTORED, FilterEntry.Placement.TAIL, null, z));
    }

    public static List<FilterEntry> entries(MasterConfig masterConfig) {
        ArrayList arrayList = new ArrayList();
        X10Filters.add(arrayList);
        classic(arrayList, CUBE_LUT_ID, "大师童话", true);
        classic(arrayList, "Emerald.cube.rgb.bin", "大师风光", true);
        classic(arrayList, "kodak.cube.rgb.bin", "菲林", true);
        classic(arrayList, "vivid-warm.cube.rgb.bin", "暖调", true);
        classic(arrayList, "fuji.cube.rgb.bin", "胶片", true);
        int i = 0;
        classic(arrayList, "morandi.cube.rgb.bin", "高级灰", false);
        classic(arrayList, "blackandwhite.cube.rgb.bin", "黑白", false);
        classic(arrayList, "mono.cube.rgb.bin", "单色", false);
        classic(arrayList, "cyberpunk-kitsch-3.1.CUBE.rgb.bin", "赛博朋克", false);
        classic(arrayList, "vivid-lut.cube.rgb.bin", "灰调", false);
        module(arrayList, "LeicaM9.cube.rgb.bin", "徕卡M9", FilterEntry.Category.LEICA);
        String[] strArr = {"ACROS", "ASTIA", "Classic Chrome", "ETERNA", "ETERNA BB", "Classic Neg.", "PRO Neg. Std", "PROVIA", "REALA ACE", "Velvia"};
        while (i < 10) {
            int i2 = i + 1;
            module(arrayList, String.format(Locale.ROOT, "LUT65_A%02d.cube.rgb.bin", Integer.valueOf(i2)), strArr[i], FilterEntry.Category.FUJI);
            i = i2;
        }
        module(arrayList, "JG_FUJI_NOSTALGIC_NEG.cube.rgb.bin", "Nostalgic Neg.", FilterEntry.Category.FUJI);
        FilterEntry.Category category = FilterEntry.Category.RICOH;
        module(arrayList, "JG_RICOH_POSITIVE.cube.rgb.bin", "Positive", category);
        module(arrayList, "JG_RICOH_NEGATIVE.cube.rgb.bin", "Negative", category);
        module(arrayList, "JG_RICOH_CINEMA_GREEN.cube.rgb.bin", "Cinema (Green)", category);
        module(arrayList, "JG_RICOH_CINEMA_YELLOW.cube.rgb.bin", "Cinema (Yellow)", category);
        module(arrayList, "JG_RICOH_VIVID.cube.rgb.bin", "Vivid", category);
        FilterEntry.Category category2 = FilterEntry.Category.FILM;
        module(arrayList, "JG_FILM_CINESTILL_50D.cube.rgb.bin", "CineStill 50D", category2);
        module(arrayList, "JG_FILM_FUJI_C200.cube.rgb.bin", "Fujicolor C200", category2);
        module(arrayList, "JG_FILM_HASSELBLAD_BLUE.cube.rgb.bin", "Hasselblad Blue", category2);
        module(arrayList, "JG_FILM_KODAK_5203.cube.rgb.bin", "Kodak 5203", category2);
        module(arrayList, "JG_FILM_KODAK_5207.cube.rgb.bin", "Kodak 5207", category2);
        module(arrayList, "JG_FILM_KODAK_5219.cube.rgb.bin", "Kodak 5219", category2);
        module(arrayList, "JG_FILM_KODAK_E200.cube.rgb.bin", "Kodak E200", category2);
        module(arrayList, "JG_FILM_KODAK_GOLD_200.cube.rgb.bin", "Kodak Gold 200", category2);
        module(arrayList, "JG_FILM_KODAK_PORTRA_400.cube.rgb.bin", "Kodak Portra 400", category2);
        module(arrayList, "JG_FILM_KODAK_ULTRAMAX_400.cube.rgb.bin", "Kodak UltraMax 400", category2);
        for (MasterConfig.Imported imported : masterConfig.imported) {
            arrayList.add(new FilterEntry(imported.id, NAME_RESOURCE, imported.title, FilterEntry.Category.IMPORTED, FilterEntry.Source.IMPORTED, FilterEntry.Placement.TAIL, CUBE_LUT_ID, true));
        }
        return arrayList;
    }

    private static void module(List<FilterEntry> list, String str, String str2, FilterEntry.Category category) {
        list.add(new FilterEntry(str, NAME_RESOURCE, str2, category, FilterEntry.Source.MODULE_LUT, FilterEntry.Placement.TAIL, CUBE_LUT_ID, true));
    }
}
