package com.leowalk.aodchange;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

/**
 * 卡片样式渲染工具：设置页与 hook 渲染层共用。
 *
 * 背景：未启用返回 null（调用方保持原有逻辑）；启用后按 bg_mode 决定背景色，
 *       auto=按图标/专辑主色（调用方算好传入）+ 样式透明度，custom=自定义色+透明度。
 * 描边：始终按自定义值（颜色/透明度/粗细），粗细 0 即无描边。
 * 字体：auto=沿用调用方算好的主色（不透明），custom=自定义色。
 */
public class CardRenderer {

    /** 生成卡片背景。未启用返回 null。cornerRadiusDp 圆角提示半径（dp）。autoColorArgb 为自动取色结果。 */
    public static GradientDrawable background(Context ctx, String prefix, int autoColorArgb,
                                              float cornerRadiusDp, float density) {
        CardStyle s = CardStyle.load(ctx, prefix);
        if (!s.enabled) return null;
        int bg = CardStyle.MODE_CUSTOM.equals(s.bgMode) ? s.bgArgb()
                : Color.argb(s.bgAlpha, Color.red(autoColorArgb), Color.green(autoColorArgb), Color.blue(autoColorArgb));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        if (s.strokeWidth > 0) gd.setStroke((int) (s.strokeWidth * density), s.strokeArgb());
        gd.setCornerRadius(cornerRadiusDp * density);
        return gd;
    }

    /** 字体颜色：自定义模式返回自定义色（不透明），否则返回 autoColor。 */
    public static int textColor(Context ctx, String prefix, int autoColor) {
        CardStyle s = CardStyle.load(ctx, prefix);
        if (!s.enabled) return autoColor;
        return CardStyle.MODE_CUSTOM.equals(s.textMode) ? s.textArgb() : autoColor;
    }

    /** 应用宽高：未启用或对应值为 0 时跳过。baseWidth 为卡片正常宽度基准。 */
    public static void applySize(Context ctx, String prefix, View card, int baseWidth, float density) {
        CardStyle s = CardStyle.load(ctx, prefix);
        if (!s.enabled) return;
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp == null) return;
        if (s.widthPct > 0) lp.width = Math.round(baseWidth * s.widthPct / 100f);
        if (s.heightDp > 0) lp.height = (int) (s.heightDp * density);
        card.setLayoutParams(lp);
    }
}