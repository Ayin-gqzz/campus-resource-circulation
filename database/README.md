# 第一周数据库设计

[001_initial.sql](001_initial.sql) 用于创建版本 1 的空数据库。应用通过 xerial `sqlite-jdbc` 3.46.1.0 接入，脚本也可用 SQLite 3.37 及以上版本命令行执行。Swing Demo 使用同一个 `DatabaseManager` 和 JDBC 仓储。首次启动时创建独立演示库并写入示例数据，已有演示库继续使用原有记录。

[002_meetup_locations.sql](002_meetup_locations.sql) 将版本 1 升级到版本 2。它新增 `item_meetup_locations`，以物品编号关联校区与具体地点。每件物品最多一条地点记录，校区仅允许南湖和桂子山。旧物品可由发布人补填，历史记录保留原数据。

[003_exchange_negotiations.sql](003_exchange_negotiations.sql) 将版本 2 升级到版本 3。它为每条既有流转保存原价格及地点，并新增协商提案记录。每笔流转最多存在一个待处理提案。双方确认在同一写事务内检查参与者、提案状态和条件版本，再更新本次交易条件及确认记录。留言复用 `exchange_messages` 的发送人、正文和时间字段。

[004_notifications.sql](004_notifications.sql) 将版本 3 升级到版本 4，新增站内提醒。预定取消、物品恢复在架和提醒写入在同一事务完成；按取消流转编号与接收人去重，保留创建和已读时间。旧数据升级时不补发历史取消提醒。

[005_meetup_time.sql](005_meetup_time.sql) 将版本 4 升级到版本 5，为发布信息、当前流转条件和协商提案添加 `meetup_at`。使用统一 UTC 时间格式保存约定交易时间，界面转换为北京时间。旧记录保留空值，须双方协商补充；迁移不修改历史价格、地点或提案状态。

[006_inventory.sql](006_inventory.sql) 将版本 5 升级到版本 6，为物品与意向添加数量，旧记录均按 1 件迁移，并移除每件物品只能存在一条成功流转的索引。预定数与成交数由相应状态的意向数量求和，余量为发布数减去两者；不额外保存重复计数。接受与完成的 UPDATE 同时检查容量，服务层在写事务中重算物品状态；库存不足返回 false，整笔动作不生效。

## 数据表

| 表 | 主要关系及约束 |
| --- | --- |
| `users` | 唯一用户名按 ASCII 大小写归一比较。保存密码哈希、角色和启用状态。 |
| `categories` | 唯一分类名，停用时保留引用。可录入每件减排克数及来源，二者须同时提供。 |
| `items` | 属于用户与分类。记录成色、流转方式和状态，售卖存整数分，交换存期望说明。 |
| `item_tags`、`favorites` | 通过组合主键约束同一物品的重复标签和同一用户的重复收藏。 |
| `exchanges` | 关联物品和请求人，保存本次申请数量；同一用户对同一物品最多有一个待处理意向。允许多笔预定及成交，但累计分配不得超过发布数。 |
| `exchange_messages` | 关联流转与发送者，按记录 ID 读取对话。 |
| `exchange_terms` | 每笔流转的当前价格、校区、具体地点与条件版本，独立于物品发布信息。 |
| `exchange_proposals` | 保存提案人、拟定条件、状态、提交及处理时间、处理人；每笔流转最多一个待回复提案。 |
| `reviews` | 每条流转中每个评价人最多一条记录，星级限制在 1–5。 |
| `notifications` | 关联接收人、物品和触发取消的流转，唯一约束防止同次取消重复通知同一人；保留正文、创建及已读时间。 |

表结构通过外键、唯一索引、状态枚举和字段检查约束单表数据。服务层验证跨表规则，包括禁止对本人发布的物品发起意向、留言人和评价人必须是参与者、评价只能在完成后提交。物品锁定后不允许编辑分类或交易条件，完成后的物品保留分类 ID。统计按历史分类 ID 分组，显示当前分类名和当前有来源的系数，导出固定本次报告使用的系数。系数缺失的流转只计入数量。

## 初始化及事务

在仓库根目录用 SQLite 命令行创建临时库：

```shell
sqlite3 week-01.db ".read database/001_initial.sql"
sqlite3 week-01.db ".read database/002_meetup_locations.sql"
sqlite3 week-01.db ".read database/003_exchange_negotiations.sql"
sqlite3 week-01.db ".read database/004_notifications.sql"
sqlite3 week-01.db ".read database/005_meetup_time.sql"
sqlite3 week-01.db ".read database/006_inventory.sql"
sqlite3 week-01.db "PRAGMA user_version; PRAGMA foreign_key_check;"
```

六份脚本按顺序执行后，`user_version` 返回 `6`，外键检查没有结果。已有数据库由应用按版本执行后续迁移。

### 应用初始化（DatabaseManager）

`DatabaseManager.initialize()` 在启动时调用一次。它先取得 SQLite 写事务，再读取 `PRAGMA user_version`，因此同时启动的实例按顺序检查版本：

| `user_version` | 行为 |
| --- | --- |
| `0` | 按顺序执行六个脚本，完成后版本为 6 |
| `1` | 执行地点、协商、提醒、时间与库存迁移，保留旧数据 |
| `2` | 执行协商、提醒、时间与库存迁移，保留旧数据 |
| `3` | 执行提醒、时间与库存迁移，保留旧数据 |
| `4` | 执行时间与库存迁移，保留旧数据 |
| `5` | 执行库存迁移，保留旧数据 |
| `6` | 直接返回，重复调用不产生副作用 |
| 其他 | 抛出 `DataAccessException`，消息为 `Unsupported database version: <版本>`，不修改数据库 |

应用优先从 classpath 的 `/database/` 读取脚本，并在同一事务中执行建表与版本更新。命令行单独执行脚本时使用脚本里的事务语句。执行失败时回滚并保留升级前的数据库版本。

### 连接与事务

每个连接在 `DriverManager.getConnection` 之后立即执行 `PRAGMA foreign_keys = ON` 和 `PRAGMA busy_timeout = 5000`，外键检查不依赖调用方，写锁冲突最多等待 5 秒。

写操作用 `DatabaseManager.inWriteTransaction` 以 `BEGIN IMMEDIATE` 开启，在读写之前先取得保留锁，避免读到状态后升级锁失败。事务连接绑定到当前线程：嵌套的 `inWriteTransaction` 复用同一连接且不提前提交；`executeWithConnection` 在事务内复用连接，在事务外新开连接并在回调结束后关闭，供仓储方法自行组合。任一步抛出异常都整体 `ROLLBACK`，`SQLException` 经 `SqlExceptions` 转换为 `DataAccessException`、`UniqueConstraintException` 或 `ForeignKeyException`。仓储方法自身不提交，跨表写操作由服务层在同一个写事务内组合。

### 时间格式

时间字段统一由 `UtcTimestamps` 写为固定毫秒精度的 UTC 文本，格式为 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`，例如 `2026-11-08T08:30:00.000Z`，保证时间范围与文本排序一致。

接受、取消、完成、下架及提交意向都应在单个写事务中完成，详见[状态与事务](../docs/architecture.md#状态与事务)。
