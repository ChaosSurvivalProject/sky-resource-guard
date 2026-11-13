# SKY Resource Guard

> 面向 **Minecraft Java 版 Purpur 1.21.11 / Java 21** 的开源、插件式、AI 辅助资源治理与动态保护平台。
> **当前状态：实验性（P0 可信观测）**。这一版是纯 Java 核心逻辑，**尚不能作为插件安装到服务端**。

[![CI](https://github.com/ChaosSurvivalProject/sky-resource-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/ChaosSurvivalProject/sky-resource-guard/actions/workflows/ci.yml)
License: **GPL-3.0-or-later** · Java **21** · Package `dev.skyguard.api`

---

## 这个项目要解决的问题

服务端的世界逻辑高度依赖主线程 tick。当实体、掉落物、区块、寻路、方块实体、插件任务和后台负载叠加时，必须优先保护**登录、聊天、背包、交互、建造、战斗和世界一致性**。

SKY 的目标不是宣称"恒定 20 TPS"或"零延迟"，而是建立一个：

1. **可观测** —— 知道时间、CPU、内存、世界负载和玩家影响花在哪里；
2. **可解释** —— 每次降级与恢复都能说明证据、目标、风险和结果；
3. **可回滚** —— 所有生产变更都有边界、期限、验证和回退；
4. **可渐进适配** —— 自研插件优先深度接入，第三方插件不适配也能被观测和有限治理；
5. **AI 可替换、可关闭** —— AI 只能提出建议和分析，绝不能成为世界安全的事实控制者；
6. **适合开源协作** —— 模块清晰、接口稳定、文档完整、测试可复现。

## 能做什么 / 不能做什么

**能做（本版已实现，纯 Java，可脱离服务端测试）**

- tick/MSPT 环形缓冲与直方图、证据相关器、带迟滞与 `LOCKOUT` 的保护状态机；
- 动作网关：能力校验 → 权限 → 依赖 → 安全范围 → dry-run → 租约 → 快照 → 执行 → 验证 → 审计 → 释放/回滚；
- 带 TTL、checksum、并发冲突检测和幂等回滚的租约管理；
- 追加式、序列号 + 哈希链的审计日志，以及 UUID/IP/坐标/NBT/令牌脱敏；
- 资源参与者 SPI、外部插件适配器 SPI、四级证据（L0–L3）与 `OBSERVE_ONLY` 默认策略；
- 掉落物状态机（NORMAL → MERGED → FROZEN → SERIALIZED → RESTORING → NORMAL）与保护规则；
- 可运行的 SHADOW 报告 CLI 演示、测试假实现与确定性压测场景；
- 两个示例适配器（原生 SPI 与 `ExternalPluginAdapter`）。

**不能做（明确非目标，任务书 §1.2 / §20）**

> - **不能强制安全冻结未知插件的线程**；不调用 `Thread.stop/suspend/resume`，不反射清空未知字段，不强制 GC，不按插件精确回收 JVM 堆。
> - **不能保证零延迟或恒定 20 TPS**；不修改 Minecraft / Paper / Purpur 源码，不要求 Mod Loader，不把任意世界写操作改成异步。
> - **不能让 AI 或玩家聊天直接修改生产参数**；AI 不执行任意命令、不热加载代码、不读原始敏感信息。
> - 不自动执行经济写改、库存/世界/备份删除、封禁或其他不可逆操作。

**本版尚未实现（后续阶段，不伪装完成）**：可安装的 Bukkit/Paper 插件 JAR、`plugin.yml`、真实世界/掉落物操作、AI HTTP 客户端、Purpur 配置写入、MockBukkit 集成测试、JMH 压测。见 [CHANGELOG.md](CHANGELOG.md) 与 `docs/architecture.md`。

## 目标平台与版本事实

| 项目 | 值 | 状态 |
| --- | --- | --- |
| Java | 21（`release = 21`） | 已验证（CI + 本地 toolchain） |
| 服务端 | Purpur **1.21.11**（用户锁定版本） | **UNRESOLVED**：具体 build、SHA-256、上游 Paper build 未能复核 |
| 上游 API | Bukkit/Paper 公开 API 优先 | P1 起接入，本版零服务端依赖 |
| AI | 默认 `deepseek-flash`，可替换 | 默认 `enabled: false`，本版无网络调用 |

**我们不编造构建号、下载链接或性能数字。** 详见 [`docs/version-matrix.md`](docs/version-matrix.md) —— 未复核项一律标 `UNRESOLVED`，并给出 `PROBE PLAN`。

## 快速开始

```bash
# 需要 JDK 21（Gradle toolchain 会自动选择）
./gradlew clean build          # 编译 + 单元测试 + javadoc
./gradlew :sky-cli:run         # 运行 SHADOW 只读报告演示
./gradlew test                 # 仅测试
```

`sky-api`、`sky-core`、`sky-audit` **不依赖任何服务端或 AI 网络**，可在无 Minecraft 环境下完整测试。

## 模块边界

| 模块 | 层 | 职责 |
| --- | --- | --- |
| `sky-api` | api | 稳定 SPI / DTO / 枚举 / 错误码 / 权限常量 / JSON Schema，最少依赖 |
| `sky-core` | core | 指标、状态机、策略引擎、动作网关、租约、预算仲裁、事件总线 |
| `sky-platform-paper` | platform | 能力探针接口与可选反射探测（失败关闭） |
| `sky-platform-purpur` | platform | Purpur 专有能力探测、配置键守卫（未知键只报告不写入） |
| `sky-world` | world | 掉落物状态机、保护规则、分帧恢复契约 |
| `sky-plugin-runtime` | runtime | 参与者注册表、未知插件策略与证据等级 |
| `sky-ai-client` | ai | 供应商抽象、Schema 校验、安全默认配置 |
| `sky-audit` | audit | 哈希链日志、脱敏、诊断包 |
| `sky-cli` | cli | 命令解析、dry-run、SHADOW 演示 |
| `sky-bootstrap` | bootstrap | 纯 Java 装配与兼容检查 |
| `sky-testkit` / `sky-benchmarks` | test | 假实现、故障注入、确定性回放 |
| `adapters/*` | adapters | 两个独立版本化的示例适配器，默认不随核心启用 |

见 [`docs/architecture.md`](docs/architecture.md)、[`docs/spi.md`](docs/spi.md)。

## 安装与最小配置（面向未来可安装版本）

本版**没有可安装 JAR**。以下配置是默认安全基线，也是 P1 接入时的目标契约：

```yaml
sky:
  mode: SHADOW              # SHADOW / ADVISE / PROTECT / AUTOMATE
  automation-enabled: false
  write-config-dry-run: true
  unknown-plugin-policy: OBSERVE_ONLY
  ai:
    enabled: false
    provider: deepseek
    base-url: ${SKY_AI_BASE_URL}
    api-key-ref: secret:sky/ai-api-key
    model-alias: deepseek-flash
    display-name: "DeepSeek V4.1 Flash"
    timeout-ms: 8000
    max-inflight: 2
    queue-capacity: 32
    allow-execute: false
    prompt-schema-version: 1
```

> **必须先以 SHADOW 模式运行**，观察报告与自身开销，再考虑升级模式。密钥只从受限文件或外部 secret store 引用，绝不写进配置或日志。

## SPI 快速开始

```java
public final class MyPluginParticipant implements ResourceParticipant {
    @Override public ParticipantMetadata metadata() { /* id, version, deps, tier */ }
    @Override public CapabilitySet capabilities() { /* 声明真实能力，不要夸大 */ }
    @Override public HealthSnapshot sample(SampleBudget budget) { /* 只读、低开销 */ }
    @Override public boolean requestLowerPower(PowerLevel level, Lease lease) { /* 合作式降额 */ }
    @Override public CompletableFuture<CheckpointReceipt> checkpoint(CheckpointRequest r) { /* ... */ }
    @Override public CompletableFuture<QuiesceReceipt> quiesce(QuiesceRequest r) { /* ... */ }
    @Override public CompletableFuture<ResumeReceipt> resume(ResumeToken t) { /* 幂等 */ }
    @Override public CompletableFuture<ReleaseReceipt> releaseForeignResources(ReleaseRequest r) { /* ... */ }
}
```

`quiesce` 契约：原子拒绝新任务 → 完成/安全取消现有任务 → checkpoint → 刷新队列 → 关闭**自有**执行器/连接/监听器 → 报告结果。`resume` 先恢复连接与缓存，再恢复调度，最后开放命令，且必须幂等。完整说明见 [`docs/spi.md`](docs/spi.md)。

## 自研插件适配指南

按优先级分四层（任务书 §4）：**原生 SPI 模式** → **官方桥接模块** → **受控集成模式** → **外部观测模式**。自研身份只提高适配优先级，**不自动获得高预算或隐式特权**；权限由策略、服务等级、玩家影响和动作风险决定。示例见 `adapters/sky-adapter-selfexample-a`（纯后台 I/O）与 `adapters/sky-adapter-selfexample-b`（缓存/数据库/命令/世界同步形态）。

## 未知插件支持矩阵

| 阶段 | 能力 | 本版状态 |
| --- | --- | --- |
| U0 | 身份、公开 API、事件与线程观测 | 已实现（策略层） |
| U1 | 按插件/子系统/调用点的证据链（L0 已知事实、L1 强关联、L2 弱关联、L3 猜测） | 已实现 |
| U2 | 可选官方适配器（`ExternalPluginAdapter`） | 接口 + 示例 |
| U3 | 通用低耗模式/健康检查社区接口 | 未开始 |
| U4 | 独立进程 / sidecar 隔离 | 未开始（设计阶段） |

**可观测 ≠ 可归因，可限制接入点 ≠ 可安全冻结。** 只有 L0/L1 证据可用于自动限制；L2 只告警；L3 只进调查建议。详见 [`docs/unknown-plugins.md`](docs/unknown-plugins.md)。

## 权限与命令

权限常量：`sky.observe.read`、`sky.advice.read`、`sky.plan.dryrun`、`sky.protect.manage`、`sky.action.safe`、`sky.action.expensive`、`sky.action.plugin.lifecycle`、`sky.approve.standard`、`sky.approve.critical`、`sky.ai.configure`、`sky.audit.export`、`sky.override.emergency`。经济、清理、传送、封禁、不可逆删除不进入全自动角色。

命令规划（本版 CLI 仅实现 `status` / `shadow-report` / `audit query` 的只读部分）：`/sky status`、`/sky metrics`、`/sky plan dryrun|approve|apply|verify|rollback`、`/sky plugin inspect`、`/sky participant quiesce --dry-run`、`/sky drops inspect`、`/sky ai status`、`/sky diagnostic create`。见 [`docs/actions.md`](docs/actions.md)。

## AI 隐私边界

AI 可以聚类错误、解释时间线、提出假设、推荐预注册动作、生成 dry-run 计划；**不可以**执行命令、绕过权限、生成热加载代码、修改核心 jar、访问原始敏感信息或把玩家文本当作管理员指令。异步边界为：事件摘要 → 脱敏 → 有界队列 → 限流 → HTTP → Schema 校验 → 建议 → 本地策略 → 人工/自动批准。AI 离线不影响 P0/P1 保护。日志、错误报告和 AI 请求必须脱敏 UUID、IP、聊天原文、坐标、背包 NBT、令牌、口令和内部主机名。

## 回滚与卸载

每个动作在租约中保存旧值、回滚动作、TTL、验证查询与 checksum；验证失败自动回滚，控制插件崩溃或租约丢失到期恢复，回滚失败进入 `LOCKOUT` 并生成诊断包请求管理员。掉落物序列化前写 `RESTORE_PENDING` 写前日志，启动时发现 pending 记录必须重建或告警，绝不静默丢弃。卸载策略见 [`docs/operations.md`](docs/operations.md)。

## 性能基准方法

采用固定 seed 与实体布局，分别测 `baseline / shadow / protect` 三种模式，报告 P50/P95/P99、置信区间和失败率，每次只改变一个策略族。**本版没有任何实测性能数据，也不承诺任何百分比提升。** 方法与未测项见 [`docs/benchmark.md`](docs/benchmark.md)。

## 版本兼容矩阵

见 [`docs/version-matrix.md`](docs/version-matrix.md)（含 `UNRESOLVED` 项与验证探针计划）。

## 安全问题上报

**请勿开公开 issue。** 通过本仓库 GitHub Security Advisories 私下报告，见 [SECURITY.md](SECURITY.md)。

## 参与贡献与许可

见 [CONTRIBUTING.md](CONTRIBUTING.md)、[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)、[RELEASE.md](RELEASE.md)。

本项目采用 **GPL-3.0-or-later**（[LICENSE](LICENSE)）。不混入来源不明或许可证不兼容的代码；依赖许可证清单见 [`docs/architecture.md`](docs/architecture.md)。
