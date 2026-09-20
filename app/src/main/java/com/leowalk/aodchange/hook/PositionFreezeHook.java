package com.leowalk.aodchange.hook;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterfaceWrapper;

/**
 * 钉死 AOD 时钟位置，阻止 doze/入场动画/防烧屏把 clock_container 整体下移。
 * 只动 translation，绝不 cancel View.animate / Folme.clean，以免打断入场 alpha 导致时钟消失。
 */
public class PositionFreezeHook {

    private static final String CLASS_NAME = "com.miui.aod.AODUpdatePositionController";
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static final long[] REAPPLY_DELAYS_MS = {50L, 300L, 1000L};

    private static volatile boolean sInited = false;
    private static volatile float sFrozenY = 0f;
    private static volatile View sClockContainer;
    private static volatile boolean sApplyingFreeze = false;
    private static Field sFClockTranslation;
    private static Field sFContainer;
    private static ClassLoader sCl;

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) throws Exception {
        if (sInited) {
            D("INIT skip duplicate");
            return;
        }
        sInited = true;
        sCl = cl;
        D("INIT positionFreezeHook start");

        Class<?> cls = Class.forName(CLASS_NAME, false, cl);
        Method m1 = cls.getDeclaredMethod("updatePosition",
                View.class, boolean.class, boolean.class, boolean.class);
        Method m2 = cls.getDeclaredMethod("updateTranslation",
                boolean.class, int.class, float.class);
        Method mInitParams = cls.getDeclaredMethod("initParams",
                android.content.Context.class, int.class);
        Method mSetCanMove = cls.getDeclaredMethod("setCanMove", boolean.class);

        Field fC = cls.getDeclaredField("mCanMove"); fC.setAccessible(true);
        Field fM = cls.getDeclaredField("mAodMoveCurrent"); fM.setAccessible(true);
        Field fTV = cls.getDeclaredField("mTargetView"); fTV.setAccessible(true);

        sFrozenY = 0f;

        xiw.hook(mInitParams).intercept(chain -> {
            chain.proceed();
            Object thiz = chain.getThisObject();
            fC.setBoolean(thiz, false);
            fM.setInt(thiz, 0);
            return null;
        });

        xiw.hook(mSetCanMove).intercept(chain ->
                chain.proceed(new Object[]{false}));

        xiw.hook(m1).intercept(chain -> {
            Object thiz = chain.getThisObject();
            fC.setBoolean(thiz, false);
            fM.setInt(thiz, 0);
            chain.proceed();
            View target = (View) fTV.get(thiz);
            freezeTranslation(target, "updatePosition", true);
            return null;
        });

        xiw.hook(m2).intercept(chain ->
                chain.proceed(new Object[]{chain.getArg(0), 0, sFrozenY}));

        hookDozeHost(xiw, cl);
        D("INIT positionFreezeHook done frozenY=" + sFrozenY);
    }

    private static void hookDozeHost(XposedInterfaceWrapper xiw, ClassLoader cl) {
        Class<?> host;
        try {
            host = Class.forName("com.miui.aod.DozeHost", false, cl);
            sFClockTranslation = host.getDeclaredField("mClockTranslation");
            sFClockTranslation.setAccessible(true);
            sFContainer = host.getDeclaredField("mContainer");
            sFContainer.setAccessible(true);
        } catch (Throwable t) {
            D("FAIL find DozeHost: " + t);
            android.util.Log.w("AodChange", "find DozeHost fail", t);
            return;
        }

        try {
            Method mUpd = host.getDeclaredMethod("updateClockTranslation", Bundle.class);
            xiw.hook(mUpd).intercept(chain -> {
                Object thiz = chain.getThisObject();
                try {
                    sFClockTranslation.setFloat(thiz, sFrozenY);
                } catch (Throwable ignored) {}
                View c = containerOf(thiz);
                D("HIT updateClockTranslation -> freeze c=" + (c != null));
                freezeTranslation(c, "updateClockTranslation", true);
                scheduleReapply(c);
                return null;
            });
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "updateClockTranslation hook fail", t);
        }

        try {
            Method mEnter = host.getDeclaredMethod("startEnterAnim",
                    android.animation.AnimatorListenerAdapter.class, boolean.class);
            xiw.hook(mEnter).intercept(chain -> {
                Object thiz = chain.getThisObject();
                try {
                    sFClockTranslation.setFloat(thiz, sFrozenY);
                } catch (Throwable ignored) {}
                Object ret = chain.proceed();
                View c = containerOf(thiz);
                D("HIT startEnterAnim c=" + (c != null)
                        + " ty=" + (c != null ? c.getTranslationY() : -999f)
                        + " a=" + (c != null ? c.getAlpha() : -1f));
                // 入场刚把 alpha 动画跑起来：只钳 translation，绝不 cancel animate/Folme 全量
                freezeTranslation(c, "startEnterAnim", true);
                scheduleReapply(c);
                return ret;
            });
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "startEnterAnim hook fail", t);
        }

        try {
            Class<?> rid = Class.forName("com.miui.aod.R$id", false, cl);
            final int clockContainerId = rid.getField("clock_container").getInt(null);
            Method stY = View.class.getDeclaredMethod("setTranslationY", float.class);
            xiw.hook(stY).intercept(chain -> {
                View v = (View) chain.getThisObject();
                if (v != null && v.getId() == clockContainerId) {
                    sClockContainer = v;
                    float val = ((Float) chain.getArg(0)).floatValue();
                    if (Math.abs(val - sFrozenY) < 0.5f) {
                        return chain.proceed();
                    }
                    D("HIT redirect setTranslationY " + val + " -> " + sFrozenY);
                    return chain.proceed(new Object[]{sFrozenY});
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "clamp clock_container setTranslationY fail", t);
        }
    }

    private static View containerOf(Object host) {
        try {
            View c = (View) sFContainer.get(host);
            if (c != null) sClockContainer = c;
            return c;
        } catch (Throwable t) {
            return sClockContainer;
        }
    }

    /**
     * @param cancelTransAnim 是否只取消 Folme 的 TRANSLATION_X/Y（不影响 alpha）
     */
    private static void freezeTranslation(View v, String reason, boolean cancelTransAnim) {
        if (v == null) return;
        sClockContainer = v;
        sApplyingFreeze = true;
        try {
            if (cancelTransAnim) cancelFolmeTranslationOnly(v);
            if (Math.abs(v.getTranslationX()) > 0.5f) v.setTranslationX(0f);
            if (Math.abs(v.getTranslationY() - sFrozenY) > 0.5f) {
                D("freeze(" + reason + ") ty " + v.getTranslationY() + " -> " + sFrozenY
                        + " alpha=" + v.getAlpha() + " vis=" + v.getVisibility());
                v.setTranslationY(sFrozenY);
            }
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "freezeTranslation fail", t);
        } finally {
            sApplyingFreeze = false;
        }
        notifyFollow();
    }

    private static void scheduleReapply(View v) {
        if (v == null) return;
        final View target = v;
        for (long delay : REAPPLY_DELAYS_MS) {
            sMain.postDelayed(() -> {
                // 重钳时不再 cancel 动画，只纠正跑偏的 ty；并兜底恢复被误杀的 alpha
                freezeTranslation(target, "reapply@" + delay, false);
                restoreAlphaIfNeeded(target, delay);
            }, delay);
        }
    }

    /** 若可见但 alpha 仍接近 0（入场动画曾被打断），强制拉回可见 */
    private static void restoreAlphaIfNeeded(View v, long delay) {
        try {
            if (v == null) return;
            if (v.getVisibility() == View.VISIBLE && v.getAlpha() < 0.05f && delay >= 300L) {
                D("restoreAlpha ty=" + v.getTranslationY() + " a=" + v.getAlpha());
                v.setAlpha(1f);
            }
        } catch (Throwable ignored) {}
    }

    /** 只取消位移相关 Folme，保留 alpha 入场 */
    private static void cancelFolmeTranslationOnly(View v) {
        try {
            ClassLoader cl = sCl != null ? sCl : v.getClass().getClassLoader();
            Class<?> folme = Class.forName("miuix.animation.Folme", false, cl);
            Method useAt = folme.getMethod("useAt", View[].class);
            Object agent = useAt.invoke(null, (Object) new View[]{v});
            if (agent == null) return;

            Class<?> vp = Class.forName("miuix.animation.property.ViewProperty", false, cl);
            Object ty = vp.getField("TRANSLATION_Y").get(null);
            Object tx = vp.getField("TRANSLATION_X").get(null);

            Class<?> floatProp = Class.forName("miuix.animation.property.FloatProperty", false, cl);
            Object arr = Array.newInstance(floatProp, 2);
            Array.set(arr, 0, ty);
            Array.set(arr, 1, tx);

            for (Method m : agent.getClass().getMethods()) {
                if (!"cancel".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && pts[0].isArray()) {
                    m.invoke(agent, arr);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void notifyFollow() {
        try {
            LyricHook.onClockPositionChanged();
        } catch (Throwable ignored) {}
    }

    private static void D(String msg) {
        try {
            String[] paths = {"/data/local/tmp/aod_dbg.txt",
                    "/storage/emulated/0/aod_dbg.txt",
                    "/storage/emulated/0/Download/aod_dbg.txt"};
            for (String p : paths) {
                try {
                    java.io.FileOutputStream os = new java.io.FileOutputStream(p, true);
                    os.write((System.currentTimeMillis() + " " + msg + "\n").getBytes("UTF-8"));
                    os.close();
                    return;
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {}
    }
}
