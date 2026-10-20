#!/system/bin/sh
# Stage the data the Java hooks read. Nothing here replaces a library or the camera config: this is
# the GR port's LMT tree, the watermark artwork and a handful of camera settings keys.
#
# Removed with the algorithm stack (see Profile.java): master colour engine data, POP path remap and
# pld_watermark, 25MP config copies, 200MP decision copies, tilt-shift mode row, the gallery palette
# jar (libCombineLut), the retro / tilt / flash-level feature flags and the audio volume curve.

MODDIR=${0%/*}

[ -f /data/adb/omnicam_nostack_off ] && exit 0

PREF_DIR=/data/user/0/com.oplus.camera/shared_prefs
PREF_FILE="$PREF_DIR/override_config_data.xml"
APP=/data/user/0/com.oplus.camera
OUT=$APP/files/ricoh_gr/lmt
GR_AVAILABLE='0.6(14),1(23),2(47),3(70),6(139),10(230)'
GR_MARKED='1.2173913(28),1.7391304(40)'

until [ -d "$APP" ]; do
    sleep 1
done

APP_UID=$(stat -c %u "$APP")
mkdir -p "$PREF_DIR"
chown "$APP_UID:$APP_UID" "$PREF_DIR"
chmod 0770 "$PREF_DIR"

if [ ! -f "$PREF_FILE" ]; then
    printf '%s\n' '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>' '<map>' '</map>' > "$PREF_FILE"
fi

set_config() {
    KEY=$1
    VALUE=$2
    if grep -q "name=\"$KEY\"" "$PREF_FILE"; then
        sed -i "s#<string name=\"$KEY\">[^<]*</string>#<string name=\"$KEY\">$VALUE</string>#" "$PREF_FILE"
    else
        sed -i "/<\/map>/i\    <string name=\"$KEY\">$VALUE</string>" "$PREF_FILE"
    fi
}

# GR mode in the mode list, and the HDR capture modes it is allowed to join.
set_config com.oplus.gr.mode.support 1
set_config com.oplus.camera.mode.data.db.version 102
set_config com.oplus.available.gr.mode.zoomvalues "$GR_AVAILABLE"
set_config com.oplus.gr.mode.marked.zoomvalues "$GR_MARKED"
set_config com.oplus.camera.capture.hdr.cap.mode.value 'common,portrait,professional,night,highPixel,xpan,underWater,telephoto,gr'
set_config com.oplus.camera.preview.hdr.cap.mode.value 'common,professional,night,highPixel,xpan,underWater,telephoto,gr'

chown "$APP_UID:$APP_UID" "$PREF_FILE"
chmod 0660 "$PREF_FILE"
restorecon "$PREF_FILE"

# GR preview/capture LMT tree: the phone's own LMTs, with the GR profile's SC16 / SC32 / CWCM
# adjustment tables from payload/gr_adjustment_lmt in place of the stock ones. Palette LUTs
# (LMTPhotoLut*, SCLut*) stay in the shared ODM tree, GR uses its own profile.
mkdir -p "$OUT"
chown "$APP_UID:$APP_UID" "$APP/files"
chmod 0770 "$APP/files"
for ORIGINAL in /odm/etc/camera/basictone/lmt/*; do
    NAME=${ORIGINAL##*/}
    case "$NAME" in
        LMTPhotoLut*|SCLut*)
            continue
            ;;
        SC16*|SC32*|CWCM*)
            cp "$MODDIR/payload/gr_adjustment_lmt/$NAME" "$OUT/$NAME"
            ;;
        *)
            if [ -L "$OUT/$NAME" ]; then rm "$OUT/$NAME"; fi
            if [ -d "$ORIGINAL" ]; then
                mkdir -p "$OUT/$NAME"
                cp -R "$ORIGINAL/." "$OUT/$NAME/"
            else
                cp "$ORIGINAL" "$OUT/$NAME"
            fi
            ;;
    esac
done
chown -R "$APP_UID:$APP_UID" "$APP/files/ricoh_gr"
restorecon -R "$APP/files/ricoh_gr"

# GR watermark fonts: the styles name their fonts by URL, which the camera cannot load. Fonts the
# gallery already downloaded for its watermark editor are copied where the GR hook finds them
# (FZLTZCHK is also in the camera's own files/video_watermark).
GALLERY=/data/user/0/com.coloros.gallery3d
until [ -d "$GALLERY" ]; do
    sleep 1
done
GALLERY_UID=$(stat -c %u "$GALLERY")
GALLERY_FONTS=$GALLERY/files/watermark_master/materia
mkdir -p "$APP/files/jiege/fonts"
for FONT in GilroySemiBold.otf FZLTZCHK.TTF; do
    [ -f "$GALLERY_FONTS/$FONT" ] && ! cmp -s "$GALLERY_FONTS/$FONT" "$APP/files/jiege/fonts/$FONT" && cp "$GALLERY_FONTS/$FONT" "$APP/files/jiege/fonts/$FONT"
done
chown -R "$APP_UID:$APP_UID" "$APP/files/jiege"
chmod 0771 "$APP/files/jiege"
restorecon -R "$APP/files/jiege"

# Gallery: GR watermark styles (JSON + webp artwork, no algorithm data) and the switch that lets the
# gallery show them.
GALLERY_PREF="$GALLERY/shared_prefs/business_featureSwitch_config.xml"
WAIT=0
while [ ! -f "$GALLERY_PREF" ] && [ "$WAIT" -lt 120 ]; do
    sleep 1
    WAIT=$((WAIT + 1))
done
if [ -f "$GALLERY_PREF" ]; then
    if grep -q 'name="business_featureSwitch_is_camera_gr_supported"' "$GALLERY_PREF"; then
        sed -i 's/name="business_featureSwitch_is_camera_gr_supported" value="false"/name="business_featureSwitch_is_camera_gr_supported" value="true"/' "$GALLERY_PREF"
    else
        sed -i '/<\/map>/i\    <boolean name="business_featureSwitch_is_camera_gr_supported" value="true" />' "$GALLERY_PREF"
    fi
    chown "$GALLERY_UID:$GALLERY_UID" "$GALLERY_PREF"
    chmod 0660 "$GALLERY_PREF"
    restorecon "$GALLERY_PREF"
fi

STYLE_OUT="$GALLERY/files/ricoh_gr_styles"
mkdir -p "$STYLE_OUT"
cp "$MODDIR/payload/watermark_master_styles/"*.json "$STYLE_OUT/"
if [ -d "$MODDIR/payload/watermark_drawables" ]; then
    cp "$MODDIR/payload/watermark_drawables/"*.webp "$STYLE_OUT/" 2>/dev/null || true
fi
chown -R "$GALLERY_UID:$GALLERY_UID" "$STYLE_OUT"
chmod 0644 "$STYLE_OUT"/*
restorecon -R "$STYLE_OUT"

# Camera: the same styles, in the layout the camera's watermark list reads.
CAM_ML="$APP/files/mode_limit_watermark"
mkdir -p "$CAM_ML"
for i in 1 2 3 4 5; do
    ID="gr_style_${i}"
    NAME="gr_style_${i}.json"
    JSON="$MODDIR/payload/watermark_master_styles/$NAME"
    if [ -f "$JSON" ]; then
        mkdir -p "$CAM_ML/$ID"
        cp -f "$JSON" "$CAM_ML/$ID/$NAME"
        cp -f "$JSON" "$CAM_ML/$NAME"
        if [ -d "$MODDIR/payload/watermark_drawables" ]; then
            cp -f "$MODDIR/payload/watermark_drawables/"*.webp "$CAM_ML/$ID/" 2>/dev/null || true
        fi
    fi
done
if [ -d "$MODDIR/payload/watermark_drawables" ]; then
    cp -f "$MODDIR/payload/watermark_drawables/"*.webp "$CAM_ML/" 2>/dev/null || true
fi
chown -R "$APP_UID:$APP_UID" "$CAM_ML"
find "$CAM_ML" -type f -exec chmod 0644 {} \;
find "$CAM_ML" -type d -exec chmod 0755 {} \;
restorecon -R "$CAM_ML"
