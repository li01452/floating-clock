# 毫秒悬浮钟

极简原生安卓悬浮时钟。Java 编写，无第三方依赖，支持 Android 7.0 及以上。

## 功能

- 显示 `HH:mm:ss.S`，秒保留一位小数。
- 悬浮窗可拖动，位置自动保存。
- 大小在 50%～200% 之间调节。
- 可切换透明背景，数字有细黑描边。
- 设置立即生效并自动保存。
- 点击悬浮条右侧 × 或主页按钮关闭。

安装后开启“显示在其他应用上层”权限。时间取自手机系统，建议开启自动设置时间。显示小数不代表与抢票服务器同步。无需网络、无障碍或存储权限。

## 构建

需要 Java 17 JDK 和 Android SDK（platforms;android-35、build-tools;35.0.0）。设置 ANDROID_HOME 或 ANDROID_SDK_ROOT，运行：

```bash
bash build.sh
```

输出 `build/MillisecondClock.apk`。默认在本地生成并复用开发签名。要沿用已有证书，可设置 `SIGNING_KEYSTORE`、`SIGNING_STORE_PASSWORD` 和 `SIGNING_KEY_ALIAS`。不要将证书或密码提交到仓库。原安装包的签名材料保存在原始源码备份中，不包含在这个仓库里；新证书签名的 APK 无法直接覆盖旧证书安装的应用。

## 验证情况

1.0 已由用户在安卓设备上确认可用。1.1 已通过编译、APK 结构、签名及新旧签名一致性检查，尚未完成设备运行验证。
