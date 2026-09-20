# AodChange

一个用于增强 MIUI 万象息屏（AOD）的 Xposed 模块，支持自定义组件卡片样式、歌词样式、自定义文字与日历日程等内容。

## 功能特性

### 组件卡片样式
对普通通知卡片、焦点通知卡片、歌曲信息卡片提供统一样式自定义：
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
无媒体播放时显示，分两个区域，卡片背景与描边分别配置：

- 区域一 · 自定义文字：指定文字，最多 4 行，每行可单独取色，支持靠左/居中/靠右
- 区域二 · 小组件：横向等宽排列，最多 4 个，可选天气、日出日落、湿度、AQI、体感、风、步数、站立、闹钟、日程
- 日程取未来三天内最近一条，可混排节日/节气；3 天内黄色、1 天内红色。首次使用需授予日历权限
- 闹钟读取系统下一用户闹钟（`next_alarm_clock_long`）；12 小时内黄色、1 小时内红色
- 天气类数据来自小米天气本地缓存（`content://weather/actualWeatherData/2`）

### 通知卡片管理
- 普通通知与焦点通知分类展示
- 折叠图标栏对齐设置

### 音乐应用白名单
仅白名单内应用显示音乐信息与歌词，可管理白名单应用列表。

## 安装与使用

### 安装要求
- Root 权限
- LSPosed 框架（v2.1.0，API 102）
- Xiaomi HyperOS 3.0 / 4.0（验证：HyperOS 3.0.303.0 Android 16；兼容 HyperOS 4 插件/指纹/亮度 API 差异）

### 作用域配置
- `com.android.systemui`
- `com.miui.aod`

### 使用方法
1. 在 LSPosed 中启用 AodChange 模块并勾选上述作用域。
2. 歌词功能需安装并激活 LyricFocus（需 v1.9.0 及以上版本）并打开其"aodchange 外部渲染"选项。
3. 重载作用域或重启设备后生效。
4. 打开应用进行各项样式与内容设置。

> 注意：本模块使用自渲染的方式显示焦点通知，因此 LyricFocus（v1.9.0 及以上）的万象息屏 AOD 歌词不能在本应用启用时显示。

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
├── PlaceholderActivity.java   # 自定义文字与小组件设置
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
    ├── LockscreenDataHelper.java
    └── ...
```

## 版本历史

### v1.3
- 自定义内容拆成文字区与小组件区：天气/日出日落/湿度/AQI/体感/风/步数/站立/闹钟/日程，最多 4 个等宽横排
- 两区独立卡片背景与描边；文字按行取色
- 日程、闹钟按剩余时间黄/红提示紧迫性

### v1.2
- 兼容 HyperOS 3 / 4：指纹信号改走 `DozeHost.fireFingerprintPressed`；插件字段与 Doze 亮度超时字段双路径适配。

### v1.1
- release 签名与混淆、隐藏桌面图标、LyricFocus 版本说明、关于页等。

### v1.0
- 初始版本：组件卡片样式、歌词样式、自定义文字/日历日程、通知卡片管理、音乐应用白名单、关于页。

## 许可证

未指定，保留所有权利。