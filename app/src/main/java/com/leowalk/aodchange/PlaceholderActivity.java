package com.leowalk.aodchange;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

public class PlaceholderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("自定义");
        toolbar.setTitleTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        toolbar.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(M3.dp(this, 16), M3.dp(this, 8), M3.dp(this, 16), M3.dp(this, 24));

        // 自定义文字（一个卡片：开关 + 文本 + 底部保存按钮）
        LinearLayout txtContent = M3.cardContent(this);
        txtContent.addView(M3.title(this, "自定义文字"));
        addModeSwitch(txtContent, "排版", "placeholder_gravity",
                new String[]{"left", "center", "right"}, new String[]{"靠左", "居中", "靠右"}, "center", 3);
        txtContent.addView(M3.switchRow(this, "显示文字", "无媒体播放时显示以下文字",
                SettingsHelper.get(this, "placeholder_test", false),
                v -> writeSetting("placeholder_test", ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked())));
        LinearLayout textRow = M3.editRowMulti(this, "自定义文本",
                SettingsHelper.getString(this, "placeholder_text", "农历八月十五 · 中秋"),
                "农历八月十五 · 中秋");
        txtContent.addView(textRow);
        com.google.android.material.button.MaterialButton saveBtn = new com.google.android.material.button.MaterialButton(this);
        saveBtn.setText("保存");
        saveBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        saveBtn.setOnClickListener(v -> {
            try {
                String text = ((android.widget.EditText) textRow.getTag()).getText().toString();
                writeSetting("placeholder_text", text);
                android.widget.Toast.makeText(this, "已保存", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                android.widget.Toast.makeText(this, "保存失败", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        txtContent.addView(saveBtn);
        list.addView(M3.card(this, txtContent));

        // 日历日程（独立卡片）
        LinearLayout calContent = M3.cardContent(this);
        calContent.addView(M3.title(this, "日历日程"));
        addModeSwitch(calContent, "排版", "calendar_gravity",
                new String[]{"left", "center", "right"}, new String[]{"靠左", "居中", "靠右"}, "left", 3);
        calContent.addView(M3.switchRow(this, "显示日程", "无媒体播放时显示未来三天内的日程，按开始时间优先（24 小时内开始的高亮提醒）",
                SettingsHelper.get(this, "calendar_enabled", false),
                v -> {
                    writeSetting("calendar_enabled", ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked());
                    if (((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked()) {
                        requestCalendarPermission();
                    }
                }));
        calContent.addView(M3.switchRow(this, "显示节日节气",
                "与日程按时间次序混排显示（今天/明天/后天/M月d日）",
                SettingsHelper.get(this, "calendar_show_festival", false),
                v -> writeSetting("calendar_show_festival",
                        ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked())));
        calContent.addView(M3.tipContent(this, "仅显示未来三天内即将到来的日程（最多三条），最近的排最前，即将开始（24 小时内）的日程高亮提醒。首次开启需要授予“日历”权限。"));
        list.addView(M3.card(this, calContent));

        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void requestCalendarPermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 100);
            }
        } catch (Exception ignored) {}
    }

    private void writeSetting(String key, Object value) {
        SettingsHelper.writeSetting(this, key, value);
    }

    private void addModeSwitch(LinearLayout parent, String name, String key,
                               String[] values, String[] labels, String def, int columns) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView nv = new TextView(this);
        nv.setText(name);
        nv.setTextSize(15);
        nv.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv);
        String cur = SettingsHelper.getString(this, key, def);
        final java.util.List<com.google.android.material.button.MaterialButton> btns = new java.util.ArrayList<>();
        com.google.android.material.button.MaterialButtonToggleGroup group =
                new com.google.android.material.button.MaterialButtonToggleGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        for (int i = 0; i < values.length; i++) {
            final String val = values[i];
            com.google.android.material.button.MaterialButton btn = M3.segmentButton(this, labels[i], val.equals(cur));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) blp.leftMargin = M3.dp(this, 4);
            group.addView(btn, blp);
            btns.add(btn);
        }
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < btns.size(); i++) {
                if (btns.get(i).getId() == checkedId) {
                    writeSetting(key, values[i]);
                    for (int j = 0; j < btns.size(); j++) {
                        btns.get(j).setChecked(j == i);
                    }
                    break;
                }
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = M3.dp(this, 6);
        row.addView(group, glp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = M3.dp(this, 8);
        row.setLayoutParams(rlp);
        parent.addView(row);
    }
}
