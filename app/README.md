# Spikive GCS Android Host

## 模块目标

`app` 是 Spikive 地面站的 Android 宿主。它不是独立的飞控客户端，也不是 ROS/Foxglove 后端。

Android App 负责：

- 将主界面锁定为传感器横屏；Lichtblick 地面站不支持竖屏布局。
- 在 APK 内携带 Spikive-Lichtblick 的生产 Web 静态资源。
- 启动时只显示网络选择页，不启动本地 HTTP Server，也不创建 WebView。
- 分别发现 Android 标准 USB Ethernet 和当前 Wi-Fi 的本机 IP；忽略蜂窝网络。
- USB 网口和 Wi-Fi 都采用两次点击闭环：第一次“搜索 IP”，找到后按钮变为“进入地面站”，第二次才绑定网络并进入；退出后已使用的按钮恢复为“搜索 IP”。
- 只把 App 进程绑定到用户明确选择的 USB Ethernet 或 Wi-Fi `Network`，禁止回退到 4G/5G。
- 进入时才启动 `127.0.0.1:8766` 本地服务器和 WebView；所选网络断开时立即销毁 WebView、停止服务器并回到选择页。
- 显示 USB Ethernet/Wi-Fi 的本机 IP 和搜索状态。

网页负责：

- Lichtblick UI、三维可视化和多无人机状态。
- 作为 Foxglove WebSocket 客户端连接自组网模块或机载 Bridge。
- ROS1 Topic、消息 Schema、订阅、发布、航点和 Manager 命令逻辑。

默认数据路径：

```text
网络选择页
  -> 搜索 USB Ethernet 或 Wi-Fi 本机 IP
  -> 用户第二次点击“进入地面站”
  -> App 绑定到所选 Android Network
  -> HTTP 127.0.0.1:8766 加载 APK 内 Web 静态资源
  -> WebView 内 WebSocket 使用所选网络访问操作员填写的 Foxglove URL
```

Android 不固定、不校验 Foxglove 模块 IP。Web 页面可以继续使用默认地址，也允许操作员输入同一所选网络内的其他模块地址。

## 重要边界

- 默认可写范围是 `app/`。只有 Android 宿主确实需要时，才可修改根目录的 Android Gradle 配置。
- 只修改 Android 功能时，不要修改 `../webProject/Spikive-Lichtblick` 中的源码。
- 不要修改 Web `.webpack`、Android `build/`、`.gradle/`、IDE 元数据或依赖缓存等生成内容。
- USB 自组网设备按标准 Android Ethernet 网卡处理。
- 没有明确硬件证据时，不要使用 `UsbManager` 读取原始 USB 字节。
- Android 不解析 ROS 消息，不重新实现 Foxglove 协议服务端。
- 不要为网络路由问题增加 Android WebSocket 代理。
- 本地静态服务器必须只绑定 `127.0.0.1`，禁止绑定 `0.0.0.0`。
- 本地静态服务器固定使用 `127.0.0.1:8766`，保持 WebView Origin 稳定，从而保留 Lichtblick 的 LocalStorage、IndexedDB、布局和连接记录。
- 不要添加不必要的 `addJavascriptInterface` JavaScript Bridge。
- 不要在 Android 层自动重试飞行控制或 Manager 命令。
- 如果需求必须修改 Web 协议或业务逻辑，只完成 Android 侧接口并明确说明 Web 侧缺口，不得越界代改。

## 当前代码结构

| 路径 | 职责 |
| --- | --- |
| `src/main/java/com/example/spikivegcs/MainActivity.kt` | 双网络按钮闭环、WebView 配置和页面错误 |
| `src/main/java/com/example/spikivegcs/SpikiveHostViewModel.kt` | 网络搜索/进入状态、本地服务器与 WebView 生命周期 |
| `src/main/java/com/example/spikivegcs/network/NetworkAddressPolicy.kt` | 可用接口地址过滤，不绑定固定子网 |
| `src/main/java/com/example/spikivegcs/server/AssetWebServer.kt` | Loopback HTTP 静态资源服务器 |
| `src/main/java/com/example/spikivegcs/server/AssetRequestResolver.kt` | URL 规范化、SPA fallback 和 MIME 类型 |
| `src/main/java/com/example/spikivegcs/network/NetworkMonitor.kt` | USB Ethernet/Wi-Fi 发现、IP 状态与显式进程绑定 |
| `src/test/java/com/example/spikivegcs/server/AssetRequestResolverTest.kt` | 静态资源路径和 MIME 单元测试 |
| `src/test/java/com/example/spikivegcs/network/NetworkAddressPolicyTest.kt` | 任意子网和不可用地址过滤单元测试 |
| `build.gradle.kts` | Android 构建及 Lichtblick Web 资源构建/同步任务 |

修改前必须重新读取实际文件。本文描述的是架构约束，不保证文件始终保持当前实现细节。

## Web 资源构建

生产 Web 工程位于：

```text
../webProject/Spikive-Lichtblick
```

Web 生产输出位于：

```text
../webProject/Spikive-Lichtblick/web/.webpack
```

Android Gradle 中的任务：

| 任务 | 用途 |
| --- | --- |
| `:app:installSpikiveWebDependencies` | 使用 immutable lockfile 安装依赖，并跳过 Electron/Playwright 二进制下载 |
| `:app:buildSpikiveWeb` | 通过 Corepack 使用 `package.json` 声明的 Yarn 版本执行 `web:build:prod` |
| `:app:syncDebugSpikiveWebAssets` | 将 Web 输出同步到 Debug APK 的生成 assets |
| `:app:syncReleaseSpikiveWebAssets` | 重新构建并同步 Release Web assets |

默认行为：

- Debug APK 构建前会构建并同步 Lichtblick。
- 第一次构建，或 `yarn.lock`、Workspace `package.json`、`.yarnrc.yml`、补丁发生变化时，会先安装依赖；未变化时 Gradle 复用安装结果。
- Lichtblick 根级 Web 构建依赖根 Workspace 的构建工具，因此使用完整 immutable lockfile 安装；Android Gradle 设置 `ELECTRON_SKIP_BINARY_DOWNLOAD=1` 和 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，跳过 Android WebView 不需要、且最容易在 Link/Postinstall 阶段卡住的桌面二进制下载。
- 依赖安装任务每次都会让 Yarn 做一次完整性检查，不使用 `.yarn-state.yml` 作为 Gradle 成功标记；这是为了避免安装被中断后残留状态文件导致 Gradle 错误跳过。依赖未变化时 Yarn 自身会快速结束。
- `YN0002` 和 `YN0060` 是现有 Lichtblick Workspace 的 peer dependency 警告，不是当前卡顿原因；真正耗时的是首次依赖 Link/Postinstall 和后续 Webpack 生产构建。
- 修改 Gradle 构建命令后，已经启动的旧 Yarn 进程不会自动切换。必须先在 Android Studio 停止当前 Build，再重新运行。
- Web assets 同步使用 Gradle 内置 `Sync` 任务和静态 assets 目录，避免脚本内自定义任务类破坏 Configuration Cache。
- Web 仓库当前声明了一个未提交的 `.yarn/yarn-wrapper.js`；Android 构建任务会设置 `YARN_IGNORE_PATH=1`，由 Corepack 根据 `packageManager` 使用 Yarn 3.6.3。
- Android Gradle 会优先使用 `spikiveCorepackExecutable`，否则自动检查 `NVM_BIN`、`~/.nvm/versions/node/*/bin`、Homebrew 和系统 `PATH`。仍无法发现时，可在用户级 `~/.gradle/gradle.properties` 中配置 `spikiveCorepackExecutable=/corepack/绝对路径`，避免把本机路径提交到仓库。
- 如果 `web/.webpack/index.html` 已存在，可使用 Gradle 属性 `spikiveReuseWebAssets=true` 跳过 Debug Web 重建。
- Release 必须重新构建 Web，不允许复用不确定的旧产物。
- 不要把 `.webpack` 或 Gradle 生成目录当作手写源码修改。

## Android 开发规则

- `MainActivity` 必须保持 `sensorLandscape`，允许设备在两个横屏方向之间旋转，但禁止进入竖屏。
- 不要为适配竖屏而修改 Lichtblick Web 布局；如需竖屏，应作为独立产品需求重新设计。
- Android 启动页、等待态和错误态使用 Lichtblick 同一套设计令牌：深色背景 `#15151A`、面板 `#27272B`、菜单 `#35363A`、主色 `#EF833A`、分隔线 `#585861` 和 2dp 圆角。
- UI 结构优先复用 Lichtblick 的紧凑 AppBar、outlined 数据源入口、橙色主操作和等宽地址展示，不再使用 Android 模板紫色或独立蓝绿色视觉体系。
- 使用 Kotlin 和 Jetpack Compose，不新增 XML 页面布局。
- 保持职责分离，不把服务器、Ethernet callback 和所有状态直接堆进 Activity。
- 优先修复根因，不通过放宽 WebView 安全设置掩盖资源或网络错误。
- WebView 仅允许本地应用 Origin 的顶层导航。
- 保持 `allowFileAccess=false` 和 `allowContentAccess=false`，除非有明确且经过验证的需求。
- WebView Debugging 只能在 `BuildConfig.DEBUG` 下启用。
- Android 模块必须启用 `buildFeatures.buildConfig`，用于生成 `BuildConfig.DEBUG`。
- 启动时禁止自动加载 WebView，也禁止自动选择网络。
- USB Ethernet/Wi-Fi 按钮必须保持两阶段语义：先搜索 IP，再显式进入；网络变化后旧 FOUND 状态必须失效。
- `NetworkRequest` 只收集 Ethernet 和 Wi-Fi 候选，蜂窝网络不能出现在按钮候选中。
- 只有用户第二次点击时才能调用 `bindProcessToNetwork`；所选网络丢失、退出 WebView或 ViewModel 清理时必须解绑。
- 私有自组网没有 Internet Validation 不代表 Ethernet 不可用；只要接口具有本机 IP 就允许用户选择。
- 搜索不假设固定子网；正常 IPv4 和可路由 IPv6 都可用，但不会把 loopback、未指定地址、组播地址或仅表示接口刚出现的 IPv6 link-local 地址当作搜索成功。
- Android 不读取 Web 内部 Foxglove URL，不固定 `192.168.111.x`；实际 TCP、Foxglove 握手和数据状态由 Web 判断。
- 修改公共行为时添加或更新测试；不要为了当前任务修复无关问题。

## 排障顺序

必须从最底层失败点开始：

1. APK 和 Activity 是否正常启动。
2. 启动页是否保持无 WebView、无本地 Server。
3. USB Ethernet/Wi-Fi 搜索是否获得正确本机 IP，第二次点击是否绑定选中网络。
4. Loopback HTTP Server、`index.html`、chunk、worker、字体和 WASM 是否正常。
5. WebView 是否有 JavaScript、CSP、MIME、mixed-content 或渲染进程错误。
6. Web 中操作员填写的 Foxglove IP/端口是否在所选网络可达。
7. Foxglove WebSocket subprotocol 和 channel advertisement 是否正常。
8. 最后才检查 ROS Topic、`drone_id`、航点、Manager 和三维渲染业务逻辑。

纯 Web/Foxglove/ROS 业务问题应读取：

```text
../webProject/Spikive-Lichtblick/.codex/skills/spikive-lichtblick-groundstation/SKILL.md
```

## 验证方式

AI Agent 在修改代码后应说明建议的 Android Studio 验证步骤，但不要假设命令行环境具备完整 Java、Android SDK、Node 或 Yarn。

由开发者在 Android Studio 中执行：

1. Gradle Sync。
2. 运行 `testDebugUnitTest`。
3. 构建 Debug APK。
4. 在真实 Android 设备上启动 App。
5. 验证启动后两个按钮都为“搜索 IP”，没有 WebView 和本地 Server。
6. USB Ethernet：第一次点击显示本机 IP 并变为“进入地面站”，第二次点击后进入 Web。
7. Wi-Fi：第一次点击显示当前 Wi-Fi IP 并变为“进入地面站”，第二次点击后进入 Web。
8. 同时存在 USB/Wi-Fi 时，分别选择并确认 WebView 流量只走所选 `Network`。
9. 仅有 4G/5G 时两个按钮都搜索失败，不得进入 WebView。
10. 断开当前所选网络后，应立即退出 WebView、停止本地 Server 并返回选择页。

## Agent 工作方式

- 开始任务前先读取本文件以及将要修改的源码。
- 只做用户要求的 Android 改动，除非用户明确允许，否则不要修改 Web 工程。
- 不自行猜测自组网模块 IP、子网、DHCP、静态 IP 或认证方式；这些参数应配置化或由用户提供。
- 未实际运行的测试必须明确标注“未运行”，不要宣称通过。
- 用户选择在 Android Studio 编译时，不要在终端执行 Gradle 或 Web 编译。
- 最终说明修改文件、关键行为、需要用户在 Android Studio 执行的验证步骤以及已知硬件依赖。
