# OfflinePython 依赖配置面板 v2 — 执行进度（续接用）

- 日期：2026-06-29
- 分支：`feature/offline-python-builder-v1`
- 设计文档：`docs/superpowers/specs/2026-06-29-offlinepython-deps-config-ui-v2-design.md`
- 实现计划（含 5 个任务的完整代码与步骤）：`docs/superpowers/plans/2026-06-29-offlinepython-deps-config-ui-v2.md`
- 执行方式：subagent-driven（每任务：实现 → spec 合规 review → 代码质量 review → 提交；全部完成后做一次整库 final review）

## 进度总览 — ✅ 5/5 全部完成

| # | 任务 | 状态 | 提交 |
|---|---|---|---|
| 1 | `BuildConfig.depPlatforms` 数据模型 | ✅ 完成（spec + 质量 review 通过） | `90d0210`（已在 origin） |
| 2 | `WheelInfo` + `DepsService` wheel 解析 | ✅ 完成（spec ✅ + 质量 ✅） | `cff65c4` |
| 3 | 构建管线按平台集合分组下载 | ✅ 完成（spec ✅ + 质量 ✅） | `ceace9c` |
| 4 | `PyPISearchDialog` 在线搜索对话框 | ✅ 完成（spec ✅ + 质量 ✅ + §4.D 按钮置灰修订） | `4ff235a` |
| 5 | `DepsPanel` 三行布局 + per-dep 平台 + 主从编辑 | ✅ 完成（spec ✅ + 质量 ✅ + 死代码清理） | `225c25d` |

> 本地领先 origin 4 个提交（Task 2–5）；Task 1 + docs 已在 origin。
> 自动化门禁全绿：`mvn -pl SwissKitJ-Plugin-OfflinePython -am test` = **75/75**，`test-compile` BUILD SUCCESS。

## Review 结论

**每任务**：spec 合规 ✅、代码质量 Approved（仅 Minor 非阻塞，见下）。
**整库 final review（opus）**：Ready to merge **Yes**，无 Critical/Important。5 个跨层集成点逐一验证通过：
1. `normalizeName` 契约：`DepsPanel.persist` 写 `depPlatforms[normalizeName(name)]` ↔ `BuildService.resolvePlatforms` 读 `containsKey(normalizeName(name))`，同一函数，往返正确且有 `BuildServiceTest.normalizesDepNameForKeyLookup` 覆盖。
2. `depPlatforms` 生命周期：legacy config（无 key）→ Gson field-initializer 兜底空 map → 构建兜底 `python.platforms` 单组；`BuildConfigTest.legacyConfigWithoutDepPlatformsLoadsEmptyMap` 验证。
3. 平台并集：`BuildService` 写 `unionPlatforms(groups)` 进 manifest；面板全程**不**改写 `cfg.python.platforms`（降为"新增依赖默认平台"）。
4. 目录外平台标签（如 `manylinux_2_28_x86_64`）从 `WheelInfo.platformTag` → 行平台 → `--platform` 端到端透传，不报错。
5. 各任务边界类型/API 无错配。

## 延后的 Minor 项（final review 判定非阻塞，建议后续清理）

- **D1 `humanSize` 三处重复**：`PyPISearchDialog.human` 与 `DepsPanel.humanSize` 逐字节相同（`%.1f`）；第三处 `BuildPanel.humanBytes`（`%.0f`，本特性未触碰）格式不同。完整整合需一并改 `BuildPanel`（属范围外 + 改用户可见格式），故延后到一次专门的 cleanup（抽一个 `HumanSize` helper，迁移全部三处）。
- **D2 DRY**：`DepsPanel.loadFromProject` 与 `doImport` 重复了 parse→Row 循环（旧 `toRows` 被内联两次）。可抽 `toRows(text, dp, defaults)`。纯 DRY，行为无误。
- **D3 null-safety**：`DepsPanel.persist` 在 `getConfig()!=null` 守卫内假定 `getPython()!=null`。`BuildConfig` field-initializer + Gson 兜底使其在实践中不会 null（仅手搓 BuildConfig 理论场景）。无需修。
- **Task 3 Minor**：`BuildService.writeManifest` 再次 `readRequirements`（build 已读过一次）——plan 有意让 writeManifest 独立可调；`resolvePlatforms` 硬编码 `"win_amd64"` 兜底（从 `build()` 不可达）。
- **Task 4 Minor**：`DepsService.searchWheels` 拼 URL 未 `URLEncoder`（PyPI 包名字符集本身 URL-safe，404 已被空列表兜底）。

## ⚠️ 唯一未完成项：手动 UI 验证（Task 5 Step 4）

JavaFX 节点无单测；**Task 5 Step 4 的 DevLauncher 手动验证尚未执行**（当前环境无图形显示）。自动化门禁（编译 + 75/75 单测）已过，但 UI 行为需人工确认。**合并前请在 IDEA 运行 `plugin.swisskit.offlinepython.DevLauncher`**，按计划 Task 5 Step 4 逐项核对：
- 布局：导入独占行1；包名/版本/目标平台同行；在线搜索+保存配置同行；表格/选项/底栏正常。
- 手动新增 → 表格新增行 + `config.json` 的 `python.depPlatforms` 含该 key + `requirements.txt` 含该行。
- per-dep 平台：两行平台不同，`config.json` 两 key 各自正确。
- 主从编辑：选中行 → 行2载入；改后保存 → 更新（非新增）。
- 在线搜索：弹窗搜 `numpy` → 列出 wheel → 选 win_amd64 → 回填包名/版本/平台/大小。
- 构建：日志按平台集合分组出现多条 `$ pip download ... --platform ...`；`manifest.json` 的 `python.platforms` 为所有依赖平台并集。

## 环境备注（续接者必读）

- **Maven 未在 PATH**。本机会话用直接下载的 binary：`MVN=~/tools/apache-maven-3.9.16/bin/mvn`，配合 `JAVA_HOME=/Users/phoebej/Library/Java/JavaVirtualMachines/azul-21.0.11/Contents/Home`（项目 target 21；勿用默认 JDK 25）。Homebrew 的 `brew install maven` 在本机未持久化（`brew info maven` 显示 Not installed），故用上述独立 binary。
- 跑测试：`$MVN -pl SwissKitJ-Plugin-OfflinePython -am test`（单类加 `-Dtest=<TestClass>`）。**勿加 `-q`**（会吞掉 surefire 的 `Tests run:` 摘要）。
- IDEA MCP（`mcp__idea__*`）可用，调用需带 `projectPath=/Users/phoebej/Develop/Java/SwissKiJ-Plugin`。权威验证以 Maven surefire 为准。
- **subagent detached-HEAD 事故**：Task 4 的实现 subagent 曾在 detached HEAD 下提交（commit 内容正确，但分支 ref 未前进）。已用 `git branch -f feature/offline-python-builder-v1 HEAD && git checkout feature/offline-python-builder-v1` 修复。后续给 subagent 派活须显式要求：开工前 `git status -sb` 确认在分支上、禁止 `git checkout/switch`、提交后 `git log --decorate -1` 确认 `(HEAD -> 分支名)`。

## 续接后的下一步

1. 先做上面的 **DevLauncher 手动 UI 验证**（唯一未关门项）。
2. 通过后，用 `superpowers:finishing-a-development-branch` 收尾（推送 / PR / 合并）。
3. （可选）择期清理 D1/D2 等 Minor 项。
