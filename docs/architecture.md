# 架构与设计模式规划

本文记录第一周的目标架构和接口约定，相关流程见 [UML 类图与时序图](uml.md)。Swing Demo 用 `DemoService` 和 `DemoDatabase` 验证其中的物品、流转和评价流程，正式模块仍按下述边界规划。

## 模块边界

| 包 | 职责 |
| --- | --- |
| `domain` | 用户、角色、物品、分类、标签、收藏、意向、流转、评价等实体与状态 |
| `repository` | 数据访问接口、SQLite JDBC 实现、映射与连接管理 |
| `service` | 账号、物品、流转、评价、统计、权限校验和事务协调 |
| `ui` | Swing 页面、表格模型、事件绑定和用户提示 |

依赖方向建议为 `ui → service → repository`，业务实体放在 `domain`。通过构造器注入依赖，业务逻辑避免依赖 Swing。耗时查询和导出应使用后台任务，界面更新回到 Swing 事件派发线程。

## 模块接口

以下参数使用设计名称。除注册和登录外，每次调用都从当前会话取得操作人，服务层重新核验账号状态、角色及资源归属。金额使用整数分，时间使用 UTC。查询无结果返回空列表，输入错误、权限不足、状态冲突和存储失败分别返回可区分的错误，供界面显示。

| 服务 | 主要操作与返回值 | 接口约束 |
| --- | --- | --- |
| AuthService | `register(username, password) → userId`，`login(username, password) → session`，`logout(session)` | 注册固定为学生角色，登录失败使用统一提示。会话仅驻留内存。 |
| ItemService | `publish(draft) → itemId`，`edit(itemId, draft)`，`withdraw(itemId)`，`search(filter) → items`，`setFavorite(itemId, saved)` | 校验发布人、在架状态、分类和价格。下架和关闭待处理意向在同一事务中执行。 |
| ExchangeService | `request(itemId, message) → exchangeId`，`sendMessage(exchangeId, text)`，`transition(exchangeId, action)`，`history() → exchanges` | 提交意向时原子写入首条留言。每次状态变更验证身份和当前状态。历史按参与者过滤。 |
| ReviewService | `submit(exchangeId, stars, comment)`，`credit(userId) → scoreSummary` | 从流转记录推导被评价人。仅已完成记录的参与者可评价，每方一次。 |
| StatsService | `summarize(filter) → report`，`exportCsv(report, path)` | 按角色限制统计范围。导出复用已生成的报告快照，保留筛选范围、系数来源和覆盖情况。 |
| AdminService | `setStudentEnabled(userId, enabled)`，`saveCategory(draft) → categoryId`，`withdrawItem(itemId)` | 仅管理员调用。停用分类保留历史分类，停用学生及下架操作连带更新相关意向。 |

仓储接口按聚合划分为用户、分类、物品、流转和评价。每个仓储方法通过 JDBC 参数化 SQL 访问存储。查询关键词先转义通配符，再作为绑定参数使用。跨仓储的写操作由服务层在同一个连接和事务内执行，仓储方法不单独提交。

## 状态与事务

| 操作 | 意向变化 | 物品变化 |
| --- | --- | --- |
| 提交意向 | 创建 `PENDING`，同时保存首条留言 | 保持 `AVAILABLE` |
| 接受 | 指定意向进入 `ACCEPTED`，同一物品的其他 `PENDING` 进入 `REJECTED` | `AVAILABLE → RESERVED` |
| 拒绝 / 撤回待处理意向 | `PENDING → REJECTED` / `CANCELLED` | 保持 `AVAILABLE` |
| 参与人取消已接受意向 | `ACCEPTED → CANCELLED` | `RESERVED → AVAILABLE` |
| 发布人确认交接完成 | `ACCEPTED → COMPLETED`，写入完成时间 | `RESERVED → COMPLETED` |
| 下架 | 关闭全部 `PENDING`。管理员也可取消 `ACCEPTED` | 在架物品进入 `WITHDRAWN`，管理员也可下架已锁定物品 |

已完成与已下架的物品保留原记录。已拒绝、取消、完成的意向保留历史和留言。停用学生前检查其是否有已接受意向，若有则拒绝停用并提示先处理。否则在同一事务内停用账号并取消其作为发布人或请求人的待处理意向。停用账号的物品在检索时隐藏，恢复账号后重新按物品状态显示。

对接受、取消、完成等动作先取得 SQLite 写事务，再读取状态并校验权限。更新语句以预期旧状态为条件，影响行数不符时回滚并提示刷新。任何一步失败都回滚，避免出现意向与物品状态不一致。

## 三种设计模式候选

| 模式 | 应用位置 | 预期解决的问题 |
| --- | --- | --- |
| 策略 | 不同物品类别的减排估算策略 | 分离类别算法，避免集中堆叠条件判断 |
| 观察者 | 流转状态变化通知列表和统计视图 | 降低业务状态变化与多个界面刷新的耦合 |
| 工厂方法 | 后台统计报表的创建与导出流程 | 在共享导出流程中创建不同报表，支持扩展报表类型 |

候选方案应在实际业务需要时落地。最终报告需要给出对应类、调用关系、替代方案和测试依据；不能仅用接口或类名宣称完成设计模式。

## 第二周评审

- [ ] 评审实体关系、接口命名和状态转换。
- [ ] 确认密码哈希方案、首个管理员初始化流程和日志字段。
- [ ] 确认减排系数的来源、单位和版本记录方式。
- [ ] 确定三个设计模式的使用场景，并在业务实现后补齐相应类图。
