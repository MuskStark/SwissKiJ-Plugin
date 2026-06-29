# OfflinePython 依赖配置面板 v2 — 执行进度（续接用）

- 日期：2026-06-29
- 分支：`feature/offline-python-builder-v1`
- 设计文档：`docs/superpowers/specs/2026-06-29-offlinepython-deps-config-ui-v2-design.md`
- 实现计划（含 5 个任务的完整代码与步骤）：`docs/superpowers/plans/2026-06-29-offlinepython-deps-config-ui-v2.md`
- 执行方式：subagent-driven（每任务：实现 → spec 合规 review → 代码质量 review → 提交）

## 进度总览

| # | 任务 | 状态 | 提交 |
|---|---|---|---|
| 1 | `BuildConfig.depPlatforms` 数据模型 | ✅ 完成（spec + 质量 review 通过） | `90d0210` |
| 2 | `WheelInfo` + `DepsService` wheel 解析 | ⬜ 待实现 | — |
| 3 | 构建管线按平台集合分组下载 | ⬜ 待实现 | — |
| 4 | `PyPISearchDialog` 在线搜索对话框 | ⬜ 待实现 | — |
| 5 | `DepsPanel` 三行布局 + per-dep 平台 + 主从编辑 | ⬜ 待实现 | — |

## 已完成详情

**Task 1（commit `90d0210`）** — `BuildConfig.Python` 新增字段：
```java
private java.util.Map<String, java.util.List<String>> depPlatforms = new java.util.LinkedHashMap<>();
```
`BuildConfigTest` +2 用例（`roundTripsDepPlatformsMap` Gson 往返、`legacyConfigWithoutDepPlatformsLoadsEmptyMap` legacy 空 map 兜底）；`mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=BuildConfigTest` 6/6 绿。
Review：spec 合规 ✅；代码质量 Approved（仅 2 条**非阻塞 Minor**：① `platforms` 字段上有一行前瞻性 Javadoc，待后续任务接线后即准确；② 可选地为 `roundTripsDepPlatformsMap` 加一行插入顺序断言以固化 LinkedHashMap 选择——均不影响推进）。

## 如何在另一台机器续接

1. `git pull` 到 `feature/offline-python-builder-v1`。
2. **续接前自检**：`git log --oneline -3` 应含 `90d0210`（Task 1）；`mvn -pl SwissKitJ-Plugin-OfflinePython -am test -Dtest=BuildConfigTest` 应 6/6 绿。
3. 按计划文档 **Task 2 → Task 5** 顺序执行；每个任务的完整代码、TDD 步骤、提交命令都在计划文档里，直接照做即可。
4. 任务依赖：Task 3 依赖 Task 1；Task 5 依赖 1/2/4；Task 2、4 相对独立。
5. 测试：`mvn -pl SwissKitJ-Plugin-OfflinePython -am test`（单类加 `-Dtest=<TestClass>`）。
6. Task 4/5 是 JavaFX，无单测——靠运行 `plugin.swisskit.offlinepython.DevLauncher`（main 类）手动验证（计划 Task 5 Step 4 有逐项检查清单）。
7. 建议沿用 subagent-driven：每任务「实现 → spec 合规 review → 代码质量 review → 提交」。

## 环境备注

- 为跑测试，本机用 Homebrew 装了 Maven（`brew install maven` → Apache Maven 3.9.16）。**项目无 `mvnw` wrapper**，另一台机器若无 `mvn` 也需安装，并确保 `JAVA_HOME` 指向 JDK 21（项目 `target 21`）。
- IDEA MCP（`mcp__idea__*`）可用于编译/重构，但权威验证以 Maven surefire 为准。

## 续接后的下一步

从 **Task 2（`WheelInfo` + `DepsService.searchWheels/parseWheels/extractPlatformTag`）** 开始。完成后继续 Task 3 → 4 → 5；全部完成后做一次整库 final code review，再用 `superpowers:finishing-a-development-branch` 收尾。
