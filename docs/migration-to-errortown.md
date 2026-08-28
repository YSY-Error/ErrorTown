# SummerTown → ErrorTown 迁移指南

本插件已从 `SummerTown` 更名为 `ErrorTown`，作者标注为 `YSYError`。

更名会影响四类**以插件名为寻址依据**的状态。其中两类由插件自动迁移，一类保持向后兼容，
一类必须手动处理。**升级前请先完整备份**：`plugins/SummerTown/`、所有家园世界目录、
MySQL 数据库、以及旧 jar。

---

## 一、自动迁移

### 1. 数据目录 `plugins/SummerTown/` → `plugins/ErrorTown/`

Bukkit 根据 `plugin.yml` 的 `name:` 决定数据目录，所以更名会让插件去找一个空目录 ——
配置、家园 yml、备份、审计日志全部"消失"。

`com.Util.RenameMigration.migrateDataFolder()` 在 `Main.init()` 最开头执行（必须早于任何
`saveDefaultConfig` / `saveResource`，否则新目录先被建出文件，迁移就会被跳过）：

- 只有旧目录存在时才动作；
- 优先 `ATOMIC_MOVE` 整体改名；
- 文件系统拒绝改名（跨卷、句柄占用）时退化为**复制**，并保留旧目录，日志会提示你自行删除；
- 若新旧目录同时存在且新目录非空，**不做任何事**并打印警告 —— 这种情况需要人工合并，
  自动合并只会造成更难排查的混乱。

### 2. MySQL 表 `SummerTown_Users` / `SummerTown_Servers`

仅在 `BungeeCord: true` 的跨服模式下使用。表名同样带插件名，不迁移会让所有家园数据
在新版本眼里为空。

`RenameMigration.migrateDatabaseTables()` 在 `HikariCPUtils` 建立连接之后、
`MySQL.init()` **之前**执行（顺序很关键：`CREATE TABLE IF NOT EXISTS` 一旦跑过，新表就
存在了，迁移条件不再满足）：

- 仅当旧表存在**且**新表不存在时执行 `RENAME TABLE`；
- 失败会打印警告并给出手动 SQL 的指引，不会静默继续。

手动等效操作：

```sql
RENAME TABLE SummerTown_Users   TO ErrorTown_Users;
RENAME TABLE SummerTown_Servers TO ErrorTown_Servers;
```

---

## 二、向后兼容（无需动作，但建议逐步收敛）

### 3. 权限节点 `SummerTown.*` → `ErrorTown.*`

权限保存在 LuckPerms 等**外部插件**里，本插件无权也无法安全改写。若直接更名，
所有玩家会瞬间失去全部权限。

因此所有权限判定都走 `com.Util.Perm.has()`：先查新节点，再回退旧节点。
配置项控制这个回退：

```yaml
# 权限组全部改完之后设为 false
LegacyPermissions: true
```

建议流程：
1. 保持 `LegacyPermissions: true` 上线，确认一切正常；
2. 在 LuckPerms 里把权限组的 `SummerTown.` 前缀批量改成 `ErrorTown.`；
3. 改为 `LegacyPermissions: false` 并重启，确认权限仍然生效；
4. 从权限组里删除残留的 `SummerTown.*` 节点。

节点名称本身未变，只有前缀变了 —— `SummerTown.Create.1` 对应 `ErrorTown.Create.1`。

### 4. PlaceholderAPI 标识符 `%SummerTown_...%` → `%ErrorTown_...%`

占位符字符串存在**其他插件**的配置里（TAB、计分板、聊天插件），本插件无法改写它们。

`com.PlaceHolder.API.registerWithLegacyAlias()` 同时注册两个扩展，所以
`%SummerTown_World_Name%` 与 `%ErrorTown_World_Name%` 都能解析。你可以按自己的节奏
去改其他插件的配置，改完之后旧前缀继续可用也无害（只是多注册一个扩展）。

本插件自带的 `GUI.yml` 与语言文件里的占位符已全部改为 `%ErrorTown_...%`。

### 5. BungeeCord 插件消息子频道

子频道名从 `SummerTown` 改为 `ErrorTown`。**发送**只用新名，**接收**同时接受两者，
所以滚动升级（一部分子服已升级、一部分未升级）期间不会中断 —— 但新版本发出的消息
旧版本收不到，因此**仍应尽快把整个群组升级到同一版本**。

---

## 三、必须手动处理

### 6. 世界文件夹与 `WorldPrefix`

家园世界名由 `config.yml` 的 `WorldPrefix` 决定，**不含插件名**，因此更名不影响它。
但如果你的部署恰好把世界目录命名为 `SummerTownWorld`，那是服务端层面的目录，本插件
不会去动它。要改名请在服务器停止时手动改，并同步更新 `bukkit.yml`/`server.properties`
里对它的引用。

### 7. jar 文件名

产物从 `SummerTown-<version>.jar` 变为 `ErrorTown-<version>.jar`。
**部署前请删除旧 jar** —— 两个 jar 同时存在会让服务端加载两个插件实例，
它们会争抢同一批世界与数据目录。

---

## 四、升级检查清单

```
[ ] 停服，备份 plugins/SummerTown/、家园世界、MySQL、旧 jar
[ ] 删除 plugins/SummerTown-*.jar
[ ] 放入 plugins/ErrorTown-<version>.jar
[ ] 启动，检查控制台：
      - "Migrated plugins/SummerTown/ to plugins/ErrorTown/."
      - 跨服模式下 "Renamed database table(s) for the ErrorTown rename: [...]"
      - ConfigValidator 与 Lang.audit 的输出（详见 README「启动自检」）
[ ] 进服确认：家园可进入、等级/边界正确、成员名单在、GUI 文本正常
[ ] 确认权限正常（此时靠 LegacyPermissions 回退生效）
[ ] 在 LuckPerms 把 SummerTown.* 改为 ErrorTown.*
[ ] 设 LegacyPermissions: false，重启，再次确认权限
[ ] 逐步把其他插件配置里的 %SummerTown_...% 改为 %ErrorTown_...%
```

如果任何一步异常：停服，恢复备份，放回旧 jar。数据目录迁移在失败时会保留旧目录，
MySQL 迁移是单条 `RENAME TABLE`，两者都可以反向手动执行。

---

## 五、未改动的内容

以下**故意保持原样**，改动它们会破坏数据或历史记录：

- `config.yml` 的所有配置键名（`WorldPrefix`、`MaxLevel`、`HomeUpgrade.LevelSizes` …）
- 语言文件的键名（`NoPermissionInteract` 等），只有其中的展示文案与权限节点示例被更新
- 数据库表的**列名**
- 家园 yml 的字段名
