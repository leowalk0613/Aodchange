package com.leowalk.aodchange;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONObject;

public class SettingsHelper {

    private static final Uri URI = Uri.parse("content://com.leowalk.aodchange.notifications");
    private static JSONObject sCache;
    private static long sCacheTime = 0;

    /** 供各 Activity 直接读写完整设置 JSON（走 ContentProvider，实时、无缓存） */
    public static JSONObject loadSettings(android.content.Context ctx) {
        try {
            android.os.Bundle b = ctx.getContentResolver().call(URI, "settings", null, null);
            if (b != null) {
                String json = b.getString("n");
                if (json != null && !"{}".equals(json)) return new JSONObject(json);
            }
        } catch (Exception ignored) {}
        return new JSONObject();
    }

    public static void saveSettings(android.content.Context ctx, JSONObject o) {
        try {
            android.os.Bundle b = new android.os.Bundle();
            b.putString("n", o.toString());
            ctx.getContentResolver().call(URI, "putsettings", null, b);
        } catch (Exception ignored) {}
    }

    /** 读取现有设置并写入单个字段（保留其他字段） */
    public static void writeSetting(android.content.Context ctx, String key, Object value) {
        try {
            JSONObject o = loadSettings(ctx);
            if (value instanceof Boolean) o.put(key, (Boolean) value);
            else if (value instanceof Integer) o.put(key, (Integer) value);
            else if (value instanceof Float) o.put(key, (Float) value);
            else if (value instanceof Long) o.put(key, (Long) value);
            else o.put(key, String.valueOf(value));
            saveSettings(ctx, o);
        } catch (Exception ignored) {}
    }

    /** 读取音乐应用白名单（逗号分隔、去重） */
    public static java.util.List<String> getWhitelist(android.content.Context ctx) {
        java.util.List<String> list = new java.util.ArrayList<>();
        try {
            String s = getString(ctx, "music_whitelist", "");
            if (s != null && !s.isEmpty()) {
                for (String p : s.split(",")) {
                    String t = p.trim();
                    if (!t.isEmpty() && !list.contains(t)) list.add(t);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    /** 保存音乐应用白名单 */
    public static void saveWhitelist(android.content.Context ctx, java.util.List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String p : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p.trim());
        }
        writeSetting(ctx, "music_whitelist", sb.toString());
    }

    /** 锁屏出现等关键时机强制刷新：标记过期触发后台重新加载。
     *  保留旧缓存（不清空），避免主线程渲染瞬间回退默认值导致 AOD 反复闪变重建 */
    public static void invalidate() {
        sCacheTime = 0;
    }

    public static boolean get(Context ctx, String key, boolean def) {
        try {
            load(ctx);
            if (sCache != null && sCache.has(key)) return sCache.getBoolean(key);
        } catch (Exception ignored) {}
        return def;
    }

    public static int getInt(Context ctx, String key, int def) {
        try {
            load(ctx);
            if (sCache != null && sCache.has(key)) return sCache.getInt(key);
        } catch (Exception ignored) {}
        return def;
    }

    public static float getFloat(Context ctx, String key, float def) {
        try {
            load(ctx);
            if (sCache != null && sCache.has(key)) return (float) sCache.getDouble(key);
        } catch (Exception ignored) {}
        return def;
    }

    public static String getString(Context ctx, String key, String def) {
        try {
            load(ctx);
            if (sCache != null && sCache.has(key)) return sCache.getString(key);
        } catch (Exception ignored) {}
        return def;
    }

    private static volatile boolean sLoading = false;

    private static void load(Context ctx) {
        try {
            long now = android.os.SystemClock.elapsedRealtime();
            if (sCache != null && now - sCacheTime < 2000) return;
            // 主线程绝不发起同步 binder 调用（provider 冷启动会阻塞 1-5s 导致 SystemUI ANR），
            // 仅读缓存；缓存缺失/过期时后台异步加载，本次返回默认值
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                if (sCache == null || now - sCacheTime >= 5000) scheduleAsyncLoad(ctx);
                return;
            }
            sCacheTime = now;
            Bundle b = ctx.getContentResolver().call(URI, "settings", null, null);
            if (b == null) return;
            String json = b.getString("n");
            if (json == null || "{}".equals(json)) return;
            if (sCache == null || !json.equals(sCache.toString())) {
                sCache = new JSONObject(json);
            }
        } catch (Exception ignored) {}
    }

    private static void scheduleAsyncLoad(final Context ctx) {
        if (sLoading) return;
        sLoading = true;
        new Thread(() -> {
            try {
                Bundle b = ctx.getContentResolver().call(URI, "settings", null, null);
                if (b != null) {
                    String json = b.getString("n");
                    if (json != null && !"{}".equals(json) && (sCache == null || !json.equals(sCache.toString()))) {
                        sCache = new JSONObject(json);
                    }
                }
                sCacheTime = android.os.SystemClock.elapsedRealtime();
            } catch (Exception ignored) {
            } finally {
                sLoading = false;
            }
        }).start();
    }
}
