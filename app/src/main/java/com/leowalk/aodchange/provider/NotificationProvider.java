package com.leowalk.aodchange.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class NotificationProvider extends ContentProvider {
    private static final String PREFS = "aodchange_settings";
    private static volatile String sJson = "[]";
    private static volatile String sFocusJson = "[]";
    private static volatile String sLyricJson = "{}";
    private static volatile String sMediaJson = "{}";
    private static volatile String sSettingsJson = "{}";
    private static volatile String sCalendarJson = "[]";
    private static volatile String sStatusJson = "{}";
    private static volatile android.os.ParcelFileDescriptor sLyricPfd;
    private static volatile int sVJson = 0, sVFocus = 0, sVLyric = 0, sVLyricFd = 0, sVMedia = 0, sVCalendar = 0, sVSettings = 0, sVStatus = 0;
    public static void update(String json) { sJson = json; sVJson++; notifyChanged(); }
    public static void updateFocus(String json) { sFocusJson = json; sVFocus++; notifyChanged(); }
    public static void updateLyric(String json) { sLyricJson = json; sVLyric++; notifyChanged(); }
    public static void updateLyricMedia(String json) { sMediaJson = json; sVMedia++; notifyChanged(); }
    public static void updateSettings(String json) { sSettingsJson = json; sVSettings++; notifyChanged(); }
    public static void updateCalendar(String json) { sCalendarJson = json; sVCalendar++; notifyChanged(); }
    public static void updateStatus(String json) { sStatusJson = json; sVStatus++; notifyChanged(); notifyChanged(); }
    private static volatile android.content.Context sCtx;
    @Override public boolean onCreate() {
        try {
            sCtx = getContext();
            SharedPreferences sp = sCtx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
            String saved = sp.getString("settings_json", null);
            if (saved != null) sSettingsJson = saved;
            String st = sp.getString("status_json", null);
            if (st != null) sStatusJson = st;
        } catch (Throwable ignored) {}
        return true;
    }
    /** 数据变化通知：systemui 侧 ContentObserver 事件驱动（替代轮询，及时且省 binder） */
    private static void notifyChanged() {
        try {
            if (sCtx == null) return;
            sCtx.getContentResolver().notifyChange(
                    android.net.Uri.parse("content://com.leowalk.aodchange.notifications"), null);
        } catch (Throwable ignored) {}
    }

    @Override public Bundle call(String m, String a, Bundle e) {
        if ("versions".equals(m)) {
            Bundle r = new Bundle();
            r.putInt("notif", sVJson); r.putInt("focus", sVFocus);
            r.putInt("lyric", sVLyric); r.putInt("lyricfd", sVLyricFd); r.putInt("media", sVMedia);
            r.putInt("calendar", sVCalendar); r.putInt("settings", sVSettings);
            r.putInt("status", sVStatus);
            return r;
        }
        if ("get".equals(m)) { Bundle r = new Bundle(); r.putString("n", sJson); return r; }
        if ("focus".equals(m)) { Bundle r = new Bundle(); r.putString("n", sFocusJson); return r; }
        if ("lyric".equals(m)) { Bundle r = new Bundle(); r.putString("n", sLyricJson); return r; }
        if ("media".equals(m)) { Bundle r = new Bundle(); r.putString("n", sMediaJson); return r; }
        if ("calendar".equals(m)) { Bundle r = new Bundle(); r.putString("n", sCalendarJson); return r; }
        if ("settings".equals(m)) { Bundle r = new Bundle(); r.putString("n", sSettingsJson); return r; }
        if ("status".equals(m)) { Bundle r = new Bundle(); r.putString("n", sStatusJson); return r; }
        if ("put".equals(m) && e != null) { String j = e.getString("n"); if (j != null) sJson = j; return new Bundle(); }
        if ("putlyric".equals(m) && e != null) { String j = e.getString("n"); if (j != null) { sLyricJson = j; sVLyric++; notifyChanged(); } return new Bundle(); }
        if ("putlyricfd".equals(m) && e != null) {
            // 歌词全量走文件描述符：绕开 binder 大数据传输（独立版本，换行轻量推送不触发全量重读）
            android.os.ParcelFileDescriptor pfd = e.getParcelable("fd");
            if (pfd != null) {
                if (sLyricPfd != null) { try { sLyricPfd.close(); } catch (Throwable ignored) {} }
                sLyricPfd = pfd;
                sVLyricFd++;
                notifyChanged();
            }
            return new Bundle();
        }
        if ("lyric_fd".equals(m)) {
            Bundle r = new Bundle();
            if (sLyricPfd != null) r.putParcelable("fd", sLyricPfd);
            return r;
        }
        if ("putcalendar".equals(m) && e != null) { String j = e.getString("n"); if (j != null) { sCalendarJson = j; notifyChanged(); } return new Bundle(); }
        if ("putstatus".equals(m) && e != null) {
            String j = e.getString("n");
            if (j != null) {
                try {
                    // 按字段合并：不同进程（设备进程/主进程）各自推送，避免互相覆盖
                    org.json.JSONObject cur = new org.json.JSONObject(sStatusJson);
                    org.json.JSONObject inc = new org.json.JSONObject(j);
                    java.util.Iterator<String> keys = inc.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        cur.put(k, inc.get(k));
                    }
                    sStatusJson = cur.toString();
                } catch (Throwable ignored) {
                    sStatusJson = j;
                }
                sVStatus++; notifyChanged();
                try {
                    getContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                            .edit().putString("status_json", sStatusJson).apply();
                } catch (Throwable ignored) {}
            }
            return new Bundle();
        }
        if ("putsettings".equals(m) && e != null) {
            String j = e.getString("n");
            if (j != null) {
                sSettingsJson = j;
                sVSettings++; notifyChanged();
                try {
                    SharedPreferences sp = getContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
                    sp.edit().putString("settings_json", j).apply();
                } catch (Throwable ignored) {}
            }
            return new Bundle();
        }
        return null;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] sa) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] sa) { return 0; }
}
