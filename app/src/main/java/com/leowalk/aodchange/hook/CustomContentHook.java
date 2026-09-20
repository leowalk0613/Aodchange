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

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义内容：区域一自定义文字（可分行着色）；区域二横向最多 4 个小组件。
 * 无媒体播放时显示，媒体播放时隐藏。
 */
public class CustomContentHook {

    private static final Uri URI = Uri.parse("content://com.leowalk.aodchange.notifications");

    public static final String[] WIDGET_IDS = {
            "weather", "sun", "humidity", "aqi", "feels", "wind",
            "steps", "stand", "alarm", "schedule"
    };
    public static final String[] WIDGET_LABELS = {
            "天气", "日出日落", "湿度", "AQI", "体感", "风",
            "步数", "站立", "闹钟", "日程"
    };
    private static final int MAX_WIDGETS = 4;
    private static final int WIDGET_CELL_HEIGHT_DP = 72;
    private static final int COLOR_WARN = 0xFFFFDC64;
    private static final int COLOR_URGENT = 0xFFFF5252;
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final long DAY_MS = 24L * HOUR_MS;

    private static ViewGroup sRoot;
    private static LinearLayout sContainer;
    private static FrameLayout sPlaceholder;
    private static FrameLayout sCalendar;

    private static boolean sShowingPlaceholder = false;
    private static String sLastPlaceholderMain = null;
    private static String sLastCustomKey = "";
    private static volatile boolean sCustomRefreshPending = true;

    public static void setPlaceholder(View v) {
        if (sPlaceholder == null) return;
        try {
            sPlaceholder.removeAllViews();
            if (v != null) sPlaceholder.addView(v);
        } catch (Exception ignored) {}
    }

    public static void setup(ViewGroup root, LinearLayout container) {
        sRoot = root;
        sContainer = container;
        if (sContainer == null) return;
        float d = root.getResources().getDisplayMetrics().density;

        sPlaceholder = new FrameLayout(root.getContext());
        sPlaceholder.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = (int) (6 * d);
        sContainer.addView(sPlaceholder, plp);

        sCalendar = new FrameLayout(root.getContext());
        sCalendar.setVisibility(View.GONE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = (int) (6 * d);
        sContainer.addView(sCalendar, clp);
    }

    public static void resetCaches() {
        sLastCustomKey = "";
        sLastPlaceholderMain = null;
        sCustomRefreshPending = true;
        sShowingPlaceholder = false;
    }

    public static void setCustomRefreshPending(boolean p) {
        sCustomRefreshPending = p;
    }

    public static boolean isCustomRefreshPending() {
        return sCustomRefreshPending;
    }

    /** 解析已选小组件（有序，最多 4 个）；兼容旧 calendar_enabled */
    public static List<String> parseSelectedWidgets(android.content.Context ctx) {
        List<String> out = new ArrayList<>();
        String raw = com.leowalk.aodchange.SettingsHelper.getString(ctx, "custom_widgets", "");
        if (raw != null && !raw.isEmpty()) {
            for (String p : raw.split(",")) {
                String id = p.trim();
                if (id.isEmpty() || out.contains(id)) continue;
                if (!isKnownWidget(id)) continue;
                out.add(id);
                if (out.size() >= MAX_WIDGETS) break;
            }
        }
        if (out.isEmpty()
                && com.leowalk.aodchange.SettingsHelper.get(ctx, "calendar_enabled", false)) {
            out.add("schedule");
        }
        return out;
    }

    private static boolean isKnownWidget(String id) {
        for (String w : WIDGET_IDS) if (w.equals(id)) return true;
        return false;
    }

    public static void applyCustomOrPlaceholder() {
        if (sRoot == null) return;
        ElementSyncHook.setPaused(true);
        boolean textOn = com.leowalk.aodchange.SettingsHelper.get(
                sRoot.getContext(), "placeholder_test", false);
        List<String> widgets = parseSelectedWidgets(sRoot.getContext());
        android.util.Log.i("AodChange", "customOrPlaceholder text=" + textOn
                + " widgets=" + widgets.size());
        if (textOn || !widgets.isEmpty()) {
            applyCustom(textOn, widgets);
            return;
        }
        applyPlaceholder();
    }

    private static void applyCustom(boolean textOn, List<String> widgets) {
        if (sRoot == null) return;
        try {
            android.content.Context ctx = sRoot.getContext();
            String text = textOn
                    ? com.leowalk.aodchange.SettingsHelper.getString(
                    ctx, "placeholder_text", "农历八月十五 · 中秋")
                    : "";
            String lineColors = textOn
                    ? com.leowalk.aodchange.SettingsHelper.getString(ctx, "placeholder_line_colors", "")
                    : "";
            boolean showFestival = widgets.contains("schedule")
                    && com.leowalk.aodchange.SettingsHelper.get(ctx, "calendar_show_festival", false);
            List<FestivalUtil.Festival> festivals = null;
            if (showFestival) {
                FestivalUtil.ensureLoad(ctx);
                festivals = FestivalUtil.getCached();
            }
            String cjson = widgets.contains("schedule") ? readProvider("calendar") : "";
            LockscreenDataHelper.WeatherSnapshot weather = needsWeather(widgets)
                    ? LockscreenDataHelper.readWeather(ctx) : null;
            Integer steps = widgets.contains("steps") ? LockscreenDataHelper.readSteps(ctx) : null;
            Integer stand = widgets.contains("stand") ? LockscreenDataHelper.readStand(ctx) : null;
            LockscreenDataHelper.AlarmSnapshot alarm = widgets.contains("alarm")
                    ? LockscreenDataHelper.readNextAlarm(ctx) : null;
            Object[] nextSchedule = widgets.contains("schedule")
                    ? resolveNextScheduleItem(cjson, festivals) : null;

            java.text.SimpleDateFormat dayFmt =
                    new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault());
            String todayStr = dayFmt.format(new java.util.Date());
            String alarmKey = alarm == null ? "" : alarm.display + "@" + alarm.triggerAt
                    + "#" + alarmUrgency(alarm.triggerAt);
            long schedTime = nextSchedule != null ? (Long) nextSchedule[0] : 0L;
            String key = textOn + "|" + text + "|" + lineColors + "|" + widgets
                    + "|" + cjson + "|" + showFestival + "|" + todayStr
                    + "|" + festivalKey(festivals)
                    + "|" + weatherKey(weather)
                    + "|" + steps + "|" + stand + "|" + alarmKey
                    + "|" + schedTime + "#" + scheduleUrgency(schedTime)
                    + "|" + styleKey(ctx, com.leowalk.aodchange.CardStyle.P_CUSTOM_TEXT)
                    + "|" + styleKey(ctx, com.leowalk.aodchange.CardStyle.P_CUSTOM_WIDGET);

            if (key.equals(sLastCustomKey)) {
                LyricHook.hideMediaViews();
                if (sPlaceholder != null && sPlaceholder.getChildCount() > 0 && !sShowingPlaceholder) {
                    sShowingPlaceholder = true;
                    sPlaceholder.setAlpha(0f);
                    sPlaceholder.setVisibility(View.VISIBLE);
                    sPlaceholder.animate().alpha(1f).setDuration(300L).start();
                }
                return;
            }
            sLastCustomKey = key;
            buildCustomCards(textOn, text, lineColors, widgets, nextSchedule,
                    weather, steps, stand, alarm);
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "applyCustom fail", t);
        }
    }

    private static boolean needsWeather(List<String> widgets) {
        for (String w : widgets) {
            if ("weather".equals(w) || "sun".equals(w) || "humidity".equals(w)
                    || "aqi".equals(w) || "feels".equals(w) || "wind".equals(w)) {
                return true;
            }
        }
        return false;
    }

    private static String weatherKey(LockscreenDataHelper.WeatherSnapshot w) {
        if (w == null) return "0";
        return w.temperature + "|" + w.condition + "|" + w.humidity + "|" + w.aqi
                + "|" + w.feelsLike + "|" + w.wind + "|" + w.sunrise + "|" + w.sunset;
    }

    private static String styleKey(android.content.Context ctx, String prefix) {
        com.leowalk.aodchange.CardStyle s = com.leowalk.aodchange.CardStyle.load(ctx, prefix);
        return s.enabled + "|" + s.bgMode + "|" + s.bgColor + "|" + s.bgAlpha
                + "|" + s.strokeColor + "|" + s.strokeAlpha + "|" + s.strokeWidth;
    }

    private static String festivalKey(List<FestivalUtil.Festival> festivals) {
        if (festivals == null || festivals.isEmpty()) return "0";
        StringBuilder sb = new StringBuilder();
        for (FestivalUtil.Festival f : festivals) {
            sb.append(f.time).append('_').append(f.name).append(';');
        }
        return sb.toString();
    }

    private static void buildCustomCards(boolean textOn, String text, String lineColorsJson,
                                         List<String> widgets, Object[] nextSchedule,
                                         LockscreenDataHelper.WeatherSnapshot weather,
                                         Integer steps, Integer stand,
                                         LockscreenDataHelper.AlarmSnapshot alarm) {
        try {
            float d = sRoot.getResources().getDisplayMetrics().density;
            android.content.Context ctx = sRoot.getContext();
            int txtGravity = gravityOf(ctx, "placeholder_gravity", Gravity.CENTER);

            LinearLayout wrap = new LinearLayout(ctx);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setGravity(Gravity.CENTER);

            if (textOn) {
                LinearLayout txtCard = new LinearLayout(ctx);
                txtCard.setOrientation(LinearLayout.VERTICAL);
                txtCard.setGravity(txtGravity);
                applyFrameBackground(txtCard, d, com.leowalk.aodchange.CardStyle.P_CUSTOM_TEXT);
                LinearLayout.LayoutParams txtLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                txtLp.bottomMargin = (int) (6 * d);
                txtCard.setLayoutParams(txtLp);

                String[] lines = text == null ? new String[]{""} : text.split("\n", -1);
                int[] colors = parseLineColors(lineColorsJson, lines.length);
                for (int i = 0; i < lines.length; i++) {
                    if (i >= 4) break;
                    TextView tv = new TextView(ctx);
                    tv.setText(lines[i]);
                    tv.setTextSize(15);
                    tv.setTextColor(colors[i] | 0xFF000000);
                    tv.setTypeface(Typeface.DEFAULT);
                    tv.setGravity(txtGravity);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    txtCard.addView(tv);
                }
                wrap.addView(txtCard);
            }

            if (!widgets.isEmpty()) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = (int) (6 * d);
                row.setLayoutParams(rowLp);

                int cellH = (int) (WIDGET_CELL_HEIGHT_DP * d);
                for (int i = 0; i < widgets.size(); i++) {
                    String id = widgets.get(i);
                    LinearLayout cell = buildWidgetCell(ctx, id, weather, steps, stand, alarm,
                            nextSchedule, d);
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, cellH, 1f);
                    if (i > 0) clp.leftMargin = (int) (6 * d);
                    row.addView(cell, clp);
                }
                wrap.addView(row);
            }

            if (sPlaceholder != null) {
                sPlaceholder.animate().cancel();
                sPlaceholder.removeAllViews();
                sPlaceholder.addView(wrap);
                sPlaceholder.setVisibility(View.VISIBLE);
                sShowingPlaceholder = true;
            }
            if (sCalendar != null) sCalendar.setVisibility(View.GONE);
            LyricHook.hideMediaViews();
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "buildCustomCards fail", t);
        }
    }

    private static LinearLayout buildWidgetCell(android.content.Context ctx, String id,
                                                LockscreenDataHelper.WeatherSnapshot weather,
                                                Integer steps, Integer stand,
                                                LockscreenDataHelper.AlarmSnapshot alarm,
                                                Object[] nextSchedule, float d) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        applyFrameBackground(cell, d, com.leowalk.aodchange.CardStyle.P_CUSTOM_WIDGET);

        String title;
        String value;
        int valueColor = Color.WHITE;
        switch (id) {
            case "weather":
                title = weather != null ? weather.condition : "天气";
                value = weather != null ? weather.temperature : "--°";
                break;
            case "sun": {
                title = "日出日落";
                if (weather != null) {
                    value = LockscreenDataHelper.formatMinute(weather.sunrise)
                            + "\n" + LockscreenDataHelper.formatMinute(weather.sunset);
                } else {
                    value = "--";
                }
                break;
            }
            case "humidity":
                title = "湿度";
                value = weather != null ? weather.humidity : "--";
                break;
            case "aqi":
                title = "AQI";
                value = weather != null ? weather.aqi : "--";
                break;
            case "feels":
                title = "体感";
                value = weather != null ? weather.feelsLike : "--°";
                break;
            case "wind":
                title = "风";
                value = weather != null ? weather.wind : "--";
                break;
            case "steps":
                title = "步数";
                value = steps != null ? String.valueOf(steps) : "--";
                break;
            case "stand":
                title = "站立";
                value = stand != null ? String.valueOf(stand) : "--";
                break;
            case "alarm":
                title = "闹钟";
                if (alarm != null) {
                    value = alarm.display;
                    valueColor = urgencyColor(alarmUrgency(alarm.triggerAt), Color.WHITE);
                } else {
                    value = "无";
                }
                break;
            case "schedule":
                if (nextSchedule != null) {
                    title = (String) nextSchedule[1];
                    value = (String) nextSchedule[2];
                    boolean festival = nextSchedule.length > 3 && Boolean.TRUE.equals(nextSchedule[3]);
                    if (festival) {
                        valueColor = Color.rgb(100, 174, 255);
                    } else {
                        valueColor = urgencyColor(scheduleUrgency((Long) nextSchedule[0]), Color.WHITE);
                    }
                } else {
                    title = "日程";
                    value = "无";
                }
                break;
            default:
                title = id;
                value = "--";
                break;
        }

        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextSize(10);
        titleTv.setTextColor(Color.argb(180, 255, 255, 255));
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(titleTv);

        TextView valueTv = new TextView(ctx);
        valueTv.setText(value);
        valueTv.setTextSize(13);
        valueTv.setTextColor(valueColor);
        valueTv.setTypeface(Typeface.DEFAULT_BOLD);
        valueTv.setGravity(Gravity.CENTER);
        valueTv.setMaxLines(2);
        valueTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = (int) (2 * d);
        cell.addView(valueTv, vlp);
        return cell;
    }

    /** 0 正常，1 黄，2 红。日程：3 天内黄，1 天内红。 */
    private static int scheduleUrgency(long start) {
        if (start <= 0) return 0;
        long delta = start - System.currentTimeMillis();
        if (delta <= 0) return 0;
        if (delta <= DAY_MS) return 2;
        if (delta <= 3L * DAY_MS) return 1;
        return 0;
    }

    /** 0 正常，1 黄，2 红。闹钟：12 小时内黄，1 小时内红。 */
    private static int alarmUrgency(long triggerAt) {
        if (triggerAt <= 0) return 0;
        long delta = triggerAt - System.currentTimeMillis();
        if (delta <= 0) return 0;
        if (delta <= HOUR_MS) return 2;
        if (delta <= 12L * HOUR_MS) return 1;
        return 0;
    }

    private static int urgencyColor(int level, int normal) {
        if (level >= 2) return COLOR_URGENT;
        if (level == 1) return COLOR_WARN;
        return normal;
    }

    /** @return [time, title, timeLabel] 或 null */
    private static Object[] resolveNextScheduleItem(String cjson,
                                                    List<FestivalUtil.Festival> festivals) {
        try {
            List<Object[]> items = new ArrayList<>();
            long now = System.currentTimeMillis();
            long threeDaysEnd = now + 3L * 24 * 60 * 60 * 1000;
            if (cjson != null && !cjson.isEmpty() && !"[]".equals(cjson)) {
                org.json.JSONArray arr = new org.json.JSONArray(cjson);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    long start = o.optLong("s", 0);
                    if (start < now || start > threeDaysEnd) continue;
                    String t = o.optString("t", "");
                    if (t.isEmpty()) continue;
                    boolean allDay = o.optBoolean("a", false);
                    items.add(new Object[]{start, t,
                            formatEventTime(start, allDay), false});
                }
            }
            if (festivals != null) {
                for (FestivalUtil.Festival f : festivals) {
                    if (f.time < now || f.time > threeDaysEnd) continue;
                    items.add(new Object[]{f.time, f.name,
                            formatFestivalTime(f.time), true});
                }
            }
            if (items.isEmpty()) return null;
            items.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            Object[] first = items.get(0);
            return new Object[]{first[0], first[1], first[2], first[3]};
        } catch (Throwable t) {
            return null;
        }
    }

    private static int[] parseLineColors(String json, int lineCount) {
        int[] colors = new int[Math.max(lineCount, 1)];
        for (int i = 0; i < colors.length; i++) colors[i] = 0xFFFFFF;
        if (json == null || json.isEmpty()) return colors;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < colors.length && i < arr.length(); i++) {
                colors[i] = arr.optInt(i, 0xFFFFFF) & 0xFFFFFF;
            }
        } catch (Throwable ignored) {}
        return colors;
    }

    private static String readProvider(String key) {
        try {
            Bundle b = sRoot.getContext().getContentResolver().call(URI, key, null, null);
            String s = (b != null) ? b.getString("n") : null;
            return s != null ? s : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void applyPlaceholder() {
        if (sRoot == null) return;
        boolean enabled = com.leowalk.aodchange.SettingsHelper.get(
                sRoot.getContext(), "placeholder_test", false);
        if (enabled && sPlaceholder != null) {
            String text = com.leowalk.aodchange.SettingsHelper.getString(
                    sRoot.getContext(), "placeholder_text", "农历八月十五 · 中秋");
            if (sPlaceholder.getChildCount() == 0 || !text.equals(sLastPlaceholderMain)) {
                sLastPlaceholderMain = text;
                sPlaceholder.animate().cancel();
                sPlaceholder.removeAllViews();
                sPlaceholder.addView(buildTestPlaceholder(sRoot.getContext()));
            }
        }
        LyricHook.hideMediaViews();
        showPlaceholder();
    }

    private static View buildTestPlaceholder(android.content.Context ctx) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        String text = com.leowalk.aodchange.SettingsHelper.getString(
                ctx, "placeholder_text", "农历八月十五 · 中秋");
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

    private static void showPlaceholder() {
        if (sContainer == null) return;
        if (sCalendar != null) sCalendar.setVisibility(View.GONE);
        if (sPlaceholder == null || sPlaceholder.getChildCount() == 0) {
            LyricHook.hideMediaViews();
            sContainer.setVisibility(View.GONE);
            return;
        }
        if (sShowingPlaceholder) return;
        sShowingPlaceholder = true;
        if (sPlaceholder != null) {
            sPlaceholder.setAlpha(0f);
            sPlaceholder.setVisibility(View.VISIBLE);
            sPlaceholder.animate().alpha(1f).setDuration(300L).start();
        }
        LyricHook.fadeOutMedia();
    }

    public static void showMedia() {
        if (sContainer == null) return;
        ElementSyncHook.setPaused(false);
        if (sCalendar != null) sCalendar.setVisibility(View.GONE);
        if (!sShowingPlaceholder) return;
        sShowingPlaceholder = false;
        if (sPlaceholder != null) {
            sPlaceholder.animate().alpha(0f).setDuration(200L)
                    .withEndAction(() -> {
                        try { sPlaceholder.setVisibility(View.GONE); } catch (Throwable ignored) {}
                    })
                    .start();
        }
    }

    private static int gravityOf(android.content.Context ctx, String key, int def) {
        String g = com.leowalk.aodchange.SettingsHelper.getString(ctx, key, "");
        if ("left".equals(g)) return Gravity.LEFT;
        if ("right".equals(g)) return Gravity.RIGHT;
        if ("center".equals(g)) return Gravity.CENTER;
        return def;
    }

    private static void applyFrameBackground(View v, float d, String stylePrefix) {
        android.graphics.drawable.GradientDrawable bg =
                com.leowalk.aodchange.CardRenderer.background(
                        sRoot.getContext(), stylePrefix, Color.WHITE, 14f, d);
        if (bg == null) {
            // 兼容旧版统一前缀
            bg = com.leowalk.aodchange.CardRenderer.background(
                    sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_CUSTOM, Color.WHITE, 14f, d);
        }
        if (bg == null) {
            bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.argb(20, 255, 255, 255));
            bg.setStroke((int) (1f * d), Color.argb(60, 255, 255, 255));
            bg.setCornerRadius(14 * d);
        }
        v.setBackground(bg);
        v.setPadding((int) (10 * d), (int) (8 * d), (int) (10 * d), (int) (8 * d));
    }

    private static String formatEventTime(long start, boolean allDay) {
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

    private static String formatFestivalTime(long time) {
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
