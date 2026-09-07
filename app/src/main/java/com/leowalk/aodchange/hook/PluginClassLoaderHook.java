package com.leowalk.aodchange.hook;

import android.content.ComponentName;
import android.content.ContextWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterfaceWrapper;

public class PluginClassLoaderHook {

    private static volatile boolean sDone = false;

    public static void init(XposedInterfaceWrapper xiw, ClassLoader sysuiCl) throws Exception {
        Class<?> factoryCls = Class.forName(
                "com.android.systemui.shared.plugins.PluginInstance$PluginFactory",
                false, sysuiCl);
        Method createCtx = factoryCls.getDeclaredMethod("createPluginContext");
        // HyperOS4 起字段名由 mComponentName 改为 componentName
        final Field fComponent = findComponentField(factoryCls);
        fComponent.setAccessible(true);

        xiw.hook(createCtx).intercept(chain -> {
            Object result = chain.proceed();
            if (!sDone && result instanceof ContextWrapper) {
                ContextWrapper ctx = (ContextWrapper) result;
                try {
                    ComponentName cn = (ComponentName) fComponent.get(chain.getThisObject());
                    if (cn != null && cn.getClassName().contains("DozeServicePluginImpl")) {
                        ClassLoader pluginCl = ctx.getClassLoader();
                        sDone = true;
                        installHooks(xiw, pluginCl);
                    }
                } catch (Exception ignored) {}
            }
            return result;
        });
    }

    private static Field findComponentField(Class<?> factoryCls) throws NoSuchFieldException {
        try {
            return factoryCls.getDeclaredField("componentName");
        } catch (NoSuchFieldException e) {
            return factoryCls.getDeclaredField("mComponentName");
        }
    }

    private static void installHooks(XposedInterfaceWrapper xiw, ClassLoader aodCl) {
        android.util.Log.i("AodChange", "installHooks called, cl=" + aodCl);
        try { ElementSyncHook.init(xiw, aodCl); } catch (Throwable t) { android.util.Log.w("AodChange", "ElementSync init err", t); }
        try { PositionFreezeHook.init(xiw, aodCl); } catch (Throwable ignored) {}
        try { FocusRowHook.init(xiw, aodCl); } catch (Throwable ignored) {}
        try { BatteryRepositionHook.init(xiw, aodCl); } catch (Throwable ignored) {}
        try { TimeTickHook.init(xiw, aodCl); } catch (Throwable ignored) {}
        try { LyricHook.init(xiw, aodCl); } catch (Throwable t) { android.util.Log.e("AodChange", "Lyric init err", t); }
        try { NotificationCardHook.init(xiw, aodCl); } catch (Throwable ignored) {}
        try { DozeBrightnessHook.initForAod(xiw, aodCl); } catch (Throwable t) { android.util.Log.w("AodChange", "DozeBrightness init err", t); }
    }
}
