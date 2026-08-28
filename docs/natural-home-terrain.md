# 自然地形家园方案

## 已实现的策略

开启 `HomeTerrain.Enabled` 后，新建家园使用 Paper 原版 `WorldType.NORMAL` 生成器：

- 不使用 `CustomChunkGenerator`；
- `generateStructures(false)`，不生成村庄、要塞、海底神殿等结构；
- `doMobSpawning=false`，怪物、动物、环境生物和巡逻/流浪商人生成上限为 0；
- 默认永昼，时间为 `HomeTerrain.DayTime`；
- 家园世界禁止通过插件传送、末影珍珠和传送门进入下界或末地；
- 家园边界按等级计算，硬上限 96×96；
- 创建完成并准备好出生区域前，玩家不能进入家园。

## 尺寸和升级

默认等级尺寸为：

```text
1: 8×8   2: 16×16  3: 24×24  4: 32×32
5: 40×40 6: 48×48  7: 56×56  8: 64×64
9: 72×72 10: 80×80 11: 88×88 12: 96×96
```

物品升级入口沿用原有 `/st update items` 和升级 GUI；`Upgrade.EnableItems` 默认开启。金币和点券升级仍保留为兼容选项，可分别关闭。

## 创建队列

`HomeCreationQueue.MaxConcurrent` 默认是 2，实际值还会被程序硬限制在 2 以内。创建任务分为等待、运行和失败状态：

1. 玩家完成原有创建费用检查后进入队列；
2. 最多两个任务进入世界初始化；
3. 其余任务显示队列位置；
4. 世界生成、出生区域异步加载完成后才传送玩家；
5. 等待期间所有进入该家园的传送都会被拒绝。

Bukkit 的 `createWorld` 仍必须在主线程调用，因此不能承诺创建世界时绝对没有瞬时 CPU 峰值；本方案避免的是大范围同步预生成和玩家在未准备好的世界中卡住。

## 维护结构

- `HomeTerrainPolicy`：纯 Java 规则，负责等级尺寸、96 格硬上限、并发硬上限、区块数和容量估算；
- `HomeTerrainPolicy.configuredBorderSize(...)`：统一自然等级表与旧 `WorldBoard`/`UpdateRadius` 边界公式。`VIPAdd` 按配置含义作为额外半径，因此会扩展边界直径的两侧，最终仍执行 96 格上限；
- `HomeTerrainPolicy.upgradedBorderSize(...)`：升级时只替换基础等级尺寸，保留已生效的权限额外直径，避免自定义等级表或刷新边界时回退；
- `HomeCreationQueue`：纯 Java FIFO 状态机，负责等待、准入、运行和最近 256 条有界失败记录，避免常驻服务器出现无界历史增长；
- `NaturalHomeWorldFactory`：唯一负责构造自然家园的 `WorldCreator`，固定原版主世界、无结构、非出生区块常驻和兼容种子处理；
- `HomeCreationMessages`：唯一负责创建流程语言键、占位符和旧语言文件的默认文本回退；
- `HomeCreationCoordinator`：只负责 Bukkit 调度、玩家状态、超时、区块异步准备、世界规则和上述小类的编排。

维护时应优先修改这些小类，不要继续向反编译的 `CommandListener.java` 添加队列集合、状态变量或 YAML 列表换算。命令类只保留原命令流程，并通过公开方法调用策略与协调器。

相关验证命令：

```powershell
.\tools\Run-HomeTerrainPolicyProbe.ps1
.\tools\Run-HomeCreationQueueProbe.ps1
.\tools\Run-HomeCreationMessagesProbe.ps1
.\tools\Run-NaturalHomeWorldFactoryProbe.ps1
.\tools\Run-HomeCreationCoordinatorCompileProbe.ps1
```

`Chinese.yml`、`Chinese_TW.yml` 和 `English.yml` 都提供 `HomeCreation*` 消息键。既有生产语言文件不会自动覆盖；代码会在键缺失或值为空时采用内置兼容文本，因此升级不会因旧语言文件而中断创建流程。管理员可将资源文件中的新键手动合并进生产语言文件后再自定义文本。

## 硬盘估算

自然世界的实际区块文件大小取决于地形、实体和玩家建筑。建议按每个最终 96×96 家园约 36 个核心区块、每区块 10–40 KiB 的压缩区域数据估算：

```text
单家园核心区块：36
理论区域数据：约 0.35–1.4 MiB
考虑 region 文件、level.dat、备份和玩家建筑余量：约 2–8 MiB/家园
```

日活 10 人不等于每天新增 10 个世界。按“每天新建 10 个家园且全部扩展到 96×96”计算：

```text
约 20–80 MiB/天
约 0.6–2.4 GiB/月
约 7.3–29.2 GiB/年
```

如果 10 名日活玩家只是反复访问已有家园，新增硬盘主要来自实际探索区块和建筑，通常远低于上限。生产上建议至少预留 50 GiB 给一年期家园区块、备份、日志和服务器其他世界；若允许大量建筑、频繁重置或保留多份备份，建议 100 GiB 以上。

## 配置示例

```yaml
HomeTerrain:
  Enabled: true
  GenerateStructures: false
  PermanentDay: true
  DayTime: 6000

HomeCreationQueue:
  MaxConcurrent: 2

HomeTravel:
  AllowNetherEnd: false

HomeUpgrade:
  LevelSizes: [8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96]
```

## 兼容边界

已有家园不会被自动改造成自然地形，也不会被自动压缩或删除。改动配置只影响后续创建和升级；生产部署前必须备份原始 JAR、家园 YAML、世界目录和玩家数据。
