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
import com.google.android.material.slider.Slider;

/**
 * 组件卡片样式设置：普通通知/焦点通知/歌曲信息/自定义 四类卡片的
 * 背景颜色、描边、字体颜色、宽高大小设置；并承载迁移自歌词样式的歌曲信息设置。
 */
public class CardStyleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("卡片样式");
        toolbar.setTitleTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        toolbar.setBackgroundColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F));
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(M3.dp(this, 16), M3.dp(this, 8), M3.dp(this, 16), M3.dp(this, 24));

        addStyleSection(list, "普通通知卡片", CardStyle.P_NOTIF, true, true);
        addStyleSection(list, "焦点通知卡片", CardStyle.P_FOCUS, false, true);
        addSongInfoSection(list);
        // 自定义文字/组件卡片背景与描边已移至「自定义」设置页，分区域配置

        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    // ==================== 卡片样式区块 ====================

    private void addStyleSection(LinearLayout list, String title, String p, boolean isNotif, boolean withFont) {
        LinearLayout content = M3.cardContent(this);
        content.addView(M3.title(this, title));
        list.addView(M3.card(this, content));
        addCardStyleControls(content, p, withFont);

        if (isNotif) {
            addSlider(content, "显示个数 (0=全部折叠进图标)", "notif_max_count", 0, 3, 3);
            addModeSwitch(content, "折叠图标栏对齐", "icon_row_gravity",
                    new String[]{"left", "center", "right"}, new String[]{"靠左", "居中", "靠右"}, "left", 3);
        }
    }

    /** 卡片样式控制组：启用开关 + 背景/描边/字体 子块（宽高已移除，全部用默认） */
    private void addCardStyleControls(LinearLayout content, String p, boolean withFont) {
        final CardStyle st = CardStyle.load(this, p);
        content.addView(M3.switchRow(this, "启用自定义样式",
                "关闭则沿用默认样式", st.enabled, v ->
                writeSetting(p + "enabled", ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked())));

        // 背景：自动取色/自定义 + 颜色 + 透明度
        addColorBlock(content, "背景", p + "bg_mode", p + "bg_color", p + "bg_alpha",
                st.bgMode, st.bgColor, st.bgAlpha, true);
        // 描边：颜色 + 透明度 + 粗细
        addStrokeBlock(content, p, st);
        // 字体：自动取色/自定义 + 颜色；自定义卡片不改文字色
        if (withFont) {
            addColorBlock(content, "字体", p + "text_mode", p + "text_color", null,
                    st.textMode, st.textColor, 255, false);
        }
    }

    /** 紧凑子块：标题 + 模式段 + 颜色行 +（可选）透明度 */
    private void addColorBlock(LinearLayout parent, String label, String modeKey, String colorKey,
                               String alphaKey, String initMode, int initColor, int initAlpha, boolean withAlpha) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bbg = new android.graphics.drawable.GradientDrawable();
        bbg.setColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930));
        bbg.setCornerRadius(M3.dp(this, 10));
        block.setBackground(bbg);
        block.setPadding(M3.dp(this, 12), M3.dp(this, 6), M3.dp(this, 12), M3.dp(this, 6));

        TextView ml = new TextView(this);
        ml.setText(label);
        ml.setTextSize(14);
        ml.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        block.addView(ml);

        int idx = "custom".equals(initMode) ? 1 : 0;
        final boolean isCustom = "custom".equals(initMode);
        final LinearLayout[] ccRef = new LinearLayout[1];
        final TextView[] hintRef = new TextView[1];
        LinearLayout seg = M3.segmentGroup(this, new String[]{"自动取色", "自定义"}, new int[]{0, 1}, idx, 2, sel -> {
            writeSetting(modeKey, sel == 1 ? CardStyle.MODE_CUSTOM : CardStyle.MODE_AUTO);
            if (ccRef[0] != null) ccRef[0].setVisibility(sel == 1 ? View.VISIBLE : View.GONE);
            if (hintRef[0] != null) hintRef[0].setVisibility(sel == 1 ? View.GONE : View.VISIBLE);
        });
        block.addView(seg);

        // 自动取色提示（自动模式下显示，说明取色来源）
        TextView hint = new TextView(this);
        hint.setText("自动取色：按专辑主色");
        hint.setTextSize(12);
        hint.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        hintRef[0] = hint;
        block.addView(hint);
        hint.setVisibility(isCustom ? View.GONE : View.VISIBLE);

        LinearLayout cc = new LinearLayout(this);
        cc.setOrientation(LinearLayout.VERTICAL);
        final View[] dot = new View[1];
        addColorRow(cc, "颜色", colorKey, alphaKey, initColor, dot);
        if (withAlpha) addAlphaRow(cc, "透明度", alphaKey, initAlpha, dot, colorKey, initColor);
        cc.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        ccRef[0] = cc;
        block.addView(cc);

        block.setLayoutParams(marginRow());
        parent.addView(block);
    }

    /** 描边子块：颜色 + 透明度 + 粗细（无模式段，启用即生效） */
    private void addStrokeBlock(LinearLayout parent, String p, CardStyle st) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bbg = new android.graphics.drawable.GradientDrawable();
        bbg.setColor(M3.attrColor(this, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930));
        bbg.setCornerRadius(M3.dp(this, 10));
        block.setBackground(bbg);
        block.setPadding(M3.dp(this, 12), M3.dp(this, 6), M3.dp(this, 12), M3.dp(this, 6));

        TextView ml = new TextView(this);
        ml.setText("描边");
        ml.setTextSize(14);
        ml.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        block.addView(ml);

        final View[] dot = new View[1];
        addColorRow(block, "描边颜色", p + "stroke_color", p + "stroke_alpha", st.strokeColor, dot);
        addAlphaRow(block, "透明度", p + "stroke_alpha", st.strokeAlpha, dot, p + "stroke_color", st.strokeColor);
        addSlider(block, "粗细 (dp)", p + "stroke_width", 0, 8, st.strokeWidth);

        block.setLayoutParams(marginRow());
        parent.addView(block);
    }

    /** 颜色选择行（色点取色），alphaKey 传 null 表示不透明度（字体），返回色点引用 */
    private void addColorRow(LinearLayout parent, String label, final String colorKey,
                             final String alphaKey, final int initColor, final View[] dotRef) {
        final int dotSize = M3.dp(this, 26);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView nv = new TextView(this);
        nv.setText(label);
        nv.setTextSize(14);
        nv.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        row.addView(nv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int initA = alphaKey == null ? 255 : SettingsHelper.getInt(this, alphaKey, 255);
        final View dot = new View(this);
        dot.setBackground(colorDot(Color.argb(initA,
                Color.red(initColor), Color.green(initColor), Color.blue(initColor)), dotSize));
        row.addView(dot, new LinearLayout.LayoutParams(dotSize, dotSize));
        row.setOnClickListener(v -> M3.colorPicker(this, label,
                SettingsHelper.getInt(this, colorKey, initColor), c -> {
                    writeSetting(colorKey, c);
                    int a = alphaKey == null ? 255 : SettingsHelper.getInt(this, alphaKey, 255);
                    dot.setBackground(colorDot(Color.argb(a, Color.red(c), Color.green(c), Color.blue(c)), dotSize));
                }));
        parent.addView(row, marginRow());
        dotRef[0] = dot;
    }

    /** 透明度滑块，实时联动刷新对应色点 */
    private void addAlphaRow(LinearLayout parent, String label, final String alphaKey, int initAlpha,
                             final View[] dotRef, final String colorKey, final int initColor) {
        LinearLayout alphaCol = new LinearLayout(this);
        alphaCol.setOrientation(LinearLayout.VERTICAL);
        TextView av = new TextView(this);
        av.setText(label);
        av.setTextSize(12);
        av.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        alphaCol.addView(av);
        Slider asl = new Slider(this);
        asl.setValueFrom(0);
        asl.setValueTo(255);
        asl.setValue(initAlpha);
        asl.addOnChangeListener((s, val, fromUser) -> {
            if (!fromUser) return;
            writeSetting(alphaKey, (int) val);
            if (dotRef != null && dotRef[0] != null) {
                int c = SettingsHelper.getInt(this, colorKey, initColor);
                dotRef[0].setBackground(colorDot(Color.argb((int) val,
                        Color.red(c), Color.green(c), Color.blue(c)), M3.dp(this, 26)));
            }
        });
        alphaCol.addView(asl);
        parent.addView(alphaCol, marginRow());
    }

    // ==================== 歌曲信息设置（卡片样式 + 内容设置合并） ====================

    private void addSongInfoSection(LinearLayout list) {
        LinearLayout content = M3.cardContent(this);
        content.addView(M3.title(this, "歌曲信息"));
        list.addView(M3.card(this, content));

        // 卡片样式（背景/描边/字体）
        addCardStyleControls(content, CardStyle.P_SONG, true);

        // 内容设置
        TextView layoutHint = new TextView(this);
        layoutHint.setText("以下设置不受启用自定义样式的影响，可以直接调节。");
        layoutHint.setTextSize(12);
        layoutHint.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        LinearLayout.LayoutParams lhlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lhlp.topMargin = dp(4);
        lhlp.bottomMargin = dp(8);
        content.addView(layoutHint, lhlp);
        addSwitch(content, "显示歌曲信息卡片", "info_show_card", true);
        addModeSwitch(content, "排版", "info_gravity",
                new String[]{"", "left", "center", "right"}, new String[]{"自动", "靠左", "居中", "靠右"}, "", 2);
        addSwitch(content, "封面图标", "title_icon_albumart", false);
        addSwitch(content, "标题图标", "title_icon_title", false);
        addSlider(content, "图标大小", "title_icon_size", 12, 28, 18);
        addModeSwitch(content, "副标题", "title_bracket_mode",
                new String[]{"default", "remove", "shrink", "line"}, new String[]{"默认", "去掉", "缩小", "分行"}, "default", 2);
        // 两个图标的自定义取色
        addColorBlock(content, "标题图标取色", "title_icon_title_mode", "title_icon_title_color",
                null, "auto", 0xFFFFFF, 255, false);
        addColorBlock(content, "封面图标取色", "title_icon_albumart_mode", "title_icon_albumart_color",
                null, "auto", 0xFFFFFF, 255, false);
        addInfoToggleGroup(content);
    }

    private void addInfoToggleGroup(LinearLayout parent) {
        final com.google.android.material.materialswitch.MaterialSwitch[] switches =
                new com.google.android.material.materialswitch.MaterialSwitch[5];
        final String[] keys = {"info_show_title", "info_show_artist", "info_show_albumart", "info_show_albumname"};
        final boolean[] defs = {true, true, false, false};
        final String[] labels = {"歌名", "歌手", "封面", "专辑名"};

        for (int i = 0; i < 4; i++) {
            final int fi = i;
            final com.google.android.material.materialswitch.MaterialSwitch[] swRef =
                    new com.google.android.material.materialswitch.MaterialSwitch[1];
            LinearLayout row = M3.switchRow(this, labels[i], "", SettingsHelper.get(this, keys[i], defs[i]), v -> {
                boolean checked = swRef[0].isChecked();
                writeSetting(keys[fi], checked);
                if (checked) {
                    switches[4].setOnCheckedChangeListener(null);
                    switches[4].setChecked(false);
                    attachAllOffListener(switches, keys);
                }
            });
            swRef[0] = (com.google.android.material.materialswitch.MaterialSwitch) row.getChildAt(1);
            switches[i] = swRef[0];
            parent.addView(row);
        }

        final com.google.android.material.materialswitch.MaterialSwitch[] allSwRef =
                new com.google.android.material.materialswitch.MaterialSwitch[1];
        LinearLayout allRow = M3.switchRow(this, "全部隐藏", "隐藏所有歌曲信息", false, v -> {
            boolean checked = allSwRef[0].isChecked();
            for (int i = 0; i < 4; i++) {
                switches[i].setOnCheckedChangeListener(null);
                switches[i].setChecked(!checked);
                writeSetting(keys[i], !checked);
            }
            attachAllOffListener(switches, keys);
        });
        allSwRef[0] = (com.google.android.material.materialswitch.MaterialSwitch) allRow.getChildAt(1);
        switches[4] = allSwRef[0];
        parent.addView(allRow);

        boolean allOff = true;
        for (int i = 0; i < 4; i++) {
            if (SettingsHelper.get(this, keys[i], defs[i])) { allOff = false; break; }
        }
        switches[4].setChecked(allOff);
    }

    private void attachAllOffListener(
            final com.google.android.material.materialswitch.MaterialSwitch[] switches, final String[] keys) {
        switches[4].setOnCheckedChangeListener((b, checked) -> {
            for (int i = 0; i < 4; i++) {
                switches[i].setOnCheckedChangeListener(null);
                switches[i].setChecked(!checked);
                writeSetting(keys[i], !checked);
            }
        });
    }

    // ==================== 通用控件辅助 ====================

    private void addSwitch(LinearLayout parent, String name, String key, boolean def) {
        parent.addView(M3.switchRow(this, name, "", SettingsHelper.get(this, key, def),
                v -> writeSetting(key, ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked())));
    }

    private void addSlider(LinearLayout parent, String name, String key, int min, int max, int def) {
        parent.addView(M3.sliderRow(this, name, min, max, SettingsHelper.getInt(this, key, def),
                (s, val, fromUser) -> { if (fromUser) writeSetting(key, (int) val); }));
    }

    private void addSlider(LinearLayout parent, String name, String key, int min, int max, float def) {
        parent.addView(M3.sliderRow(this, name, min, max, SettingsHelper.getFloat(this, key, def),
                (s, val, fromUser) -> { if (fromUser) writeSetting(key, (float) val); }));
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
        final java.util.List<com.google.android.material.button.MaterialButton> btns =
                new java.util.ArrayList<>();
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
        row.setLayoutParams(marginRow());
        parent.addView(row);
    }

    private LinearLayout.LayoutParams marginRow() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private android.graphics.drawable.GradientDrawable colorDot(int argb, int size) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(argb);
        bg.setCornerRadius(size / 2f);
        return bg;
    }

    private void writeSetting(String key, Object value) {
        SettingsHelper.writeSetting(this, key, value);
    }

    private int dp(float d) {
        return M3.dp(this, d);
    }
}