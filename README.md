# 低碳校园资源循环流转桌面系统

2026 年《软件构造》分组实验项目：面向高校校园的闲置物品发布、检索、交换与售卖管理桌面应用。

**当前版本提供基于统一 JDBC 数据层的 Swing Demo。** 两个示例身份可以在本机体验发布、搜索、意向、流转和互评。正式业务功能按迭代计划继续开发。

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

如果已经下载 Jar，直接运行 `java -jar campus-resource-circulation-0.1.0-SNAPSHOT.jar` 即可。首次启动在工作目录下创建 `data/demo.db` 并写入两个示例身份和三件物品，之后继续使用已有记录。也可用 `java -Dcampus.demo.db=demo-run.db -jar target/campus-resource-circulation-0.1.0-SNAPSHOT.jar` 指定独立数据库。

## 演示流程

1. 以默认的**陈同学**打开「闲置集市」，选中「高等数学教材」，点击「发起意向」并提交留言。
2. 切换到**林同学**，在「我的流转」中选中记录并点击「接受」。模拟线下交接后点击「确认交接完成」。
3. 林同学提交评价，再切换为陈同学提交另一方的评价。切换身份可以查看各自收到的信用评分。
4. 返回集市可发布售卖或换物物品，并按分类和关键词筛选。重启后，物品、流转记录和评分保留。

身份下拉框用于现场切换角色。正式注册与登录、收藏、管理后台和统计报表留在后续迭代。GitHub Actions 使用临时 SQLite 数据库验证流转、互评和重启后的数据持久化。

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
- [第二周设计评审与验收](docs/week-02.md)
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

上述设计和分工是起步建议，需由小组结合成员情况继续细化。请用真实的开发、测试和提交记录填写实验材料
