# AI 适配指南

> 本文说的「AI 适配」有两条完全不同的路径，别混为一谈：
>
> - **路径 A —— 接入 AI Provider**：让 SKY 能向某个大模型提问，产出**建议**。
> - **路径 B —— 用 AI Skill 生成适配器**：让 AI 产出真实代码，使第三方插件**自动接入** SKY 的观测体系。
>
> 两条路径都遵守同一条底线：**AI 只能建议，不能成为世界安全的事实控制者**。

当前状态（P1.1）：路径 B 已可运行并有生成物入库；路径 A **只提供契约层，插件不加载任何 AI Provider、不发起任何网络请求**。

---

## 路径 A：接入 AI Provider

### 现状与边界

| 组件 | 位置 | 状态 |
| --- | --- | --- |
| `AiProvider` SPI | `sky-ai-client` | 已有：`id()` / `complete(redactedPrompt)` / `isAvailable()` |
| `AiConfig` | `sky-ai-client` | 已有：默认 `enabled=false`，`allowExecute` 在构造器里被**强制改为 false** |
| `AdviceSchemaValidator` | `sky-ai-client` | 已有：对象级校验 + 注入标记扫描，`Verdict.executable` 恒为 false |
| `AdviceProposal` DTO | `sky-api` | 已有：AI 唯一允许产出的形状 |
| `advice-proposal.schema.json` | `schemas/` | 已有：Draft 2020-12，CI 强制校验 |
| `Redactor` | `sky-audit` | 已有：UUID / IP / 坐标 / NBT / 令牌脱敏 |
| HTTP 客户端 | — | **未实现（UNRESOLVED）**，P3 才引入经审查的依赖 |

### 实现 `AiProvider` 的强制清单

1. **永远不在主线程 tick 内调用**。`complete()` 必须是异步的（`CompletableFuture`），由受控的后台执行器驱动，带连接 / 请求 / 总超时。
2. **有界**：`maxInflight`、`queueCapacity` 来自 `AiConfig`（默认 2 / 32），队列满则丢弃并告警，不得无限堆积。
3. **入参必须已脱敏**。`complete(String redactedPrompt)` 的契约是「传入的文本已经过 `Redactor`」，实现方不得再接收原始聊天、UUID、IP、坐标、背包 NBT 或玩家名。
4. **密钥只引用不落地**。`apiKeyRef` 必须以 `secret:` 开头（`AiConfig` 构造器会抛异常拒绝字面量），实现方从受限文件或外部 secret store 读取，绝不写进配置、日志或异常消息。
5. **失败关闭**：超时、限流、熔断、HTTP 错误一律返回不可用，回退到本地规则；**AI 离线不得影响 P0/P1 保护**。
6. **输出先过 Schema 再过策略**：原始响应 → JSON 解析（P3 引入经审查依赖）→ `schemas/advice-proposal.schema.json` 校验 → `AdviceSchemaValidator.validate()` → 本地 `PolicyEngine` → 人工/自动批准。任何一步失败只保存建议，不执行。
7. **不可越权**：`AdviceSchemaValidator` 会拒绝未注册/高风险动作与提示注入标记；`Verdict.executable()` 恒为 `false`，`AiConfig.allowExecute` 恒为 `false`。不要为「方便」绕过它们。

### 明确禁止

> 执行任意命令、绕过权限、生成热加载代码、修改核心 jar、访问原始敏感信息、把玩家聊天当作管理员指令、根据 AI 建议直接改世界或配置、把 AI 置信度当作硬约束的覆盖依据。

### 一个最小实现骨架（示意，不引入依赖）

```java
public final class MyProvider implements AiProvider {
    private final AiConfig config;      // enabled=false by default
    @Override public String id() { return config.provider(); }
    @Override public boolean isAvailable() { return config.enabled() && httpHealthy(); }
    @Override public CompletableFuture<String> complete(String redactedPrompt) {
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AI offline"));
        }
        // bounded executor + timeout + Redactor already applied upstream
        return boundedHttpCall(redactedPrompt);
    }
}
```

> 注意：上面是**契约示意**，不是已实现的功能。P1.1 的可安装插件根本不装配 `AiProvider`。

---

## 路径 B：用 AI Skill 生成适配器（自动适配）

这是本轮已经跑通的部分。Skill 位于 [`skills/sky-adapter-generator/`](../skills/sky-adapter-generator/SKILL.md)。

### 「自动适配」到底指什么

1. 你（或 AI Agent）把目标插件的**事实**写进一份 spec JSON；
2. Skill 的确定性生成器产出完整 Gradle 模块：Java 实现 + `META-INF/services/dev.skyguard.api.ResourceParticipant` + 契约测试 + `GENERATED.md`；
3. 生成物被打进可安装插件 `SKYResourceGuard-0.1.0-p1.jar`；
4. `SkyPlugin.onEnable()` 用 `java.util.ServiceLoader` 读到该注册项，自动把参与者加入 `ParticipantRegistry` 并开始只读采样。

**不需要修改插件主体源码** —— 这就是「用 Skill 产出的代码自动适配」。生成的测试里有一条 `serviceLoaderDiscoversTheGeneratedAdapter()`，它是这条链路的可执行证据。

### 操作步骤

```bash
# 1) 写 spec（字段含义见 SKILL.md 与 schemas/adapter-spec.schema.json）
# 2) 生成
python3 skills/sky-adapter-generator/scripts/generate_adapter.py \
  --spec skills/sky-adapter-generator/examples/acme-quests.json \
  --out adapters/sky-adapter-generated-acme \
  --module-label adapters/sky-adapter-generated-acme

# 3) 在 settings.gradle.kts 加模块、在插件依赖里加模块、同步 services 文件
# 4) 验证
python3 tools/verify-generated-adapters.py
./gradlew clean build javadoc
bash tools/check-plugin-jar.sh
```

### 生成器自带的硬约束

- `observeOnly` 必须为 `true`；拒绝任何主动能力（`QUIESCE` / `CHECKPOINT` / `LOWER_POWER` / `RESUME` / `RELEASE_RESOURCES` / `EXECUTOR_SHUTDOWN`）。
- `sample()` 只读、有界、不含玩家可识别数据；所有变更类生命周期方法一律**拒绝**或返回未实现。
- 能力声明严格等于 spec，不会替你「推断」或夸大。
- `CORE` 服务等级直接拒绝（需人工复核）。
- `--check` 模式可重生成并 diff，CI 用 `tools/verify-generated-adapters.py` 防止生成物腐化。

### 人工审查清单（AI 产出 ≠ 可信）

- [ ] spec 里的 id / 版本 / 能力来自真实插件，没有 `UNRESOLVED` 被悄悄写成具体值。
- [ ] 生成代码没有 `Thread.stop/suspend/resume`、`System.gc()`、`Runtime.exec`、反射越界或世界写操作。
- [ ] 没有编造构建号、下载链接、TPS / P99 / 性能提升数字。
- [ ] `./gradlew build` 全绿，`ServiceLoader` 发现测试通过。
- [ ] `GENERATED.md` 的限制说明仍在。

---

## 仍未解决 / 不要过度承诺

- 路径 A 的 HTTP Provider、密钥读取实现、真实模型响应解析：**未实现**。
- 跨插件 classloader 的服务发现：**UNRESOLVED（P2）**。当前只发现与 SKY 插件同一 classloader 可见的适配器（即打进 fat jar 的模块），把任意外部插件 jar 丢进 `plugins/` 并不会自动被 SKY 发现。
- `sample()` 返回的是 spec 声明的初始值，**不是实测**；接真实只读读数需人工审查。
- 性能开销：**NOT MEASURED**，仓库没有任何实测数字。
- 具体 Purpur 服务端 build 与 SHA-256：见 [`version-matrix.md`](version-matrix.md)，仍为 `UNRESOLVED`。

## 命令速查

| 目的 | 命令 |
| --- | --- |
| 校验 AI 建议 Schema | `python3 tools/validate-schemas.py` |
| 校验生成适配器 | `python3 tools/verify-generated-adapters.py` |
| 校验可安装 jar | `bash tools/check-plugin-jar.sh` |
| 实证自动适配（jar 内 ServiceLoader） | `bash tools/check-autodiscovery.sh` |
| 敏感模式扫描 | `bash tools/scan-sensitive.sh` |
| 全量构建 | `./gradlew clean build javadoc` |
