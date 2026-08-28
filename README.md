# ErrorTown

错误庄园 —— Minecraft 私人家园（Home / Island）插件。

每位玩家获得一个独立世界作为「庄园」，通过升级扩大边界（8×8 → 96×96，共 12 级），
并可邀请成员、设置管理员、修改群系、开启专属下界、调整刷怪规则等。

- **作者**：YSYError
- **支持平台**：Spigot / Paper / Purpur / Leaves（**不支持 Folia**，原因见 [为什么不支持 Folia](#为什么不支持-folia)）
- **支持版本**：Minecraft **1.21 – 26.2**
- **Java**：产物为 Java 21 字节码；1.21.x 需 Java 21，26.1+ 服务端本身要求 Java 25
- **构建**：Gradle（含 wrapper）

> **从 SummerTown 升级？** 插件已更名，数据目录、MySQL 表名、权限节点与占位符前缀
> 都随之变化。数据目录与数据库表会自动迁移，权限与占位符提供向后兼容回退。
> **升级前必读** [`docs/migration-to-errortown.md`](docs/migration-to-errortown.md)。

---

## 目录

- [快速开始](#快速开始)
- [平台与版本支持](#平台与版本支持)
- [自定义：文本格式（MiniMessage）](#自定义文本格式minimessage)
- [自定义：菜单与按钮](#自定义菜单与按钮)
- [CraftEngine 集成](#craftengine-集成)
- [配置要点](#配置要点)
- [启动自检](#启动自检)
- [构建](#构建)
- [依赖](#依赖)
- [目录结构](#目录结构)
- [测试](#测试)

---

## 快速开始

1. 把 `ErrorTown-<version>.jar` 放进服务端 `plugins/`。
2. 装好硬依赖 **Vault** 与 **NBTAPI**（缺任一插件不会加载）。
3. 启动一次以生成 `plugins/ErrorTown/config.yml`、`GUI.yml` 与语言文件。
4. 检查控制台的[启动自检](#启动自检)输出，按需修改 `config.yml`，重启。

**升级已有服务器前**：备份 `plugins/ErrorTown/`、所有家园世界目录以及旧 jar。
不要在服务器运行时覆盖 jar。隔离测试流程见 `scripts/New-TestServer.ps1`。

---

## 平台与版本支持

一个 jar 覆盖全部支持组合。做法是**按最低支持 API 编译、按最高支持 API 验证**：

| 用途 | 服务端 API |
|---|---|
| 生产编译（`compileJava`） | `org.spigotmc:spigot-api:1.21`（下限） |
| 验证编译（`apiCheckSpigot`） | `org.spigotmc:spigot-api:26.2`（上限，最严格） |
| 验证编译（`apiCheckPaper`） | `io.papermc.paper:paper-api:26.2`（覆盖 Purpur / Leaves） |

按下限编译意味着「最老的服务端给不了的调用」直接编译失败；两个 `apiCheck` 任务用同一份源码
对上限重编译，捕捉被移除或改签名的 API。`gradlew check` 会同时跑这三个。

Spigot API 是 Paper API 的子集，Purpur 与 Leaves 是 Paper 分支且不移除 API，所以这三条
编译覆盖了全部四个平台。跨版本差异集中在
[`com.Util.Platform`](src/main/java/com/Util/Platform.java) 与
[`com.Util.GameRuleNames`](src/main/java/com/Util/GameRuleNames.java)，其余代码不感知平台。

> **Leaves 注意**：Leaves 官方目前最新只到 1.21.11，没有 26.x 构建，所以 Leaves 实际覆盖到 1.21.11。

### 为什么不支持 Folia

Folia `ver/26.2.x` 的 README 把以下 API 明确列为 broken，而它们正是本插件的核心：

- **World loading/unloading** —— 本插件为每位玩家创建独立世界（`createWorld` / `unloadWorld`）。
- **`Entity#teleport`** —— 原文：「This will NEVER UNDER ANY CIRCUMSTANCE come back, use teleportAsync」。
- 传送门 / 重生 / 登录相关 API，以及全部 scoreboard API。

也就是说「每人一个世界」的家园插件在 Folia 上无法完整工作，这是上游未实现，不是工作量问题。
插件启动时会检测 Folia 并在控制台明确报告，而不是在第一次 `/sh create` 时抛异常。

---

## 自定义：文本格式（MiniMessage）

所有提示消息、GUI 标题、按钮名称与 Lore 都经过
[`com.Util.Text`](src/main/java/com/Util/Text.java)，三种写法可以自由混用、无需迁移：

| 写法 | 例子 |
|---|---|
| MiniMessage | `<gradient:#ff5f6d:#ffc371>错误庄园</gradient>`、`<bold><#55FF55>创建成功`、`<rainbow>欢迎回家` |
| `&` 颜色码 | `&a绿色`、`&l粗体`、`&#ff5f6d` 十六进制 |
| `§` 颜色码 | 原有全部文本，保持不变 |

`config.yml` 的 `Formatting` 段控制行为：

```yaml
Formatting:
  # auto        - 默认。只有含 <标签> 的文本才走 MiniMessage
  # minimessage - 全部按 MiniMessage 解析（转义规则更严格）
  # legacy      - 完全关闭 MiniMessage
  Mode: auto
  # 是否把 &a / &#RRGGBB 也当颜色码（§ 码始终有效）
  TranslateAmpersand: true
```

默认 `auto` 的理由：语言文件里大量 `<Name>` / `<player>` / `<Key>` 占位符**看起来就是标签**，
必须原样保留。这一点有专门的回归测试守着（`TextTest`）。

**实现方式**：MiniMessage 解析成 Adventure `Component` 后，再序列化回 `§` 字符串，交给
Bukkit 的 String API。Paper 自带 Adventure、Spigot 完全不向插件暴露 Adventure，所以 Adventure
被 **shade 进 jar 并重定位** 到 `com.ErrorTown.libs.kyori`；因为没有任何 Adventure 类型跨越
服务端 API 边界，重定位后的副本与 Paper 自带的那份互不干扰。这也是为什么 MiniMessage 在
Spigot 上同样可用。产物因此从 ~715 KB 增至 ~1.49 MB。

---

## 自定义：菜单与按钮

### 标题

`GUI.yml` 的 `<菜单名>Title`。其中 **10 个标题过去写死在 Java 里**（群系、规则、升级、
移动中心、庄园选择、创建付费，以及 4 个服务费用确认界面），既不能翻译也不能改样式，现在
都是配置键；删掉某一项即回落到内置默认文本，所以现有服务器升级后外观不变。

### 行数

`GUI.yml` 的 `<菜单名>Size`，填 1–6（行）或 9 的倍数（最多 54）。

非法值（7、20、63 之类）会回落到内置尺寸并告警 —— 它们本会让 `Bukkit.createInventory`
直接抛异常、整个菜单打不开，所以这一层夹取有专门的回归测试（`GuiSizeTest`）。
缩小菜单后超出范围的按钮 `Index` 会被跳过。

### 按钮

除原有的 `Material` / `CustomName` / `Lores` / `Enchants` / `Index` / `SubID` /
`LeftInTo` / `RightInTo` / `KeepOpen` 之外，新增下列可选键（不写即不生效）：

| 键 | 说明 |
|---|---|
| `Material` | 现在也接受 CraftEngine 自定义物品 ID，如 `myfurniture:oak_chair` |
| `Permission` | 没有该权限的玩家看不到这个按钮。**纯显示控制** —— 按钮执行的指令本身仍各自校验权限，隐藏按钮不等于授权 |
| `Amount` | 按钮显示的堆叠数量 |
| `CustomModelData` | 资源包 CustomModelData |
| `ItemModel` | 1.21.4+ 的 `item_model` 组件；更低版本自动忽略并告警一次 |
| `Glow` | 附魔光效且不显示附魔文字（Spigot / Paper 通用做法） |
| `Unbreakable` / `HideAll` / `ItemFlags` | 清理物品提示框 |
| `SkullOwner` | 渲染成玩家头颅，`<viewer>` 表示打开菜单的玩家 |

示例：

```yaml
Button7:
  InMenu: 'Create'
  Material: 'myfurniture:oak_chair'
  Permission: 'ErrorTown.Create.1'
  CustomName: '<gradient:#ff5f6d:#ffc371>普通地形</gradient>'
  CustomModelData: 10001
  Glow: true
  HideAll: true
  Index: 21
  Lores:
  - '&7左键 &f= &a直接创建'
  LeftInTo: 'sh create 1'
  KeepOpen: false
```

---

## CraftEngine 集成

[`com.Util.CraftEngineBridge`](src/main/java/com/Util/CraftEngineBridge.java) 是一个纯反射桥，
CraftEngine 不在时全部返回「没有」，因此既不是硬依赖也不需要 `softdepend`。

- **GUI 按钮**与**家园图标**（`/sh icon`）都可直接填 `namespace:id` 自定义物品。
- **自定义方块限额**（默认关闭）—— 原有的 `TileList` 限额是数 `Chunk.getTileEntities()`，而
  CraftEngine 的自定义方块大多不是 block entity，根本不会出现在那个列表里，**过去完全不受
  `TileList` 约束**。开启后，`TileList` 里加 `ce:` 前缀的条目即生效：

  ```yaml
  CraftEngineBlockLimit:
    Enabled: true
    ScanRadius: 6      # 1-17，默认 6（即 13×13×13）
  TileList:
  - 'chunk|ce:myfurniture:oak_chair|4'
  ```

  之所以默认关闭且只扫放置点周围一个立方体：正因为不是 block entity，没有现成列表可数，
  只能实际去看方块，而整块区块约 9.8 万个方块、每次放置都扫一遍不可接受。这意味着限额是
  **局部的**而非整块区块的，是刻意的取舍。扫描只看已加载区块，不会触发磁盘 I/O。
- 启动自检会打印 `CraftEngine: <版本> (items=…, blocks=…)`，装没装、API 对不对一眼可见。

用反射而非编译期依赖是刻意的：CraftEngine 自己的源码写着「This will be refactored before the
1.0 release」。签名一旦变动，这里退化为「自定义物品不可用」并告警一次，而不是让整个插件加载失败。
方块 ID 解析有两条路（`owner().value().id()`，失败则退回解析状态的 `toString()`），因为这条链
比物品那条更深入 CraftEngine 的内部。

---

## 配置要点

`config.yml` 约 160 个键，以下几组最容易配错：

- **`HomeTerrain.Enabled`** 决定尺寸体系。`true` 用 `HomeUpgrade.LevelSizes` 等级表；
  `false` 退回旧的 `WorldBoard` + `UpdateRadius` 线性公式。两者都受 96 方块硬上限约束。
- **`MaxLevel` 必须等于价格表长度 + 1**。`MoneyNeed` / `PointsNeed` / `ItemsNeed`
  是「每次升级」的价格，12 级需要 11 条。缺条目时对应升级按钮显示为不可用（不会变成免费）。
- **`HomeCreationQueue.MaxConcurrent`** 硬上限为 2。
  `HomeCreationQueue.TimeoutSeconds`（默认 300）同时是卡死槽位的回收阈值。
- **`doMobSpawning`** 是家园刷怪的唯一开关。开启后由 `HomeSpawnCompensation`
  决定怪物上限，`HomeRulesDefaults` 决定每个家园的细则。
- **`BungeeCord: true`** 时数据走 MySQL，且自然地形创建队列不启用。
- **`SoilType`** 请保持 `FARMLAND`。填 1.13 之前的 `SOIL` 会让交互保护整体失效。
- **`Superflat.GeneratorSettings`** 留空即用内置超平坦预设；要自定义就必须包含 `layers`。

---

## 启动自检

插件在 `onEnable` 末尾执行下列自检并输出到控制台，**首次部署请检查这段输出**：

- **平台与版本** —— 例如 `Platform: Purpur | MC 26.2-R0.1-SNAPSHOT | Java 25`。
  检测到 Folia 会以 SEVERE 明确说明不支持。
- **CraftEngine** —— `CraftEngine: <版本> (items=…, blocks=…)` 或 `not installed`。
- **语言文件补全**（`Lang.seedMissingFromBundle`）—— 从 jar 内置副本补齐缺失键并报告数量。
- **语言文件审计**（`Lang.audit`）—— 把活动语言文件与 jar 内置副本做键集 diff 并列出仍缺失的键。
- **配置校验**（`ConfigValidator`）—— 按 SEVERE / WARNING / INFO 分级列出配置矛盾，
  例如 `MaxLevel` 与价格表长度不匹配、`HikariCP.minimumIdle > maximumPoolSize`、
  `doMobSpawning: false` 却开了 `EnableHomeNether`。
- **创建费用账本恢复**（`CreateCostLedger`）—— 回放 `create-cost-ledger.yml`，退还上次
  运行中未结算的创建费用。

审计记录写在 `plugins/ErrorTown/audit.log`（每条一行，可直接 grep）。

---

## 构建

```bash
./gradlew clean build          # Linux / macOS
gradlew.bat clean build        # Windows
```

产物：`build/libs/ErrorTown-<version>.jar`（已 shade MiniMessage，约 1.49 MB）。
同目录的 `ErrorTown-<version>-thin.jar` 只是 shade 前的中间产物，**不要部署它**。

只想验证跨平台 API 兼容性时：

```bash
./gradlew apiCheck             # 对 Spigot 26.2 与 Paper 26.2 各重编译一次
```

所有依赖都从公共 Maven 仓库解析，**克隆后无需本地 Minecraft 服务端即可构建**。

> **Windows 注意**：若仓库路径含非 ASCII 字符（例如中文目录名），`gradlew test`
> 会因 Gradle 测试进程的 classpath 编码问题抛 `ClassNotFoundException`。
> 这不是代码问题 —— 把仓库放到纯 ASCII 路径下即可。编译与打包不受影响。

---

## 依赖

| 依赖 | 类型 | 用途 |
|---|---|---|
| Spigot API 1.21（编译下限） | 必需（compileOnly） | 服务端 API |
| Adventure / MiniMessage 4.26.1 | 必需（shade 进 jar） | 文本格式化 |
| Vault | 必需（`depend`） | 经济系统 |
| NBT-API | 必需（`depend`） | 方块/物品 NBT 限额统计 |
| PlaceholderAPI | 必需 | 变量与 GUI 文本 |
| CraftEngine | 可选（反射） | 自定义物品 / 方块 |
| PlayerPoints | 可选 | 点券支付 |
| ProtocolLib | 可选 | 礼物箱 |
| WorldEdit / FastAsyncWorldEdit | 可选 | 边界外壳、示意图 |
| Multiverse-Core 2.x / 5.x | 可选（反射） | 多世界兼容 |
| DecentHolograms / HolographicDisplays | 可选（反射） | 边界全息提示 |
| RealisticSeasons | 可选（反射） | 季节同步 |

可选依赖的接入约定见 [`docs/optional-dependencies.md`](docs/optional-dependencies.md)。
未发布到 Maven 的 API 请放入 [`libs/`](libs/README.md)（该目录下的 jar 不会上传）。

---

## 目录结构

```
├── build.gradle.kts          构建、依赖与 shade 配置（单一来源）
├── settings.gradle.kts
├── gradlew / gradlew.bat     Gradle wrapper
├── .gitignore                上传黑名单
├── libs/                     未发布到 Maven 的可选 API（jar 不上传）
├── scripts/                  隔离测试服与运维 PowerShell 脚本
├── docs/
│   ├── natural-home-terrain.md         自然地形 / 队列 / 存储估算
│   ├── optional-dependencies.md        可选依赖接入约定
│   ├── migration-to-errortown.md       SummerTown -> ErrorTown 迁移指南
│   └── world-generation-investigation.md
├── src/main/java/            插件源码
├── src/main/resources/       config.yml、GUI.yml、语言文件、plugin.yml
└── src/test/java/            JUnit 测试
```

### 跨版本 / 自定义相关的关键类

| 类 | 职责 |
|---|---|
| `com.Util.Platform` | 平台探测；游戏规则、区块、坐标、掩体保护等跨版本封装 |
| `com.Util.GameRuleNames` | 1.21.11 游戏规则改名的归一化、别名与取值反转（纯逻辑，可单测） |
| `com.Util.Text` | MiniMessage / `&` / `§` 三种写法统一成显示文本 |
| `com.Util.ClickableText` | 可点击聊天行；bungee-chat 依赖只集中在这一个类 |
| `com.Util.ItemSpec` | GUI 按钮的物品构建与全部可选装饰键 |
| `com.Util.GuiSafe` | 菜单标题 / 行数 / 材质 / PAPI 的容错解析 |
| `com.Util.CraftEngineBridge` | CraftEngine 反射桥 |
| `com.Util.CraftEngineBlockLimit` | CraftEngine 自定义方块的限额（默认关闭） |
| `com.Util.SuperflatPreset` | 超平坦地层设置 |

---

## 测试

```bash
./gradlew test
```

当前 **123 个测试**，全部不依赖运行中的服务端。覆盖：

- `TextTest` —— MiniMessage / `&` / `§` 混用、渐变、十六进制、坏标签降级，
  以及**语言文件占位符不能被 MiniMessage 吃掉**这个关键回归。
- `GameRuleNamesTest` —— 1.21.11 改名的双向别名、取值反转、被删除的 `doFireTick`。
- `GuiSizeTest` —— 菜单尺寸夹取，含「本会让 `createInventory` 抛异常的非法值」分支。
- `HelpPagesResourceTest` —— 三语六页帮助齐全、命令行只有一个逗号、命令都指向 `/sh`。
- `CsvUtilTest`、`HomeTerrainPolicyTest`、`HomeCreationQueueTest`、`HomeCreationMessagesTest`、
  `ConfigValidatorTest`、`PlaceholderValueCacheTest`、`DurabilityTest`、`HomeAuditConcurrencyTest`
  —— 核心逻辑与账本 / 审计的持久化契约。
- `MainLifecycleSourceTest`、`CreateCostContractTest`、`PermTest`、`PluginDescriptorTest`
  —— 需要活服务端才能执行的关卡，通过 `SourceContract` 做源码契约，标记失效时清楚失败。

修改相关代码前请先跑测试。测试 classpath 上有 `spigot-api` 与 Adventure，可以直接引用
Bukkit 类型（但没有运行中的服务端，Bukkit 静态方法仍不可用）。
