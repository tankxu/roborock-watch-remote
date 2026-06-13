# 扫地机遥控 · Android 手表 App

在 Android 手表上**直接遥控石头扫地机**的遥控器:十字方向键(按住走/松手停)+ 中心回充键。普通 Android app(非 Wear OS,Kotlin,minSdk 26 / Android 8.0),为**小天才 Z9**(320×360)做。界面沿用 [roborock-cardboard-remote](https://github.com/tankxu/roborock-cardboard-remote) 网页控制台风格(深色 + 圆角方块 D-pad + 按下发光)。

## 和悟空遥控的区别:不需要电脑中枢

悟空要 Frida 注入,所以走「手表 → 电脑 server.py → 机器人」。**扫地机走的是局域网 miIO 协议(UDP),手表能直接发给扫地机**,所以:

```
┌──────────┐   WiFi / UDP 54321 + AES   ┌──────────────┐
│ 手表 App  │ ─────────────────────────> │   扫地机       │
│ (本App)   │   miIO (握手/加密全在手表)   │  Roborock     │
└──────────┘                            └──────────────┘
```

手表和扫地机在同一 WiFi 即可,**不用电脑、不用云、不用 ESP32**。miIO 的握手 + AES-128-CBC + MD5 校验全部用 Kotlin 在手表上实现(`Miio.kt`)。

## 交互

- 十字方向键:**按住** ↑前进 / ↓后退 / ←左转 / →右转,**松手即停**(死手保护,松手/断连即停)
- 中心 ⟳ 键:回充(退出遥控 + `app_charge`)
- 顶部状态条:显示「扫地机在线/离线/遥控中」,**点它改配置**(IP / token / DID,存 SharedPreferences)

## 配置

首次内置默认值在 `Config.kt`(IP / token / DID)。换设备就点状态条改,或改 `Config.kt`。
- **token / DID 获取**:见 [roborock-cardboard-remote](https://github.com/tankxu/roborock-cardboard-remote) 的 README(`miiocli cloud` 或 vevs 日志)
- 连不上起始 IP 时会**自动扫整个网段**按 DID 找(防 DHCP 换 IP)

> ⚠️ `Config.kt` 含 token(控制扫地机的密钥)。若公开本仓库,请将其加入 `.gitignore` 并提供 `Config.example.kt`。

## 构建与安装

JDK 17 + Android SDK,命令行即可(Gradle wrapper 锁 8.9,compileSdk 34):

```bash
cd roborock-watch-remote
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk

# 手表开 adb-over-WiFi (root): su -c 'setprop service.adb.tcp.port 5555; stop adbd; start adbd'
adb connect <手表IP>:5555
adb -s <手表IP>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

> 图标沿用传统 PNG mipmap,兼容 Z9 的 xtc launcher。

## 协议适用范围

miIO 本地协议对**绑定在米家/小米云上的 Roborock 扫地机通用**,换台机器只需改 token/DID/IP。绑 Roborock 自家 App 的设备不适用。
