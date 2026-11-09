# 低碳校园资源循环流转桌面系统

2026 年《软件构造》分组实验项目：面向高校校园的闲置物品发布、检索、交换与售卖管理桌面应用。

**当前状态：已完成需求设计、SQLite 建表脚本、领域模型（`domain`）与 JDBC 数据访问层（`repository`），并通过 49 个 JUnit 5 数据层集成测试。** 业务服务层（`service`）与 Swing 业务界面（`ui`）进入下一阶段开发。

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

当前启动后显示项目说明窗口。SQLite 数据访问层与 49 个 JUnit 5 仓储集成测试已就绪，可通过 `mvn clean verify` 运行全部测试并生成可执行 Jar。

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

- [第一周交付记录](docs/week-01.md)
- [实验要求与交付清单](docs/requirements.md)
- [项目需求分析文档](docs/requirements-analysis.md)
- [8 周迭代计划](docs/iterations.md)
- [三人分工与周会记录](docs/team.md)
- [架构与设计模式规划](docs/architecture.md)
- [UML 类图与时序图](docs/uml.md)
- [SQLite 数据库设计](database/README.md)
- [测试计划](docs/testing.md)
- [重构记录](docs/refactoring.md)
- [提交和协作规范](CONTRIBUTING.md)

上述设计和分工是起步建议，需由小组结合成员情况继续细化。请用真实的开发、测试和提交记录填写实验材料。
