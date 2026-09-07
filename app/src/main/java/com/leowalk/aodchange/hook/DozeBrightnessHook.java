package com.leowalk.aodchange.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterfaceWrapper;

/**
 * 阻止 AOD 息屏时的亮度调整，保持锁屏时的亮度。
 *
 * 1. Hook DreamServiceUtils.setDozeScreenBrightness：将低亮度替换为高亮度
 * 2. Hook MiuiDozeBrightnessTimeoutAdapter：取消延迟亮度任务，防止二次调整
 *
 * HyperOS 3 / 4 差异：
 * - 高亮值：OS4 用 DreamServiceUtils.getDefaultHighBrightness；OS3 用 CommonUtils.BRIGHTNESS_ON
 * - 延迟任务字段：OS4 为 mBrightnessAlarmTimeout.cancel()；OS3 为 mBrightnessTask.remove()
 */
public class DozeBrightnessHook {

    private static int sHighBrightness = -1;

    public static void initForAod(XposedInterfaceWrapper xiw, ClassLoader cl) {
        resolveHighBrightness(cl);
        hookDreamServiceUtils(xiw, cl);
        hookBrightnessTimeoutAdapter(xiw, cl);
    }

    /** OS4: getDefaultHighBrightness；OS3: CommonUtils.BRIGHTNESS_ON */
    private static void resolveHighBrightness(ClassLoader cl) {
        try {
            Class<?> dsuCls = Class.forName("com.miui.aod.utils.DreamServiceUtils", false, cl);
            Method getDefaultHigh = dsuCls.getMethod("getDefaultHighBrightness");
            sHighBrightness = (int) getDefaultHigh.invoke(null);
            android.util.Log.i("AodChange", "DozeBrightness high=" + sHighBrightness + " (DreamServiceUtils)");
            return;
        } catch (Throwable ignored) {}
        try {
            Class<?> cu = Class.forName("com.miui.aod.utils.CommonUtils", false, cl);
            Field f = cu.getField("BRIGHTNESS_ON");
            sHighBrightness = f.getInt(null);
            android.util.Log.i("AodChange", "DozeBrightness high=" + sHighBrightness + " (CommonUtils.BRIGHTNESS_ON)");
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "DozeBrightness resolve high fail: " + e);
        }
    }

    private static void hookDreamServiceUtils(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> dsuCls = Class.forName("com.miui.aod.utils.DreamServiceUtils", false, cl);
            Method setBrightness = dsuCls.getDeclaredMethod("setDozeScreenBrightness",
                    Class.forName("android.service.dreams.DreamService", false, cl), int.class);

            xiw.hook(setBrightness).intercept(chain -> {
                int original = (int) chain.getArg(1);
                int replacement = sHighBrightness > 1 ? sHighBrightness : original;
                if (original != replacement) {
                    android.util.Log.i("AodChange", "DozeBrightness: " + original + " -> " + replacement);
                    return chain.proceed(new Object[]{chain.getArg(0), replacement});
                }
                return chain.proceed();
            });
            android.util.Log.i("AodChange", "DozeBrightnessHook(DreamServiceUtils) initialized OK");
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "DozeBrightnessHook(DreamServiceUtils) init fail: " + e);
        }
    }

    private static void hookBrightnessTimeoutAdapter(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> adapterCls = Class.forName("com.miui.aod.doze.MiuiDozeBrightnessTimeoutAdapter", false, cl);
            Method setBrightness = adapterCls.getDeclaredMethod("setDozeScreenBrightness", int.class);
            // OS4: mBrightnessAlarmTimeout；OS3: mBrightnessTask
            final Field timeoutField = findTimeoutField(adapterCls);
            timeoutField.setAccessible(true);

            xiw.hook(setBrightness).intercept(chain -> {
                chain.proceed();
                try {
                    Object timeout = timeoutField.get(chain.getThisObject());
                    if (timeout != null) {
                        cancelTimeout(timeout);
                    }
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "Cancel DozeBrightness timeout fail", t);
                }
                return null;
            });
            android.util.Log.i("AodChange", "DozeBrightnessHook(TimeoutAdapter) initialized OK field="
                    + timeoutField.getName());
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "DozeBrightnessHook(TimeoutAdapter) init fail: " + e);
        }
    }

    private static Field findTimeoutField(Class<?> adapterCls) throws NoSuchFieldException {
        try {
            return adapterCls.getDeclaredField("mBrightnessAlarmTimeout");
        } catch (NoSuchFieldException e) {
            return adapterCls.getDeclaredField("mBrightnessTask");
        }
    }

    /** OS4 AlarmTimeout.cancel()；OS3 CancelableWakeLockTask.remove() */
    private static void cancelTimeout(Object timeout) throws Exception {
        try {
            Method cancel = timeout.getClass().getDeclaredMethod("cancel");
            cancel.setAccessible(true);
            cancel.invoke(timeout);
            android.util.Log.i("AodChange", "Cancelled DozeBrightness timeout (cancel)");
            return;
        } catch (NoSuchMethodException ignored) {}
        Method remove = timeout.getClass().getDeclaredMethod("remove");
        remove.setAccessible(true);
        remove.invoke(timeout);
        android.util.Log.i("AodChange", "Cancelled DozeBrightness timeout (remove)");
    }
}
