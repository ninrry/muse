# ADR-0002: 数据库决策

## 状态

已接受

## 决策标题

使用 Room 作为本地数据库解决方案

## 上下文

Muse 需要持久化存储以下数据：
- 歌曲元数据（标题、艺术家、专辑等）
- 歌词（同步歌词、纯文本歌词）
- 用户偏好设置
- 播放历史

需要考虑的方案：
1. Room (Android Jetpack)
2. SQLite 直接操作
3. 第三方 ORM（如 Realm）

## 决策

采用 **Room** 作为数据库解决方案，理由：

1. **官方支持**: Android Jetpack 组件，长期维护保障
2. **编译时验证**: SQL 语句在编译时检查
3. **Flow 支持**: 原生支持 Kotlin Flow，便于响应式编程
4. **迁移支持**: 内置数据库迁移机制
5. **类型安全**: 自动生成类型安全的 DAO 代码

### 数据库结构

```kotlin
@Database(
    entities = [SongEntity::class, AlbumEntity::class, ArtistEntity::class, LyricsEntity::class, LyricsOffsetEntity::class],
    version = 3,
    exportSchema = true
)
abstract class MuseDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun lyricsOffsetDao(): LyricsOffsetDao
}
```

### 实体设计

- **SongEntity**: 歌曲核心数据
- **AlbumEntity**: 专辑聚合数据
- **ArtistEntity**: 艺术家聚合数据
- **LyricsEntity**: 歌词缓存
- **LyricsOffsetEntity**: 歌词时间偏移

## 后果

### 正面影响

- **开发效率**: 自动生成样板代码
- **类型安全**: 编译时捕获 SQL 错误
- **响应式**: 原生 Flow 支持
- **可测试**: 内置 MigrationTestHelper

### 负面影响

- **学习成本**: 需要学习 Room 注解和 API
- **灵活性受限**: 相比直接 SQLite，某些复杂查询受限
- **包体积**: 增加约 200KB

### 风险缓解

- 导出 Schema 用于版本控制
- 编写迁移测试确保数据安全
- 对复杂查询使用 @RawQuery

## 相关决策

- [ADR-0001: 架构决策](./0001-architecture.md)

## 参考资料

- [Room 官方文档](https://developer.android.com/training/data-storage/room)
- [Room 最佳实践](https://developer.android.com/training/data-storage/room/referencing-data)
