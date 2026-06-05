# 贡献指南

感谢你对 Muse 的兴趣！欢迎通过 Issue 报告问题或提交 Pull Request 改进代码。

---

## 目录

- [行为准则](#行为准则)
- [开发环境](#开发环境)
- [分支模型](#分支模型)
- [提交信息规范](#提交信息规范)
- [代码风格](#代码风格)
- [提交 Pull Request](#提交-pull-request)
- [本地测试](#本地测试)

---

## 行为准则

参与本项目即表示你同意遵守 [行为准则](CODE_OF_CONDUCT.md)。

---

## 开发环境

| 工具 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Android Studio | Ladybug 或更新版本 |
| Android SDK | 36 (compileSdk / targetSdk) |
| minSdk | 28 (Android 9.0+) |
| Kotlin | 2.3+ |

### 初始化步骤

```bash
git clone https://github.com/ninrry/muse.git
cd muse
./gradlew assembleDebug   # 验证环境可用
```

---

## 分支模型

| 分支 | 用途 |
|------|------|
| `main` | 稳定分支，每个发布版本的基础 |
| `feature/<name>` | 新功能开发 |
| `fix/<name>` | Bug 修复 |
| `refactor/<name>` | 代码重构（不改变外部行为） |
| `docs/<name>` | 文档变更 |

**不要直接向 `main` 提交代码**，请通过 Pull Request 合并。

---

## 提交信息规范

本项目遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/) 规范：

```
<类型>(<范围>): <简短描述>

[可选的正文]

[可选的脚注]
```

**类型列表：**

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不影响外部行为） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `docs` | 文档变更 |
| `build` | 构建系统或依赖变更 |
| `ci` | CI 配置变更 |
| `chore` | 其他杂务（不影响 src / test） |

**示例：**

```
feat(player): 添加睡眠定时器功能

支持在完成当前曲目后停止和按分钟定时停止两种模式。

Closes #42
```

---

## 代码风格

本项目使用 **ktlint** 和 **Detekt** 强制代码规范，CI 会自动检查。

**提交前请运行：**

```bash
# 自动格式化
./gradlew ktlintFormat

# 检查（模拟 CI）
./gradlew ktlintCheck detekt
```

主要约定：
- 行长度上限 140 字符
- 使用 `@Suppress` 注解时必须注明原因
- 不允许通配符导入（`import foo.*`）超过阈值
- 空函数体必须包含注释说明意图

---

## 提交 Pull Request

1. **Fork** 仓库并基于 `main` 创建你的分支
2. 完成修改并确保所有检查通过（见 [本地测试](#本地测试)）
3. 如涉及新功能，请同步更新文档
4. 提交 PR 并填写模板，关联相关 Issue
5. 等待代码审查

PR 合并要求：
- ✅ CI 全部通过（ktlint / detekt / 单元测试 / 构建）
- ✅ 至少一位 Reviewer 批准
- ✅ 无未解决的 Review 意见

---

## 本地测试

```bash
# 格式检查
./gradlew ktlintCheck detekt

# 单元测试
./gradlew :core:common:test :core:model:test :core:domain:test \
  :core:data:testDebugUnitTest :core:media:testDebugUnitTest \
  --max-workers=1

# Android Lint
./gradlew lint --max-workers=1

# 仪器测试（需连接设备）
./gradlew :core:database:connectedDebugAndroidTest \
          :app:connectedDebugAndroidTest --max-workers=1

# 完整构建验证
./gradlew assembleDebug assembleRelease --max-workers=1
```

---

## 报告安全漏洞

请**不要**通过公开 Issue 报告安全漏洞，参见 [安全政策](SECURITY.md)。
