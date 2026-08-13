package com.leowalk.aodchange;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("AodChange");
        toolbar.setTitleTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        toolbar.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));

        android.widget.TextView restart = new android.widget.TextView(this);
        restart.setText("重启界面");
        restart.setTextSize(14);
        restart.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF));
        restart.setPadding(M3.dp(this, 16), M3.dp(this, 6), M3.dp(this, 16), M3.dp(this, 6));
        restart.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
                    p.waitFor();
                } catch (Exception ignored) {}
            }).start();
        });
        toolbar.addView(restart, new androidx.appcompat.widget.Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(M3.dp(this, 16), M3.dp(this, 8), M3.dp(this, 16), M3.dp(this, 24));

        // 权限与状态
        LinearLayout permContent = M3.cardContent(this);
        permContent.addView(M3.title(this, "权限与状态"));
        boolean notifAccess = hasNotificationAccess();
        boolean postNotif = hasPostNotificationPermission();
        boolean calendar = hasCalendarPermission();
        permContent.addView(M3.permissionRow(this, "通知使用权", notifAccess,
                v -> startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permContent.addView(M3.permissionRow(this, "发送通知", postNotif,
                    v -> startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()))));
        }
        permContent.addView(M3.permissionRow(this, "日历权限", calendar,
                v -> requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 101)));
        list.addView(M3.card(this, permContent));

        list.addView(M3.clickRow(this, "歌词样式", "歌词/多行/歌曲信息/封面等样式设置\n需要安装激活 LyricFocus 并打开“aodchange 外部渲染”选项，本功能不使用焦点通知，而是使用自渲染的方式显示焦点通知，因此LyricFocus的万象息屏AOD歌词不能在本应用启用时显示。",
                v -> startActivity(new android.content.Intent(this, LyricStyleActivity.class))));

        list.addView(M3.clickRow(this, "卡片样式", "普通通知/焦点通知/歌曲信息/自定义卡片的背景、描边、字体颜色设置",
                v -> startActivity(new android.content.Intent(this, CardStyleActivity.class))));

        list.addView(M3.clickRow(this, "自定义", "自定义文字/日历日程",
                v -> startActivity(new android.content.Intent(this, PlaceholderActivity.class))));

        list.addView(M3.clickRow(this, "关于", "版本号与项目地址",
                v -> startActivity(new android.content.Intent(this, AboutActivity.class))));

        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        writeConfig();
        setContentView(root);
    }

    private void writeConfig() {
        try {
            JSONObject o = SettingsHelper.loadSettings(this);
            o.put("notif_enabled", true);
            o.put("focus_enabled", true);
            o.put("battery_reposition", true);
            o.put("realtime_tick", true);
            o.put("disable_double_screen", true);
            o.put("fixed_clock", true);
            // 清理已删除功能的残留设置
            String[] stale = {
                "alarm_enabled", "gif_enabled", "gif_path",
                "refresh_interval", "lyric_realtime_refresh", "realtime_refresh_ms",
                "aod_lyric_high_fps", "aod_brightness_boost", "aod_keep_brightness",
                "aod_brightness_fixed", "aod_brightness_value"
            };
            for (String k : stale) o.remove(k);
            SettingsHelper.saveSettings(this, o);
        } catch (Exception ignored) {}
    }

    private boolean hasNotificationAccess() {
        try {
            String flat = android.provider.Settings.Secure.getString(getContentResolver(),
                    "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private boolean hasPostNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
