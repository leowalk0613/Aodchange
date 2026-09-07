package com.leowalk.aodchange;

import com.leowalk.aodchange.hook.BatteryRepositionHook;
import com.leowalk.aodchange.hook.NotificationCardHook;
import com.leowalk.aodchange.hook.PluginClassLoaderHook;
import com.leowalk.aodchange.hook.PositionFreezeHook;
import com.leowalk.aodchange.hook.TimeTickHook;

import io.github.libxposed.api.XposedInterfaceWrapper;
import io.github.libxposed.api.XposedModule;

public class MainHook extends XposedModule {

    public static final String PKG_AOD = "com.miui.aod";
    public static final String PKG_SYSUI = "com.android.systemui";

    @FunctionalInterface
    private interface HookInit {
        void run() throws Exception;
    }

    private boolean isTarget(String pkg) {
        return PKG_AOD.equals(pkg) || PKG_SYSUI.equals(pkg)
                || "com.mi.health".equals(pkg);
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        android.util.Log.i("AodChange", "onPackageReady: " + pkg);
        if (!isTarget(pkg)) return;

        ClassLoader cl = param.getClassLoader();
        XposedInterfaceWrapper xiw = this;

        if (PKG_SYSUI.equals(pkg)) {
            initHook("PluginClassLoaderHook", () -> PluginClassLoaderHook.init(xiw, cl));
        }
        if (PKG_AOD.equals(pkg)) {
            initHook("BatteryRepositionHook", () -> BatteryRepositionHook.init(xiw, cl));
            initHook("TimeTickHook", () -> TimeTickHook.init(xiw, cl));
            initHook("NotificationCardHook", () -> NotificationCardHook.init(xiw, cl));
            initHook("PositionFreezeHook", () -> PositionFreezeHook.init(xiw, cl));
        }
    }

    private void initHook(String name, HookInit initFn) {
        try {
            initFn.run();
            android.util.Log.i("AodChange", name + " initialized OK");
        } catch (Throwable t) {
            android.util.Log.w("AodChange", name + " init fail", t);
        }
    }
}
