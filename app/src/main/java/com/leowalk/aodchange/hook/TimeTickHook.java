package com.leowalk.aodchange.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.leowalk.aodchange.SettingsHelper;
import io.github.libxposed.api.XposedInterfaceWrapper;

public class TimeTickHook {

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("com.miui.aod.doze.DozeUi", false, cl);
            Field ctxField = cls.getDeclaredField("mContext");
            ctxField.setAccessible(true);
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("scheduleTimeTick")) continue;
                xiw.hook(m).intercept(chain -> {
                    chain.proceed();
                    try {
                        Field f = cls.getDeclaredField("mTimeTicker");
                        f.setAccessible(true);
                        Object ticker = f.get(chain.getThisObject());
                        if (ticker != null) {
                            android.content.Context ctx = (android.content.Context) ctxField.get(chain.getThisObject());
                            Method sched = ticker.getClass().getDeclaredMethod("schedule", long.class, int.class);
                            sched.invoke(ticker, SettingsHelper.get(ctx, "realtime_tick", true) ? 1000L : 60000L, 2);
                        }
                    } catch (Exception ignored) {}
                    return null;
                });
                return;
            }
        } catch (Exception ignored) {}
    }
}
