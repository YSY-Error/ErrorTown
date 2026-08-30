# 管理员配置与指令手册

面向服主与管理员。玩家视角的用法请看 [`user-guide.md`](user-guide.md)。

`README.md` 讲的是"怎么装、怎么改"；本文补齐"每一项到底做什么、它和哪些项互相牵制、改错了会怎样"。
两份文档冲突时以 `README.md` 为准，并请把本文的错处一并修掉。

---

## 目录

- [1. 运行环境与依赖](#1-运行环境与依赖)
- [2. 两种存储模式](#2-两种存储模式)
- [3. 指令总览](#3-指令总览)
- [4. 管理员指令详解](#4-管理员指令详解)
- [5. 权限节点全表](#5-权限节点全表)
- [6. config.yml 逐项说明](#6-configyml-逐项说明)
- [7. GUI.yml 菜单与按钮](#7-guiyml-菜单与按钮)
- [8. 语言文件与文本格式](#8-语言文件与文本格式)
- [9. PlaceholderAPI 变量](#9-placeholderapi-变量)
- [10. 边界尺寸是怎么算出来的](#10-边界尺寸是怎么算出来的)
- [11. 收费项一览](#11-收费项一览)
- [12. 启动自检与排错](#12-启动自检与排错)
- [13. 已知限制](#13-已知限制)

---

## 1. 运行环境与依赖

| 项目 | 要求 |
| --- | --- |
| 服务端 | Spigot / Paper / Purpur / Leaves，Minecraft **1.21 – 26.2**，同一个 jar 通吃 |
| Java | 服务端自带的 Java 21 即可运行（jar 是 21 字节码） |
| Folia | **不支持**，且是刻意不支持：Folia 没有世界加载/卸载 API，也没有同步的 `Entity#teleport` |

### 前置插件

`plugin.yml` 里 `depend` 的两个是硬依赖，服务端会先加载它们：

- **Vault** —— 经济接口。缺失时插件在 `onEnable` 直接自我禁用，唯一例外是
  `DisableFunctionButTeleport: true`（纯传送子服不需要经济）。
- **NBTAPI** —— 方块 NBT 读取。缺失时 `TileList` 那套"按 NBT 关键字限制方块数量"完全不可用。

`softdepend`（装了就接，不装只是少功能，不会报错）：

| 插件 | 缺失后失去什么 |
| --- | --- |
| PlaceholderAPI | 全部 `%ErrorTown_*%` 变量、GUI 里用变量写的 Lore |
| PlayerPoints | 点券支付。所有"点券"价格自动变成不可选，只能走金币 |
| Multiverse-Core | 创建/删除家园时同步多世界插件的世界列表（`MultiverseCoreCompability`） |
| FastAsyncWorldEdit / WorldEdit | `FaweSwitch` 的实体边界墙（`/sh refresh` 重建的那圈基岩） |
| HolographicDisplays | `HDSwitch` 的边界四角全息文字 |
| ProtocolLib | 礼物盒（`/sh gift`）相关交互 |
| RealisticSeasons | `RealisticSeasons: true` 时的季节同步 |
---

## 2. 两种存储模式

`config.yml` 里的 `BungeeCord` 决定一切。插件里没有"存储抽象层"，几乎每个读写点都直接
`if (Variable.bungee)` 分叉，所以这一项改错等于换了一套数据。

| | `BungeeCord: false`（默认） | `BungeeCord: true` |
| --- | --- | --- |
| 家园数据 | `plugins/ErrorTown/playerdata/<家园名>.yml`，一家一个文件 | MySQL 表 `ErrorTown_Users` |
| 家园列表 | 直接列 `playerdata` 目录 | 查表 |
| 服务器登记 | 不需要 | MySQL 表 `ErrorTown_Servers` |
| 连接池 | 无 | HikariCP，`HikariCPUtils` |
| `/sh homes` | 可用 | **不可用**（跨服拿不到别人服的世界） |

切换模式**不会**自动搬数据：从 YAML 换到 MySQL，旧的 `playerdata/*.yml` 会被无视，玩家看到
的是一个空服。要迁移请自行导数据，或者干脆保持不动。

### 两个耐久性侧写文件

都在 `plugins/ErrorTown/` 下，和上面的存储模式无关，两种模式都会写：

- **`audit.log`** —— `HomeAudit`。一行一条记录，同一时刻只有一个写线程，其余记录进内存队列。
  故意不用 YAML：原版实现每写一条就把整个文件重新解析并回写一遍，而且是在调用线程上做的。
  升级、改规则、踢人、删家园这类动作都会留痕。
- **`create-cost-ledger.yml`** —— `CreateCostLedger`。记录"某玩家为创建家园付了多少"，
  这样世界创建失败、区块加载失败、玩家中途掉线、排队超时、占位被回收这五条失败路径都能退款。
  每次变动同步落盘；`onEnable` 里 `recoverPending()` 重放未结清的记录；后台任务每 120 秒
  重试一次卡住的退款。按默认配置一次创建收 59999 金币 + 520 点券，所以这个文件不要手删。

### 改名迁移

`RenameMigration` 处理 SummerTown → ErrorTown：先搬数据目录（`ATOMIC_MOVE`，失败则复制；
如果两个目录同时存在且新目录非空就拒绝动手），再在连接池起来之后、`MySQL.init()` 执行
`CREATE TABLE IF NOT EXISTS` 之前把表改名。

**权限节点和 PlaceholderAPI 变量不迁移**，改为运行时兼容：`LegacyPermissions: true` 时
`Perm.has` 会回落到旧的 `SummerTown.X` 节点，占位符也同时注册 `SummerTown` 标识符。详见
[`migration-to-errortown.md`](migration-to-errortown.md)。

---

## 3. 指令总览

`plugin.yml` 只注册了**一条**指令 `/st`，别名 `/sh`。本文一律写 `/sh`。

分派结构（`CommandListener.java`）：

1. `onCommand` 入口。若 `DisableFunctionButTeleport: true` **且** `BungeeCord: true`，进入
   **降级代理模式**（`:925-1418`），只重新实现一小部分子指令；其余全部无响应。
2. 少量早期重定向：`/sh reload`、`/sh admin …`、`/sh ForceDelete`、`/sh UnLoad`、`/sh rank`、
   `/sh dimension`。这一段接受**控制台**，所以它们能写进定时任务或从后台执行。
3. 其余交给 `onCommandPlayer`（`:2439-8597`），约 120 个 `args[0]` 分支，**必须是玩家**，
   最后兜底打开主菜单。

因此：**只有第 2 段的指令能在控制台跑**，`onCommandPlayer` 里的一律回
`CommandSenderTip`（"该指令只能由玩家执行"）。

### 补全不完整

Tab 补全（`:8588-8633`）是手写的第三份清单，和实际分支、和语言文件的 `Help-N` 三份**互不同步**：
它漏掉 `update`、`rule`、`togglecc`、`upgrademember`、`createNether`、`title`、`desc`、`fly`、
`seed`、`refresh`、`quit`、`undeny`、`resetOverworld`、`resetNether`、`calc`、`servicecost`、
`cicdifficulty`、`SetBlockLimit` 等，而且 `setspawn` 被 `list.add` 了**两次**（`:8605` 和
`:8631`），补全里会出现两条一样的候选。这是既有状态，改动它需要同时改另两份清单。

`/sh admin` 的二级补全只给 `setSpawn / dimension / export / import / setlevel / pwp`，
实际还有 `clearout`、`load`、`tp`、`addlevel`、`create`，以及仅代理模式可用的
`homeinfo / playerhomes / audit / stalehomes`。另外 `dimension` 在补全里挂在 `admin` 下，
真正的分支却是顶层的 `/sh dimension`（`:1999`）。

### `/sh GameMode` 的第四个难度

难度切换检查四个节点 `ErrorTown.GAMEMODE.EASY / NORMAL / HARD / PEACEFUL`
（`:5537/5568/5599/5630`），但 `plugin.yml` **没有声明 NORMAL**（`:215-223` 只有其余三个），
Tab 补全也只列三个。想开放"普通"难度，请在 LuckPerms 里直接给
`ErrorTown.GAMEMODE.NORMAL`，未声明的节点一样能生效。

---

## 4. 管理员指令详解

"控制台"列表示该指令能否由控制台或命令方块执行。"节点"列写的是实际检查的权限，
`ErrorTown.Admin` 一律可以顶替（代码里几乎全是 `节点 || ErrorTown.Admin` 的写法）。

### 4.1 等级与世界

| 指令 | 控制台 | 节点 | 说明 |
| --- | --- | --- | --- |
| `/sh admin setlevel <家园> <等级>` | ✅ | `Admin.SetLevel` | 直接写 `Level` 字段，随后 `FirstBorderShaped.AddShapeBorder` 重画实体边界墙。**不校验 `MaxLevel`**，写 999 就是 999，尺寸再由 `HomeTerrainPolicy` 夹到 96 |
| `/sh admin addlevel <家园> <增量>` | ✅ | `Admin.AddLevel` | 同上，改为累加。增量可为负 |
| `/sh admin create <玩家> <类型>` | ✅ | `Admin.Admin` | 替玩家建家园。BungeeCord 模式下先查该玩家是否已在别的子服有家园/已加入他人家园，有则报出服务器名并中止 |
| `/sh admin load <世界名>` | ✅ | `Admin` | 加载一个磁盘上已存在但未载入的世界，并把执行者传送过去。ARCLIGHT 与非前缀模式的路径拼法不同，见 `:1562-1570` |
| `/sh admin tp <世界名>` | ✅ | `Admin.TP.<世界名>` 或 `Admin.TP.*` | 按需加载并传送。**注意节点里含世界名**，逐个放权时要写全 |
| `/sh admin setspawn` | ❌ 仅玩家 | `Admin` 或 `SetSpawn` | 把**当前所在世界**的出生点设为脚下。装了 Multiverse-Core 会同步写 MV 的出生点 |
| `/sh UnLoad <世界名>` | ✅ | `Admin` | 把世界里的人传回 `Spawn` 配置的世界，然后 `unloadWorld(save=true)`。参数是**完整世界名**（含 `ErrorTownWorld/` 前缀），不是家园名 |
| `/sh ForceDelete <家园名>` | ✅ | `Admin` | 卸载 + 删目录 + 删数据行 + 从 Multiverse 配置里摘掉 + 把 `NowID` 减一。参数是**不含前缀的家园名**。**不可撤销，且不退款** |
| `/sh admin clearout <天数>` | ✅ | `Admin` | 批量清理"多少天未活动"的家园。第一次执行只是提示确认，**5 秒内**再执行一次同样的指令才真正删除（`:1507` 的 `runTaskLater 100L`）。单机模式按 `playerdata/*.yml` 的文件修改时间判断，BungeeCord 模式按数据库里的 `VisitTime` |
| `/sh dimension` | ✅ | `Admin` | 打印各世界的维度 ID。**只在 1.12.2 和 1.7.10 上有输出**，其他版本回 `DimensionNotAllow`。属于历史遗留 |

`clearout` 的确认窗口是全局 5 秒，且键是发送者名字；确认期内执行**别的**指令不会打断它。

### 4.2 数据与排查

| 指令 | 控制台 | 节点 | 说明 |
| --- | --- | --- | --- |
| `/sh admin export` | ✅ | `Admin` | 导出 MySQL 数据。**只在 `BungeeCord: true` 下可用**，否则回 `ExportOrImportButBungeeCordHasBeenDisabled` |
| `/sh admin import` | ✅ | `Admin` | 导入。同样只在 BungeeCord 模式可用 |
| `/sh admin pwp` | ✅ | `Admin` | 从 PlayerWorldsPro 迁数据：读 `plugins/PlayerWorldsPro/` 下的 `config.yml`、`players.yml`、`data.yml`，转换 PvP / 拾取 / 丢弃 / 天气锁 / 公开状态 / 出生点 / 成员表。**路径写死为 `plugins\PlayerWorldsPro`（反斜杠）**，Linux 下这一条不可用 |
| `/sh rank <页码>` | ✅ | `Rank` 或 `command.user` | 家园负载排行。异步执行；`Variable.world_StaticsTick` 为空时先跑一次 `ScheduledTasks.refreshWorldStatics(false)`。表头/表尾/行格式分别是配置里的 `StatisticsTop`、`StatisticsEnd`、`ShowFormat`，行占位符 `<index> <world> <tile> <chunk> <entity> <drop> <tps>`。每页 10 条 |
| `/sh mobs` | ❌ 仅玩家 | `Admin` | 打印当前世界所有 `LivingEntity` 的类型，一行输出。实体多时会很长 |
| `/sh nbt` | ❌ 仅玩家 | `Admin` | 开关方块 NBT 调试。开启后每次放方块都会把 NBT 串以可点击复制的形式发给自己 —— 这是拿到 `TileList` 关键字的正规办法 |
| `/sh item` | ❌ 仅玩家 | `Admin` | 打印手持物品的 NBT 串，可点击复制。<br>**`:3961` 还有一个完全相同的 `item` 分支**，它本想打印 `Material:xxx,SubID:n`，但被前一个分支挡住，永远执行不到。要用它得先改条件 |
| `/sh calc` | ❌ 仅玩家 | `Calc` | 内部计算功能，默认 `false` |

`/sh admin homeinfo <家园>`、`/sh admin playerhomes <玩家>`、`/sh admin audit <家园>`、
`/sh admin stalehomes <天数>` 这四条走 `ErrorTown.Admin.Info`，但它们的分支写在
`:1024-1090`，位于 `DisableFunctionButTeleport && bungee` 的**代理模式块内部**。也就是说
它们**只在纯传送子服上可用**，正常服上执行会一路落到 `onCommandPlayer` 而打开主菜单。
如果你需要在主服排查数据，目前只能看 `audit.log` 和 `playerdata/*.yml`。

### 4.3 经济与道具下发

| 指令 | 控制台 | 节点 | 说明 |
| --- | --- | --- | --- |
| `/sh gift send all` | ❌ 仅玩家 | `Admin` | 把**手上的物品**发给**所有家园**的礼物箱。每家上限 45 件，满的会列进"未送达"名单；若某家的礼物箱此刻正被人打开，会强制关掉对方界面。物品会被追加一行 `GiftLoreAddPrefix + 发送者名` 的 Lore |
| `/sh gift send <玩家>` | ❌ 仅玩家 | `Gift.Send`（默认 true） | 给单个玩家送手持物品 |
| `/sh gift inv <家园>` | ❌ 仅玩家 | `Gift.Inv`（默认 false） | 直接查看/操作**别人家园**的礼物箱，用来处理申诉。同一家园同时只允许一人打开 |
| `/sh popularity add <玩家> <数量>` | ❌ 仅玩家 | `Admin` | 给玩家的家园加人气值 |
| `/sh flower add <玩家> <数量>` | ❌ 仅玩家 | `Admin` | 给玩家发放可投递的鲜花数 |

### 4.4 方块数量上限

三条指令都**只判断 `isOp()`，不看权限节点**（`:4609/4700/4752`），所以 LuckPerms 给
`ErrorTown.Admin` 也用不了，必须是真正的服务器 OP。

| 指令 | 说明 |
| --- | --- |
| `/sh AddBlockLimit <家园> <类型> <NBT关键字> <数量>` | 在原有额度上**累加**。已存在同 `<类型>|<关键字>` 的条目时把数量相加 |
| `/sh SetBlockLimit <家园> <类型> <NBT关键字> <数量>` | 先删掉同键条目再写入，即**覆盖** |
| `/sh DelBlockLimit <家园> <类型> <NBT关键字>` | 删除该条目，家园回落到 `config.yml` 的 `TileList` 默认额度 |

存储格式是 `<类型>|<关键字大写>|<数量>`，`<类型>` 目前只有 `chunk` 有意义。关键字会被
强制转大写，匹配时也用大写比较，所以大小写无关。这些额度是**加在** `TileList` 基准之上的
（`BlockPlaceListener.java:129-139`：先取 `TileList` 里的基准值，再加上家园自己的额度）。

拿关键字的办法：`/sh nbt` 开调试，放一次该方块，把打印出来的 NBT 串里挑一段唯一子串。

### 4.5 `/sh reload`

节点 `ErrorTown.Admin.Reload` 或 `ErrorTown.Admin`，可由控制台执行。做四件事：

1. 关掉所有玩家打开的本插件 GUI —— **但只认 9 个类**：`CheckGui`、`CreateGui`、`DenyGui`、
   `InviteGui`、`MainGui`、`ManageGui`、`ManageGui2`、`TrustGui`、`VisitGui`
   （`:1437-1445`，代理模式那份 `:935-943` 完全相同）。`ManageGui3`、`SetSpawnGui`、
   `BiomeGui`、`RulesGui`、`UpgradeGui`、`CreateCostGui`、`ServiceCostGui`、`GiftGui`、
   `OwnedHomesGui` **不在名单里**，重载时这些界面会留在屏幕上，而它按钮依赖的 `GUI.yml`
   已经换成新对象，点下去大概率什么也不发生。重载前提醒在线玩家关掉菜单。
2. 删除所有世界的全息边界标记（`Variable.hololist`）。
3. 调用 `Main.init()`：重跑数据目录迁移、重读 `config.yml` / `GUI.yml` / 语言文件、
   重跑 `ConfigUpdate.update()` 与 `HomeDataUpgrade.apply()`。
4. BungeeCord 模式下 `Main.init()` 会重建 HikariCP 连接池，**每次都重新读取数据库凭据**，
   所以改完 MySQL 账号密码 `/sh reload` 就能生效；旧池会先 `shutdown()` 关闭，不会泄漏连接。

`reload` **不**重新注册监听器，也**不**重启 `ScheduledTasks`。改了监听相关的开关
（`EnableHeightLimit`、`DisableFunctionButTeleport` 等）必须重启服务器。

---

## 5. 权限节点全表

用 `Perm.has` 判定，所以 `LegacyPermissions: true` 时每个节点都还认改名前的
`SummerTown.*` 同名节点。**大小写按下表照抄** —— LuckPerms 的节点是大小写不敏感的，
但如果你用的是别的权限插件，请注意 `ErrorTown.info` 和 `ErrorTown.check` 是小写。

### 5.1 玩家基础（`ErrorTown.command.user` 的子节点，默认 true）

| 节点 | 作用 |
| --- | --- |
| `ErrorTown.Create.1` / `.2` / `.airland` | 分别是普通 / 超平坦 / 空岛家园的创建权 |
| `ErrorTown.Create.*` | 所有类型，包含模板复制类型；默认 false |
| `ErrorTown.Visit` | `/sh tp`、`/sh visit`、`/sh v` |
| `ErrorTown.Update` | `/sh update` 升级 |
| `ErrorTown.Look` | `/sh look` 查看当前家园信息 |
| `ErrorTown.Public` | `/sh public` 公开开关 |
| `ErrorTown.PVP` / `.PickUp` / `.Drop` | 三个开关 |
| `ErrorTown.Rank` | `/sh rank` |
| `ErrorTown.Flowers` + `.1`…`.10` | 投花，数字是单次可投上限 |
| `ErrorTown.Trust` / `.Deny` / `.Invite` / `.Quit` | 成员管理四件套 |
| `ErrorTown.check` | `/sh check` |
| `ErrorTown.tpset` | `/sh tpSet` 设传送点 |
| `ErrorTown.WholeDelete` | `/sh wholeDelete` 自助删家园 |

上面这些还受 `config.yml` 的 `Permission.*` 开关影响：`Permission.CommandUser: true` 会
**跳过**大部分基础节点的检查（代码写法是 `!配置 && !节点A && !节点B`），
`Permission.Visit` / `.Nether` / `.End` 同理。想靠权限组精细放权，就要把这些配置设为 false。

### 5.2 需要单独授予（默认 false）

| 节点 | 作用 |
| --- | --- |
| `ErrorTown.SetSpawn` | `/sh setspawn`、`/sh admin setspawn` |
| `ErrorTown.locktime` / `ErrorTown.lockweather` | 锁时间 / 锁天气 |
| `ErrorTown.Day` / `.Night` / `.Sun` / `.Rain` | 单次改时间/天气 |
| `ErrorTown.Nether` / `.End` | 传送到**公共**下界 / 末地 |
| `ErrorTown.Biome` | `/sh setBiome` 改群系 |
| `ErrorTown.Fly` | `/sh fly` 家园内飞行 |
| `ErrorTown.Refresh` | `/sh refresh` 重建实体边界墙 |
| `ErrorTown.Icon` | `/sh Icon` 用手持物品作家园图标 |
| `ErrorTown.info` | `/sh info` 改家园简介（**小写 info**） |
| `ErrorTown.Info.Color` | 简介里允许使用颜色代码 |
| `ErrorTown.Gift.Send` / `.Open` / `.Inv` | 送礼 / 开礼物箱 / 查看他人礼物箱（Send 与 Open 默认 true） |
| `ErrorTown.Togglecc` | `/sh togglecc` 隐藏自己的边界显示 |
| `ErrorTown.Seed` | `/sh seed` 查看世界种子 |
| `ErrorTown.MobSpawn` | `/sh MobSpawn` 生物生成开关 |
| `ErrorTown.Popularity` | 进别人家园时给对方涨人气（`PlayerTeleportListener:209`） |
| `ErrorTown.Calc` | `/sh calc` |
| `ErrorTown.forcetp` | **无视黑名单和非公开状态**强行进入任何家园 |
| `ErrorTown.GAMEMODE.EASY` / `.NORMAL` / `.HARD` / `.PEACEFUL` | 难度切换，NORMAL 未在 `plugin.yml` 声明但代码会检查 |

### 5.3 数值型节点

这三组的写法是"节点里带数字，取**能拿到的最大值**"，所以给玩家 `ErrorTown.Level.20`
不需要同时给 1…19。

| 节点族 | 上限来源 | 说明 |
| --- | --- | --- |
| `ErrorTown.Level.<n>` | `plugin.yml` 声明了 1–10、15、20、25、30、35、40、45、50 | 创建家园时**直接给到该等级**（`:7873`、`:7985`）。也被 `PlayerTeleportListener:115` 用于进家园时补等级 |
| `ErrorTown.MaxJoin.<n>` | 声明了 1–10、15…50 | 该玩家名下家园的成员位上限。实际上限 = `max(MaxJoin配置 + 家园已购扩容, 权限节点最大值)`（`:156-160`） |
| `ErrorTown.MaxOP.<n>` | 声明了 1–10 | 管理位上限，算法同上 |
| `ErrorTown.Flowers.<n>` | 声明了 1–10 | 单次投花上限 |
| `ErrorTown.ChunkPlace.<关键字>.<n>` | 未声明，自由命名 | 单区块内某类方块的额度，`n` 取 1–100（`BlockPlaceListener:109-117` 从 100 倒着找） |
| `ErrorTown.WorldPlace.<关键字>.<n>` | 未声明 | 整个世界内的额度，`WorldBlockPlaceListener:102` |

`getPermissionBasedLimit`（`:121-134`）的扫描范围是从 `max(1, 默认值) * 1000` 倒数到默认值。
`MaxJoin: 10` 时它会从 10000 试到 11，一次判定最多 9990 次 `hasPermission`。这不是热路径
（只在邀请/加管理时跑一次），但把 `MaxJoin` 配得很大会放大这个循环。

### 5.4 管理节点

| 节点 | 默认 | 作用 |
| --- | --- | --- |
| `ErrorTown.Admin` | op | 总开关，几乎所有管理判断都接受它 |
| `ErrorTown.Admin.SetLevel` / `.AddLevel` | op | 改等级 |
| `ErrorTown.Admin.Create` / `.Delete` | op | 声明了但**代码里没有单独检查**，实际走 `ErrorTown.Admin` |
| `ErrorTown.Admin.TP` | op | 同上，实际检查的是带世界名的 `Admin.TP.<世界>` |
| `ErrorTown.Admin.TP.*` | op | 传送到任意家园 |
| `ErrorTown.Admin.Reload` | op | `/sh reload` |
| `ErrorTown.Admin.Info` | 未声明 | `homeinfo` / `playerhomes` / `audit` / `stalehomes`（仅代理模式可用） |
| `ErrorTown.command.admin` | op | 声明了，代码里没有引用 |

`/sh AddBlockLimit`、`SetBlockLimit`、`DelBlockLimit` 不走节点，只看 `isOp()`。

---

## 6. config.yml 逐项说明

按文件里的顺序走。**默认值**列写的是 `src/main/resources/config.yml` 里发布的值。

新增的配置项由 `ConfigUpdate.mergeMissingDefaultConfigKeys` 在启动时自动补进已有的
`config.yml`，所以升级插件不需要删配置重生成；但**改动某一项的默认值**需要在
`InternalMigrations` 里加标记块才会覆盖旧值，否则你的旧值会被保留。

### 6.1 基础与数据库

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `Version` | `2.1` | 配置版本号，`ConfigUpdate` 用它决定跑哪些迁移块。**不要手改** |
| `Language` | `Chinese` | 语言文件名，可选 `Chinese` / `Chinese_TW` / `English`，对应 `Language/<值>.yml` |
| `BungeeCord` | `false` | 见[第 2 节](#2-两种存储模式)。改这一项等于换存储后端 |
| `Type` | `MySQL` | 数据库类型标识，目前只有 MySQL 一种实现 |
| `Host` / `Port` / `Database` / `Username` / `Password` | `localhost` / `3306` / `root` / `root` / `root` | 数据库凭据。**发布前必须改**，默认值是明显的占位。`/sh reload` 会重读并重建连接池 |
| `HikariCP.connectionTimeout` | `30000` | 取连接的超时毫秒 |
| `HikariCP.minimumIdle` | `50` | 最小空闲连接。**这个默认值比 `maximumPoolSize` 还大**，HikariCP 会把它压到 `maximumPoolSize`，等于池永远保持满负荷。多子服共库时建议改成 5–10 |
| `HikariCP.maximumPoolSize` | `30` | 池上限 |
| `Server` | `家园一区` | 本子服名字。**多子服必须各不相同**，跨服路由靠它 |
| `Prefix` | `§b[§d错误庄园§b]` | 插件前缀 |
| `Debug` | `false` | 打开后在控制台打印跨服通信集合、创建流程等细节。排查跨服问题时开，平时关 |

### 6.2 子服角色与负载

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `DisableFunctionButTeleport` | `false` | 纯传送子服模式。为 `true` **且** `BungeeCord: true` 时插件只保留一小部分指令，并且**缺少 Vault 也不会自我禁用** |
| `AutoReCreateInLowerLagHome` | `true` | 创建家园时自动挑一个负载低的子服 |
| `DecideBy` | `Player` | 挑选依据。`Player` 按在线人数，`TPS` 按三次 TPS 均值 —— 后者需要先装 PAPI 的 Server 扩展（`/papi ecloud download Server`），没装就退化 |
| `AutoMoveWorldFilesToOther` | `false` | 按 `DecideBy` 把存档文件搬到别的子服。**动的是磁盘上的世界目录，风险高**，默认关着就别开 |
| `MoveWorldAfterUnLoad` | `false` | 只在世界已卸载时才允许搬档。开 `AutoMoveWorldFilesToOther` 的话这一项应当一起开 |

### 6.3 边界与世界生成

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `HomeTerrain.Enabled` | `true` | 自然地形模式总开关。开启后**所有**创建请求都被强制为类型 1（原版 NORMAL 地形、结构关闭），`/sh create 2`、`/sh create airland` 会静默变成普通地形。关闭后恢复类型 1/2/airland 三选 |
| `HomeTerrain.GenerateStructures` | `false` | 自然地形是否生成村庄等结构 |
| `HomeTerrain.PermanentDay` / `DayTime` | `true` / `6000` | 家园世界永久白天及锁定的游戏刻 |
| `HomeUpgrade.LevelSizes` | `8…96 步长 8` | 各等级边界边长表。**会被排序、剔除超出 [8,96] 的条目**，乱序表会让升级缩边 |
| `WorldBoard` / `UpdateRadius` | `8` / `8` | 关闭 `HomeTerrain.Enabled` 时才用的旧公式：初始边长 + 每级加的半径 |
| `FaweSwitch` | `false` | FAWE 形状边界（`FirstBorderShaped` 物理方块壳）。**必须同时装 FAWE 且此项为 true** 才生效；主线程 2000 方块/tick |
| `BorderMaterial` / `BorderShape` / `UpdateClearOld` | `BEDROCK` / `Square` | 形状边界的材质与形状（`Circle` 实际是球形边界），升级时是否拆掉旧壳 |
| `SoilType` | `FARMLAND` | 耕地材质，1.12.2 以下服务端改成 `SOIL` |
| `EnableHeightLimit` / `MaxHeight` | `true` / `255` | 家园内建筑高度上限 |
| `Seed` | `0` | 自定义世界种子，0 为随机 |
| `Superflat.GeneratorSettings` | `''` | 超平坦地层 JSON（原版 `flat_level_generator_settings` 格式，必须含 `layers`）。留空用内置预设（基岩1+泥土2+草1，平原）。控制台的 `No key layers in MapLike[{}]` 警告与此相关，属未解决问题，见 `docs/world-generation-investigation.md` |
| `generateStructures` | `false` | 家园世界是否生成自然建筑（与 `HomeTerrain.GenerateStructures` 是两个键） |
| `KeepSpawnInMemory` | `false` | 世界出生区块常加载。纯服建议 false 省内存；某些模组服（IC2 线缆）反而需要 true |
| `EnableTimeLock` | `true` | 时间锁定功能的实现层。spark 里看到 locktime 占用高就关 |
| `MultiverseCoreCompability` | `true` | 多世界插件兼容模式 |
| `EnableMultiverseCoreCreate` | `false` | 用 Multiverse 创建世界而不是 Bukkit 原生 |

**边界尺寸计算**统一走 `HomeTerrainPolicy`：最终边长 = 等级表（或旧公式）+ `VIPAdd` 半径×2，再夹到 [8, 96]。VIP 加成取"主人或任一在线管理员"持有节点中的**最大值**，并且有棘轮保护（`VipBorderRatchet`）—— VIP 下线后边界不会当场缩水把人埋进去，重启后才回落。

### 6.4 创建收费与模板

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `Random` | `['1','2']` | `/sh create random` 从这个列表里随机抽一个类型 |
| `NormalType` | `1` | `/sh create` 不带参数时默认的类型（0 = 禁用） |
| `CreateCost.Enable` | `true` | 创建收费总开关。**左键=随机种子，右键=指定种子**，两档价格分开 |
| `CreateCost.RandomSeed.Money/Points` | `59999` / `520` | 随机种子价 |
| `CreateCost.CustomSeed.Money/Points` | `99999` / `999` | 指定种子价。金币优先扣，扣不起再看点券，两个都付不起则免费 —— 语义是"金币为准，点券兜底" |
| `CreateTemplate.EnableNormal/NormalWorld` | `false` / `''` | 普通地形用预生成模板目录复制代替实时生成，比在线生成稳定。填世界目录名或绝对路径 |
| `CreateTemplate.EnableFlat/FlatWorld` | `false` / `''` | 同上，超平坦版 |
| `ClearInventoryBeforeCreate` | `false` | 创建前清背包 |
| `EnableSpawnProtection` | `true` | 出生点在虚空上时垫一块草方块（空岛推荐） |
| `HomeTravel.AllowNetherEnd` | `false` | 访问他人家园时能否进入其地狱/末地 |
| `SkyIsland.Enable` | `true` | 空岛（airland）模式总开关 |
| `SkyIsland.CreateKey` | `airland` | 空岛的类型参数，`/sh create airland` 里的那个词 |
| `SkyIsland.CenterX/CenterZ/SpawnY` | `0`/`0`/`65` | 空岛中心与出生高度 |
| `SkyIsland.ClearHeight` / `PlatformRadius` | `6` / `3` | 清空高度与出生平台半径 |
| `SkyIsland.PlatformTop/Middle/Bottom` | 草/土/基岩 | 出生平台三层材质 |
| `SkyIsland.StarterChest.Enable` | `true` | 出生礼箱（默认含岩浆桶、冰、树苗、泥土 —— 标准空岛开局） |
| `SkyIsland.StarterChest.Items` | 4 项 | 格式 `物品,数量,槽位` |
| `ProhibitPlaceInCenter` | `[STONE]` | 禁止在中心点放置的材质列表 |
| `SubStringNBT` | `50` | 调试输出 NBT 的截断长度 |
| `IlleagalName` | `zc`,`world` | 禁止用作家园名的关键词（防止与真实世界重名） |

### 6.5 升级价格与上限

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `MaxLevel` | `12` | 家园可升到的最高**等级**。注意 `MoneyNeed`/`PointsNeed`/`ItemsNeed` 数的是**升级次数**，所以每个开启的价格表需要 `MaxLevel - 1 = 11` 条 —— 多余的忽略，缺的该次升级免费 |
| `MaxOwnedHomes` | `3` | 每人最多拥有的家园数 |
| `MaxOP` / `MaxJoin` | `2` / `10` | 每家园管理员/成员上限（可用 `ErrorTown.MaxOP.数字`、`ErrorTown.MaxJoin.数字` 权限和付费扩容叠加，取最大值） |
| `MaxDelete` | `3` | 可删除家园的次数 |
| `Upgrade.EnableMoney/EnablePoints/EnableItems` | `false`/`false`/`true` | 升级菜单显示哪些付费方式。全部关掉的话玩家无法升级 |
| `MoneyNeed` | 11 条 | 每次升级的金币价 |
| `PointsNeed` | 11 条 | 每次升级的点券价 |
| `ItemsNeed` | 11 条 | 格式 `物品,数量[,显示名]`，如 `DIAMOND,64,§a钻石` |
| `ItemsChineseName` | 11 条 | 物品中文名，供 PAPI 变量显示 |
| `InviteAccess.MaxTotalHomes` | `3` | 一名玩家最多能同时被邀请进几个家园 |

数值型权限节点（`ErrorTown.Level.数字`、`MaxOP.数字`、`MaxJoin.数字`、`Flowers.数字`、`ChunkPlace.<nbt>.数字`、`WorldPlace.<nbt>.数字`）是"取最大命中"逻辑，代码从上限往下扫，命中即停。注意扫描起点是 `max(1,fallback)*1000` —— 比如给玩家 `ErrorTown.MaxJoin.500` 会**查不到**，因为只扫 10000→11。这个区间外的节点写了也不生效。

### 6.6 负载保护与自动清理

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `MaxTiles` | `30000` | 家园 tile 实体警告线，超线在统计里标黄 |
| `UnLoadTiles` | `50000` | 家园 tile 实体封锁线，超线**自动卸载世界** |
| `DeleteItems` | `500` | 家园掉落物上限，超了自动清 |
| `DeleteEntities` | `2000` | 家园实体总数上限，超了自动清 |
| `CheckTime` | `600` | 检查频率（秒），0 = 禁用 |
| `WhiteEntities` | 4 项 | 清理时跳过的实体名关键字（VILLAGE/PLAYER/ICEANDFIRE/ASTRALSORCERY） |
| `ArmorStand` | `-1` | 盔甲架单独限额，-1 不启用 |
| `SaveTime` | `300` | 家园世界自动保存间隔（秒），0 = 禁用 |
| `UnAutoSaveWorlds` | `[DIM34676]` | 不参与自动保存的世界名 |
| `AutoBackup` | `0` | 自动备份间隔（秒），默认禁用。建议 300 秒以上，否则报错刷屏 |
| `NoBackup` | `3` | 多少天没被访问的家园取消自动备份 |
| `CustomBackupLocation` | `''` | 自定义备份路径，如 `E:\`，空为插件目录 |
| `OptimizeType` | `0` | 优化类型：1 = 卸载世界，2 = 卸载区块。0 = 关闭。模组服**不建议**开 |
| `OptimizeTime` | `0` | 优化检查间隔（秒） |
| `UnOptimizeWorlds` | `[ZC]` | 优化白名单（不参与优化的世界） |

### 6.7 游戏规则默认值

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `KeepInventory` | `true` | 家园内死亡不掉落 |
| `doMobSpawning` | `true` | 家园是否刷怪 |
| `mobGriefing` | `true` | 家园内生物破坏（苦力怕炸方块等） |
| `doFireTick` | `false` | 火势蔓延 |
| `NormalPublic` / `NormalPVP` | `false`/`false` | 新家园默认公开/PVP |
| `NormalPickup` / `NormalDrop` | `true`/`true` | 成员默认可拾取/丢弃 |
| `BeKickedCommand` | `spawn <Name>` | 被踢出家园后后台执行的指令 |
| `HomeRulesDefaults.MaxMobCount` | `0` | 玩家可自设的刷怪上限默认值（0 = 不设限） |
| `HomeRulesDefaults.MaxMobCountMin/Max/Step` | `0`/`512`/`8` | 玩家调节数量的下限/上限/步长 |
| `HomeRulesDefaults.MaxVillagerCount` | `12` | 村民单独上限 |
| `HomeRulesDefaults.AllowVillagerPickupItems` | `true` | 村民能否捡物品 |
| `HomeRulesDefaults.CountVillagersInMobCap` | `false` | 村民是否计入刷怪上限 |
| `HomeSpawnCompensation.Enable` | `true` | 家园刷怪补偿。原版刷怪上限按世界算，小地块的家园会分不到怪，开启后按家园数量把服务端 monster spawn limit 抬到 `TargetMonsterCap` |
| `HomeSpawnCompensation.TargetMonsterCap` | `70` | 目标 monster spawn limit |
| `HomeSpawnCompensation.MinMonsterSpawnLimit/Max…` | `70`/`1024` | 补偿后的上下限夹取 |
| `HomeSpawnCompensation.ApplyToNether` | `true` | 补偿是否作用于家园地狱 |

**注意**：`applyWorldPolicy` 不允许把刷怪关成 0 —— `doMobSpawning: false` 只是关原版自然刷怪，家园地狱独立刷怪，具体上限由 `HomeRulesDefaults` 树控制，`HomeSpawnUtil` 是唯一的怪物上限归属处。

### 6.8 方块/实体/物品限制

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `CustomTileMax` | `false` | TileList 限额总开关（数的是 `Chunk.getTileEntities()`） |
| `TileList` | 4 条 | 格式 `<chunk\|world>\|<KEYWORD>\|<数量>`。`chunk` = 每区块限量，`world` = 全世界限量。匹配串是 NBT 关键字（子串匹配，`/sh nbt` 可查） |
| `AnotherChunkLimit` | 3 条 | 高等级放宽：格式 `<KEYWORD>\|<等级>\|<该等级限量>`。家园等级 ≥ 该值时用这个数量替代基础量 |
| `EnableClearExtraBlocks` | `false` | 超限时**删除已放置的**多余方块再拒绝新的（默认只拒绝新的） |
| `CraftEngineBlockLimit.Enabled` | `false` | CraftEngine 自定义方块限额。CE 方块大多不是 block entity，TileList 数不到它们，这里是单独补的口子。**限额是"局部"的**：只扫放置点周围一个立方体，不是整区块 |
| `CraftEngineBlockLimit.ScanRadius` | `6` | 扫描半径 1–17（默认 13×13×13）。调大显著增加放置开销。规则同样写在 `TileList`，匹配串加 `ce:` 前缀 |
| `CustomEntityMax` | `false` | 实体限额总开关 |
| `EntityList` | `[ZOMBIE\|8]` | 格式 `<实体类型>\|<数量>`，动物统一写 `Animals` |
| `CheckEntityInterval` | `60` | 实体检查间隔（秒） |
| `EnableBlackEntities` | `false` | 生物黑名单总开关 |
| `BlackEntitiesList` | `[VILLAGER]` | 禁止生成/养殖的实体类型 |
| `EnableBlackItemsUseInNoPermission` | `false` | 禁止无权限者在非自家园使用指定物品 |
| `BlackItems` | `[DIAMOND_SWORD]` | 物品黑名单 |

### 6.9 家园专属下界与末地

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `EnableHomeNether` | `true` | 每个家园主世界配一个独立地狱（世界名 = 家园名 + `HomeNetherSuffix`）。玩家在自家园搭下界传送门即可过去，边界随主世界按 `HomeNetherScale` 换算。需要 `allow-nether=true`；开启后即使 `DisablePortalCreate=true` 也允许在自家园搭门 |
| `HomeNetherSuffix` | `_nether` | 地狱世界名后缀 |
| `HomeNetherScale` | `1` | 主世界:地狱坐标比例。1 = 1:1（推荐，双维度对照建筑最直观）；8 = 原版比例（地块小的家园地狱会很小） |
| `HomeNetherPortal.SearchRadius` / `CreationRadius` | `16`/`16` | 找门/建门半径 |
| `HomeNetherPortal.UseVanillaResolver` | `true` | 找门建门交给原版内核（坐标更接近原版）。false 用插件手动逻辑，只在原版 POI 搜索异常时兜底 |
| `EnableNetherTeleport` / `NetherWorldName` | `false`/`DIM-1` | **公共**下界传送 `/sh nether`（与家园专属下界是两个功能），指向你服务器已有的地狱世界 |
| `EnableEndTeleport` / `EndWorldName` | `false`/`DIM1` | 同上，公共末地 `/sh end` |
| `NeitherGameRules` / `EndGameRules` | 4 条 | 进入公共下界/末地时应用的规则，格式 `规则名,值` |
| `DisablePortalCreate` | `true` | 禁止在非自家园创建传送门 |
| `DimensionCreate.CreateNetherCost/Points` | `20000`/`400` | 付费创建家园下界的价格。**注意 `createEnd`/`resetEnd` 目前硬编码禁用、不收费**，对应价格暂时无效 |
| `DimensionCreate.ResetOverworldCost/Points` | `12000`/`240` | 重置主世界价格 |
| `DimensionCreate.ResetNetherCost/Points` | `10000`/`200` | 重置下界价格 |
| `DimensionCreate.CustomSeedExtraCost/Points` | `10000`/`200` | 自定义种子重置的加价 |

### 6.10 玩家个性化与付费服务

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `HomeTitle.Enable` | `true` | 家园标题/描述功能 |
| `HomeTitle.MaxTitleLength` | `32` | 标题最长字符 |
| `HomeTitle.MaxDescriptionLines` | `5` | 描述最多几行 |
| `DifficultyChange.Enable` | `true` | 家园难度切换服务 |
| `DifficultyChange.Cost/Points` | `1500`/`30` | 切换一次的价格（金币优先） |
| `Difficulty` | `Easy` | 新家园默认难度（也是切换的候选：Easy/Normal/Hard/Peaceful） |
| `BiomeChange.Enable` | `true` | 群系修改服务 |
| `BiomeChange.SingleChunkCost` | `1000` | 单区块群系修改价 |
| `BiomeChange.AllChunksCost` | `8000` | 边界内全部修改价 |
| `FeatureUnlock.LockTimeLevel` | `3` | `/sh locktime` 需要的家园等级 |
| `FeatureUnlock.LockWeatherLevel` | `3` | `/sh lockweather` 需要的家园等级 |
| `FeatureUnlock.BiomeChangeLevel` | `3` | 群系修改需要的家园等级 |
| `FeatureUnlock.MobRuleAdvancedLevel` | `3` | 高级怪物规则（`/sh rule mobcap`）需要的家园等级 |
| `MemberUpgrade.Enable` | `true` | 成员扩容服务 |
| `MemberUpgrade.Plans` | 4 档 | 格式 `花费金币,花费点券,增加人数`。**文件里的注释说 2 个字段，实际是 3 个**，以实机为准 |
| `MemberUpgrade.AbsoluteMax` | `50` | 扩容后成员绝对上限。**买一次 upgrademember 同时抬成员和管理员两个上限** |
| `SetSpawn.GoldFee/PointFee` | `1000`/`20` | 移动庄园中心点价格 |
| `SetSpawn.CooldownSeconds` | `86400` | 移动冷却（默认 24 小时） |
| `SetSpawn.SyncTeleportAndRespawn` | `true` | 传送点与复活点同步 |
| `VIPAdd` | `[v1up,0]`,`[v2up,0]` | 格式 `权限,半径加成`。持 `ErrorTown.<权限>` 的主人/在线管理员给家园边界加半径（最终×2 进边长）。默认两条都是 0，即模板 |
| `VIPDiscount` | `[st1,95]`… | 格式 `权限后缀,百分比`。持 `ErrorTown.st1` 的玩家升级付 95%。**多个取最优惠**，同时作用于金币和点券升级 |

### 6.11 跨维度与显示杂项

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `EnableAsnycTime` / `AsyncTimeWorld` | `false`/`world` | 时间同步（拼写就是 Asnyc，历史名）。以 `AsyncTimeWorld` 世界的时间为准同步到各家园 |
| `RealisticSeasons` | `false` | RealisticSeasons 季节插件兼容同步 |
| `NormalJoinWorld` | `''` | 玩家进服时强制传送到的世界（解决个别客户端进服维度崩溃的 BUG）。留空不启用 |
| `EnableAutoRespawnInHome` | `false` | 死亡后自动在自家园复活 |
| `EnableEntityInteract` | `true` | 家园实体交互保护（盔甲架等无权限禁止交互），对拔刀剑实体无效 |
| `EnableChatPrefix` | `true` | 聊天前缀自动显示家园名（没有 MiaoChat 等 PAPI 聊天插件时开） |
| `Material` | `COMPASS` | 右键打开主菜单的物品材质 |
| `ShiftFOpenMainMenu.Enable` | `true` | 按 F（拾取键）开主菜单 |
| `ShiftFOpenMainMenu.RequireSneaking` | `true` | 需要**同时按住潜行**（即 shift+F） |
| `ShiftFOpenMainMenu.CancelOffhandSwap` | `true` | 拦截原本的副手交换动作 |
| `ShiftFOpenMainMenu.Command` | `sh open` | 触发的指令 |
| `VisitGuiShowAll` | `Load` | 访问菜单显示哪些家园：`ALL`/`Public` 显示公开（跨服），`Load` 只显示已加载（仅单服） |
| `HomeInfoPanel.Enable` | `true` | 家园信息面板 |
| `HomeWorldLifecycle.EnableAutoUnload` | `true` | 无人时自动卸载家园世界 |
| `HomeWorldLifecycle.UnloadDelaySeconds` | `180` | 无人后延迟多少秒卸载 |
| `Spawn` | `world` | 删除家园/卸载/被踢出后传送到哪个世界 |
| `CheckUpdate` | `true` | 版本检测（给 OP 发更新提示） |
| `MaxFlowers` | `5` | 每天最多投递鲜花数（可被 `ErrorTown.Flowers.数字` 权限放大） |
| `PopularityAdd` / `FlowerAdd` | `0.1`/`0.3` | 人气/鲜花对家园人气值的权重 |
| `EnableTilesAndChunksAndDropItemsStatisticsTop` | `true` | 开启负载排行播报 |
| `ShowAmount` | `5` | 每次播报前几名 |
| `ShowTimes` | `600` | 播报间隔（秒） |
| `OneTileTick`/`OneEntityTick`/`OneChunkTick`/`OneDropTick` | 见文件 | 统计里各类对象的耗时权重（估算用） |
| `FormatInfo` | `%.2f` | 统计数字的小数格式 |
| `StatisticsTop`/`ShowFormat`/`StatisticsEnd` | 见文件 | 播报的头/行/尾模板。行模板占位符：`<index> <world> <tile> <chunk> <entity> <drop> <tps>` |
| `LegacyPermissions` | `true` | 同时接受旧 `SummerTown.*` 权限节点（权限存在 LuckPerms 里无法自动迁移）。**全部改完后设 false** |
| `Permission.CommandUser` | `true` | 默认权限开关表：玩家基础指令默认给不给 |
| `Permission.Visit/SetSpawn/Nether/End/Rain/Sun/Night/Day` | 见文件 | 对应 `ErrorTown.Visit` 等节点的默认值 |
| `Permission.Create-1/Create-2/Create-airland` | `true` | 三种创建类型的默认值 |
| `Permission.LockTime/LockWeather` | `false` | 锁时间/锁天气节点默认值 |

> `Permission.*` 表改的是"**没被权限插件授予时**的默认值"，不是替代权限插件。给玩家组加节点仍然要在 LuckPerms 里操作。

### 6.12 Formatting（文本格式）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `Formatting.Mode` | `auto` | `auto` 只把**看起来含 `<标签>`** 的文本交给 MiniMessage，其余原样输出 —— 零开销零风险。`minimessage` 全部按 MM 解析（转义更严格，改前通读 MM 文档）。`legacy` 完全关闭 MM |
| `Formatting.TranslateAmpersand` | `true` | 把 `&a`/`&l`/`&#RRGGBB` 也当颜色码。消息里需要字面 `&` + 字母时关掉 |

MiniMessage 示例：`<gradient:#ff5f6d:#ffc371>错误庄园</gradient>`、`<bold><#55FF55>家园创建成功`、`<rainbow>欢迎回家`。

**陷阱**：语言文件里大量占位符（`<Name>`、`<player>`、`<Key>`）长得就像 MM 标签，它们能活着是因为 MM 对未知标签按字面输出。所以：不要把 Mode 默认改成 `minimessage`；不要引入与真实标签同名的占位符（`<key>`、`<color>`、`<font>`、`<newline>`、`<reset>`、`<lang>` 都是真实标签）；不要把玩家输入拼进模板再过 `Text.format`。

---

## 7. GUI.yml 菜单与按钮

`GUI.yml` 同时定义所有菜单的**标题、行数和按钮**，以及 `Language` 文件里的提示文本分工 —— GUI 结构在这里，按钮文本也在这里，提示消息在 `Language/*.yml`。

### 7.1 菜单结构

| 键 | 说明 |
| --- | --- |
| `<Menu>Title` | 菜单标题 |
| `<Menu>Size` | 菜单行数。**填 1–6（行）或 9–54（格）**，其他值让 `Bukkit.createInventory` 抛异常导致菜单永远打不开 —— `GuiSafe.size` 会拦下并回落为 6 行，同时警告一次 |
| `<Menu>` 按钮块 | 根级 `ButtonN` 条目，见下表 |

### 7.2 按钮字段

| 字段 | 说明 |
| --- | --- |
| `InMenu` | 属于哪个菜单 |
| `Index` | 槽位，**1 起数**。超出菜单容量的按钮直接跳过不渲染 |
| `Material` | 物品材质。经 `GuiSafe.material` 解析，非法值回落并警告（不要在这里用 1.12 之前的旧名） |
| `CustomName` | 显示名。**这是点击匹配的键**，见下面的陷阱 |
| `Lores` | Lore 列表 |
| `Permission` | **只控制显不显示**，不控制能不能用。点击后实际派发的指令自己检查权限 |
| `LeftInTo` / `RightInTo` | 左键/右键触发的指令（下 tick 由玩家执行），如 `sh create 1` |
| `KeepOpen` | 点击后是否保持菜单打开 |

### 7.3 点击是怎么匹配的（重要）

`InventoryClickListener` 取消所有点击，然后按**玩家点到的物品显示名**反查按钮：扫 `GUI.yml` 所有根键，找 `InMenu` 匹配当前菜单且 `CustomName` **逐字符等于**玩家看到的显示名的那个。

由此推出两个坑：

1. **`CustomName` 里写了 MiniMessage 标签或 `&` 代码，按钮就永远点不动** —— 渲染出来的显示名被 `Text.format` 转成了 `§` 色，而匹配的是原始 YAML 字符串。GUI.yml 里现有的 CustomName 都是裸 `§` 或纯文本，改的时候保持这一点。
2. **`CustomName` 必须在同一个菜单里唯一**，两个按钮同名时只有先扫到的那个能被点到。

### 7.4 GUI.yml 里的费用文案是死的

`GUI.yml` 里几个按钮 Lore 写死了价格示例（Button28 移动中心点 2000/24h、Button52 难度 3000/30、Button54 创建下界 10000、Button51 群系 500/5000、Button53 扩容 1000+1 成员）。它们**不会跟随 config.yml 的实际价格更新**，实价以 §6.4/6.9/6.10 各表为准。调价后请手改这两处，或把 Lore 改成不含具体数字的文案。

---

## 8. 语言文件与提示文本

三个文件完全同构：`Language/Chinese.yml`（简中）、`Chinese_TW.yml`（繁中）、`English.yml`。

### 8.1 读取规则

- **代码里读提示一律走 `com.Util.Lang`**（`get`/`list`/`format`/`send`/`sendFramed`），不要直接 `Variable.Lang_YML.getString`。`Lang.get` 在键缺失时回落到 jar 内置默认值，并向控制台报告一次缺失键。
- **老服务器升级后新键是空的**：Bukkit 只在语言文件不存在时才写出内置默认，所以后续版本新增的键不会自动出现在已存在的文件里。`Lang.seedMissingFromBundle()` 在启动时把缺失键补齐，`Lang.audit()` 随后 diff 一遍并报告仍缺失的部分。升级后看到 `Missing language key` 告警，重启一次即可自愈。
- `PlaceHolders.RefreshTime`（PAPI 缓存秒数）**在语言文件里**，不在 config.yml。

### 8.2 帮助页契约

`/sh help 1…6` 的六页内容由语言文件里的 `Help-1`…`Help-6` 列表驱动。每条格式：

```
- '显示文本,/sh 实际执行的指令'
```

**每条恰好一个逗号**（第一个逗号前是文本，后面是可点击执行的指令，指令必须以 `/sh ` 开头），由 `HelpPagesResourceTest` 强制。改文案时别加逗号。另有 `HelpPageMissing`（`<Key>` 占位符）在页码越界时显示，和每页末尾的导航行（含 下一/上一/第一/Next/Prev/First 字样，同样被测试钉住）。

`Chinese.yml` 里历史上存在**两套** `Help-1…6`，YAML 后者覆盖前者，前面那套（带引号键的）是死数据，改文案请改文件靠后那套。

### 8.3 已知的怪名字

- `HeadLineTtitle` / `BottomLineTtitle` —— Ttitle 是拼写错误但**被代码引用着**，改名会静默退回默认值，别动。
- `LookInfo` 里的 `%ErrorTown_WOrld_DropItem%` —— WOrld 大小写也是历史遗留，同理别"修"。
- `HDTags*` 系列的方向词是乱的（前后左右对不上实际方向），全息标记的提示文案，无功能影响。

---

## 9. PlaceholderAPI 变量

标识符 `ErrorTown`，同时注册了旧标识 `SummerTown` 的别名（其他插件配置里已写死的 `%SummerTown_xxx%` 不用改）。

服务端作用域（`player` 为 null，给 TAB/计分板头部用）与玩家作用域分两支。**玩家分支全部走 `PlaceholderValueCache`**（`Variable.cache`）：8192 条上限、访问序 LRU、键不区分大小写、TTL = 语言文件里的 `PlaceHolders.RefreshTime`（默认 5 秒）。服务端分支**不缓存**，每次现算。

常用变量（完整列表以 `PlaceHolder/API.java` 的 `check.contains(...)` 链为准）：

| 变量 | 说明 |
| --- | --- |
| `%ErrorTown_HasHome%` / `%ErrorTown_HomeName%` | 是否有家园 / 家园名 |
| `%ErrorTown_Level%` | 家园等级 |
| `%ErrorTown_World_Size%` / `%ErrorTown_Tile%` / `%ErrorTown_Entity%` / `%ErrorTown_Chunk%` / `%ErrorTown_DropItem%` | 边界尺寸 / tile 数 / 实体数 / 区块数 / 掉落物数 |
| `%ErrorTown_WOrld_DropItem%` | 同上的历史拼写照不改 |
| `%ErrorTown_Member_Count%` / `%ErrorTown_OP_Count%` | 成员/管理员数 |
| `%ErrorTown_Title%` / `%ErrorTown_Description%` | 家园标题/描述 |
| `%ErrorTown_NextMoney%` / `%ErrorTown_NextPoints%` / `%ErrorTown_NextItems%` / `%ErrorTown_NextItemsChineseName%` | 下一次升级需要的钱/点券/物品及其中文名 |
| `%ErrorTown_IsPublic%` / `%ErrorTown_IsLock%` | 公开/上锁状态 |
| `%ErrorTown_Popularity%` / `%ErrorTown_Flowers%` | 人气值 / 收到鲜花数 |

变量解析在主线程跑且被计分板插件高频调用，解析内部有 MySQL/YAML 读 —— 这就是缓存存在的原因。把 `PlaceHolders.RefreshTime` 调小会明显放大数据库压力。

---

## 10. 边界尺寸是怎么算出来的

一个数字，三条路，最后夹到 [8, 96]（`HomeTerrainPolicy.MINIMUM_SIZE`/`MAXIMUM_SIZE`）：

```
configuredBorderSize(level, terrainEnabled, levelSizes, worldBoard, updateRadius, vipExtra)
  = clamp( baseSize(level) + vipExtra * 2,  8, 96 )

baseSize(level):
  HomeTerrain.Enabled = true  → HomeUpgrade.LevelSizes[level-1]   （等级表直取）
  HomeTerrain.Enabled = false → WorldBoard + (level - 1) * UpdateRadius
```

要点：

1. **`VIPAdd` 填的是半径**，所以代码里 ×2 加到边长上。填 4 就是边长 +8。
2. **上限 96 是磁盘/区块预算决定的**（约 36–49 区块、每家园 2–8 MiB），不是随手写的。想放宽先读 `docs/natural-home-terrain.md` 的估算。
3. **升级永不缩边**：`upgradedBorderSize` 只替换等级基础部分，已给的 VIP 直径不会被一次降级表收回；`LevelSizes` 表本身也会被排序 + 过滤，乱序表不会让升级反向缩水。
4. **VIP 棘轮**（`VipBorderRatchet`）：加成取"主人或任一**在线**管理员"的最大节点值，人下线加成就归零 —— 直接喂给边界会把站在边界里的玩家圈死，所以六个调用点共享 `Util.border_redis` 一个高水位缓存，只会升不会降，重启后回落。
5. 边界由四套机制共同呈现，尺寸全部走上面这一个函数：
   - Bukkit `WorldBorder` API（**权威**，`ScheduledTasks` 每 60 tick 重申一次）；
   - 全息角标（`Util.refreshBorder`，`HDSwitch` 开且装了 HD 时画四个角）；
   - FAWE 物理方块壳（`FirstBorderShaped`，需 FAWE + `FaweSwitch: true`）；
   - `/sh togglecc` 的玩家级临时边界（`WBControl`）。
   保护监听器对无玩家事件（活塞、流体、火焰、爆炸等）用 `WorldBorder#isInside` 判定，保证和上面的计算**永远一致**。

---

## 11. 收费项一览

给服主对账用。所有"金币优先、点券兜底、双零免费"的项都遵循 `Util.chargeMoneyOrPoints` 语义；VIP 折扣只作用于**升级**（`VIPDiscount`），创建和其他服务不折扣。

| 项目 | 键 | 默认价（金币/点券） |
| --- | --- | --- |
| 创建家园（随机种子） | `CreateCost.RandomSeed` | 59999 / 520 |
| 创建家园（指定种子） | `CreateCost.CustomSeed` | 99999 / 999 |
| 升级家园（每级） | `MoneyNeed` / `PointsNeed` / `ItemsNeed` | 按级 11 条 |
| 移动中心点 | `SetSpawn.GoldFee` / `PointFee` | 1000 / 20 |
| 难度切换 | `DifficultyChange.Cost` / `Points` | 1500 / 30 |
| 群系单区块 | `BiomeChange.SingleChunkCost` | 1000 / — |
| 群系全边界 | `BiomeChange.AllChunksCost` | 8000 / — |
| 成员扩容 | `MemberUpgrade.Plans` | 3000/60/1 人 … 30000/600/8 人 |
| 创建家园下界 | `DimensionCreate.CreateNetherCost` / `Points` | 20000 / 400 |
| 创建家园末地 | `DimensionCreate.CreateEndCost` / `Points` | **暂无效**（end 功能硬编码禁用） |
| 重置主世界 | `DimensionCreate.ResetOverworldCost` / `Points` | 12000 / 240 |
| 重置下界 | `DimensionCreate.ResetNetherCost` / `Points` | 10000 / 200 |
| 重置末地 | `DimensionCreate.ResetEndCost` / `Points` | **暂无效**（同上） |
| 重置加自定义种子 | `DimensionCreate.CustomSeedExtraCost` / `Points` | 10000 / 200 |

**扣费与退款**：每笔创建费写入 `create-cost-ledger.yml`（见 §2），创建失败（世界创建异常、区块加载失败、断线、排队超时、僵尸槽位回收）都会走 `CreateCostLedger` 退款；`recoverPending()` 在每次开服时重放未决账目，后台任务每 120 秒重试卡住的退款。**不要手删账本文件** —— 里面有已收款未交付的记录。

---

## 12. 启动自检与排错

开服日志里会依次出现（顺序固定）：

| 步骤 | 日志特征 | 说明 |
| --- | --- | --- |
| 平台报告 | `reportPlatform` | 服务端类型、MC 版本、是否识别为 Paper 系 |
| 语言补种 | `Lang.seedMissingFromBundle` | 把新版本语言键补进旧文件 |
| 语言审计 | `Lang.audit` | 报告仍缺失的键；有输出就再重启一次让它补完 |
| 配置体检 | `reportConfigFindings` | `ConfigValidator` 的 11 条规则，输出 `Finding(严重度, 键, 说明)` |
| 账本重放 | `CreateCostLedger.recoverPending` | 退款未决记录重放 |

常见启动告警对照：

| 告警 | 原因 | 处置 |
| --- | --- | --- |
| `VIPAdd entry ... is not 'permission,radius'` | 条目缺逗号或非数字 | 修条目；负数按 0 处理只是 WARN |
| `Ignoring malformed VIPAdd entry` | 同上（运行时路径） | 同上 |
| `minimumIdle > maximumPoolSize` 类问题 | HikariCP 默认值失衡 | 见 §6.1，调小 minimumIdle |
| `Could not execute test class ...`（仅开发环境） | 仓库路径含非 ASCII 字符 | 环境问题，见 CLAUDE.md 的测试方法 |
| `No key layers in MapLike[{}]`（创建超平坦时） | 未解决的原版告警 | 忽略，**不要**因此删世界或改 WorldType |
| 玩家付费后没建家园 | 账本有 pending 记录 | 看 `create-cost-ledger.yml`，等 120 秒自动重试退款 |
| 权限不生效但 LuckPerms 有节点 | 节点大小写或前缀不对 | 检查是否 `ErrorTown.` 前缀；旧服先确认 `LegacyPermissions` |
| LuckPerms 配了 `ErrorTown.MaxJoin.500` 没生效 | 超出扫描区间 | 见 §5.3/6.5，扫描只到 `fallback*1000` 往下 |
| 家园世界被卸载 | tile 超 `UnLoadTiles` | 看统计播报，清理后自动恢复 |
| 菜单打不开 | `GUI.yml` 的 `Size` 非法 | 见 §7.1，改成 1–6 或 9–54 |

**Folia 检测**：插件在 Folia 上会明确拒绝启动（没有世界加载/卸载 API，同步 `Entity#teleport` 也不可用），这是设计决定，不是 bug。

---

## 13. 已知限制

- **玩家身份按名字存储**（on-disk 与 MySQL 均如此），玩家改名即丢家园、丢成员身份。这是数据格式的既定限制，不要零碎地"修"某一处。
- **语言文件里的 `Help-N`、tab 补全、`plugin.yml` 权限树是三份手写列表**，没有单一来源。加指令时三处都要改（见"Adding things"）。
- tab 补全不完整且 `setspawn` 重复两次；`/sh admin` 的补全缺一半子命令。
- `/sh homes` 在 BungeeCord 模式不支持。
- `/sh admin pwp` 读硬编码的 Windows 路径 `plugins\PlayerWorldsPro`，Linux 不可用。
- `/sh dimension` 只在 1.12.2 / 1.7.10 映射下有输出，其他版本提示 `DimensionNotAllow`。
- `ErrorTown.GAMEMODE.NORMAL` 被代码检查但 `plugin.yml` 没声明 —— 给玩家授这个节点仍会生效（权限插件不管声明），但 tab 补全和文档里看不到它。
- `createEnd` / `resetEnd` 硬编码禁用，对应价格配置暂不生效。
- GUI.yml 的费用 Lore 与 config 实价不同步（§7.4）。
- `/sh item` 的第二分支不可达（第一个分支已匹配同名 token）。
- **反编译残留**：`CommandListener` 8858 行、`MySQL` 3147 行、`onRequest` 1550 行，是 Vineflower 反编译产物重建的。新逻辑按 CLAUDE.md 的约定进 `com.Util` 的小类，不要在命令类里继续长。


