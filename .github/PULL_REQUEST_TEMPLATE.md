## What changed

<!-- One verifiable increment per PR. Link the P0-P4 stage from docs/architecture.md. -->

## Work-round report (task book 19)

```text
【本轮目标】
【已修改文件】
【行为变化】玩家 / 管理员 / 插件开发者
【架构边界】api / core / platform / world / runtime / ai / audit / cli
【约束核验】
  - 未使用 Thread.stop/suspend/resume：
  - 未强制回收未知插件对象：
  - 未把 AI 放入 tick：
  - 动作均有 TTL / 审计 / 回滚：
  - 已脱敏：
  - 未知插件仍为 observe-only：
【测试结果】命令、环境、结果、失败项
【兼容性】Purpur / Paper / Java / 插件 API
【性能开销】前后数据；无数据请写「未测」
【风险与未决项】事实 / 假设 / 待验证
【下一步】最小可验证增量
```

## Checklist

- [ ] `./gradlew clean build` passes on JDK 21.
- [ ] No fabricated API, config key, build number, test result or performance gain.
- [ ] No secret, real log, player data or world file added.
- [ ] New public API is documented; breaking SPI changes have a migration note.
- [ ] Performance claims (if any) include baseline, hardware, commands and raw results.
- [ ] Unknown plugins still default to `OBSERVE_ONLY`.
