package com.leowalk.aodchange.hook;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import io.github.libxposed.api.XposedInterfaceWrapper;

public class NotificationCardHook {

    private static final Uri URI = Uri.parse("content://com.leowalk.aodchange.notifications");
    private static FrameLayout sOverlay;
    /** 业务期望显隐（多行 mask 打开时为 false）；与 ElementSync 条件期望对齐，避免 AOD 同步时闪回 */
    private static volatile boolean sOverlayWanted = true;

    public static void setOverlayVisible(boolean visible) {
        sOverlayWanted = visible;
        try {
            if (sOverlay != null) {
                ElementSyncHook.setDesiredVisible(sOverlay, visible);
            }
        } catch (Exception ignored) {}
    }
    private static String sLastCardsJson;
    private static String sLastFocusJson;
    private static List<Card> sCards;
    private static List<Card> sFocusCards;

    private static class Card { String pkg, title, text; long time; int priority; }

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) throws Exception {
        Class<?> av = Class.forName("com.miui.aod.AODView", false, cl);
        Method hm = av.getDeclaredMethod("handleUpdateView", boolean.class, boolean.class, boolean.class);
        xiw.hook(hm).intercept(chain -> {
            chain.proceed();
            try {
                ViewGroup root = (ViewGroup) chain.getThisObject();
                // setup 有守卫，必须执行保证视图树完整；
                // 指纹过渡中延迟执行，避免与 system_server 显示/亮度/窗口重排高峰碰撞
                com.leowalk.aodchange.hook.ElementSyncHook.deferDuringFingerprint(() -> {
                    setup(root);
                    // 解锁/AOD 过渡瞬间 system_server 有 binder 高峰：
                    // 延迟 200ms 执行刷新，避免与系统事务风暴叠加，且 1s 节流防高频
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - sLastRefresh >= 1000) {
                        sLastRefresh = now;
                        final ViewGroup fRoot = root;
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                refresh(fRoot);
                            } catch (Throwable ignored) {}
                        }, 200);
                    }
                });
            } catch (Throwable t) {
                android.util.Log.w("AodChange", "handleUpdateView refresh fail", t);
            }
            return null;
        });
    }

    private static long sLastRefresh = 0;

    private static void setup(ViewGroup root) {
        if (sOverlay != null && sOverlay.getParent() == root) return;
        if (sOverlay != null) {
            try { ViewGroup p = (ViewGroup) sOverlay.getParent(); if (p != null) p.removeView(sOverlay); }
            catch (Exception ignored) {}
        }
        sOverlay = new FrameLayout(root.getContext());
        sOverlay.setClipChildren(false); sOverlay.setClipToPadding(false);
        root.addView(sOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ElementSyncHook.setDesiredVisible(sOverlay, sOverlayWanted);
        int iid = root.getResources().getIdentifier("icons", "id", "com.miui.aod");
        View orig = root.findViewById(iid);
        if (orig != null) orig.setVisibility(View.GONE);
    }

    private static void refresh(ViewGroup root) {
        if (sOverlay == null) return;
        ViewGroup r = root != null ? root : (ViewGroup) sOverlay.getParent();
        if (r == null) return;
        boolean hasRegular = load(r);
        sOverlay.removeAllViews();
        float d = r.getResources().getDisplayMetrics().density;
        int w = r.getWidth(); if (w <= 0) w = r.getResources().getDisplayMetrics().widthPixels;
        int cw = w - (int)(64 * d);

        List<Card> focusCards = loadFocus(r);
        if (focusCards != null && !focusCards.isEmpty()) addFocusCards(r, d, cw, focusCards);

        if (!hasRegular) return;

        LinkedHashMap<String, Card> latest = new LinkedHashMap<>();
        HashMap<String, Integer> counts = new HashMap<>();
        for (Card c : sCards) {
            // 同一应用且标题相同才合并
            String key = c.pkg + "|" + c.title;
            Integer n = counts.get(key); counts.put(key, n == null ? 1 : n + 1);
            Card old = latest.get(key);
            if (old == null || c.time >= old.time) latest.put(key, c);
        }
        // 按重要性优先（priority 降序），其次新通知优先（time 降序）
        List<Card> cards = new ArrayList<>(latest.values());
        cards.sort((a, b) -> {
            if (a.priority != b.priority) return Integer.compare(b.priority, a.priority);
            return Long.compare(b.time, a.time);
        });
        LinearLayout cont = new LinearLayout(r.getContext());
        cont.setOrientation(LinearLayout.VERTICAL);
        int maxCount = com.leowalk.aodchange.SettingsHelper.getInt(r.getContext(), "notif_max_count", 3);
        int drawn = 0;
        for (int i = 0; i < cards.size() && drawn < maxCount; i++) {
            Card c = cards.get(i);
            if (c.title == null || c.title.isEmpty()) continue;
            String txt = c.text;
            Integer cnt = counts.get(c.pkg + "|" + c.title);
            if (cnt != null && cnt > 1) txt = "...\u7b49" + cnt + "\u6761\u901a\u77e5";
            Drawable icon = null;
            try { icon = r.getContext().getPackageManager().getApplicationIcon(c.pkg); } catch (Exception ignored) {}
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int)(6 * d);
            cont.addView(buildCard(r.getContext(), c.title, txt, icon, c.time, d), lp);
            drawn++;
        }
        // 超出显示个数（或 0 个时）全部折叠进图标行
        if (drawn >= maxCount && cards.size() > maxCount) {
            LinearLayout ir = new LinearLayout(r.getContext());
            String ig = com.leowalk.aodchange.SettingsHelper.getString(r.getContext(), "icon_row_gravity", "left");
            int irGrav = "center".equals(ig) ? Gravity.CENTER_HORIZONTAL
                    : "right".equals(ig) ? Gravity.RIGHT : Gravity.LEFT;
            ir.setOrientation(LinearLayout.HORIZONTAL);
            ir.setGravity(irGrav | Gravity.CENTER_VERTICAL);
            int max = Math.min(cards.size() - maxCount, 5);
            for (int i = maxCount; i < maxCount + max; i++) {
                try {
                    Drawable ic = r.getContext().getPackageManager().getApplicationIcon(cards.get(i).pkg);
                    if (ic != null) { ImageView iv = new ImageView(r.getContext()); iv.setImageDrawable(ic);
                        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams((int)(20*d),(int)(20*d));
                        vlp.setMargins((int)(3*d), 0, (int)(3*d), 0);
                        ir.addView(iv, vlp); }
                } catch (Exception ignored) {}
            }
            if (cards.size() - maxCount > 5) {
                TextView dots = new TextView(r.getContext()); dots.setText("..."); dots.setTextSize(18);
                dots.setTextColor(Color.argb(200,255,255,255)); ir.addView(dots);
            }
            // 圆角背景（线框风格）
            GradientDrawable irBg = new GradientDrawable();
            irBg.setColor(Color.argb(20,255,255,255));
            irBg.setStroke((int)(1f*d), Color.argb(60,255,255,255));
            irBg.setCornerRadius(12*d);
            ir.setPadding((int)(10*d), (int)(4*d), (int)(10*d), (int)(4*d));
            ir.setBackground(irBg);
            LinearLayout.LayoutParams irlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            irlp.gravity = irGrav;
            ir.setLayoutParams(irlp);
            cont.addView(ir);
        }
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;         fp.bottomMargin = (int)(180 * d);
        sOverlay.addView(cont, fp);
    }

    private static void addFocusCards(ViewGroup r, float d, int cw, List<Card> focusCards) {
        LinearLayout frow = new LinearLayout(r.getContext());
        frow.setOrientation(LinearLayout.HORIZONTAL);
        frow.setGravity(Gravity.CENTER);
        for (int i = 0; i < focusCards.size() && i < 3; i++) {
            Card c = focusCards.get(i);
            LinearLayout fc = new LinearLayout(r.getContext());
            fc.setOrientation(LinearLayout.HORIZONTAL);
            fc.setGravity(Gravity.CENTER_VERTICAL);
            fc.setPadding((int)(12*d),(int)(8*d),(int)(12*d),(int)(8*d));
            // 焦点通知卡片取专辑色背景（浅色半透明）+ 白描边
            // 先获取图标（自定义或 app 图标），用于取色
            Drawable icon = null;
            int customRes = resolveFocusIconRes(c.pkg, c.title);
            if (customRes != 0) {
                try {
                    android.content.Context mc = r.getContext().createPackageContext("com.leowalk.aodchange", 0);
                    icon = mc.getResources().getDrawable(customRes, null);
                } catch (Exception ignored) {}
            }
            if (icon == null) {
                try { icon = r.getContext().getPackageManager().getApplicationIcon(c.pkg); } catch (Exception ignored) {}
            }
            // 焦点卡片背景按图标主色取色（浅色半透明）+ 白描边
            int iconColor = tintColor(icon);
            float[] hb = new float[3];
            android.graphics.Color.colorToHSV(iconColor, hb);
            hb[1] = Math.min(hb[1], 0.5f);
            hb[2] = 1f;
            int base = android.graphics.Color.HSVToColor(hb);
            GradientDrawable bg = com.leowalk.aodchange.CardRenderer.background(
                    r.getContext(), com.leowalk.aodchange.CardStyle.P_FOCUS, base, 14f, d);
            if (bg == null) {
                bg = new GradientDrawable();
                bg.setColor(android.graphics.Color.argb(38, android.graphics.Color.red(base),
                        android.graphics.Color.green(base), android.graphics.Color.blue(base)));
                bg.setStroke((int)(1f*d), Color.argb(80,255,255,255));
                bg.setCornerRadius(14*d);
            }
            fc.setBackground(bg);

            if (icon != null) { ImageView iv = new ImageView(r.getContext()); iv.setImageDrawable(icon); fc.addView(iv, new LinearLayout.LayoutParams((int)(26*d),(int)(26*d))); }

            LinearLayout textCol = new LinearLayout(r.getContext());
            textCol.setOrientation(LinearLayout.VERTICAL);
            boolean hasSub = c.text != null && !c.text.trim().isEmpty();
            LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tcp.leftMargin = (int)(6*d);
            tcp.gravity = Gravity.CENTER_VERTICAL;

            TextView tv = new TextView(r.getContext());
            tv.setText(c.title != null && !c.title.isEmpty() ? c.title : c.pkg);
            tv.setTextSize(12);
            tv.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(
                    r.getContext(), com.leowalk.aodchange.CardStyle.P_FOCUS, tintColor(icon)));
            tv.setSingleLine(true); tv.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!hasSub) tvlp.gravity = Gravity.CENTER_VERTICAL;
            textCol.addView(tv, tvlp);

            if (hasSub) {
                TextView cv = new TextView(r.getContext());
                cv.setText(c.text); cv.setTextSize(11); cv.setTextColor(Color.argb(160,255,255,255));
                cv.setSingleLine(true);
                textCol.addView(cv);
            } else {
                TextView cv = new TextView(r.getContext());
                String appName = c.pkg;
                try {
                    android.content.pm.ApplicationInfo ai = r.getContext().getPackageManager().getApplicationInfo(c.pkg, 0);
                    CharSequence n = r.getContext().getPackageManager().getApplicationLabel(ai);
                    if (n != null && n.length() > 0) appName = n.toString();
                } catch (Exception ignored) {}
                cv.setText(appName); cv.setTextSize(11); cv.setTextColor(Color.argb(140,255,255,255));
                cv.setSingleLine(true);
                textCol.addView(cv);
            }
            fc.addView(textCol, tcp);
            LinearLayout.LayoutParams fp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) fp2.leftMargin = (int)(8*d);
            frow.addView(fc, fp2);
        }
        FrameLayout.LayoutParams frp = new FrameLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT);
        frp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        frp.bottomMargin = (int)(120 * d);
        sOverlay.addView(frow, frp);
    }

    // 焦点通知自定义图标：手电筒(miui.systemui.plugin)、秒表/倒计时(com.android.deskclock)
    private static int resolveFocusIconRes(String pkg, String title) {
        if (pkg == null) return 0;
        String t = title == null ? "" : title;
        if (pkg.contains("miui.systemui.plugin") || t.contains("手电筒")) {
            return com.leowalk.aodchange.R.drawable.ic_focus_flashlight;
        }
        if (pkg.contains("deskclock")) {
            if (t.contains("秒表")) return com.leowalk.aodchange.R.drawable.ic_focus_stopwatch;
            if (t.contains("倒计时")) return com.leowalk.aodchange.R.drawable.ic_focus_timer;
        }
        return 0;
    }

    private static List<Card> loadFocus(ViewGroup r) {
        try {
            Bundle b = r.getContext().getContentResolver().call(URI, "focus", null, null);
            if (b == null) return null;
            String json = b.getString("n");
            if (json == null || json.isEmpty() || "[]".equals(json)) { sFocusCards = null; return null; }
            if (sLastFocusJson != null && json.equals(sLastFocusJson)) return sFocusCards;
            sLastFocusJson = json;
            org.json.JSONArray arr = new org.json.JSONArray(json);
            List<Card> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Card c = new Card();
                c.pkg = o.optString("p"); c.title = o.optString("t");
                c.text = o.optString("x", ""); c.time = o.optLong("pt");
                if (!c.pkg.isEmpty()) list.add(c);
            }
            sFocusCards = list.isEmpty() ? null : list;
            return sFocusCards;
        } catch (Exception e) { return null; }
    }

    private static boolean load(ViewGroup r) {
        try {
            Bundle b = r.getContext().getContentResolver().call(URI, "get", null, null);
            if (b == null) return false;
            String json = b.getString("n");
            if (json == null || json.isEmpty() || "[]".equals(json)) { sCards = null; sLastCardsJson = null; return false; }
            if (sLastCardsJson != null && json.equals(sLastCardsJson)) return true;
            sLastCardsJson = json;
            org.json.JSONArray arr = new org.json.JSONArray(json);
            List<Card> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Card c = new Card();
                c.pkg = o.optString("p"); c.title = o.optString("t");
                c.text = o.optString("x", ""); c.time = o.optLong("pt");
                c.priority = o.optInt("pr", 0);
                if (!c.pkg.isEmpty() && !c.title.isEmpty()) list.add(c);
            }
            if (list.isEmpty()) { sCards = null; return false; }
            sCards = list; return true;
        } catch (Exception e) { return false; }
    }

    private static LinearLayout buildCard(android.content.Context c, String t, String x, Drawable i, long tm, float d) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int)(10*d),(int)(7*d),(int)(10*d),(int)(7*d));
        int autoColor = tintColor(i);
        GradientDrawable bg = com.leowalk.aodchange.CardRenderer.background(c, com.leowalk.aodchange.CardStyle.P_NOTIF, autoColor, 14f, d);
        if (bg == null) {
            bg = new GradientDrawable(); bg.setColor(Color.argb(20,255,255,255));
            bg.setStroke((int)(1f*d), Color.argb(60,255,255,255)); bg.setCornerRadius(14*d);
        }
        card.setBackground(bg);
        if (i != null) { ImageView ai = new ImageView(c); ai.setImageDrawable(i); card.addView(ai, new LinearLayout.LayoutParams((int)(26*d),(int)(26*d))); }
        LinearLayout tc = new LinearLayout(c); tc.setOrientation(LinearLayout.VERTICAL); tc.setPadding((int)(10*d),0,(int)(8*d),0);
        TextView tv = new TextView(c); tv.setText(t); tv.setTextSize(14);
        tv.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(c, com.leowalk.aodchange.CardStyle.P_NOTIF, autoColor));
        tv.setSingleLine(true); tv.setTypeface(Typeface.DEFAULT_BOLD); tc.addView(tv);
        if (x != null && !x.isEmpty() && !x.equals(t)) {
            TextView xv = new TextView(c); xv.setText(x); xv.setTextSize(12);
            xv.setTextColor(Color.argb(204,255,255,255)); xv.setSingleLine(true); tc.addView(xv);
        }
        card.addView(tc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView mt = new TextView(c);
        mt.setText(new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(tm)));
        mt.setTextSize(10); mt.setTextColor(Color.argb(140,255,255,255)); card.addView(mt);
        return card;
    }

    private static int tintColor(Drawable icon) {
        if (icon == null) return Color.WHITE;
        try {
            Bitmap bmp = icon instanceof BitmapDrawable ? ((BitmapDrawable)icon).getBitmap() : null;
            if (bmp == null) { bmp = Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888); Canvas cv = new Canvas(bmp); icon.setBounds(0,0,48,48); icon.draw(cv); }
            int bw=bmp.getWidth(), bh=bmp.getHeight(), best=Color.WHITE; float bestS=-1;
            for (int[] p : new int[][]{{bw/2,bh/2},{bw/4,bh/4},{3*bw/4,bh/4},{bw/4,3*bh/4},{3*bw/4,3*bh/4}}) {
                int px=bmp.getPixel(p[0],p[1]); if(Color.alpha(px)<128) continue;
                float[] h=new float[3]; Color.colorToHSV(px,h);
                if(h[1]>0.15f&&h[2]>0.3f&&h[1]*h[2]>bestS){bestS=h[1]*h[2];best=px;}
            }
            float[] h=new float[3]; Color.colorToHSV(best,h);
            h[1]=Math.max(h[1],0.4f); h[2]=Math.max(h[2],0.7f);
            return Color.HSVToColor(h);
        } catch(Exception e){return Color.WHITE;}
    }
}
