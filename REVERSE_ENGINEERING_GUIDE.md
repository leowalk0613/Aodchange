# AOD功能逆向工程分析指南

## 1. APK反编译工具准备
- **Jadx** (推荐): 图形化界面，支持Java代码反编译
- **Apktool**: 资源文件反编译
- **dex2jar**: 将dex转换为jar文件
- **JD-GUI**: Java代码查看器

## 2. 反编译命令示例

### 2.1 使用JADX反编译
```
jadx-gui com.android.systemui-202501210.apk
jadx-gui com.miui.aod-22324101.apk
jadx-gui AOD_Lyric-4.2.12.60.-release.apk
```

### 2.2 使用Apktool反编译资源
```
apktool d com.android.systemui-202501210.apk
apktool d com.miui.aod-22324101.apk
apktool d AOD_Lyric-4.2.12.60.-release.apk
```

## 3. 关键组件分析

### 3.1 com.android.systemui (系统UI)
重点关注以下组件：
- **Keyguard** - 锁屏相关组件
- **AOD相关服务** - Always On Display服务
- **Notification相关** - 通知管理和显示
- **MediaSession相关** - 媒体播放信息获取

关键类路径可能包括：
- `com.android.systemui.statusbar.phone.KeyguardBouncer`
- `com.android.systemui.doze.DozeService`
- `com.android.systemui.statusbar.policy.*`

### 3.2 com.miui.aod (MIUI AOD模块)
这是AOD功能的核心，注意分析：
- **AODView** - AOD显示视图
- **AODController** - AOD控制器
- **NotificationController** - 通知处理
- **Clock相关** - 时钟显示和更新
- **省电相关** - 低功耗模式

### 3.3 AOD_Lyric (墨息歌词)
分析其实现方式：
- 如何获取歌曲信息
- 歌词同步机制
- 实时更新策略
- 与AOD集成方式

## 4. 关注的技术点

### 4.1 实时刷新机制
- **定时器实现**: AlarmManager vs Handler
- **唤醒策略**: WAKE_LOCK使用
- **刷新频率控制**: 根据AOD状态调整

### 4.2 省电优化
- **Doze模式适配**
- **CPU唤醒控制**
- **屏幕刷新率管理**

### 4.3 数据获取机制
- **媒体信息监听**: MediaSession
- **通知监听**: NotificationListener
- **系统服务交互**

## 5. AOD实时刷新实现参考

### 5.1 时间秒更新
```java
// 关键实现实例
AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
    SystemClock.elapsedRealtime() + 1000, // 每秒更新
    "AodSecondUpdater", listener, handler);
```

### 5.2 歌词实时更新
- 监听MediaSession播放状态
- 获取实时歌词数据
- 平滑动画切换

### 5.3 通知实时刷新
- 使用NotificationListener获取最新通知
- 自定义通知排序算法
- 动态更新AOD显示

## 6. 常见的AOD类和方法

### 6.1 AOD视图相关
- `com.miui.aod.AODView.handleUpdateView()`
- `com.miui.aod.AODUpdatePositionController.updatePosition()`
- `com.miui.aod.notification.NotificationController`

### 6.2 电源管理相关
- `com.android.systemui.doze.DozeHost`
- `com.android.systemui.doze.DozeMachine`
- `WakeLock`管理

## 7. 实现技巧

### 7.1 Hook建议
- 优先Hook AODView的更新方法
- 替换或增强NotificationController
- 修改时钟更新逻辑

### 7.2 性能考虑
- 减少不必要的UI重绘
- 使用硬件加速
- 控制内存使用

### 7.3 兼容性处理
- 不同MIUI版本的差异
- 不同设备屏幕尺寸适应
- 系统版本兼容

## 8. 重要注意事项

### 8.1 安全机制
- 避免绕过安全限制
- 遵守系统权限控制
- 注意隐私数据保护

### 8.2 用户体验
- 保持原有AOD设计风格
- 确保刷新不影响正常功能
- 平滑过渡动画

## 9. 测试验证要点

1. **功能验证**: 实时刷新是否正常工作
2. **稳定性**: 长时间运行是否稳定
3. **功耗**: 对电池的影响
4. **兼容性**: 不同设备上的表现
5. **恢复能力**: 异常情况下的恢复