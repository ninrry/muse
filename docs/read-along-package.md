# Muse 同步阅读模块

有声书阅读器只消费工作流已经生成的资源，不在 App 内接入系统 TTS、Qwen3-TTS、OmniVoice 或任何网络语音服务。

## 导入形式

- `.readalong.zip`：推荐的单一项目包；
- 普通 ZIP：只要内部包含 EPUB，也按同一规则尝试绑定资源；
- 解压后的完整文件夹：选择文件夹后保留 EPUB、manifest、alignment、音频及其它资源；
- `.epub`：可作为纯文本 EPUB 导入，章节没有配套音频时明确显示“本章没有配套音频”。

## 推荐包结构

```
book.readalong.zip
├── manifest.json
├── book.epub
├── alignment.jsonl
├── provenance.json                 # 可选，仅作溯源，不会被误当 manifest
└── audio/
    ├── ch001.m4a
    ├── ch002.m4a
    └── ...
```

## manifest 最小契约

```json
{
  "version": 1,
  "title": "书名",
  "author": "作者",
  "epub": "book.epub",
  "alignment": "alignment.jsonl",
  "audio_root": "audio",
  "toc_regex": [
    "^第[0-9一二三四五六七八九十百千万]+章"
  ],
  "chapters": [
    {
      "id": "ch001",
      "title": "第一章",
      "index": 0,
      "href": "OEBPS/ch001.xhtml",
      "audio": "audio/ch001.m4a"
    }
  ]
}
```

字段规则：

- `chapters[].audio` 优先，路径必须是包内相对路径；
- 没有显式 `audio` 时使用 `audio_root` + 章节 `id`/`filename`/文件名推导；
- 未提供 `chapters` 时扫描 `audio_root` 并按章节 ID、文件名回退绑定；
- `toc_regex` 可覆盖目录回退规则；非法正则会被丢弃并回到内置规则；
- 所有资源路径都会做规范化和目录穿越检查，禁止 `../` 逃出导入根目录；
- `alignment.jsonl` 中 `audio_locator.chapter_*_seconds` 是章节内绝对时间；`unit_timings[].start/end` 是当前句内相对时间，导入后统一转换为毫秒并加上句起点；
- 对齐文件支持逐字/逐词两级粒度；只有句级数据时，长按跳转自动降级到句起点；
- 音频、对齐、EPUB 缺失时保留可读性，但不合成替代声音。

## 阅读器能力

- 书架网格：第一格固定为带 `+` 的导入骨架卡；支持书包和完整文件夹；
- EPUB 原生 `nav`/NCX 目录优先；无标准目录时使用中文章节、英文 Chapter/Part/Section、多级数字标题等回退规则；
- 竖向滚动与 CSS 多栏分页；字体、字重、字号、行距、段距、主题和自动跟随可持久化；
- 已有章节音频由 ExoPlayer 播放；当前句和当前字/词通过 alignment 高亮，自动滚动到可见区域；
- 拖动音频进度定位文本；开启“文字跳转”后长按文本会跳到对应字/词（无逐字对齐时降级为句）；
- 章节结束自动播放下一章；返回书架前保存章节、音频、文字滚动和阅读设置进度；
- 搜索、批注、书签、统计和补齐音频/对齐资源。

## 设计边界

Readest 是 AGPL-3.0，Legado 是 GPL-3.0。当前 Muse 没有直接复制其源文件，而是把其成熟交互模式映射到现有 Kotlin/Compose/WebView/Room 工程；若后续决定直接移植代码，必须把对应许可证和来源文件一并纳入项目。
