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

    // 暂停/恢复元素动画（日历/自定义显示时暂停，避免时钟同步闪烁）
    public static void setPaused(boolean p) {
        sPaused = p;
    }

    public static boolean isAodVisible() {
        return sAodVisible;
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
                    applyVisibility((boolean) chain.getArg(0));
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
                        applyVisibility((boolean) chain.getArg(0));
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

    private static void hookFingerprintClock(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> dss = Class.forName("com.miui.aod.doze.DozeScreenState$1", false, cl);
            Method m = dss.getDeclaredMethod("onFingerprintPressed", boolean.class, boolean.class);
            xiw.hook(m).intercept(chain -> {
                try {
                    Object a0 = chain.getArg(0);
                    android.util.Log.i("AodChange", "FP hook arg0=" + a0 + " cls=" + (a0 == null ? "null" : a0.getClass().getName()));
                    boolean pressing = Boolean.TRUE.equals(a0);
                    android.util.Log.i("AodChange", "FP pressing=" + pressing);
                    applyVisibilityFp(!pressing);
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "fingerprint sync fail", t);
                }
                return chain.proceed();
            });
            android.util.Log.i("AodChange", "Hooked DozeScreenState.onFingerprintPressed");
        } catch (Throwable e) {
            android.util.Log.w("AodChange", "hookFingerprintClock fail: " + e);
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
                    android.util.Log.i("AodChange", "DozeHost.setClockViewVisible called visible=" + visible);
                    if (visible) {
                        applyVisibility(true);
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
