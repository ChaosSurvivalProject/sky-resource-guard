---
name: sky-adapter-generator
description: 为第三方 Minecraft/Purpur 插件生成 SKY Resource Guard 的 OBSERVE_ONLY 适配器代码，并通过 ServiceLoader 自动适配。当用户要「给某个插件写 SKY 适配器 / 自动适配插件 / AI 生成适配代码 / generate SKY adapter」时使用。产出的是真实可编译、可测试的 Java 模块，不是伪代码。
---

# SKY 适配器生成器（AI Skill）

这个 Skill 把「AI 产出的代码」变成 SKY 能**自动发现并注册**的真实适配器：生成的
`META-INF/services/dev.skyguard.api.ResourceParticipant` 会被 `SkyPlugin.onEnable()`
通过 `java.util.ServiceLoader` 读取，自动加入 `ParticipantRegistry` 并被只读采样，
**不需要修改插件主体源码**。

配套文档：[`docs/ai-adaptation.md`](../../docs/ai-adaptation.md)（AI Provider 适配 + 审查规范）。

## 何时使用

- 用户说「给 X 插件做 SKY 适配」「自动生成适配器」「AI 写适配代码」。
- 需要把一个尚未接入 SKY SPI 的插件纳入只读观测。

## 铁律（不可绕过）

1. **只写事实**。插件 id、版本、服务等级、能力必须来自 `plugin.yml` / 源码 / 文档。
   不知道就写 `UNRESOLVED` 并停下来问，绝不猜。
2. **P1.1 只生成 OBSERVE_ONLY 适配器**。`observeOnly` 必须为 `true`；生成器会
   拒绝任何主动能力（`QUIESCE` / `CHECKPOINT` / `LOWER_POWER` / `RESUME` /
   `RELEASE_RESOURCES` / `EXECUTOR_SHUTDOWN`）。
3. **生成代码不得改动世界**：不写配置、不调度世界写操作、不关闭他人线程/执行器、
   不反射绕过访问边界、不调用任何被 `tools/scan-sensitive.sh` 禁止的 API。
4. **不声称已测量**。`sample()` 返回的是 spec 里声明的初始值，不是实测数据；
   不得写入任何 TPS / P99 / 性能提升数字。
5. **生成物必须入库并验证**：生成后要跑构建与一致性校验，不能只把代码贴给用户。

## 工作流

### 1. 收集事实，写 spec

参照 [`examples/acme-quests.json`](examples/acme-quests.json) 与
[`schemas/adapter-spec.schema.json`](schemas/adapter-spec.schema.json) 新建 JSON。
字段含义：

| 字段 | 来源 | 注意 |
| --- | --- | --- |
| `pluginId` / `version` | 目标插件 `plugin.yml` | 必须与真实值一致 |
| `serviceTier` | 人工判定 | `CORE` 需人工复核，生成器会拒绝，先用 `BACKGROUND` |
| `executionPhase` | 该插件主要工作阶段 | 决定采样标签 |
| `faultDomain` | 故障隔离域 | 自由命名，需稳定 |
| `capabilities` | **只填观测类** | `HEALTH_CHECK` / `QUEUE_REPORT` / `CACHE_REPORT` / `LEAK_DETECTION` / `DRY_RUN` |
| `sampling.*` | 声明的初始值 | 是声明值，不是实测值 |
| `observeOnly` | 固定 `true` | P1.1 硬约束 |

### 2. 生成模块

```bash
python3 skills/sky-adapter-generator/scripts/generate_adapter.py \
  --spec skills/sky-adapter-generator/examples/acme-quests.json \
  --out adapters/sky-adapter-generated-acme \
  --module-label adapters/sky-adapter-generated-acme
```

生成 5 个文件：`build.gradle.kts`、参与者实现、services 注册文件、契约测试、
`GENERATED.md`。**不要手改这些文件**，改 spec 后重新生成。

### 3. 接入构建与自动注册

- 在 `settings.gradle.kts` 的 `include(...)` 中加入新模块。
- 把生成模块加入 `sky-plugin-paper` 的依赖（会被打进可安装 fat jar）。
- 把生成 services 文件里的类名追加到
  `sky-plugin-paper/src/main/resources/META-INF/services/dev.skyguard.api.ResourceParticipant`。
  `tools/verify-generated-adapters.py` 会检查这一步是否遗漏。

### 4. 验证（必须全部通过）

```bash
python3 tools/verify-generated-adapters.py   # spec/Schema + 生成物一致性 + 注册完整性
./gradlew clean build javadoc                # 生成代码真实编译、契约测试 + ServiceLoader 发现测试
bash tools/check-style.sh
bash tools/scan-sensitive.sh
bash tools/check-plugin-jar.sh               # 可安装 jar 不含服务端 API
bash tools/check-autodiscovery.sh            # 从可安装 jar 实证 ServiceLoader 自动发现
```

### 5. 人工审查清单（AI 产出不等于可信）

- [ ] 能力声明与真实插件一致，没有夸大；没有主动能力。
- [ ] `sample()` 只读、有界、不含玩家名 / UUID / IP / 坐标 / 聊天原文。
- [ ] `quiesce` / `checkpoint` / `resume` / `release` 全部拒绝或声明未实现。
- [ ] 没有 `UNRESOLVED` 被悄悄改成具体值，没有编造构建号或性能数字。
- [ ] 生成的测试里 `ServiceLoader` 发现测试通过（这就是自动适配的证据）。
- [ ] `GENERATED.md` 的三个限制段未被删除。

## 失败与边界

- spec 校验失败：修正事实后重来，**不要放宽 Schema 或生成器规则**。
- 生成物与模板不一致：重新生成，不要手工补丁。
- 跨插件 classloader 的服务发现仍是 **UNRESOLVED（P2）**：当前只发现与 SKY
  插件同一个 classloader 可见的适配器（即打进 fat jar 的模块）。不要向用户
  承诺「把任意外部插件 jar 丢进去就会自动适配」。
- 需要主动降额 / 静默 / 检查点等能力时，属于 P2+ 的**人工编写**适配器，
  不在本 Skill 的生成范围内。
