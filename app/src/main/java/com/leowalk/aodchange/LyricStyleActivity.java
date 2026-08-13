package com.leowalk.aodchange;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

public class LyricStyleActivity extends AppCompatActivity {

    private LinearLayout sCardContent;

    private LinearLayout sectionContent(LinearLayout parent, String label) {
        LinearLayout content = M3.cardContent(this);
        content.addView(M3.title(this, label));
        parent.addView(M3.card(this, content));
        return content;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("歌词样式");
        toolbar.setTitleTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        toolbar.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(M3.dp(this, 16), M3.dp(this, 8), M3.dp(this, 16), M3.dp(this, 24));

        sCardContent = sectionContent(list, "歌词显示");
        addSwitch(sCardContent, "显示歌词", "lyric_show", false);
        addSlider(sCardContent, "歌词字号", "lyric_text_size", 12f, 32f, 20f);
        addSlider(sCardContent, "歌词宽度", "lyric_width_percent", 30, 90, 100);
        addLineMode(sCardContent, "歌词行数", "lyric_max_lines", 2);
        addLineMode(sCardContent, "翻译行数", "translation_max_lines", 1);
        addModeSwitch(sCardContent, "对齐", "lyric_gravity", new String[]{"left", "center", "right"}, new String[]{"左", "中", "右"}, "center");
        addSwitch(sCardContent, "翻译原文互换", "swap_lyric_translation", false);
        addSlider(sCardContent, "歌词同步提前(ms)", "lyric_advance_ms", -1000, 1000, 200);
        addSwitch(sCardContent, "显示前奏", "lyric_show_intro", false);

        sCardContent = sectionContent(list, "颜色");
        addSwitch(sCardContent, "Monet 取色", "lyric_monet", false);
        addColorMode(sCardContent);

        sCardContent = sectionContent(list, "音乐应用白名单");
        final com.google.android.material.materialswitch.MaterialSwitch[] whitelistSwRef =
                new com.google.android.material.materialswitch.MaterialSwitch[1];
        final View[] manageRowRef = new View[1];
        LinearLayout whitelistRow = M3.switchRow(this, "启用白名单", "开启后仅白名单内应用显示音乐信息与歌词", SettingsHelper.get(this, "music_whitelist_enabled", false),
                v -> {
                    boolean checked = whitelistSwRef[0].isChecked();
                    writeSetting("music_whitelist_enabled", checked);
                    if (checked && SettingsHelper.getWhitelist(this).isEmpty()) {
                        SettingsHelper.saveWhitelist(this, new java.util.ArrayList<>(java.util.Arrays.asList(
                                "com.netease.cloudmusic", "com.tencent.qqmusic", "com.luna.music",
                                "com.miui.player", "com.kugou.android", "com.kuwo.kwmusiccar",
                                "cn.kuwo.player", "com.apple.android.music",
                                "com.google.android.apps.youtube.music", "com.spotify.music")));
                    }
                    if (manageRowRef[0] != null) {
                        manageRowRef[0].setAlpha(checked ? 1f : 0.5f);
                        manageRowRef[0].setEnabled(checked);
                    }
                });
        whitelistSwRef[0] = (com.google.android.material.materialswitch.MaterialSwitch) whitelistRow.getChildAt(1);
        sCardContent.addView(whitelistRow);
        boolean wlEnabled = SettingsHelper.get(this, "music_whitelist_enabled", false);
        View manageRow = M3.clickRow(this, "管理白名单应用", "添加/移除允许显示音乐信息与歌词的应用",
                v -> startActivity(new android.content.Intent(this, AppWhitelistActivity.class)));
        manageRowRef[0] = manageRow;
        manageRow.setAlpha(wlEnabled ? 1f : 0.5f);
        manageRow.setEnabled(wlEnabled);
        sCardContent.addView(manageRow);

        sCardContent = sectionContent(list, "多行歌词");
        addSwitch(sCardContent, "多行歌词", "multi_line", false);
        addSwitch(sCardContent, "显示翻译", "multi_line_show_translation", true);
        addSwitch(sCardContent, "显示通知图标", "multi_show_notif_icons", false);
        addModeSwitch(sCardContent, "歌词图标栏对齐", "multi_icon_row_gravity",
                new String[]{"left", "center", "right"}, new String[]{"靠左", "居中", "靠右"}, "left", 3);
        addSlider(sCardContent, "行距", "multi_line_spacing", 0, 30, 14);
        addSlider(sCardContent, "下沿位置", "multi_bottom_margin", 60, 600, 165);

        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void addSwitch(LinearLayout parent, String name, String key, boolean def) {
        parent.addView(M3.switchRow(this, name, "", SettingsHelper.get(this, key, def),
                v -> writeSetting(key, ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked())));
    }

    private void addSlider(LinearLayout parent, String name, String key, int min, int max, int def) {
        parent.addView(M3.sliderRow(this, name, min, max, SettingsHelper.getInt(this, key, def),
                (s, val, fromUser) -> { if (fromUser) writeSetting(key, (int) val); }));
    }

    private void addSlider(LinearLayout parent, String name, String key, float min, float max, float def) {
        parent.addView(M3.sliderRow(this, name, min, max, SettingsHelper.getFloat(this, key, def),
                (s, val, fromUser) -> { if (fromUser) writeSetting(key, (float) val); }));
    }

    private void addModeSwitch(LinearLayout parent, String name, String key, String[] values, String[] labels, String def) {
        addModeSwitch(parent, name, key, values, labels, def, values.length);
    }

    private void addModeSwitch(LinearLayout parent, String name, String key, String[] values, String[] labels, String def, int columns) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView nv = new TextView(this);
        nv.setText(name);
        nv.setTextSize(15);
        nv.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv);

        String cur = SettingsHelper.getString(this, key, def);
        final java.util.List<com.google.android.material.button.MaterialButton> btns =
                new java.util.ArrayList<>();
        // 所有选项占满一行，等宽排布
        com.google.android.material.button.MaterialButtonToggleGroup group =
                new com.google.android.material.button.MaterialButtonToggleGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        for (int i = 0; i < values.length; i++) {
            final String val = values[i];
            com.google.android.material.button.MaterialButton btn = M3.segmentButton(this, labels[i], val.equals(cur));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) blp.leftMargin = dp(4);
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
        glp.topMargin = dp(6);
        row.addView(group, glp);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(12);
        row.setLayoutParams(rlp);
        parent.addView(row);
    }

    // 歌词行数/翻译行数：默认/一行/二行
    private void addLineMode(LinearLayout parent, String name, String key, int defLines) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView nv = new TextView(this);
        nv.setText(name);
        nv.setTextSize(15);
        nv.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv);

        com.google.android.material.button.MaterialButtonToggleGroup group =
                new com.google.android.material.button.MaterialButtonToggleGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);

        // 存储值：-1=默认(跟随模块默认), 1=一行, 2=二行
        final int[] vals = {-1, 1, 2};
        final String[] labels = {"默认", "一行", "二行"};
        int cur = SettingsHelper.getInt(this, key, defLines);
        int curIdx = (cur == 1) ? 1 : (cur == 2) ? 2 : 0;
        final java.util.List<com.google.android.material.button.MaterialButton> btns =
                new java.util.ArrayList<>();
        for (int i = 0; i < vals.length; i++) {
            com.google.android.material.button.MaterialButton btn = M3.segmentButton(this, labels[i], i == curIdx);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) blp.leftMargin = dp(4);
            group.addView(btn, blp);
            btns.add(btn);
        }
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < btns.size(); i++) {
                if (btns.get(i).getId() == checkedId) {
                    writeSetting(key, vals[i]);
                    break;
                }
            }
        });

        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(6);
        glp.bottomMargin = dp(4);
        row.addView(group, glp);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(12);
        parent.addView(row, rlp);
    }

    private LinearLayout.LayoutParams marginRow() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private void addColorMode(LinearLayout parent) {
        // 颜色模式选择（白色/专辑/预设/自定义）
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView modeLabel = new TextView(this);
        modeLabel.setText("颜色模式");
        modeLabel.setTextSize(15);
        modeLabel.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        modeRow.addView(modeLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.Spinner spinner = new android.widget.Spinner(this);
        final String[] modeLabels = {"白色", "专辑主色", "预设", "自定义"};
        final String[] modeValues = {"white", "album", "preset", "custom"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, modeLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String curMode = SettingsHelper.getString(this, "lyric_color_mode", "white");
        int curIdx = 0;
        for (int i = 0; i < modeValues.length; i++) {
            if (modeValues[i].equals(curMode)) { curIdx = i; break; }
        }
        spinner.setSelection(curIdx);
        modeRow.addView(spinner);
        parent.addView(modeRow, marginRow());

        // 预设色板容器（选预设时显示）
        final LinearLayout presetContainer = new LinearLayout(this);
        presetContainer.setOrientation(LinearLayout.VERTICAL);
        TextView presetLabel = new TextView(this);
        presetLabel.setText("预设颜色");
        presetLabel.setTextSize(15);
        presetLabel.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        presetContainer.addView(presetLabel);
        int[] presets = {
            Color.WHITE, 0xFFF5F5F5, 0xFFFFF8E1, 0xFFECEFF1,
            0xFFB0BEC5, 0xFFB3E5FC, 0xFF81D4FA, 0xFF80DEEA,
            0xFFA5D6A7, 0xFFC5E1A5, 0xFFE6EE9C, 0xFFFFF59D,
            0xFFFFCCBC, 0xFFFFAB91, 0xFFF48FB1, 0xFFF8BBD0,
            0xFFE1BEE7, 0xFFCE93D8, 0xFFB39DDB, 0xFFFFE082,
            0xFFFFCC80, 0xFF90CAF9, 0xFF80CBC4, 0xFFF06292
        };
        final int dotSize = dp(26);
        final int perRow = 6;
        int curColor = SettingsHelper.getInt(this, "lyric_custom_color", Color.WHITE);
        for (int r = 0; r < (presets.length + perRow - 1) / perRow; r++) {
            LinearLayout presetRow = new LinearLayout(this);
            presetRow.setOrientation(LinearLayout.HORIZONTAL);
            presetRow.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            for (int col = 0; col < perRow; col++) {
                final int idx = r * perRow + col;
                if (idx >= presets.length) break;
                final int c = presets[idx];
                View dot = new View(this);
                dot.setBackground(makeDotDrawable(c, c == curColor, dotSize));
                dot.setOnClickListener(v -> {
                    writeSetting("lyric_custom_color", c);
                    for (int j = 0; j < presetContainer.getChildCount(); j++) {
                        View d = presetContainer.getChildAt(j);
                        if (d instanceof LinearLayout) {
                            LinearLayout ll = (LinearLayout) d;
                            for (int k = 0; k < ll.getChildCount(); k++) {
                                int pidx = j * perRow + k;
                                if (pidx < presets.length) {
                                    ll.getChildAt(k).setBackground(
                                            makeDotDrawable(presets[pidx], presets[pidx] == c, dotSize));
                                }
                            }
                        }
                    }
                });
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dotSize, dotSize);
                dlp.rightMargin = dp(6);
                dlp.bottomMargin = dp(6);
                presetRow.addView(dot, dlp);
            }
            presetContainer.addView(presetRow);
        }
        presetContainer.setVisibility("preset".equals(curMode) ? View.VISIBLE : View.GONE);
        parent.addView(presetContainer, marginRow());

        // 自定义调色盘（RGB 滑块）
        final LinearLayout customContainer = new LinearLayout(this);
        customContainer.setOrientation(LinearLayout.VERTICAL);
        TextView customLabel = new TextView(this);
        customLabel.setText("自定义颜色");
        customLabel.setTextSize(15);
        customLabel.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        customContainer.addView(customLabel);

        int cc = SettingsHelper.getInt(this, "lyric_custom_color", Color.WHITE);
        if (cc == 0) cc = Color.WHITE;
        final int[] rgb = {Color.red(cc), Color.green(cc), Color.blue(cc)};
        final View preview = new View(this);
        int psize = dp(40);
        preview.setBackground(makeDotDrawable(cc, false, psize));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(psize, psize);
        plp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        plp.bottomMargin = dp(8);
        customContainer.addView(preview, plp);

        // HEX 代码输入
        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView hexLabel = new TextView(this);
        hexLabel.setText("颜色代码");
        hexLabel.setTextSize(13);
        hexLabel.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        hexRow.addView(hexLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.EditText hexInput = new android.widget.EditText(this);
        hexInput.setText(String.format("#%06X", 0xFFFFFF & cc));
        hexInput.setSingleLine(true);
        hexInput.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        hexInput.setHintTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        hexRow.addView(hexInput, new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT));
        customContainer.addView(hexRow, marginRow());

        hexInput.setOnEditorActionListener((v, actionId, event) -> {
            applyHexColor(hexInput, rgb, preview);
            return true;
        });
        android.widget.Button hexBtn = new android.widget.Button(this);
        hexBtn.setText("应用");
        hexBtn.setOnClickListener(v -> applyHexColor(hexInput, rgb, preview));
        hexRow.addView(hexBtn);

        addRgbSlider(customContainer, "红", rgb, 0, preview);
        addRgbSlider(customContainer, "绿", rgb, 1, preview);
        addRgbSlider(customContainer, "蓝", rgb, 2, preview);

        customContainer.setVisibility("custom".equals(curMode) ? View.VISIBLE : View.GONE);
        parent.addView(customContainer, marginRow());

        // 模式切换联动
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                writeSetting("lyric_color_mode", modeValues[pos]);
                presetContainer.setVisibility("preset".equals(modeValues[pos]) ? View.VISIBLE : View.GONE);
                customContainer.setVisibility("custom".equals(modeValues[pos]) ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    private void applyHexColor(android.widget.EditText input, int[] rgb, View preview) {
        try {
            String text = input.getText().toString().trim();
            if (text.startsWith("#")) text = text.substring(1);
            int color = (int) Long.parseLong(text, 16) & 0xFFFFFF;
            color |= 0xFF000000;
            rgb[0] = Color.red(color);
            rgb[1] = Color.green(color);
            rgb[2] = Color.blue(color);
            writeSetting("lyric_custom_color", color);
            preview.setBackground(makeDotDrawable(color, false, dp(40)));
        } catch (Exception ignored) {}
    }

    private void addRgbSlider(LinearLayout parent, String name, final int[] rgb, final int idx, final View preview) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView nv = new TextView(this);
        nv.setText(name);
        nv.setTextSize(13);
        nv.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv);
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(255);
        sb.setProgress(rgb[idx]);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int v, boolean fromUser) {
                if (fromUser) {
                    rgb[idx] = v;
                    int color = Color.rgb(rgb[0], rgb[1], rgb[2]);
                    writeSetting("lyric_custom_color", color);
                    preview.setBackground(makeDotDrawable(color, false, dp(40)));
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        row.addView(sb);
        parent.addView(row, marginRow());
    }

    private android.graphics.drawable.GradientDrawable makeDotDrawable(int color, boolean selected, int size) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(size / 2f);
        if (selected) bg.setStroke(dp(3), Color.WHITE);
        return bg;
    }

    private void writeSetting(String key, Object value) {
        SettingsHelper.writeSetting(this, key, value);
    }

    private int dp(float d) {
        return M3.dp(this, d);
    }
}
