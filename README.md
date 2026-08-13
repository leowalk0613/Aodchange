# AodChange

一个用于增强 MIUI 万象息屏（AOD）的 Xposed 模块，支持自定义组件卡片样式、歌词样式、自定义文字与日历日程等内容。

## 功能特性

### 组件卡片样式
对普通通知卡片、焦点通知卡片、歌曲信息卡片、自定义卡片提供统一样式自定义：
- 背景颜色（内置预设 / 自定义取色）与透明度
- 描边颜色
- 字体颜色（内置预设 / 自定义取色）
- 启用自定义样式的独立总开关

### 歌词样式
通过自渲染方式显示焦点通知中的歌词与歌曲信息：
- 歌词 / 多行歌词 / 歌曲信息 / 封面等样式设置
- 字号、宽度、行数、对齐、翻译原文互换、同步提前、前奏显示
- Monet 取色与自定义颜色
- 多行歌词图标栏对齐
- 歌词区域与歌曲信息卡片的独立显示开关

### 自定义内容
- 自定义文字：无媒体播放时显示指定文字
- 日历日程：无媒体播放时显示未来三天内的日程，按开始时间优先，24 小时内开始的日程高亮提醒，并支持节日/节气显示
- 自定义文字与日历日程均支持靠左/居中/靠右排版

### 通知卡片管理
- 普通通知与焦点通知分类展示
- 折叠图标栏对齐设置

### 音乐应用白名单
仅白名单内应用显示音乐信息与歌词，可管理白名单应用列表。

## 安装与使用

### 安装要求
- Root 权限
- LSPosed 框架
- MIUI 系统

### 作用域配置
- `com.android.systemui`
- `com.miui.aod`

### 使用方法
1. 在 LSPosed 中启用 AodChange 模块并勾选上述作用域。
2. 歌词功能需安装并激活 LyricFocus 并打开其"aodchange 外部渲染"选项。
3. 重载作用域或重启设备后生效。
4. 打开应用进行各项样式与内容设置。

> 注意：本模块使用自渲染的方式显示焦点通知，因此 LyricFocus 的万象息屏 AOD 歌词不能在本应用启用时显示。

## 构建

```bash
./gradlew assembleDebug
```

调试 APK 输出在 `app/build/outputs/apk/debug/`。

## 依赖

- Xposed API（`libs/xposed-api.jar`）
- Lunar Java（`libs/lunar-java.jar`，用于节日/节气）
- Material Components 1.12.0

## 项目结构

```
app/src/main/java/com/leowalk/aodchange/
├── MainActivity.java          # 主设置页
├── LyricStyleActivity.java    # 歌词样式设置
├── CardStyleActivity.java     # 组件卡片样式设置
├── PlaceholderActivity.java   # 自定义文字/日历日程设置
├── AboutActivity.java         # 关于页
├── CardRenderer.java          # 卡片渲染工具
├── CardStyle.java             # 卡片样式数据模型
├── SettingsHelper.java        # 设置读写
├── M3.java                    # Material 组件/取色器
├── MainHook.java              # 模块入口
└── hook/                      # 各系统 hook 实现
    ├── NotificationCardHook.java
    ├── LyricHook.java
    ├── CustomContentHook.java
    └── ...
```

## 版本历史

### v1.0
- 初始版本：组件卡片样式、歌词样式、自定义文字/日历日程、通知卡片管理、音乐应用白名单、关于页。

## 许可证

未指定，保留所有权利。