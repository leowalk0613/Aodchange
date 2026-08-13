# libxposed 模块入口与 hook 类通过基类/反射加载，必须保留命名
-keep class com.leowalk.aodchange.MainHook { *; }
-keep class com.leowalk.aodchange.hook.** { *; }
-keep interface io.github.libxposed.api.** { *; }
-keep class io.github.libxposed.api.** { *; }

# 被 hook 系统进程可能反射调用的工具类
-keep class com.leowalk.aodchange.CardStyle { *; }
-keep class com.leowalk.aodchange.CardRenderer { *; }
-keep class com.leowalk.aodchange.SettingsHelper { *; }