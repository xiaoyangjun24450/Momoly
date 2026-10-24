# 在 XYJ 工作区开发

本目录是独立 Git 仓库，origin 为 `git@github.com:weiruchenai1/momoly.git`。保留原有源码、模块结构、依赖版本与远程；宿主不跟踪本目录，提交和推送在本仓库内进行。

## 工具链

本仓库锁定的工具版本比宿主自用 App 新，因此通过 `windows-host.json` 使用独立工具链，宿主既有 App 不受影响：

- Gradle 9.7.0：使用宿主 `.tools/downloads/gradle-9.7.0-bin.zip` 的官方发行包（入口按 `scripts/windows_env.ps1` 中固定的官方 SHA-256 校验后解压到 `.tools/gradle/gradle-9.7.0`）；压缩包缺失时回退到本仓库自带的 `gradlew.bat`。依赖缓存在共享的 `.gradle-home/`。
- AGP 9.3.1、Kotlin 2.4.10，`compileSdk`/`targetSdk` 37、`minSdk` 26。
- JDK 21：`gradle/gradle-daemon-jvm.properties` 要求 Gradle daemon 使用 21（源码 Java 目标仍为 17），放在宿主 `.tools/jdk-21/`。
- Android SDK 平台 `platforms;android-37.0` 与 AGP 9.3.1 默认的 `build-tools;36.0.0`，SDK 根目录共用宿主 `.tools/android-sdk/`。

工具安装见宿主 `docs/AGENT_SETUP.md`；`.tools` 与 `.gradle-home` 均被宿主忽略，不提交。

## 命令

在本目录或任意工作目录运行：

```powershell
.\windows_build.bat -BuildOnly        # 仅编译 debug
.\windows_build.bat -Abi arm64-v8a    # 编译并安装指定 ABI
.\windows_build.bat -Serial 序列号     # 多设备时指定目标
.\windows_simulation.bat              # 查看当前应用 Info 及以上日志
```

入口调用宿主 `scripts/windows_repository.ps1`，`windows-host.json` 声明构建任务 `:app:assembleDebug`、APK 元数据位置、使用的 JDK/SDK 与 Gradle 版本。

`services.gradle.org` 会重定向到 GitHub CDN，自动下载可能很慢或超时；此时用浏览器从 https://services.gradle.org/distributions/gradle-9.7.0-bin.zip 下载完整 ZIP，放到宿主 `.tools/downloads/gradle-9.7.0-bin.zip`，入口会按官方 SHA-256 校验后再解压。

## 构建与安装

- 产物：`app/build/outputs/apk/debug/`。debug 开启 ABI 拆分，输出 `arm64-v8a` 与 `x86_64` 两个 APK，`output-metadata.json` 记录二者。
- 安装时按已连接设备的 `ro.product.cpu.abi` 选择对应 APK；设备不匹配或未连接时需要显式 `-Abi arm64-v8a` / `-Abi x86_64`，脚本不会把两个 APK 混装。
- debug 包名为 `me.rerere.momoly.debug`，与 release 共存，数据目录独立。
- `local.properties` 由入口写入，只替换 `sdk.dir` 并保留其他键；已被本仓库忽略。
- `keystore.properties`、`google-services.json`、`BUGLY_APP_ID` 不入库：缺失时 debug 可编译；release 保持未签名，Firebase/Bugly 上报与 CI 发布流程不在本工作区验证范围。

## 回归验证

与来源 CI 一致的本地检查（需联网下载依赖）。直接运行 `gradlew.bat` 不会加载宿主环境，需先设好 `JAVA_HOME`（`.tools/jdk-21`）、`ANDROID_HOME`、`GRADLE_USER_HOME`：

```powershell
.\gradlew.bat checkArchitecture
.\gradlew.bat testDebugUnitTest
```

## 未验证范围

- 真机/模拟器上的业务功能（模型 API、联网搜索、TTS、插件、语音朗读）需要各自的凭据与服务，本次只验证编译、单元测试、架构检查与 APK 元数据。
- adb 安装链路已执行到 `adb install`，被手机系统的「USB 安装」授权拦下（`INSTALL_FAILED_USER_RESTRICTED`），因此应用未安装、未启动，`windows_simulation.bat` 的日志入口未实测。
- release 签名、版本号脚本、GitHub Actions 发布流程未在本机复现。

本次迁移只新增 Windows 入口与说明，不改动业务源码、模块结构、依赖版本、applicationId 和远程仓库。

## 常见问题

- 首次构建需要联网下载 AGP/Kotlin/Compose 依赖。本机到 `dl.google.com`、GitHub CDN 偶发连接超时，表现为 `Plugin ... was not found` 或 `Could not resolve ...`；确认是网络原因时直接重试，已下载的部分会复用。
- 安装报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`：手机系统（如 MIUI）需在开发者选项开启「USB 安装」并在手机上确认，属于设备侧授权，不是构建失败。
- Gradle daemon 用 JDK 21，而 `build-logic` 的 Java toolchain 要求 17：宿主入口会把 `.tools` 下所有 JDK 注册到 `.gradle-home/gradle.properties` 的 `org.gradle.java.installations.paths`，两个版本的 JDK 都需保留。
