package com.leowalk.aodchange.hook;

import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 自定义内容功能（与歌词渲染分离）：
 * 日历日程卡、自定义文字卡，无媒体播放时显示，按设置页顺序排列。
 */
public class CustomContentHook {

    private static final Uri URI = Uri.parse("content://com.leowalk.aodchange.notifications");

    private static ViewGroup sRoot;
    private static LinearLayout sContainer;
    private static FrameLayout sPlaceholder;
    private static FrameLayout sCalendar;

    private static boolean sShowingPlaceholder = false;
    private static String sLastPlaceholderMain = null;
    private static String sLastPlaceholderSub = null;
    private static String sLastCalendarJson = "";
    private static String sLastCustomKey = "";
    private static volatile boolean sCustomRefreshPending = true;

    /** 占位内容接口：无媒体播放时在此区域常驻显示，媒体播放时自动隐藏。 */
    public static void setPlaceholder(View v) {
        if (sPlaceholder == null) return;
        try {
            sPlaceholder.removeAllViews();
            if (v != null) sPlaceholder.addView(v);
        } catch (Exception ignored) {}
    }

    /** 由 LyricHook.setup 调用：绑定容器并创建自定义区域视图 */
    public static void setup(ViewGroup root, LinearLayout container) {
        sRoot = root;
        sContainer = container;
        if (sContainer == null) return;
        float d = root.getResources().getDisplayMetrics().density;

        sPlaceholder = new FrameLayout(root.getContext());
        sPlaceholder.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = (int)(6 * d);
        sContainer.addView(sPlaceholder, plp);

        sCalendar = new FrameLayout(root.getContext());
        sCalendar.setVisibility(View.GONE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = (int)(6 * d);
        sContainer.addView(sCalendar, clp);
    }

    /** 视图树重建时清空内容缓存（由 LyricHook.resetContentCaches 调用） */
    public static void resetCaches() {
        sLastCustomKey = "";
        sLastPlaceholderMain = null;
        sLastPlaceholderSub = null;
        sLastCalendarJson = "";
        sCustomRefreshPending = true;
        sShowingPlaceholder = false;
    }

    public static void setCustomRefreshPending(boolean p) {
        sCustomRefreshPending = p;
    }

    public static boolean isCustomRefreshPending() {
        return sCustomRefreshPending;
    }

    public static void applyCustomOrPlaceholder() {
        if (sRoot == null) return;
        ElementSyncHook.setPaused(true);
        // 日历/自定义文字 任一开启都走完整自定义渲染
        boolean calendarEnabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "calendar_enabled", false);
        boolean placeholderEnabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "placeholder_test", false);
        android.util.Log.i("AodChange", "customOrPlaceholder cal=" + calendarEnabled + " plh=" + placeholderEnabled);
        if (calendarEnabled || placeholderEnabled) {
            applyCustom();
            return;
        }
        applyPlaceholder();
    }

    private static void applyCustom() {
        if (sRoot == null) return;
        try {
            boolean calendarEnabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "calendar_enabled", false);
            String cjson = calendarEnabled ? readProvider("calendar") : "";
            boolean placeholderEnabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "placeholder_test", false);
            String text = placeholderEnabled
                    ? com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "placeholder_text", "农历八月十五 · 中秋")
                    : "";
            // key 含节日开关 + 当天日期 + 节日内容：开关变化/跨天/缓存加载后触发重建
            boolean showFestival = com.leowalk.aodchange.SettingsHelper.get(
                    sRoot.getContext(), "calendar_show_festival", false);
            java.util.List<FestivalUtil.Festival> festivals = null;
            if (showFestival) {
                FestivalUtil.ensureLoad(sRoot.getContext());
                festivals = FestivalUtil.getCached();
            }
            java.text.SimpleDateFormat dayFmt = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault());
            String todayStr = dayFmt.format(new java.util.Date());
            String key = calendarEnabled + "|" + cjson + "|" + placeholderEnabled + "|" + text
                    + "|" + showFestival + "|" + todayStr + "|" + festivalKey(festivals);
            // 内容未变：保持现有视图（视图重建时 resetCaches 已清空 key，新实例不会误命中）
            if (key.equals(sLastCustomKey)) {
                LyricHook.hideMediaViews();
                return;
            }
            sLastCustomKey = key;
            buildCustomCards(calendarEnabled, cjson, placeholderEnabled, text, festivals);
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "applyCustom fail", t);
        }
    }

    private static String festivalKey(java.util.List<FestivalUtil.Festival> festivals) {
        if (festivals == null || festivals.isEmpty()) return "0";
        StringBuilder sb = new StringBuilder();
        for (FestivalUtil.Festival f : festivals) {
            sb.append(f.time).append('_').append(f.name).append(';');
        }
        return sb.toString();
    }

    /** 构建自定义内容：按设置页顺序（自定义文字 → 日历日程）各自独立卡片，卡片间距统一 */
    private static void buildCustomCards(boolean calendarEnabled, String cjson,
                                         boolean placeholderEnabled, String text,
                                         java.util.List<FestivalUtil.Festival> festivals) {
        try {
            float d = sRoot.getResources().getDisplayMetrics().density;
            int txtGravity = gravityOf(sRoot.getContext(), "placeholder_gravity", Gravity.CENTER);
            int calGravity = gravityOf(sRoot.getContext(), "calendar_gravity", Gravity.LEFT);

            LinearLayout wrap = new LinearLayout(sRoot.getContext());
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setGravity(Gravity.CENTER);

            // 预计算日程（未来 3 天内，按开始时间升序）+ 节日节气（同规则：3 天窗口）
            java.util.List<org.json.JSONObject> upcoming = new java.util.ArrayList<>();
            if (calendarEnabled && !cjson.isEmpty() && !"[]".equals(cjson)) {
                long now = System.currentTimeMillis();
                long threeDaysEnd = now + 3L * 24 * 60 * 60 * 1000;
                org.json.JSONArray arr = new org.json.JSONArray(cjson);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    long start = o.optLong("s", 0);
                    if (start >= now && start <= threeDaysEnd) upcoming.add(o);
                }
                upcoming.sort((a, b) -> Long.compare(a.optLong("s", 0), b.optLong("s", 0)));
            }
            boolean showFestival = com.leowalk.aodchange.SettingsHelper.get(
                    sRoot.getContext(), "calendar_show_festival", false);
            if (showFestival && festivals == null) {
                FestivalUtil.ensureLoad(sRoot.getContext());
                festivals = FestivalUtil.getCached();
            }

            // 日程 + 节日/节气合并，按时间升序，合计最多 3 条
            java.util.List<Object[]> items = new java.util.ArrayList<>();
            for (org.json.JSONObject o : upcoming) {
                items.add(new Object[]{o.optLong("s", 0), o.optString("t", ""), false, o.optBoolean("a", false)});
            }
            if (festivals != null) {
                for (FestivalUtil.Festival f : festivals) {
                    items.add(new Object[]{f.time, f.name, true, false});
                }
            }
            items.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            if (items.size() > 3) items = new java.util.ArrayList<>(items.subList(0, 3));
            android.util.Log.i("AodChange", "custom build items=" + items.size()
                    + " upcoming=" + upcoming.size() + " festivals=" + (festivals == null ? -1 : festivals.size()));

            // 1. 文字卡：独立卡片（单段文字，支持换行）
            if (placeholderEnabled) {
                LinearLayout txtCard = new LinearLayout(sRoot.getContext());
                txtCard.setOrientation(LinearLayout.VERTICAL);
                txtCard.setGravity(txtGravity);
                applyFrameBackground(txtCard, d);
                android.widget.LinearLayout.LayoutParams txtLp = new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                txtLp.bottomMargin = (int)(6 * d);
                txtCard.setLayoutParams(txtLp);
                TextView mainTv = new TextView(sRoot.getContext());
                mainTv.setText(text);
                mainTv.setTextSize(15);
                mainTv.setTextColor(Color.WHITE);
                mainTv.setTypeface(Typeface.DEFAULT);
                mainTv.setGravity(txtGravity);
                mainTv.setSingleLine(false);
                mainTv.setMaxLines(4);
                mainTv.setEllipsize(TextUtils.TruncateAt.END);
                txtCard.addView(mainTv);
                wrap.addView(txtCard);
            }

            // 2. 日程卡：日程 + 节日/节气，按时间次序混排（合计最多 3 条）
            if (!items.isEmpty()) {
                LinearLayout calCard = new LinearLayout(sRoot.getContext());
                calCard.setOrientation(LinearLayout.VERTICAL);
                calCard.setGravity(calGravity);
                applyFrameBackground(calCard, d);
                android.widget.LinearLayout.LayoutParams calLp = new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                calLp.bottomMargin = (int)(6 * d);
                calCard.setLayoutParams(calLp);
                int cscW = sRoot.getWidth();
                if (cscW <= 0) cscW = sRoot.getResources().getDisplayMetrics().widthPixels;
                com.leowalk.aodchange.CardRenderer.applySize(
                        sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_CUSTOM, calCard, cscW, d);
                long now = System.currentTimeMillis();
                for (Object[] item : items) {
                    long time = (Long) item[0];
                    String t = (String) item[1];
                    boolean isFestival = (Boolean) item[2];
                    boolean allDay = (Boolean) item[3];
                    if (t == null || t.isEmpty()) continue;
                    // 日程 24 小时内开始视为紧急（节日/节气不高亮）
                    boolean urgent = !isFestival && time - now <= 24L * 60 * 60 * 1000;
                    String timeText = isFestival || allDay
                            ? formatFestivalTime(sRoot.getContext(), time, allDay)
                            : formatEventTime(sRoot.getContext(), time, false);
                    calCard.addView(buildRow(sRoot.getContext(), timeText, t, urgent, isFestival));
                }
                wrap.addView(calCard);
            }

            if (sPlaceholder != null) {
                sPlaceholder.animate().cancel();
                sPlaceholder.removeAllViews();
                sPlaceholder.addView(wrap);
                sPlaceholder.setVisibility(View.VISIBLE);
            }
            if (sCalendar != null) sCalendar.setVisibility(View.GONE);
            LyricHook.hideMediaViews();
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "buildCustomCards fail", t);
        }
    }

    private static String readProvider(String key) {
        try {
            Bundle b = sRoot.getContext().getContentResolver().call(URI, key, null, null);
            String s = (b != null) ? b.getString("n") : null;
            return s != null ? s : "";
        } catch (Throwable ignored) { return ""; }
    }

    private static void applyPlaceholder() {
        if (sRoot == null) return;
        boolean enabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "placeholder_test", false);
        if (enabled && sPlaceholder != null) {
            String text = com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "placeholder_text", "农历八月十五 · 中秋");
            if (sPlaceholder.getChildCount() == 0
                    || !text.equals(sLastPlaceholderMain)) {
                sLastPlaceholderMain = text;
                sPlaceholder.animate().cancel();
                sPlaceholder.removeAllViews();
                sPlaceholder.addView(buildTestPlaceholder(sRoot.getContext()));
            }
        }
        // 统一隐藏媒体视图（含歌曲信息行线框），防止无内容白框残留
        LyricHook.hideMediaViews();
        showPlaceholder();
    }

    private static View buildTestPlaceholder(android.content.Context ctx) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        String text = com.leowalk.aodchange.SettingsHelper.getString(ctx, "placeholder_text", "农历八月十五 · 中秋");

        TextView main = new TextView(ctx);
        main.setText(text);
        main.setTextSize(15);
        main.setTextColor(Color.WHITE);
        main.setTypeface(Typeface.DEFAULT);
        main.setGravity(Gravity.CENTER);
        main.setSingleLine(false);
        main.setMaxLines(4);
        main.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(main);
        return col;
    }

    private static int dp(android.content.Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void showPlaceholder() {
        if (sContainer == null) return;
        if (sCalendar != null) sCalendar.setVisibility(View.GONE);
        if (sPlaceholder == null || sPlaceholder.getChildCount() == 0) {
            // 无占位内容：隐藏整个自定义容器（含歌曲信息行线框）
            LyricHook.hideMediaViews();
            sContainer.setVisibility(View.GONE);
            return;
        }
        if (sShowingPlaceholder) return;
        sShowingPlaceholder = true;
        // sContainer 显隐由 ElementSyncHook 跟随时钟控制
        if (sPlaceholder != null) {
            sPlaceholder.setAlpha(0f);
            sPlaceholder.setVisibility(View.VISIBLE);
            sPlaceholder.animate().alpha(1f).setDuration(300L).start();
        }
        LyricHook.fadeOutMedia();
    }

    /** 显示媒体时隐藏占位（由 LyricHook 歌词路径调用） */
    public static void showMedia() {
        if (sContainer == null) return;
        ElementSyncHook.setPaused(false);
        if (sCalendar != null) sCalendar.setVisibility(View.GONE);
        if (!sShowingPlaceholder) return;
        sShowingPlaceholder = false;
        if (sPlaceholder != null) {
            sPlaceholder.animate().alpha(0f).setDuration(200L)
                    .withEndAction(() -> { try { sPlaceholder.setVisibility(View.GONE); } catch (Throwable ignored) {} })
                    .start();
        }
    }

    private static View buildRow(android.content.Context ctx, String time, String title, boolean urgent, boolean isFestival) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(gravityOf(ctx, "calendar_gravity", Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        TextView timeTv = new TextView(ctx);
        timeTv.setText(time);
        timeTv.setTextSize(11);
        timeTv.setTextColor(Color.argb(170, 255, 255, 255));
        timeTv.setSingleLine(true);
        row.addView(timeTv);
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextSize(14);
        // 紧急（24h 内日程）黄色；节日/节气蓝色；其余白色
        titleTv.setTextColor(urgent ? Color.argb(255, 255, 220, 100)
                : isFestival ? Color.rgb(100, 174, 255) : Color.WHITE);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = dp(ctx, 8);
        row.addView(titleTv, tl);
        android.widget.LinearLayout.LayoutParams rlp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rlp);
        return row;
    }

    private static int gravityOf(android.content.Context ctx, String key, int def) {
        String g = com.leowalk.aodchange.SettingsHelper.getString(ctx, key, "");
        if ("left".equals(g)) return Gravity.LEFT;
        if ("right".equals(g)) return Gravity.RIGHT;
        if ("center".equals(g)) return Gravity.CENTER;
        return def;
    }

    // 半透明白色圆角矩形线框（自适应宽高）；启用自定义样式时按设置覆盖背景/描边
    private static void applyFrameBackground(View v, float d) {
        android.graphics.drawable.GradientDrawable bg = com.leowalk.aodchange.CardRenderer.background(
                sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_CUSTOM, Color.WHITE, 14f, d);
        if (bg == null) {
            bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.argb(20, 255, 255, 255));
            bg.setStroke((int)(1f * d), Color.argb(60, 255, 255, 255));
            bg.setCornerRadius(14 * d);
        }
        v.setBackground(bg);
        v.setPadding((int)(14 * d), (int)(8 * d), (int)(14 * d), (int)(8 * d));
    }

    private static String formatEventTime(android.content.Context ctx, long start, boolean allDay) {
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar ec = java.util.Calendar.getInstance();
            ec.setTimeInMillis(start);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm",
                    java.util.Locale.getDefault());
            if (now.get(java.util.Calendar.YEAR) == ec.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == ec.get(java.util.Calendar.DAY_OF_YEAR)) {
                return allDay ? "今天 · 全天" : "今天 " + sdf.format(new java.util.Date(start));
            }
            now.add(java.util.Calendar.DAY_OF_YEAR, 1);
            if (now.get(java.util.Calendar.YEAR) == ec.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == ec.get(java.util.Calendar.DAY_OF_YEAR)) {
                return allDay ? "明天 · 全天" : "明天 " + sdf.format(new java.util.Date(start));
            }
            java.text.SimpleDateFormat dsf = new java.text.SimpleDateFormat("M月d日",
                    java.util.Locale.getDefault());
            return allDay ? dsf.format(new java.util.Date(start)) + " · 全天"
                    : dsf.format(new java.util.Date(start)) + " " + sdf.format(new java.util.Date(start));
        } catch (Throwable t) {
            return "";
        }
    }

    /** 节日/节气时间格式（全天）：今天/明天/后天/M月d日 */
    private static String formatFestivalTime(android.content.Context ctx, long time, boolean allDay) {
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar ec = java.util.Calendar.getInstance();
            ec.setTimeInMillis(time);
            if (now.get(java.util.Calendar.YEAR) == ec.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == ec.get(java.util.Calendar.DAY_OF_YEAR)) {
                return "今天";
            }
            now.add(java.util.Calendar.DAY_OF_YEAR, 1);
            if (now.get(java.util.Calendar.YEAR) == ec.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == ec.get(java.util.Calendar.DAY_OF_YEAR)) {
                return "明天";
            }
            now.add(java.util.Calendar.DAY_OF_YEAR, 1);
            if (now.get(java.util.Calendar.YEAR) == ec.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == ec.get(java.util.Calendar.DAY_OF_YEAR)) {
                return "后天";
            }
            java.text.SimpleDateFormat dsf = new java.text.SimpleDateFormat("M月d日",
                    java.util.Locale.getDefault());
            return dsf.format(new java.util.Date(time));
        } catch (Throwable t) {
            return "";
        }
    }
}
