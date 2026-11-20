# UML 类图与时序图

以下类图与时序图与当前已落地的 `domain`、`repository` 代码及规划中的 `service`、`ui` 层保持一致。业务规则与事务约定见[需求规格](requirements.md)和[架构设计](architecture.md)。

## 领域类与模块关系

```mermaid
classDiagram
    class User {
        +long id
        +String username
        +String passwordHash
        +Role role
        +boolean enabled
        +Instant createdAt
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
        +long ownerId
        +long categoryId
        +String title
        +String description
        +ItemCondition condition
        +TradeType tradeType
        +Long priceCents
        +String swapNote
        +ItemStatus status
        +List~String~ tags
        +Instant createdAt
        +Instant meetupAt
        +Inventory inventory
    }
    class Exchange {
        +long id
        +long itemId
        +long requesterId
        +ExchangeStatus status
        +Instant createdAt
        +Instant completedAt
        +int quantity
    }
    class ExchangeMessage {
        +long id
        +long exchangeId
        +long senderId
        +String body
        +Instant sentAt
    }
    class Review {
        +long exchangeId
        +long reviewerId
        +long revieweeId
        +int stars
        +String comment
        +Instant createdAt
    }
    class CreditSummary {
        +long userId
        +int reviewCount
        +Double averageStars
    }
    class CategoryStatRow {
        +long categoryId
        +String categoryName
        +long completedCount
        +Long avoidedGramsPerItem
        +String factorSource
    }

    User "1" --> "0..*" Item : publishes
    Category "1" --> "0..*" Item : classifies
    Item "1" --> "0..*" Exchange
    Item "1" *-- "0..1" MeetupLocation
    Item "1" *-- "1" Inventory
    class Inventory {
        +int quantity
        +int reserved
        +int completed
        +available() int
    }
    class MeetupLocation {
        +Campus campus
        +String place
    }
    User "1" --> "0..*" Exchange : requests
    Exchange "1" *-- "1..*" ExchangeMessage
    User "1" --> "0..*" ExchangeMessage : sends
    Exchange "1" *-- "0..2" Review
    User "1" --> "0..*" Review : writes
    Review "0..*" --> "1" User : rates

    class DatabaseManager {
        +initialize()
        +executeWithConnection(work)
        +inWriteTransaction(work)
    }
    class UserRepository {
        <<interface>>
    }
    class CategoryRepository {
        <<interface>>
    }
    class ItemRepository {
        <<interface>>
    }
    class ExchangeRepository {
        <<interface>>
    }
    class ReviewRepository {
        <<interface>>
    }
    class JdbcUserRepository
    class JdbcCategoryRepository
    class JdbcItemRepository
    class JdbcExchangeRepository
    class JdbcReviewRepository

    JdbcUserRepository ..|> UserRepository
    JdbcCategoryRepository ..|> CategoryRepository
    JdbcItemRepository ..|> ItemRepository
    JdbcExchangeRepository ..|> ExchangeRepository
    JdbcReviewRepository ..|> ReviewRepository
    JdbcUserRepository --> DatabaseManager
    JdbcCategoryRepository --> DatabaseManager
    JdbcItemRepository --> DatabaseManager
    JdbcExchangeRepository --> DatabaseManager
    JdbcReviewRepository --> DatabaseManager
```

## 注册和登录

```mermaid
sequenceDiagram
    actor Student as 学生
    participant UI as Swing
    participant Auth as AuthService
    participant UserRepo as UserRepository
    Student->>UI: 提交注册信息
    UI->>Auth: register(username, password)
    Auth->>Auth: 校验输入并计算加盐哈希
    Auth->>UserRepo: insert(username, passwordHash, STUDENT, true, now)
    alt 用户名可用
        UserRepo-->>Auth: 返回 userId
        Auth-->>UI: 注册成功
    else 用户名冲突（UniqueConstraintException）
        UserRepo-->>Auth: 抛出 UniqueConstraintException
        Auth-->>UI: 提示用户名已使用
    end
    Student->>UI: 提交登录凭据
    UI->>Auth: login(username, password)
    Auth->>UserRepo: findByUsername(username)
    alt 账号启用且密码哈希匹配
        Auth-->>UI: 返回会话与角色
    else 凭据错误或账号停用
        Auth-->>UI: 统一提示登录失败
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
    participant DB as DatabaseManager
    participant ExRepo as ExchangeRepository
    participant ItemRepo as ItemRepository
    participant RevRepo as ReviewRepository

    Buyer->>UI: 发起意向并附留言
    UI->>Trade: request(itemId, message, quantity)
    Trade->>ExRepo: insertWithFirstMessage(itemId, requesterId, message, now, quantity)
    ExRepo->>DB: inWriteTransaction（写入 PENDING 意向 + 首条留言）
    ExRepo-->>Trade: 返回 exchangeId
    Trade-->>UI: 显示待处理

    Owner->>UI: 接受意向
    UI->>Trade: transition(exchangeId, ACCEPT)
    Trade->>DB: inWriteTransaction
    Trade->>Trade: 校验发布人、待处理状态、交接条件和余量
    alt 校验与更新成功
        Trade->>ExRepo: transitionStatus（接受本笔，写入时再次检查容量）
        Trade->>ItemRepo: findById（汇总库存）并重算物品状态
        opt 剩余库存归零
            Trade->>ExRepo: rejectOtherPendingByItem(itemId, exchangeId)
        end
        DB-->>Trade: 提交事务
        Trade-->>UI: 显示已接受
        alt 任一参与人取消
            UI->>Trade: transition(exchangeId, CANCEL)
            Trade->>DB: inWriteTransaction
            Trade->>ExRepo: transitionStatus(exchangeId, ACCEPTED, CANCELLED, null)
            Trade->>ItemRepo: 汇总释放后的库存并重算状态
            opt 余量从零恢复为有货
                Trade->>DB: 写入符合条件的站内提醒
            end
            DB-->>Trade: 提交事务
            Trade-->>UI: 显示已取消，物品恢复在架
        else 双方完成交接
            Note over Buyer,Owner: 双方线下完成交接
            Owner->>UI: 确认流转完成
            UI->>Trade: transition(exchangeId, COMPLETE)
            Trade->>DB: inWriteTransaction
            Trade->>ExRepo: transitionStatus(exchangeId, ACCEPTED, COMPLETED, now)
            Trade->>ItemRepo: 汇总本次成交后库存并重算状态
            DB-->>Trade: 提交事务
            Trade-->>UI: 显示已完成
            Buyer->>UI: 评价发布人
            UI->>Rate: submit(exchangeId, stars, comment)
            Rate->>ExRepo: findById(exchangeId) 校验完成状态与参与身份
            Rate->>RevRepo: insert(review)
            Rate-->>UI: 显示评价结果
        end
    else 校验失败、库存不足或写入冲突
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
    participant ExRepo as ExchangeRepository
    participant CSV as CSV 文件
    User->>UI: 选择统计时间区间和分类
    UI->>Stats: summarize(filter)
    Stats->>ExRepo: summarizeCompletedByCategory(statsFilter)
    ExRepo-->>Stats: List<CategoryStatRow>（分类计数、单件减排克数及来源）
    Stats-->>UI: 报告快照与减排覆盖情况
    User->>UI: 选择导出路径
    UI->>Stats: exportCsv(report, path)
    Stats->>CSV: 写入转义后的 UTF-8 内容
    Stats-->>UI: 返回成功或文件写入错误
```
