# ADR-0001: 架构决策记录

## 状态

已接受

## 决策标题

采用 Clean Architecture + MVVM 架构模式

## 上下文

需要为 Muse 音乐播放器选择一种架构模式，要求：
- 代码结构清晰，职责分离
- 易于测试和维护
- 支持团队协作开发
- 符合 Android 开发最佳实践

## 决策

采用 **Clean Architecture** 的三层架构，结合 **MVVM** 模式：

```
UI Layer (Compose + ViewModel)
    ↓
Domain Layer (UseCase)
    ↓
Data Layer (Repository + Database + Network)
```

### 层次职责

| 层次 | 职责 | 示例 |
|------|------|------|
| UI | 界面展示、用户交互 | Screen、ViewModel、Component |
| Domain | 业务逻辑、跨仓库协调 | UseCase |
| Data | 数据获取、存储、管理 | Repository、DAO、Network |

### 依赖规则

- UI 层 → Domain 层、Data 层
- Domain 层 → Data 层（仅接口）
- Data 层 → 无上层依赖

## 后果

### 正面影响

- **职责清晰**: 每个层次有明确的职责边界
- **可测试性**: 业务逻辑独立于框架，易于单元测试
- **可维护性**: 修改某一层不会影响其他层次
- **可扩展性**: 新增功能只需在对应层次添加代码

### 负面影响

- **学习曲线**: 需要理解分层架构概念
- **代码量增加**: 相比简单架构，需要更多类和接口
- **初期成本**: 项目搭建阶段需要更多时间

### 风险缓解

- 提供清晰的代码模板和示例
- 编写详细的架构文档
- 定期进行代码审查

## 相关决策

- [ADR-0002: 数据库决策](./0002-database.md)
- [ADR-0003: 测试策略](./0003-testing.md)

## 参考资料

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Android App Architecture](https://developer.android.com/topic/architecture)
