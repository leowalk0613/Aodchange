package com.leowalk.aodchange.hook;

import android.view.View;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterfaceWrapper;

public class FocusRowHook {

    public static final List<FocusInfo> sList = new ArrayList<>();
    private static volatile Object sLastDozeHost;
    private static volatile long sLastHideTime;

    public static class FocusInfo {
        public String pkg;
        public String title;
        public String className;
        public String key;
        public long time;
    }

    /** 隐藏开关：默认开启（aodchange 渲染歌词时隐藏系统焦点通知容器，避免与 LyricFocus 自绘互抢） */
    public static boolean isHideEnabled(android.content.Context ctx) {
        try {
            return com.leowalk.aodchange.SettingsHelper.get(ctx, "focus_hide_enabled", true);
        } catch (Throwable t) {
            return true;
        }
    }

    /** 轮询兜底隐藏：LyricHook 渲染歌词期间周期性调用，弥补 diff 回调不触发时容器残留显示的问题 */
    public static void hideIfActive(android.content.Context ctx) {
        try {
            if (!isHideEnabled(ctx)) return;
            Object host = sLastDozeHost;
            if (host == null) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - sLastHideTime < 1000) return;
            sLastHideTime = now;
            hide(host);
        } catch (Throwable ignored) {}
    }

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("com.miui.aod.notification.DiffDispatch", false, cl);
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("diff")) continue;
                if (m.getParameterTypes().length < 4) continue;
                xiw.hook(m).intercept(chain -> {
                    List list = (List) chain.getArgs().get(0);
                    Object dozeHost = chain.getArgs().get(1);
                    sLastDozeHost = dozeHost;
                    capture(list);
                    chain.proceed();
                    hide(dozeHost);
                    return null;
                });
                return;
            }
        } catch (Exception ignored) {}
    }

    private static void capture(List list) {
        synchronized (sList) {
            sList.clear();
            if (list == null) return;
            for (Object item : list) {
                try {
                    FocusInfo fi = new FocusInfo();
                    fi.pkg = getField(item, "mPackageName");
                    fi.title = getField(item, "mTitle");
                    fi.className = getField(item, "mClassName");
                    fi.key = getField(item, "mKey");
                    fi.time = getLong(item, "mPostTime");
                    if (fi.pkg != null && !fi.pkg.isEmpty()) sList.add(fi);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void hide(Object dozeHost) {
        try {
            Method finder = dozeHost.getClass().getMethod("findFocusNotificationContainer");
            LinearLayout container = (LinearLayout) finder.invoke(dozeHost);
            if (container != null) {
                container.setVisibility(View.GONE);
                container.setAlpha(0f);
            }
        } catch (Exception ignored) {}
    }

    private static String getField(Object obj, String name) {
        try { Field f = obj.getClass().getField(name); Object v = f.get(obj); return v != null ? v.toString() : null; }
        catch (Exception e) { return null; }
    }

    private static long getLong(Object obj, String name) {
        try { Field f = obj.getClass().getField(name); Long v = (Long) f.get(obj); return v != null ? v : 0L; }
        catch (Exception e) { return 0L; }
    }
}
