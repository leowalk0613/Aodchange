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
import com.leowalk.aodchange.hook.CustomContentHook;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class PlaceholderActivity extends AppCompatActivity {

    private static final int REQ_CALENDAR = 100;

    private final List<String> mSelectedWidgets = new ArrayList<>();
    private LinearLayout mLineColorHost;
    private android.widget.EditText mTextEdit;
    private TextView mCalendarPermHint;
    private LinearLayout mFestivalRow;

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

        migrateLegacyWidgets();
        mSelectedWidgets.clear();
        mSelectedWidgets.addAll(CustomContentHook.parseSelectedWidgets(this));

        // ========== 区域一：自定义文字 ==========
        LinearLayout txtContent = M3.cardContent(this);
        txtContent.addView(M3.title(this, "区域一 · 自定义文字"));
        addModeSwitch(txtContent, "排版", "placeholder_gravity",
                new String[]{"left", "center", "right"}, new String[]{"靠左", "居中", "靠右"}, "center");
        txtContent.addView(M3.switchRow(this, "显示文字", "无媒体播放时显示以下文字",
                SettingsHelper.get(this, "placeholder_test", false),
                v -> writeSetting("placeholder_test",
                        ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked())));

        LinearLayout textRow = M3.editRowMulti(this, "自定义文本（最多 4 行）",
                SettingsHelper.getString(this, "placeholder_text", "农历八月十五 · 中秋"),
                "农历八月十五 · 中秋");
        mTextEdit = (android.widget.EditText) textRow.getTag();
        txtContent.addView(textRow);

        TextView colorTitle = new TextView(this);
        colorTitle.setText("各行文字颜色");
        colorTitle.setTextSize(14);
        colorTitle.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        LinearLayout.LayoutParams ctlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctlp.topMargin = M3.dp(this, 4);
        colorTitle.setLayoutParams(ctlp);
        txtContent.addView(colorTitle);

        mLineColorHost = new LinearLayout(this);
        mLineColorHost.setOrientation(LinearLayout.VERTICAL);
        txtContent.addView(mLineColorHost);
        refreshLineColorRows();

        mTextEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                refreshLineColorRows();
            }
        });

        com.google.android.material.button.MaterialButton saveBtn =
                new com.google.android.material.button.MaterialButton(this);
        saveBtn.setText("保存文字与颜色");
        saveBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        saveBtn.setOnClickListener(v -> saveTextAndColors());
        txtContent.addView(saveBtn);

        addCardStyleSection(txtContent, "文字卡片背景与描边", CardStyle.P_CUSTOM_TEXT);
        list.addView(M3.card(this, txtContent));

        // ========== 区域二：小组件 ==========
        LinearLayout widgetContent = M3.cardContent(this);
        widgetContent.addView(M3.title(this, "区域二 · 小组件"));
        widgetContent.addView(M3.tipContent(this,
                "横向最多选择 4 个组件；天气/步数/站立等依赖系统已就绪的天气与健康数据；"
                        + "日程需授予日历权限后才会查询。"));

        TextView pickerTitle = new TextView(this);
        pickerTitle.setText("可选组件（点选，最多 4 个，顺序=显示顺序）");
        pickerTitle.setTextSize(14);
        pickerTitle.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        widgetContent.addView(pickerTitle);

        LinearLayout chipHost = new LinearLayout(this);
        chipHost.setOrientation(LinearLayout.VERTICAL);
        widgetContent.addView(chipHost);
        buildWidgetChips(chipHost);

        mFestivalRow = M3.switchRow(this, "日程含节日节气",
                "开启后与日程按时间混排，取最近一条",
                SettingsHelper.get(this, "calendar_show_festival", false),
                v -> writeSetting("calendar_show_festival",
                        ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked()));
        widgetContent.addView(mFestivalRow);
        updateFestivalVisibility();

        boolean calGranted = hasCalendarPermission();
        mCalendarPermHint = new TextView(this);
        mCalendarPermHint.setTextSize(13);
        mCalendarPermHint.setPadding(0, M3.dp(this, 4), 0, M3.dp(this, 4));
        updateCalendarPermHint(calGranted);
        mCalendarPermHint.setOnClickListener(v -> {
            if (!hasCalendarPermission()) requestCalendarPermission();
        });
        widgetContent.addView(mCalendarPermHint);

        addCardStyleSection(widgetContent, "组件卡片背景与描边", CardStyle.P_CUSTOM_WIDGET);
        list.addView(M3.card(this, widgetContent));

        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCalendarPermHint != null) updateCalendarPermHint(hasCalendarPermission());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR) {
            boolean ok = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            updateCalendarPermHint(ok);
            if (!ok) {
                android.widget.Toast.makeText(this, "未授予日历权限，日程组件将无法获取数据",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void migrateLegacyWidgets() {
        String raw = SettingsHelper.getString(this, "custom_widgets", "");
        if ((raw == null || raw.isEmpty())
                && SettingsHelper.get(this, "calendar_enabled", false)) {
            writeSetting("custom_widgets", "schedule");
        }
    }

    private void saveTextAndColors() {
        try {
            String text = mTextEdit.getText().toString();
            // 限制最多 4 行
            String[] lines = text.split("\n", -1);
            if (lines.length > 4) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(lines[i]);
                }
                text = sb.toString();
                mTextEdit.setText(text);
            }
            writeSetting("placeholder_text", text);
            writeSetting("placeholder_line_colors", collectLineColorsJson());
            android.widget.Toast.makeText(this, "已保存", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "保存失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshLineColorRows() {
        if (mLineColorHost == null || mTextEdit == null) return;
        String text = mTextEdit.getText() != null ? mTextEdit.getText().toString() : "";
        String[] lines = text.split("\n", -1);
        int count = Math.min(Math.max(lines.length, 1), 4);
        int[] existing = loadLineColors(count);

        // 保留已有色点颜色
        int[] keep = new int[count];
        for (int i = 0; i < count; i++) {
            if (i < mLineColorHost.getChildCount()) {
                View row = mLineColorHost.getChildAt(i);
                Object tag = row.getTag();
                keep[i] = tag instanceof Integer ? (Integer) tag : existing[i];
            } else {
                keep[i] = existing[i];
            }
        }

        mLineColorHost.removeAllViews();
        for (int i = 0; i < count; i++) {
            final int index = i;
            final int[] color = {keep[i] & 0xFFFFFF};
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setTag(color[0]);

            TextView label = new TextView(this);
            String preview = lines[i].isEmpty() ? "(空行)" : lines[i];
            if (preview.length() > 12) preview = preview.substring(0, 12) + "…";
            label.setText("第" + (i + 1) + "行 · " + preview);
            label.setTextSize(13);
            label.setTextColor(M3.attrColor(this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final int dotSize = M3.dp(this, 26);
            final View dot = new View(this);
            dot.setBackground(colorDot(Color.argb(255, Color.red(color[0]),
                    Color.green(color[0]), Color.blue(color[0])), dotSize));
            row.addView(dot, new LinearLayout.LayoutParams(dotSize, dotSize));
            row.setOnClickListener(v -> M3.colorPicker(this, "第" + (index + 1) + "行颜色",
                    color[0], c -> {
                        color[0] = c & 0xFFFFFF;
                        row.setTag(color[0]);
                        dot.setBackground(colorDot(Color.argb(255, Color.red(c),
                                Color.green(c), Color.blue(c)), dotSize));
                    }));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = M3.dp(this, 6);
            mLineColorHost.addView(row, rlp);
        }
    }

    private int[] loadLineColors(int count) {
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) colors[i] = 0xFFFFFF;
        try {
            String json = SettingsHelper.getString(this, "placeholder_line_colors", "");
            if (json == null || json.isEmpty()) return colors;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < count && i < arr.length(); i++) {
                colors[i] = arr.optInt(i, 0xFFFFFF) & 0xFFFFFF;
            }
        } catch (Throwable ignored) {}
        return colors;
    }

    private String collectLineColorsJson() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < mLineColorHost.getChildCount(); i++) {
            Object tag = mLineColorHost.getChildAt(i).getTag();
            arr.put(tag instanceof Integer ? (Integer) tag : 0xFFFFFF);
        }
        return arr.toString();
    }

    private void buildWidgetChips(LinearLayout host) {
        host.removeAllViews();
        final List<com.google.android.material.button.MaterialButton> btns = new ArrayList<>();
        int cols = 3;
        int rows = (CustomContentHook.WIDGET_IDS.length + cols - 1) / cols;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int end = Math.min((r + 1) * cols, CustomContentHook.WIDGET_IDS.length);
            for (int i = r * cols; i < end; i++) {
                final String id = CustomContentHook.WIDGET_IDS[i];
                final String label = CustomContentHook.WIDGET_LABELS[i];
                boolean selected = mSelectedWidgets.contains(id);
                com.google.android.material.button.MaterialButton btn =
                        M3.segmentButton(this, label, selected);
                // 显示顺序序号
                if (selected) {
                    int ord = mSelectedWidgets.indexOf(id) + 1;
                    btn.setText(ord + "." + label);
                }
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i > r * cols) blp.leftMargin = M3.dp(this, 4);
                row.addView(btn, blp);
                btns.add(btn);
                btn.setOnClickListener(v -> {
                    toggleWidget(id);
                    // 重建以刷新序号与选中态
                    buildWidgetChips(host);
                    persistWidgets();
                    updateFestivalVisibility();
                    updateCalendarPermHint(hasCalendarPermission());
                    if ("schedule".equals(id) && mSelectedWidgets.contains("schedule")
                            && !hasCalendarPermission()) {
                        requestCalendarPermission();
                    }
                });
            }
            // 补齐空位
            int filled = end - r * cols;
            for (int k = filled; k < cols; k++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                slp.leftMargin = M3.dp(this, 4);
                row.addView(spacer, slp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = M3.dp(this, 4);
            host.addView(row, rlp);
        }
    }

    private void toggleWidget(String id) {
        if (mSelectedWidgets.contains(id)) {
            mSelectedWidgets.remove(id);
            return;
        }
        if (mSelectedWidgets.size() >= 4) {
            android.widget.Toast.makeText(this, "最多选择 4 个组件",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        mSelectedWidgets.add(id);
    }

    private void persistWidgets() {
        StringBuilder sb = new StringBuilder();
        for (String id : mSelectedWidgets) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        writeSetting("custom_widgets", sb.toString());
        // 旧开关：有日程则视为启用，便于兼容旧逻辑
        writeSetting("calendar_enabled", mSelectedWidgets.contains("schedule"));
    }

    private void updateFestivalVisibility() {
        if (mFestivalRow == null) return;
        mFestivalRow.setVisibility(
                mSelectedWidgets.contains("schedule") ? View.VISIBLE : View.GONE);
    }

    private boolean hasCalendarPermission() {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestCalendarPermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, REQ_CALENDAR);
            }
        } catch (Exception ignored) {}
    }

    private void updateCalendarPermHint(boolean granted) {
        if (mCalendarPermHint == null) return;
        if (granted) {
            mCalendarPermHint.setText("日历权限：已授予");
            mCalendarPermHint.setTextColor(M3.attrColor(this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        } else {
            mCalendarPermHint.setText("日历权限：未授予（点此申请，日程组件需要）");
            mCalendarPermHint.setTextColor(M3.attrColor(this,
                    com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF));
        }
        mCalendarPermHint.setVisibility(
                mSelectedWidgets.contains("schedule") ? View.VISIBLE : View.GONE);
    }

    // ---------- 卡片背景/描边（从卡片样式页迁入，分区独立） ----------

    private void addCardStyleSection(LinearLayout parent, String title, String prefix) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = M3.dp(this, 12);
        tlp.bottomMargin = M3.dp(this, 4);
        t.setLayoutParams(tlp);
        parent.addView(t);

        final CardStyle st = CardStyle.load(this, prefix);
        parent.addView(M3.switchRow(this, "启用自定义样式",
                "关闭则使用默认半透明线框", st.enabled, v ->
                        writeSetting(prefix + "enabled",
                                ((com.google.android.material.materialswitch.MaterialSwitch) v).isChecked())));
        addColorBlock(parent, "背景", prefix + "bg_mode", prefix + "bg_color", prefix + "bg_alpha",
                st.bgMode, st.bgColor, st.bgAlpha);
        addStrokeBlock(parent, prefix, st);
    }

    private void addColorBlock(LinearLayout parent, String label, String modeKey, String colorKey,
                               String alphaKey, String initMode, int initColor, int initAlpha) {
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

        TextView hint = new TextView(this);
        hint.setText("自动取色：半透明白");
        hint.setTextSize(12);
        hint.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        hintRef[0] = hint;
        block.addView(hint);
        hint.setVisibility(isCustom ? View.GONE : View.VISIBLE);

        LinearLayout cc = new LinearLayout(this);
        cc.setOrientation(LinearLayout.VERTICAL);
        final View[] dot = new View[1];
        addColorRow(cc, "颜色", colorKey, alphaKey, initColor, dot);
        addAlphaRow(cc, "透明度", alphaKey, initAlpha, dot, colorKey, initColor);
        cc.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        ccRef[0] = cc;
        block.addView(cc);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = M3.dp(this, 8);
        block.setLayoutParams(lp);
        parent.addView(block);
    }

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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = M3.dp(this, 8);
        block.setLayoutParams(lp);
        parent.addView(block);
    }

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
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = M3.dp(this, 4);
        parent.addView(row, rlp);
        dotRef[0] = dot;
    }

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
        parent.addView(alphaCol);
    }

    private void addSlider(LinearLayout parent, String name, String key, float min, float max, float def) {
        float cur = SettingsHelper.getFloat(this, key, def);
        parent.addView(M3.sliderRow(this, name, min, max, cur, (s, val, fromUser) -> {
            if (!fromUser) return;
            writeSetting(key, val);
        }));
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

    private void addModeSwitch(LinearLayout parent, String name, String key,
                               String[] values, String[] labels, String def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView nv = new TextView(this);
        nv.setText(name);
        nv.setTextSize(15);
        nv.setTextColor(M3.attrColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv);
        String cur = SettingsHelper.getString(this, key, def);
        final List<com.google.android.material.button.MaterialButton> btns = new ArrayList<>();
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
