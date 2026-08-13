package com.leowalk.aodchange.hook;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterfaceWrapper;

public class PositionFreezeHook {

    private static final String CLASS_NAME = "com.miui.aod.AODUpdatePositionController";

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) throws Exception {
        Class<?> cls = Class.forName(CLASS_NAME, false, cl);
        Method m1 = cls.getDeclaredMethod("updatePosition",
                View.class, boolean.class, boolean.class, boolean.class);
        Method m2 = cls.getDeclaredMethod("updateTranslation",
                boolean.class, int.class, float.class);
        Method mInitParams = cls.getDeclaredMethod("initParams",
                android.content.Context.class, int.class);
        Method mSetCanMove = cls.getDeclaredMethod("setCanMove", boolean.class);

        Field fC  = cls.getDeclaredField("mCanMove"); fC.setAccessible(true);
        Field fM  = cls.getDeclaredField("mAodMoveCurrent"); fM.setAccessible(true);
        Field fCT = cls.getDeclaredField("mContext"); fCT.setAccessible(true);
        Field fCH = cls.getDeclaredField("mCutoutHeight"); fCH.setAccessible(true);
        Field fTV = cls.getDeclaredField("mTargetView"); fTV.setAccessible(true);

        xiw.hook(mInitParams).intercept(chain -> {
            chain.proceed();
            Object thiz = chain.getThisObject();
            fC.setBoolean(thiz, false);
            fM.setInt(thiz, 0);
            return null;
        });

        xiw.hook(mSetCanMove).intercept(chain -> {
            return chain.proceed(new Object[]{false});
        });

        xiw.hook(m1).intercept(chain -> {
            Object thiz = chain.getThisObject();
            fC.setBoolean(thiz, false);
            fM.setInt(thiz, 0);
            chain.proceed();
            View target = (View) fTV.get(thiz);
            if (target != null) {
                int cutout = fCH.getInt(thiz);
                android.content.Context ctx = (android.content.Context) fCT.get(thiz);
                target.setTranslationX(0);
                target.setTranslationY(cutout + dp(ctx, 16));
            }
            return null;
        });

        xiw.hook(m2).intercept(chain -> {
            Object thiz = chain.getThisObject();
            int cutout = fCH.getInt(thiz);
            android.content.Context ctx = (android.content.Context) fCT.get(thiz);
            return chain.proceed(new Object[]{chain.getArg(0), 0, (float)(cutout + dp(ctx, 16))});
        });
    }

    private static int dp(android.content.Context c, int dp) {
        return (int) (dp * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
