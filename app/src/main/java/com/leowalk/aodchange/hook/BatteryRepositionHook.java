package com.leowalk.aodchange.hook;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterfaceWrapper;

public class BatteryRepositionHook {

    private static final java.util.Set<android.widget.TextView> sPercentWatched = new java.util.HashSet<>();

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> av = Class.forName("com.miui.aod.AODView", false, cl);
            Method hm = av.getDeclaredMethod("handleUpdateView", boolean.class, boolean.class, boolean.class);
            xiw.hook(hm).intercept(chain -> {
                chain.proceed();
            ViewGroup root = (ViewGroup) chain.getThisObject();
            // 指纹过渡中延迟执行，避免与 system_server 显示/亮度/窗口重排高峰碰撞
            com.leowalk.aodchange.hook.ElementSyncHook.deferDuringFingerprint(() -> {
                if (com.leowalk.aodchange.SettingsHelper.get(root.getContext(), "battery_reposition", true)) {
                    move(root);
                }
            });
            return null;
            });
        } catch (Exception ignored) {}
    }

    private static void move(ViewGroup root) {
        try {
            float d = root.getResources().getDisplayMetrics().density;
            int cid = root.getResources().getIdentifier("battery_step_container", "id", "com.miui.aod");
            View container = root.findViewById(cid);
            int iid = root.getResources().getIdentifier("aod_battery_icon", "id", "com.miui.aod");
            View icon = root.findViewById(iid);
            int did = root.getResources().getIdentifier("aod_battery_digital", "id", "com.miui.aod");
            View text = root.findViewById(did);

            View main = container != null ? container : (icon != null ? icon : text);
            if (main == null) return;

            if (main.getTag() != null) return;
            main.setTag("moved");

            ViewGroup pr = (ViewGroup) main.getParent();
            ViewGroup.LayoutParams saved = main.getLayoutParams();
            if (pr != null) pr.removeView(main);

            LinearLayout row = new LinearLayout(root.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(main, new LinearLayout.LayoutParams(saved.width, saved.height));

            if (text != null && text != main && !isChildOf(text, main)) {
                ViewGroup pt = (ViewGroup) text.getParent();
                ViewGroup.LayoutParams st = text.getLayoutParams();
                if (pt != null) pt.removeView(text);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(st.width, st.height);
                tlp.leftMargin = 0;
                row.addView(text, tlp);
            }
            if (icon != null && icon != main && !isChildOf(icon, main)) {
                ViewGroup pi = (ViewGroup) icon.getParent();
                ViewGroup.LayoutParams si = icon.getLayoutParams();
                if (pi != null) pi.removeView(icon);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(si.width, si.height);
                ilp.leftMargin = 0;
                row.addView(icon, 0, ilp);
            }

            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.gravity = Gravity.TOP | Gravity.END;
            flp.topMargin = (int)(40 * d);
            flp.rightMargin = (int)(20 * d);
            root.addView(row, flp);
            ElementSyncHook.register(row);

            if (text instanceof android.widget.TextView) {
                android.widget.TextView tv = (android.widget.TextView) text;
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tv.getPaint().setFakeBoldText(true);
                // 百分号缩小为数字的一半（系统更新文本后重新应用）
                // 只注册一次监听器，避免 handleUpdateView 反复触发时叠加
                if (sPercentWatched.add(tv)) {
                    tv.addTextChangedListener(new android.text.TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                        @Override public void onTextChanged(CharSequence s, int st, int c, int a) {}
                        @Override public void afterTextChanged(android.text.Editable e) {
                            applyPercentHalf(tv);
                        }
                    });
                }
                applyPercentHalf(tv);
            }
        } catch (Exception ignored) {}
    }

    private static boolean sPercentApplying = false;

    private static void applyPercentHalf(android.widget.TextView tv) {
        try {
            if (sPercentApplying) return;
            String t = tv.getText().toString();
            int idx = t.indexOf('%');
            if (idx < 0) return;
            sPercentApplying = true;
            try {
                android.text.SpannableString ss = new android.text.SpannableString(t);
                ss.setSpan(new android.text.style.RelativeSizeSpan(0.75f), idx, idx + 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(ss);
            } finally {
                sPercentApplying = false;
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isChildOf(View child, View parent) {
        ViewGroup p = (ViewGroup) child.getParent();
        while (p != null) {
            if (p == parent) return true;
            p = (ViewGroup) p.getParent();
        }
        return false;
    }
}
