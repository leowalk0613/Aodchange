package com.leowalk.aodchange.service;

import android.app.Notification;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.MediaMetadata;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.leowalk.aodchange.provider.NotificationProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationCollectorService extends NotificationListenerService {
    private static final Map<String, StatusBarNotification> sMap = new ConcurrentHashMap<>();
    private static String sLastMediaJson = "";

    private final android.os.Handler mSyncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mSyncRunnable = new Runnable() {
        @Override public void run() {
            updateMediaInfo();
            mSyncHandler.postDelayed(this, 1000);
        }
    };
    // 通知卡片兜底轮询：10s 一次（即时更新由 onNotificationPosted 事件驱动）
    private final Runnable mCardRunnable = new Runnable() {
        @Override public void run() {
            syncAll();
            persist();
            mSyncHandler.postDelayed(this, 10000);
        }
    };
    // 日历查询：24h 一次兜底（锁屏时由 SCREEN_OFF 广播触发即时查询）
    private final Runnable mCalendarRunnable = new Runnable() {
        @Override public void run() {
            updateCalendarInfo();
            mSyncHandler.postDelayed(this, 24 * 60 * 60 * 1000L);
        }
    };
    private String sLastCalendarJson = "";

    // 锁屏（息屏）时立即查询日历：每次锁屏都获取，无变化则跳过
    private final android.content.BroadcastReceiver mScreenOffReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
            if (android.content.Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mSyncHandler.postDelayed(() -> updateCalendarInfo(), 1500);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        // 双保险：不依赖 onListenerConnected 也能启动各轮询（服务创建即调度）
        mSyncHandler.removeCallbacksAndMessages(null);
        mSyncHandler.postDelayed(mSyncRunnable, 1000);
        mSyncHandler.postDelayed(mCardRunnable, 10000);
        mSyncHandler.postDelayed(mCalendarRunnable, 1000);
        try {
            registerReceiver(mScreenOffReceiver,
                    new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF));
        } catch (Throwable ignored) {}
    }

    @Override public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mScreenOffReceiver);
        } catch (Throwable ignored) {}
    }

    @Override public void onNotificationPosted(StatusBarNotification s) {
        if (s == null || s.getNotification() == null) return;
        sMap.put(s.getKey(), s); persist(); updateMediaInfo();
    }
    @Override public void onNotificationRemoved(StatusBarNotification s) {
        if (s == null) return;
        sMap.remove(s.getKey()); persist(); updateMediaInfo();
    }
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        syncAll();
        persist();
        updateMediaInfo();
        mSyncHandler.removeCallbacksAndMessages(null);
        mSyncHandler.postDelayed(mSyncRunnable, 1000);
        mSyncHandler.postDelayed(mCardRunnable, 10000);
        mSyncHandler.postDelayed(mCalendarRunnable, 1000);
    }

    private void syncAll() {
        try {
            StatusBarNotification[] a = getActiveNotifications();
            StringBuilder sb = new StringBuilder("syncAll active=" + (a == null ? 0 : a.length) + ":");
            if (a != null) {
                java.util.Set<String> keys = new java.util.HashSet<>();
                for (StatusBarNotification s : a) {
                    sMap.put(s.getKey(), s); keys.add(s.getKey());
                    sb.append(" ").append(s.getPackageName());
                }
                for (String k : new java.util.ArrayList<>(sMap.keySet())) {
                    if (!keys.contains(k)) sMap.remove(k);
                }
            }
            android.util.Log.i("AodChange", sb.toString());
        } catch (Exception e) {
            android.util.Log.w("AodChange", "syncAll fail", e);
        }
    }

    private void persist() {
        try {
            JSONArray regular = new JSONArray();
            JSONArray focus = new JSONArray();
            for (StatusBarNotification s : sMap.values()) {
                if (s.getNotification() == null) continue;
                boolean isFocus = s.getNotification().extras.getBoolean("miui.focus.isFocus", false)
                        || s.getNotification().extras.getParcelable("miui.focus.rv") != null
                        || s.getNotification().extras.containsKey("miui.focus.param");
                if (!isFocus) {
                    if (s.isOngoing()) continue;
                    int fl = s.getNotification().flags;
                    if ((fl & Notification.FLAG_FOREGROUND_SERVICE) != 0) continue;
                    if ((fl & Notification.FLAG_ONGOING_EVENT) != 0) continue;
                }
                if (s.getNotification().visibility == Notification.VISIBILITY_SECRET) continue;
                String t = s.getNotification().extras.getString("android.title");
                if (t == null || t.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("k", s.getKey()); o.put("p", s.getPackageName()); o.put("t", t);
                String txt = s.getNotification().extras.getString("android.text");
                String big = s.getNotification().extras.getString("android.bigText");
                o.put("x", (big != null && !big.isEmpty()) ? big : (txt != null ? txt : ""));
                o.put("pt", s.getPostTime()); o.put("v", s.getNotification().visibility);
                o.put("pr", s.getNotification().priority);
                if (isFocus) focus.put(o); else regular.put(o);
            }
            NotificationProvider.update(regular.toString());
            NotificationProvider.updateFocus(focus.toString());
        } catch (Exception ignored) {}
    }

    private void updateMediaInfo() {
        try {
            StatusBarNotification best = null;
            boolean anyPlaying = false;
            for (StatusBarNotification s : sMap.values()) {
                if (s.getNotification() == null) continue;
                if (!"transport".equals(s.getNotification().category)) continue;
                boolean curPlaying = isMediaPlaying(s.getPackageName());
                if (curPlaying) anyPlaying = true;
                if (best == null) { best = s; continue; }
                boolean bestPlaying = isMediaPlaying(best.getPackageName());
                if (curPlaying && !bestPlaying) {
                    best = s;
                } else if (curPlaying == bestPlaying) {
                    // 播放状态相同：选最新的（播放中优先最新，暂停也选最新）
                    if (s.getPostTime() > best.getPostTime()) best = s;
                }
            }
            if (best != null) {
                String title = best.getNotification().extras.getString("android.title");
                if (title != null && !title.isEmpty()) {
                    String artist = best.getNotification().extras.getString("android.text");
                    String album = "";
                    if (artist != null && artist.contains(" - ")) {
                        String[] parts = artist.split(" - ", 2);
                        artist = parts[0]; album = parts[1];
                    }
                    JSONObject o = new JSONObject();
                    o.put("t", title);
                    o.put("a", artist != null ? artist : "");
                    o.put("al", album);
                    o.put("p", isMediaPlaying(best.getPackageName()) ? 1 : 0);
                    o.put("pkg", best.getPackageName());
                    int[] colors = new int[2];
                    android.graphics.Bitmap art = getAlbumArt(best.getPackageName());
                    if (art == null) art = best.getNotification().largeIcon;
                    if (art != null) colors = extractColors(art);
                    o.put("c1", colors[0]);
                    o.put("c2", colors[1]);
                    String json = o.toString();
                    // 内容未变不更新版本：vMedia 只在媒体内容变化时递增（减少 systemui 侧 binder 读取）
                    if (json.equals(sLastMediaJson)) return;
                    sLastMediaJson = json;
                    NotificationProvider.updateLyricMedia(json);
                    return;
                }
            }
            // 无媒体：仅在状态变化时更新为空（避免每秒递增版本）
            if (!"{}".equals(sLastMediaJson)) {
                sLastMediaJson = "{}";
                NotificationProvider.updateLyricMedia("{}");
                NotificationProvider.updateLyric("{}");
            }
        } catch (Exception ignored) {}
    }

    private boolean isMediaPlaying(String pkg) {
        try {
            MediaSessionManager mgr = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = mgr.getActiveSessions(
                    new android.content.ComponentName(this, NotificationCollectorService.class));
            for (MediaController c : controllers) {
                if (pkg.equals(c.getPackageName())) {
                    android.media.session.PlaybackState state = c.getPlaybackState();
                    return state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING;
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private android.graphics.Bitmap getAlbumArt(String pkg) {
        try {
            MediaSessionManager mgr = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = mgr.getActiveSessions(
                    new android.content.ComponentName(this, NotificationCollectorService.class));
            for (MediaController c : controllers) {
                if (pkg.equals(c.getPackageName())) {
                    MediaMetadata meta = c.getMetadata();
                    if (meta != null) {
                        android.graphics.Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                        if (art != null) return art;
                        android.graphics.Bitmap icon = meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                        if (icon != null) return icon;
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private int[] extractColors(android.graphics.Bitmap bmp) {
        int[] result = {0, 0};
        try {
            int bw = bmp.getWidth(), bh = bmp.getHeight();
            // 每个色相桶存 {count, sumR, sumG, sumB}
            java.util.HashMap<Integer, long[]> freq = new java.util.HashMap<>();
            int step = Math.max(1, Math.min(bw, bh) / 10);
            for (int y = step; y < bh - step; y += step) {
                for (int x = step; x < bw - step; x += step) {
                    int px = bmp.getPixel(x, y);
                    if (android.graphics.Color.alpha(px) < 200) continue;
                    float[] h = new float[3];
                    android.graphics.Color.colorToHSV(px, h);
                    if (h[1] < 0.15f || h[2] < 0.2f || h[2] > 0.9f) continue;
                    int key = (int)(h[0] / 30) * 30;
                    long[] a = freq.get(key);
                    if (a == null) a = new long[]{0, 0, 0, 0};
                    a[0]++;
                    a[1] += android.graphics.Color.red(px);
                    a[2] += android.graphics.Color.green(px);
                    a[3] += android.graphics.Color.blue(px);
                    freq.put(key, a);
                }
            }
            if (freq.isEmpty()) {
                // 退化：取最饱和像素
                int best = android.graphics.Color.WHITE; float bestS = -1;
                for (int y = step; y < bh - step; y += step) {
                    for (int x = step; x < bw - step; x += step) {
                        int px = bmp.getPixel(x, y);
                        if (android.graphics.Color.alpha(px) < 128) continue;
                        float[] h = new float[3];
                        android.graphics.Color.colorToHSV(px, h);
                        if (h[1] * h[2] > bestS) { bestS = h[1] * h[2]; best = px; }
                    }
                }
                float[] h = new float[3]; android.graphics.Color.colorToHSV(best, h);
                h[1] = Math.max(h[1], 0.45f); h[2] = Math.max(h[2], 0.55f);
                result[0] = android.graphics.Color.HSVToColor(h);
                result[1] = result[0];
                // 保证可读性
                for (int i = 0; i < 2; i++) {
                    if (result[i] == 0) continue;
                    float[] hh = new float[3];
                    android.graphics.Color.colorToHSV(result[i], hh);
                    if (hh[2] < 0.6f) hh[2] = 0.6f;
                    if (hh[1] < 0.4f) hh[1] = 0.4f;
                    result[i] = android.graphics.Color.HSVToColor(hh);
                }
                return result;
            }
            // 取 count 最高的 2 个不同色相桶，算平均色（真实专辑色）
            java.util.List<Map.Entry<Integer, long[]>> list = new java.util.ArrayList<>(freq.entrySet());
            list.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            for (int i = 0; i < 2 && i < list.size(); i++) {
                Map.Entry<Integer, long[]> e = list.get(i);
                long[] a = e.getValue();
                int avg = android.graphics.Color.rgb(
                        (int)(a[1] / a[0]), (int)(a[2] / a[0]), (int)(a[3] / a[0]));
                result[i] = avg;
            }
            // 保证可读性：不得为深色/黑色
            for (int i = 0; i < 2; i++) {
                if (result[i] == 0) { result[i] = result[0] == 0 ? android.graphics.Color.WHITE : result[0]; continue; }
                float[] hh = new float[3];
                android.graphics.Color.colorToHSV(result[i], hh);
                if (hh[2] < 0.6f) hh[2] = 0.6f;
                if (hh[1] < 0.35f) hh[1] = 0.35f;
                result[i] = android.graphics.Color.HSVToColor(hh);
            }
        } catch (Exception e) { /* keep zeros */ }
        return result;
    }

    private void updateCalendarInfo() {
        try {
            long now = System.currentTimeMillis();
            // 只查未来 3 天内的日程（与渲染侧窗口一致），最近的排最前，最多 3 条
            long threeDaysEnd = now + 3L * 24 * 60 * 60 * 1000;
            String[] cols = {
                    android.provider.CalendarContract.Events.TITLE,
                    android.provider.CalendarContract.Events.DTSTART,
                    android.provider.CalendarContract.Events.ALL_DAY
            };
            android.database.Cursor c = getContentResolver().query(
                    android.provider.CalendarContract.Events.CONTENT_URI, cols,
                    android.provider.CalendarContract.Events.DTSTART + " >= ? AND "
                            + android.provider.CalendarContract.Events.DTSTART + " <= ?",
                    new String[]{String.valueOf(now), String.valueOf(threeDaysEnd)},
                    android.provider.CalendarContract.Events.DTSTART + " ASC");
            JSONArray arr = new JSONArray();
            if (c != null) {
                try {
                    int count = 0;
                    while (c.moveToNext() && count < 3) {
                        String t = c.getString(0);
                        long start = c.getLong(1);
                        boolean allDay = c.getInt(2) == 1;
                        if (t == null || t.isEmpty()) continue;
                        JSONObject o = new JSONObject();
                        o.put("t", t);
                        o.put("s", start);
                        o.put("a", allDay);
                        arr.put(o);
                        count++;
                    }
                } finally {
                    c.close();
                }
            }
            String json = arr.toString();
            // 无变化跳过：不更新版本号，渲染侧保持现有视图
            if (json.equals(sLastCalendarJson)) {
                android.util.Log.i("AodChange", "calendar events=" + arr.length() + " (unchanged, skip)");
                return;
            }
            sLastCalendarJson = json;
            NotificationProvider.updateCalendar(json);
            android.util.Log.i("AodChange", "calendar events=" + arr.length() + " (updated)");
        } catch (Exception e) {
            android.util.Log.w("AodChange", "calendar query fail", e);
        }
    }
}
