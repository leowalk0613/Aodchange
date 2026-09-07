package com.leowalk.aodchange.hook;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterfaceWrapper;

public class ElementSyncHook {

    private static final List<View> sElements = new ArrayList<>();
    private static volatile boolean sAodVisible = true;
    private static volatile boolean sPaused = false;
    private static volatile boolean sFingerprintPressed = false;
    private static final android.os.Handler sMainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final List<Runnable> sDeferredWork = new ArrayList<>();
    private static Runnable sOnAodVisible = null;

    /** AOD 从隐藏变为可见后调用：让 LyricHook 重新评估 mask 显隐，避免暂停时残留 */
    public static void setOnAodVisible(Runnable r) { sOnAodVisible = r; }

    /**
     * 指纹过渡（按压→解锁）中提交的工作延迟到过渡结束后执行。
     * 过渡瞬间 system_server 有显示/亮度/窗口重排的高峰（易触发框架死锁），
     * 模块的视图构建/渲染/binder 调用一律避开该窗口；非过渡时立即执行。
     */
    public static void deferDuringFingerprint(Runnable r) {
        if (sFingerprintPressed) {
            synchronized (sDeferredWork) {
                sDeferredWork.add(r);
            }
        } else {
            r.run();
        }
    }

    private static void runDeferredWork() {
        sMainHandler.post(() -> {
            List<Runnable> work;
            synchronized (sDeferredWork) {
                work = new ArrayList<>(sDeferredWork);
                sDeferredWork.clear();
            }
            // 分批执行：每批最多 2 个、间隔一帧，避免解锁瞬间所有延迟任务突发占用主线程
            for (int i = 0; i < work.size(); i++) {
                final Runnable r = work.get(i);
                sMainHandler.postDelayed(() -> {
                    try { r.run(); } catch (Throwable ignored) {}
                }, (i / 2) * 16L);
            }
        });
    }

    // 暂停/恢复元素动画（日历/自定义显示时暂停，避免时钟同步闪烁）
    public static void setPaused(boolean p) {
        sPaused = p;
    }

    public static boolean isAodVisible() {
        return sAodVisible;
    }

    /** 指纹正在按压中（同步标志，供 handleUpdateView 等 hook 跳过重活） */
    public static boolean isFingerprintPressed() {
        return sFingerprintPressed;
    }

    public static void register(View v) {
        if (v == null) return;
        synchronized (sElements) {
            if (!sElements.contains(v)) sElements.add(v);
        }
    }

    public static void unregister(View v) {
        synchronized (sElements) {
            sElements.remove(v);
        }
    }

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("com.miui.aod.DozeHost", false, cl);
            hookMaskAnimation(xiw, cl);
            hookClockVisibility(xiw, cl);
            hookFingerprintClock(xiw, cl);
            Method m1 = cls.getDeclaredMethod("setAodVisibility", boolean.class);
            xiw.hook(m1).intercept(chain -> {
                try {
                    boolean visible = (boolean) chain.getArg(0);
                    sMainHandler.post(() -> {
                        try { applyVisibility(visible); } catch (Throwable ignored) {}
                    });
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "setAodVisibility(1) sync fail", t);
                }
                return chain.proceed();
            });
            android.util.Log.i("AodChange", "Hooked DozeHost.setAodVisibility(boolean)");

            Method m2 = null;
            try {
                Class<?> cb = Class.forName("com.miui.aod.DozeHost$Callback", false, cl);
                m2 = cls.getDeclaredMethod("setAodVisibility", boolean.class, cb, boolean.class);
            } catch (Throwable ignored) {}
            if (m2 != null) {
                xiw.hook(m2).intercept(chain -> {
                    try {
                        boolean visible = (boolean) chain.getArg(0);
                        sMainHandler.post(() -> {
                            try { applyVisibility(visible); } catch (Throwable ignored) {}
                        });
                    } catch (Throwable t) {
                        android.util.Log.w("AodChange", "setAodVisibility(3) sync fail", t);
                    }
                    return chain.proceed();
                });
                android.util.Log.i("AodChange", "Hooked DozeHost.setAodVisibility(boolean,Callback,boolean)");
            }
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "ElementSyncHook init fail: " + e);
        }
    }

    private static final Runnable sClearFingerprintRunnable = () -> {
        sFingerprintPressed = false;
        // 释放回调可能丢失（解锁成功直接进桌面）：兜底执行延迟工作 + 恢复元素显隐
        runDeferredWork();
        sMainHandler.post(() -> {
            try { applyVisibilityFp(true); } catch (Throwable ignored) {}
        });
    };

    private static void hookFingerprintClock(XposedInterfaceWrapper xiw, ClassLoader cl) {
        // OS3/OS4 均有 DozeHost.fireFingerprintPressed（匿名内部类编号 $1/$2 不一致，不依赖）
        try {
            Class<?> host = Class.forName("com.miui.aod.DozeHost", false, cl);
            Method m = host.getDeclaredMethod("fireFingerprintPressed", boolean.class, boolean.class);
            xiw.hook(m).intercept(chain -> {
                onFingerprintSignal(Boolean.TRUE.equals(chain.getArg(0)));
                return chain.proceed();
            });
            android.util.Log.i("AodChange", "Hooked DozeHost.fireFingerprintPressed");
            return;
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "fireFingerprintPressed hook fail, fallback: " + e);
        }
        // 兜底：按方法探测 DozeScreenState 内部类（OS3=$1，OS4=$2）
        for (String name : new String[]{
                "com.miui.aod.doze.DozeScreenState$1",
                "com.miui.aod.doze.DozeScreenState$2",
                "com.miui.aod.doze.DozeScreenState$3"}) {
            try {
                Class<?> dss = Class.forName(name, false, cl);
                Method m = dss.getDeclaredMethod("onFingerprintPressed", boolean.class, boolean.class);
                xiw.hook(m).intercept(chain -> {
                    onFingerprintSignal(Boolean.TRUE.equals(chain.getArg(0)));
                    return chain.proceed();
                });
                android.util.Log.i("AodChange", "Hooked " + name + ".onFingerprintPressed");
                return;
            } catch (Throwable ignored) {}
        }
        android.util.Log.w("AodChange", "hookFingerprintClock: no fingerprint callback found");
    }

    private static void onFingerprintSignal(boolean pressing) {
        try {
            // 同步置位标志（轻量），供 handleUpdateView 跳过重活；
            // 显隐同步仍延迟到下一帧，避免阻塞解锁关键路径
            sFingerprintPressed = pressing;
            if (pressing) {
                // 兜底：2s 后若未收到释放回调则清除标志，避免永久跳过渲染
                sMainHandler.removeCallbacks(sClearFingerprintRunnable);
                sMainHandler.postDelayed(sClearFingerprintRunnable, 2000);
            } else {
                sMainHandler.removeCallbacks(sClearFingerprintRunnable);
                // 释放：执行过渡期间延迟的视图构建/渲染
                runDeferredWork();
            }
            sMainHandler.post(() -> {
                try { applyVisibilityFp(!pressing); } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "fingerprint sync fail", t);
        }
    }

    private static void hookMaskAnimation(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> av = Class.forName("com.miui.aod.AODView", false, cl);
            Method m = av.getDeclaredMethod("startMaskAnimation");
            java.lang.reflect.Field f = av.getDeclaredField("mMaskForSuperWallpaper");
            f.setAccessible(true);
            xiw.hook(m).intercept(chain -> {
                try {
                    Object thiz = chain.getThisObject();
                    View mask = (View) f.get(thiz);
                    if (mask != null) {
                        mask.animate().cancel();
                        mask.setAlpha(0f);
                        mask.setVisibility(View.GONE);
                    }
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "mask skip fail", t);
                }
                return null;
            });
            android.util.Log.i("AodChange", "Hooked AODView.startMaskAnimation");
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "hookMaskAnimation fail: " + e);
        }
    }

    private static void hookClockVisibility(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("com.miui.aod.DozeHost", false, cl);
            Method m = cls.getDeclaredMethod("setClockViewVisible", boolean.class);
            xiw.hook(m).intercept(chain -> {
                try {
                    boolean visible = (boolean) chain.getArg(0);
                    if (visible) {
                        sMainHandler.post(() -> {
                            try { applyVisibility(true); } catch (Throwable ignored) {}
                        });
                    }
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "setClockViewVisible sync fail", t);
                }
                return chain.proceed();
            });
            android.util.Log.i("AodChange", "Hooked DozeHost.setClockViewVisible");
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "hookClockVisibility fail: " + e);
        }
    }

    public static void applyVisibility(boolean visible) {
        sAodVisible = visible;
        synchronized (sElements) {
            for (View v : sElements) {
                try {
                    if (v == null) continue;
                    if (sPaused) continue;
                    applyElement(v, visible);
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "ElementSync apply fail", t);
                }
            }
        }
        // AOD 变为可见后：通知 LyricHook 重新评估 mask 显隐
        // applyVisibility(true) 会把所有注册元素设为 VISIBLE，包括 sMask；
        // 但暂停时 sMask 应为 GONE，此处回调让它立即修正，避免残留
        if (visible && sOnAodVisible != null) {
            try { sOnAodVisible.run(); } catch (Throwable ignored) {}
        }
    }

    // 指纹触发的显隐：始终生效（不受 paused 影响）
    public static void applyVisibilityFp(boolean visible) {
        sAodVisible = visible;
        android.util.Log.i("AodChange", "ElementSync FP visible=" + visible + " elements=" + sElements.size());
        synchronized (sElements) {
            for (View v : sElements) {
                try {
                    if (v == null) continue;
                    applyElement(v, visible);
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "ElementSync FP fail", t);
                }
            }
        }
        if (visible && sOnAodVisible != null) {
            try { sOnAodVisible.run(); } catch (Throwable ignored) {}
        }
    }

    private static void applyElement(View v, boolean visible) {
        v.animate().cancel();
        if (visible) {
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1f).setDuration(150L).start();
        } else {
            v.animate().alpha(0f).setDuration(80L)
                    .withEndAction(() -> {
                        try {
                            if (!sAodVisible) v.setVisibility(View.GONE);
                        } catch (Throwable ignored) {}
                    }).start();
        }
    }
}
