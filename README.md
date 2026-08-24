# DNS 测速

基于 Kotlin + Jetpack Compose + Miuix 的 Android DNS 测试工具。应用会主动发起 **DoH（RFC 8484）** 和 **DoT（RFC 7858）** 查询，记录 TCP 连接、TLS 握手、首字节和整段耗时，并展示解析结果，方便对比 Wi-Fi、蜂窝等网络下的延迟、稳定性和应答差异。

## 功能

- 预置 Cloudflare、Google、Quad9、AdGuard、AliDNS、DNSPod、OpenDNS 的 DoH / DoT
- 使用引导 IP，避免查询过程依赖系统 DNS
- 完整事件时间线：引导解析、TCP、TLS、HTTP/长度前缀读写、报文解析
- 多服务器并行、多轮次抖动观察
- 识别当前网络类型、计费状态、系统私有 DNS
- 历史会话保存在本机

## 环境

- JDK 17
- Android SDK（`compileSdk` / `targetSdk` 36，`minSdk` 24）
- 建议使用 Gradle Wrapper

## 命令行

```bash
# 代码风格
./gradlew ktlintCheck
./gradlew ktlintFormat

# 单元测试
./gradlew testDebugUnitTest

# 调试包
./gradlew assembleDebug
```

Windows PowerShell 可用 `.\gradlew.bat` 代替 `./gradlew`。

## adb

```bash
adb devices
.\gradlew.bat installDebug
adb shell am start -n com.dnsspeedtest.app/.MainActivity
adb logcat -s DnsSpeedtest DnsQueryEngine DohClient DotClient
```

安装当前 APK：

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 技术栈

- Kotlin 2.3.20、AGP 9.0.1、Compose BOM 2026.03.01
- Miuix 0.9.3
- OkHttp 5（DoH）、`SSLSocket`（DoT）
- DataStore + kotlinx.serialization
- ktlint Gradle 插件
