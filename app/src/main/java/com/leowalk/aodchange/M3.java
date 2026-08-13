package com.leowalk.aodchange;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class M3 {

    public static MaterialCardView card(Context ctx, LinearLayout content) {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(dp(ctx, 16));
        card.setCardElevation(0);
        card.setCardBackgroundColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFF26252B));
        card.setStrokeWidth(0);
        card.setClickable(false);
        card.addView(content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 16);
        card.setLayoutParams(lp);
        return card;
    }

    public static LinearLayout cardContent(Context ctx) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
        return ll;
    }

    public static LinearLayout permissionRow(Context ctx, String name, boolean granted,
                                             View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930));
        bg.setCornerRadius(dp(ctx, 12));
        row.setBackground(bg);
        if (listener != null) row.setOnClickListener(listener);

        TextView nv = new TextView(ctx);
        nv.setText(name);
        nv.setTextSize(15);
        nv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView st = new TextView(ctx);
        st.setText(granted ? "已授予" : "未授予");
        st.setTextSize(13);
        st.setTextColor(granted
                ? M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0)
                : M3.attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF));
        st.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(st);

        TextView arrow = new TextView(ctx);
        arrow.setText(">");
        arrow.setTextSize(14);
        arrow.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        arrow.setPadding(dp(ctx, 8), 0, 0, 0);
        row.addView(arrow);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 8);
        row.setLayoutParams(lp);
        return row;
    }

    /**
     * 编辑行：label + 单行输入框（失焦/按完成键触发 done 回调）。
     * 返回的 LinearLayout 的 tag 中存有 EditText，供外部统一保存按钮读取。
     */
    public static LinearLayout editRow(Context ctx, String label, String value, String hint,
                                       android.widget.TextView.OnEditorActionListener done) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView nv = new TextView(ctx);
        nv.setText(label);
        nv.setTextSize(14);
        nv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv);
        android.widget.EditText et = new android.widget.EditText(ctx);
        et.setText(value);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        et.setHintTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        et.setOnEditorActionListener(done);
        // 失焦时同样触发保存，避免"输入后直接返回"导致修改丢失
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && done != null) {
                try {
                    done.onEditorAction((android.widget.EditText) v, 0, null);
                } catch (Throwable ignored) {}
            }
        });
        row.addView(et);
        row.setTag(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 4);
        lp.bottomMargin = dp(ctx, 10);
        row.setLayoutParams(lp);
        return row;
    }

    /** 多行编辑框（支持换行输入），返回的 LinearLayout 的 tag 存有 EditText */
    public static LinearLayout editRowMulti(Context ctx, String label, String value, String hint) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView nv = new TextView(ctx);
        nv.setText(label);
        nv.setTextSize(14);
        nv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        row.addView(nv);
        android.widget.EditText et = new android.widget.EditText(ctx);
        et.setText(value);
        et.setHint(hint);
        et.setSingleLine(false);
        et.setMinLines(2);
        et.setMaxLines(5);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(et.getInputType() | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        et.setHintTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        row.addView(et);
        row.setTag(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 4);
        lp.bottomMargin = dp(ctx, 10);
        row.setLayoutParams(lp);
        return row;
    }

    public static LinearLayout tipContent(Context ctx, String text) {
        LinearLayout ll = cardContent(ctx);
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
        tv.setLineSpacing(dp(ctx, 2), 1f);
        ll.addView(tv);
        return ll;
    }

    public static TextView title(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 10);
        tv.setLayoutParams(lp);
        return tv;
    }

    public static LinearLayout switchRow(Context ctx, String name, String desc,
                                         boolean checked, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView nv = new TextView(ctx);
        nv.setText(name);
        nv.setTextSize(15);
        nv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        textCol.addView(nv);
        if (desc != null && !desc.isEmpty()) {
            TextView dv = new TextView(ctx);
            dv.setText(desc);
            dv.setTextSize(12);
            dv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
            dv.setPadding(0, dp(ctx, 2), 0, 0);
            textCol.addView(dv);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch sw = new MaterialSwitch(ctx);
        sw.setChecked(checked);
        sw.setTag("sw");
        sw.setOnCheckedChangeListener((b, c) -> { if (listener != null) listener.onClick(sw); });
        row.addView(sw);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 4);
        lp.bottomMargin = dp(ctx, 8);
        row.setLayoutParams(lp);
        return row;
    }

    public static LinearLayout sliderRow(Context ctx, String name, float min, float max, float value,
                                         Slider.OnChangeListener listener) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView nv = new TextView(ctx);
        nv.setText(name);
        nv.setTextSize(15);
        nv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        col.addView(nv);
        Slider slider = new Slider(ctx);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setValue(value);
        slider.addOnChangeListener(listener);
        col.addView(slider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 4);
        lp.bottomMargin = dp(ctx, 4);
        col.setLayoutParams(lp);
        return col;
    }

    public static View clickRow(Context ctx, String name, String desc, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView nv = new TextView(ctx);
        nv.setText(name);
        nv.setTextSize(20);
        nv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF));
        textCol.addView(nv);
        if (desc != null && !desc.isEmpty()) {
            TextView dv = new TextView(ctx);
            dv.setText(desc);
            dv.setTextSize(14);
            dv.setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0));
            dv.setPadding(0, dp(ctx, 3), 0, 0);
            textCol.addView(dv);
        }
        row.addView(textCol);
        row.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930));
        bg.setCornerRadius(dp(ctx, 12));
        row.setBackground(bg);
        if (listener != null) row.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 12);
        row.setLayoutParams(lp);
        return row;
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int attrColor(Context ctx, int attr, int fallback) {
        return com.google.android.material.color.MaterialColors.getColor(ctx, attr, fallback);
    }

    public static com.google.android.material.button.MaterialButton segmentButton(Context ctx, String label, boolean checked) {
        com.google.android.material.button.MaterialButton btn =
                new com.google.android.material.button.MaterialButton(ctx);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setCheckable(true);
        btn.setId(android.view.View.generateViewId());
        btn.setChecked(checked);
        int checkedBg = M3.attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF);
        int checkedFg = M3.attrColor(ctx, com.google.android.material.R.attr.colorOnPrimary, 0xFF000000);
        int uncheckedBg = M3.attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930);
        int uncheckedFg = M3.attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5);
        btn.setBackgroundTintList(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checkedBg, uncheckedBg}
        ));
        btn.setTextColor(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checkedFg, uncheckedFg}
        ));
        return btn;
    }

    // 多行分段按钮组：每行 columns 个，跨行单选
    public static LinearLayout segmentGroup(Context ctx, String[] labels, int[] values,
                                            int curValue, int columns,
                                            java.util.function.IntConsumer onSelect) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        final java.util.List<com.google.android.material.button.MaterialButton> btns =
                new java.util.ArrayList<>();
        int rows = (labels.length + columns - 1) / columns;
        for (int r = 0; r < rows; r++) {
            com.google.android.material.button.MaterialButtonToggleGroup group =
                    new com.google.android.material.button.MaterialButtonToggleGroup(ctx);
            group.setSingleSelection(true);
            group.setSelectionRequired(true);
            int end = Math.min((r + 1) * columns, labels.length);
            for (int i = r * columns; i < end; i++) {
                com.google.android.material.button.MaterialButton btn =
                        segmentButton(ctx, labels[i], values[i] == curValue);
                group.addView(btn);
                btns.add(btn);
            }
            group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                for (int i = 0; i < btns.size(); i++) {
                    if (btns.get(i).getId() == checkedId) {
                        onSelect.accept(values[i]);
                        for (int j = 0; j < btns.size(); j++) {
                            btns.get(j).setChecked(j == i);
                        }
                        break;
                    }
                }
            });
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.bottomMargin = dp(ctx, 4);
            row.addView(group, glp);
        }
        return row;
    }

    /**
     * 取色器对话框：预设色板 + RGB 滑块 + HEX 输入。
     * 回调返回 RGB（不透明）颜色值。
     */
    public static void colorPicker(Context ctx, String title, int initial,
                                   java.util.function.IntConsumer onPicked) {
        final int[] cur = {initial & 0xFFFFFF};
        final android.widget.SeekBar[] bars = new android.widget.SeekBar[3];

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), 0);

        final View preview = new View(ctx);
        final int psize = dp(ctx, 48);
        preview.setBackground(colorDot(ctx, cur[0], psize));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(psize, psize);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.bottomMargin = dp(ctx, 12);
        col.addView(preview, plp);

        // 预设色板
        int[] presets = {
                0xFFFFFFFF, 0xFFF5F5F5, 0xFFFFF8E1, 0xFFECEFF1,
                0xFFB0BEC5, 0xFFB3E5FC, 0xFF81D4FA, 0xFF80DEEA,
                0xFFA5D6A7, 0xFFC5E1A5, 0xFFE6EE9C, 0xFFFFF59D,
                0xFFFFCCBC, 0xFFFFAB91, 0xFFF48FB1, 0xFFF8BBD0,
                0xFFE1BEE7, 0xFFCE93D8, 0xFFB39DDB, 0xFFFFE082,
                0xFFFFCC80, 0xFF90CAF9, 0xFF80CBC4, 0xFFF06292,
                0xFF000000, 0xFF37474F, 0xFF455A64, 0xFF546E7A,
                0xFF607D8B, 0xFF9E9E9E, 0xFFBDBDBD, 0xFFCFD8DC
        };
        final int perRow = 8, dotSize = dp(ctx, 26);
        for (int r = 0; r * perRow < presets.length; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int c = 0; c < perRow; c++) {
                int idx = r * perRow + c;
                if (idx >= presets.length) break;
                final int pc = presets[idx];
                View dot = new View(ctx);
                dot.setBackground(colorDot(ctx, pc, dotSize));
                dot.setOnClickListener(v -> {
                    cur[0] = pc;
                    preview.setBackground(colorDot(ctx, pc, psize));
                    bars[0].setProgress(Color.red(pc));
                    bars[1].setProgress(Color.green(pc));
                    bars[2].setProgress(Color.blue(pc));
                });
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dotSize, dotSize);
                dlp.rightMargin = dp(ctx, 6);
                dlp.bottomMargin = dp(ctx, 6);
                row.addView(dot, dlp);
            }
            col.addView(row);
        }

        // RGB 滑块
        String[] names = {"红", "绿", "蓝"};
        for (int i = 0; i < 3; i++) {
            final int fi = i;
            android.widget.SeekBar sb = new android.widget.SeekBar(ctx);
            sb.setMax(255);
            sb.setProgress(fi == 0 ? Color.red(cur[0]) : fi == 1 ? Color.green(cur[0]) : Color.blue(cur[0]));
            sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar s, int v, boolean fromUser) {
                    if (!fromUser) return;
                    int rr = fi == 0 ? v : Color.red(cur[0]);
                    int gg = fi == 1 ? v : Color.green(cur[0]);
                    int bb = fi == 2 ? v : Color.blue(cur[0]);
                    cur[0] = Color.rgb(rr, gg, bb);
                    preview.setBackground(colorDot(ctx, cur[0], psize));
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
            });
            bars[fi] = sb;
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(ctx, 4);
            col.addView(sb, slp);
        }

        // HEX 输入
        LinearLayout hexRow = new LinearLayout(ctx);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        final android.widget.EditText hex = new android.widget.EditText(ctx);
        hex.setText(String.format("#%06X", 0xFFFFFF & cur[0]));
        hex.setSingleLine(true);
        hex.setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5));
        hexRow.addView(hex, new LinearLayout.LayoutParams(dp(ctx, 150), ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.Button hexBtn = new android.widget.Button(ctx);
        hexBtn.setText("应用");
        hexBtn.setOnClickListener(v -> {
            try {
                String t = hex.getText().toString().trim();
                if (t.startsWith("#")) t = t.substring(1);
                int color = (int) Long.parseLong(t, 16) & 0xFFFFFF;
                cur[0] = color;
                preview.setBackground(colorDot(ctx, color, psize));
                bars[0].setProgress(Color.red(color));
                bars[1].setProgress(Color.green(color));
                bars[2].setProgress(Color.blue(color));
            } catch (Exception ignored) {}
        });
        hexRow.addView(hexBtn);
        LinearLayout.LayoutParams hrp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hrp.topMargin = dp(ctx, 8);
        col.addView(hexRow, hrp);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(col)
                .setPositiveButton("确定", (d, w) -> { if (onPicked != null) onPicked.accept(cur[0]); })
                .setNegativeButton("取消", null)
                .show();
    }

    private static android.graphics.drawable.GradientDrawable colorDot(Context ctx, int color, int size) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF000000 | color);
        bg.setCornerRadius(size / 2f);
        return bg;
    }
}
