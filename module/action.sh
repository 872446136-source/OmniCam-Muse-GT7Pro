#!/system/bin/sh
# KernelSU 的模块卡片上按「执行」时运行这里。
#
# 目前只有一件事需要人在手机上决定：要不要用模块自带的 Meishe 渲染器
# （/odm/lib64/libmsnativefilter.so）替换原厂那一个。
#
# 为什么需要它：POP（Polaroid）和 GR 的四款胶片滤镜是 Meishe 的 renderer，不在 BasicTone 里。
# 本机原厂的 libmsnativefilter.so 只有 2,723 个导出，**没有** CNvPolaroid / CNvFilterCCD /
# CNvPortra400 / CNvShapeMask / CNvCompoundLutTiled，所以 POP 渲染不出画面（取景黑屏、成片纯绿），
# GR 除「标准」以外的四款也一样。模块带的是含这些 renderer 的版本。
#
# 这个按钮来回切：开着的时候按一下就关（写 /data/adb/omnicam_msfilter_off），再按一下再开。
# 改完要重启手机才生效，因为库在相机启动前就要挂好。
#
# 手动等价操作：
#   关：touch /data/adb/omnicam_msfilter_off && reboot
#   开：rm -f /data/adb/omnicam_msfilter_off && reboot

MODDIR=${0%/*}
OFF=/data/adb/omnicam_msfilter_off

say() {
    echo "$1"
    echo "$1" >> "$MODDIR/action.log"
}

if [ -f "$OFF" ]; then
    rm -f "$OFF"
    say "Meishe 渲染器替换：已开启"
    say "重启手机后 POP 和 GR 的四款胶片滤镜会用模块自带的渲染器。"
else
    : > "$OFF"
    say "Meishe 渲染器替换：已关闭"
    say "重启手机后回到原厂渲染器，POP 与 GR 胶片滤镜仍然是黑屏，其余功能不受影响。"
fi

# 相机正在跑的时候换库没有意义，下一次启动才会重新 dlopen。
am force-stop com.oplus.camera 2>/dev/null
say "已关闭相机进程；请重启手机让改动生效。"
