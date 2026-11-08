> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.112 / 相机 7.013.51 / 相册 17.10.41）适配版，单包无 Lite。

## 499 的判断是错的

499 把这个闪退归给了模式库：认为 7.013.51 改了 `mode_data.db` 某一列的类型，旧表读出来就炸，
于是让库跟着相机包重建。刷上去之后相机**还是**点开就退，栈一模一样：

```
Caused by: java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.String
	at mp.h.c(DataBaseOperator.java:75)
	at mp.h.h(DataBaseOperator.java:335)
	at mp.h.<init>(DataBaseOperator.java:84)
	at com.oplus.camera.Camera.s0(Camera.java:485)
	at la.d0.onCreate(QuickActivity.java:113)
```

这次是插着数据线实测的，能直接看见错在哪一步：

- 标记文件写下去了：`/data/user/0/com.oplus.camera/files/jiege/profile/mode_db_reset` 内容是
  `499:170996598-1794156656740`，说明 `ModeDataReset` 确实跑过；
- `mode_data.db` 被删掉后由相机重新种出来（22 行，`mtime` 就是崩溃那次启动）；
- 把新库拉下来 `sqlite3` 摊开看，`support_edit` / `support_front` 存的是 `'true'` / `'false'`，
  `mode_name` / `position` 是文本，没有一列混进整数。

**所以库没坏，坏的是喂给它的东西。** 删库重种只会用同一份坏料再种一遍，这就是 499 没用的原因。

## 真正的那一行

`mp.h` 是 `DataBaseOperator` 的混淆名。把 7.013.51 的 `base.apk` 拆开、`dexdump` 出 `mp.h.c`：

```
invoke-static {v0}, Lcom/oplus/camera/configure/CameraConfig;.u:(Ljava/lang/String;)Ljava/util/List;
...
check-cast v5, Ljava/lang/String;                    // 模式名
check-cast v6, Ljava/lang/String;                    // rank
invoke-static {v6}, Ljava/lang/Integer;.parseInt:(Ljava/lang/String;)I
```

它读的是 `com.oplus.feature.move.mode.to.headline` / `...to.panel` 这两条配置，
拿到一个 **(模式名, rank) 的扁平列表，两半都按 String 取**，rank 再 `parseInt`。
另一半是模块自己填进去的，在 `ModePlacement.serveHeadline`：

```java
moved.add(XPAN);
moved.add(Integer.valueOf(XPAN_HEADLINE_RANK));   // ← 499 及之前一直是这一行
```

追加的 rank 是 `Integer`，相机的 `check-cast ... String` 当场抛 `ClassCastException`。
`Camera.s0` 在 `onCreate` 里就构造这个 operator，所以是**一开相机就退**，模块的其它钩子连跑都来不及跑。

顺带解释了一个疑点：这行不是 500 才有的。它在 7.013.40 上不炸，只能说明那一版的读法不是强转
（手上只有 7.013.51 的 APK，没法再往下确认）；7.013.51 换成强转 + `parseInt` 之后，
这个类型上的偷懒就成了开关。

## 500 改了什么

`modes/ModePlacement.java` 加了一个 `rankLike(template, rank)`：**照读出来的那个元素的类型回填**。
配置给的是 String 就交 String，是数字才交数字。三处都换掉了：

- `serveHeadline` 追加 XPAN 的 rank；
- `promoteXpanInPanel` 里给 XPAN 让位的那些 rank（`other + 1`）；
- `promoteXpanInPanel` 里 XPAN 自己的 rank。

没有写成"一律 String"，是因为下一个相机版本要是真按数字读，写死字符串就会以另一种方式再炸一次；
跟着上一位元素走，两种情况都成立。

`ModeDataReset` 留着没动，`VERSION` 还是 `499`：库跟着相机包重建这件事本身是对的（模式表确实
由相机种一次，之后只查），只是它不是这次的病因。因为版本号没变，**这一版不会删库，不用重排模式栏**。

## 实测

装 500、重启、`am start -n com.oplus.camera/.Camera`：

- `topResumedActivity=com.oplus.camera/.Camera`，取景画面正常出图；
- `logcat -b crash` 里 `FATAL` 计数为 0；
- 切到视频、人像各一次，进程不重启、不闪退；
- 库里的结果与预期一致：

```
professional   position_headline  rank=0
retroCamera    position_headline  rank=1
commonVideo    position_headline  rank=2
common         position_headline  rank=3
gr             position_headline  rank=4
portrait       position_headline  rank=5
xpan           position_headline  rank=6     ← 追加进来的那一对
highPixel      position_headline  rank=7
more           position_headline  rank=2147483647
```

`xpan` 落在理光 GR 和人像之后、高像素之前，说明这条列表确实被相机读进去并种成了行——
499 崩在这一步，库是空的。

## 还没解决的

7.013.51 上 14 个符号仍然失配（快门声、POP 快门重绘、物理柔光成片、哈苏超清入口、
滤镜面板等），逐个被接住、只降级不崩，清单见 `docs/RELEASE-499.md`。

## 版本

Muse 4.28 GT7Pro（500）。
