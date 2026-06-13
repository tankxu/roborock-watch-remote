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
- 中心 ⌂ 键:回充(退出遥控 + `app_charge`)
- 顶部状态条:绿点「在线」/ 红点「离线」,**点它改配置**(IP / token / DID,存 SharedPreferences)

## 配置

敏感信息(IP / token / DID)放在 `app/src/main/java/com/tankxu/roborock/watch/Config.kt`,**已被 .gitignore 排除、不上传**。首次构建前从模板复制并填写:

```bash
cd app/src/main/java/com/tankxu/roborock/watch
cp Config.kt.example Config.kt
# 编辑 Config.kt 填入你的 VAC_IP / VAC_TOKEN / VAC_DID
```

- **token / DID 获取**:见 [roborock-cardboard-remote](https://github.com/tankxu/roborock-cardboard-remote) 的 README(`miiocli cloud` 或 vevs 日志)
- 装好后也可**点 App 顶部状态条**直接改 IP/token/DID(存 SharedPreferences)
- 连不上起始 IP 时会**自动扫整个网段**按 DID 找(防 DHCP 换 IP)
- ⚠️ `Config.kt` 含 token = 控制扫地机的密钥,切勿提交或公开

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

> 图标用 PNG mipmap(圆形方向盘+扫地机),兼容 Z9 的 xtc launcher。

## 实测要点(小天才 Z9)

- ✅ 已实机验证:同一 WiFi 下手表**直连**扫地机,顶部显示「● 在线」,按住方向键可遥控。
- 手表 WiFi 延迟大(实测 RTT 可达 1s+),已把 miIO socket 超时设到 **3s**,并做**连接重试 ×3 + 每 3 秒自动重连**;打开 App 后稍等几秒会变「在线」。
- 扫地机需在线(在米家里能看到、未深度休眠)。
- **adb-over-WiFi 重启手表后会失效**,需重新在手表上 root 执行上面的 `setprop` 命令,再 `adb connect`。
- Z9 launcher 会缓存图标,**换图标后可能要重启手表才刷新**;adb 截图在该 ROM 上不稳定(常返回空)。

## 协议适用范围

miIO 本地协议对**绑定在米家/小米云上的 Roborock 扫地机通用**,换台机器只需改 token/DID/IP。绑 Roborock 自家 App 的设备不适用。
