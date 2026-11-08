# 第一周 UML 设计

以下图描述第一周设计方案。业务规则和异常分支以[需求规格](requirements.md)及[接口与事务约定](architecture.md)为准。

## 领域类与模块关系

```mermaid
classDiagram
    class User {
        +long id
        +String username
        +Role role
        +boolean enabled
    }
    class Category {
        +long id
        +String name
        +boolean enabled
        +Long avoidedGrams
        +String factorSource
    }
    class Item {
        +long id
        +String title
        +TradeType tradeType
        +Long priceCents
        +ItemStatus status
    }
    class Favorite
    class ItemTag
    class Exchange {
        +long id
        +ExchangeStatus status
        +Instant completedAt
    }
    class ExchangeMessage
    class Review {
        +int stars
        +String comment
    }
    User "1" --> "0..*" Item : publishes
    Category "1" --> "0..*" Item : classifies
    Item "1" *-- "0..*" ItemTag
    User "1" -- "0..*" Favorite
    Item "1" -- "0..*" Favorite
    Item "1" -- "0..*" Exchange
    User "1" -- "0..*" Exchange : requests
    Exchange "1" *-- "1..*" ExchangeMessage
    User "1" -- "0..*" ExchangeMessage : sends
    Exchange "1" *-- "0..2" Review
    User "1" -- "0..*" Review : writes
    Review "0..*" --> "1" User : rates

    class SwingViews
    class AuthService
    class ItemService
    class ExchangeService
    class ReviewService
    class StatsService
    class AdminService
    class Repositories {
        <<interface>>
    }
    class JdbcRepositories
    class EmissionStrategy {
        <<interface>>
    }
    SwingViews ..> AuthService
    SwingViews ..> ItemService
    SwingViews ..> ExchangeService
    SwingViews ..> ReviewService
    SwingViews ..> StatsService
    SwingViews ..> AdminService
    AuthService ..> Repositories
    ItemService ..> Repositories
    ExchangeService ..> Repositories
    ReviewService ..> Repositories
    StatsService ..> Repositories
    AdminService ..> Repositories
    StatsService ..> EmissionStrategy
    JdbcRepositories ..|> Repositories
```

图中的 `Repositories` 代表按聚合拆分的仓储接口，`JdbcRepositories` 代表对应实现。

## 注册和登录

```mermaid
sequenceDiagram
    actor Student as 学生
    participant UI as Swing
    participant Auth as AuthService
    participant DB as 用户仓储
    Student->>UI: 提交注册信息
    UI->>Auth: register(username, password)
    Auth->>Auth: 校验输入并计算加盐哈希
    Auth->>DB: 插入学生账号
    alt 用户名可用
        DB-->>Auth: 返回用户 ID
        Auth-->>UI: 注册成功
    else 用户名冲突
        DB-->>Auth: 唯一约束冲突
        Auth-->>UI: 提示用户名已使用
    end
    Student->>UI: 提交登录凭据
    UI->>Auth: login(username, password)
    Auth->>DB: 查询账号
    alt 账号启用且密码正确
        Auth-->>UI: 返回会话与角色
    else 凭据错误或账号停用
        Auth-->>UI: 登录失败
    end
```

## 意向到互评

```mermaid
sequenceDiagram
    actor Buyer as 请求人
    actor Owner as 发布人
    participant UI as Swing
    participant Trade as ExchangeService
    participant Rate as ReviewService
    participant DB as JDBC 仓储
    Buyer->>UI: 发起意向并附留言
    UI->>Trade: request(itemId, message)
    Trade->>DB: 同一事务写入待处理意向和首条留言
    DB-->>Trade: 提交并返回意向 ID
    Trade-->>UI: 显示待处理
    Owner->>UI: 接受意向
    UI->>Trade: transition(exchangeId, ACCEPT)
    Trade->>DB: 条件更新意向、锁定物品、拒绝其他待处理意向
    alt 全部写入成功
        DB-->>Trade: 提交事务
        Trade-->>UI: 显示已接受
        Note over Buyer,Owner: 双方线下完成交接
        Owner->>UI: 确认流转完成
        UI->>Trade: transition(exchangeId, COMPLETE)
        Trade->>DB: 同一事务更新流转、物品和完成时间
        DB-->>Trade: 提交事务
        Trade-->>UI: 显示已完成
        Buyer->>UI: 评价发布人
        UI->>Rate: submit(exchangeId, stars, comment)
        Rate->>DB: 校验参与资格并写入评价
        Rate-->>UI: 显示评价结果
        Owner->>UI: 评价请求人
        UI->>Rate: submit(exchangeId, stars, comment)
        Rate->>DB: 校验参与资格并写入评价
        Rate-->>UI: 显示评价结果
    else 权限或状态冲突
        DB-->>Trade: 回滚事务
        Trade-->>UI: 提示操作失败并刷新记录
    end
```

## 统计和导出

```mermaid
sequenceDiagram
    actor User as 学生或管理员
    participant UI as Swing
    participant Stats as StatsService
    participant DB as 流转与分类仓储
    participant CSV as CSV 文件
    User->>UI: 选择统计区间和分类
    UI->>Stats: summarize(filter)
    Stats->>DB: 按会话角色和筛选范围聚合已完成流转
    DB-->>Stats: 分类计数、系数及来源
    Stats-->>UI: 报告快照与估算覆盖情况
    User->>UI: 选择导出路径
    UI->>Stats: exportCsv(report, path)
    Stats->>CSV: 写入转义后的 UTF-8 内容
    Stats-->>UI: 返回成功或文件写入错误
```
