# 低碳校园资源循环流转桌面系统

2026 年《软件构造》分组实验项目：面向高校校园的闲置物品发布、检索、交换与售卖管理桌面应用。

**当前状态：项目初始化。** 已提供 Maven 工程、Swing 启动窗口和协作文档；账号、物品、流转、评价、统计等业务功能待开发。

## 技术与目标

- Java 17 及以上，Swing 图形界面。
- Maven 管理依赖，SQLite + JDBC 持久化数据。
- JUnit 5 编写单元测试，补充集成测试与代码走查。
- 3 人协作，8 周完成，每 2 周交付一个迭代版本。
- 最终交付包含依赖的可执行 Jar、数据库脚本、实验报告与演示材料。

## 快速开始

安装 JDK 17+ 和 Maven 3.9+，确保 `java`、`mvn` 可在终端使用。

```shell
mvn clean verify
java -jar target/campus-resource-circulation-0.1.0-SNAPSHOT.jar
```

当前启动后显示项目说明窗口，不会创建数据库。SQLite 驱动和 JUnit 依赖已配置，后续实现对应模块时使用。GitHub Actions 会编译并打包；当前还没有业务测试用例，构建成功不代表实验功能已完成。

## 项目结构

```text
src/main/java/edu/campus/circulation/
  App.java                 Swing 入口
  domain/                  业务实体与状态
  repository/              数据访问接口与 JDBC 实现
  service/                 业务流程、权限及事务边界
  ui/                      Swing 视图与交互
src/main/resources/        配置与运行时资源
src/test/java/             JUnit 测试
database/                  数据库初始化与迁移脚本
docs/                      需求、架构、计划、测试及重构记录
reports/                   实验报告、演示与交付材料
.github/workflows/         自动构建配置
```

## 协作入口

- [实验要求与交付清单](docs/requirements.md)
- [8 周迭代计划](docs/iterations.md)
- [三人分工与周会记录](docs/team.md)
- [架构与设计模式规划](docs/architecture.md)
- [测试计划](docs/testing.md)
- [重构记录](docs/refactoring.md)
- [提交和协作规范](CONTRIBUTING.md)

上述设计和分工是起步建议，需由小组结合成员情况继续细化。请用真实的开发、测试和提交记录填写实验材料。
