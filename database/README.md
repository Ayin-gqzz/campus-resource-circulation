# 第一周数据库设计

[001_initial.sql](001_initial.sql) 用于创建版本 1 的空数据库。应用通过 xerial `sqlite-jdbc` 3.46.1.0 接入，脚本也可用 SQLite 3.37 及以上版本命令行执行。Swing Demo 使用同一个 `DatabaseManager` 和 JDBC 仓储。首次启动时创建独立演示库并写入示例数据，已有演示库继续使用原有记录。

## 数据表

| 表 | 主要关系及约束 |
| --- | --- |
| `users` | 唯一用户名按 ASCII 大小写归一比较。保存密码哈希、角色和启用状态。 |
| `categories` | 唯一分类名，停用时保留引用。可录入每件减排克数及来源，二者须同时提供。 |
| `items` | 属于用户与分类。记录成色、流转方式和状态，售卖存整数分，交换存期望说明。 |
| `item_tags`、`favorites` | 通过组合主键约束同一物品的重复标签和同一用户的重复收藏。 |
| `exchanges` | 关联物品和请求人，发布人从物品取得。同一用户对同一物品最多有一个待处理意向，每件物品最多有一个已接受或已完成流转。 |
| `exchange_messages` | 关联流转与发送者，按记录 ID 读取对话。 |
| `reviews` | 每条流转中每个评价人最多一条记录，星级限制在 1–5。 |

表结构通过外键、唯一索引、状态枚举和字段检查约束单表数据。服务层验证跨表规则，包括禁止对本人发布的物品发起意向、留言人和评价人必须是参与者、评价只能在完成后提交。物品锁定后不允许编辑分类或交易条件，完成后的物品保留分类 ID。统计按历史分类 ID 分组，显示当前分类名和当前有来源的系数，导出固定本次报告使用的系数。系数缺失的流转只计入数量。

## 初始化及事务

在仓库根目录用 SQLite 命令行创建临时库：

```shell
sqlite3 week-01.db ".read database/001_initial.sql"
sqlite3 week-01.db "PRAGMA user_version; PRAGMA foreign_key_check;"
```

首次执行后 `user_version` 返回 `1`，外键检查没有结果。脚本用于空数据库，仅执行一次。

### 应用初始化（DatabaseManager）

`DatabaseManager.initialize()` 在启动时调用一次。它先取得 SQLite 写事务，再读取 `PRAGMA user_version`，因此同时启动的实例按顺序检查版本：

| `user_version` | 行为 |
| --- | --- |
| `0` | 载入脚本并逐条执行，完成后版本为 1 |
| `1` | 直接返回，重复调用不产生副作用 |
| 其他 | 抛出 `DataAccessException`，消息为 `Unsupported database version: <版本>`，不修改数据库 |

脚本优先从 classpath 的 `/database/001_initial.sql` 读取，资源缺席时回退到工作目录下的 `database/001_initial.sql`。应用负责开启外键检查和管理事务，在其中执行建表语句和版本更新。命令行单独执行脚本时使用脚本里的事务语句。建表失败会整体回滚，数据库版本保持为 0。

### 连接与事务

每个连接在 `DriverManager.getConnection` 之后立即执行 `PRAGMA foreign_keys = ON` 和 `PRAGMA busy_timeout = 5000`，外键检查不依赖调用方，写锁冲突最多等待 5 秒。

写操作用 `DatabaseManager.inWriteTransaction` 以 `BEGIN IMMEDIATE` 开启，在读写之前先取得保留锁，避免读到状态后升级锁失败。事务连接绑定到当前线程：嵌套的 `inWriteTransaction` 复用同一连接且不提前提交；`executeWithConnection` 在事务内复用连接，在事务外新开连接并在回调结束后关闭，供仓储方法自行组合。任一步抛出异常都整体 `ROLLBACK`，`SQLException` 经 `SqlExceptions` 转换为 `DataAccessException`、`UniqueConstraintException` 或 `ForeignKeyException`。仓储方法自身不提交，跨表写操作由服务层在同一个写事务内组合。

### 时间格式

时间字段统一由 `UtcTimestamps` 写为固定毫秒精度的 UTC 文本，格式为 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`，例如 `2026-11-08T08:30:00.000Z`，保证时间范围与文本排序一致。

接受、取消、完成、下架及提交意向都应在单个写事务中完成，详见[状态与事务](../docs/architecture.md#状态与事务)。
