package com.leowalk.aodchange;

import android.content.Context;
import android.graphics.Color;

/**
 * 卡片样式数据类：统一管理通知/焦点/歌曲/自定义分区卡片的背景/描边/字体颜色及宽高。
 *
 * 每类卡片用独立前缀存储，常见字段：
 *   {p}_enabled      启用自定义样式（bool）
 *   {p}_bg_mode      背景颜色模式：auto=按图标/专辑主色取色，custom=自定义
 *   {p}_bg_color     自定义背景色（RGB）
 *   {p}_bg_alpha     背景透明度（0-255）
 *   {p}_stroke_color 描边颜色（RGB）
 *   {p}_stroke_alpha 描边透明度（0-255）
 *   {p}_stroke_width 描边粗细（dp）
 *   {p}_text_mode    字体颜色模式：auto=按图标主色取色，custom=自定义
 *   {p}_text_color   自定义字体色（RGB）
 *   {p}_width_pct    卡片宽度（屏宽百分比，75-100）
 *   {p}_height_dp    卡片高度（dp，0=自动跟随内容）
 *
 * 供设置页(Settings UI)与 hook 渲染层共用，避免两端重复定义与不一致。
 */
public class CardStyle {

    public static final String P_NOTIF  = "card_notif_";
    public static final String P_FOCUS  = "card_focus_";
    public static final String P_SONG   = "card_song_";
    /** 旧统一前缀，仅作兼容回退；新设置用 P_CUSTOM_TEXT / P_CUSTOM_WIDGET */
    public static final String P_CUSTOM = "card_custom_";
    public static final String P_CUSTOM_TEXT = "card_custom_text_";
    public static final String P_CUSTOM_WIDGET = "card_custom_widget_";

    public static final String MODE_AUTO = "auto";
    public static final String MODE_CUSTOM = "custom";

    public boolean enabled;
    public String bgMode;
    public int bgColor;      // RGB
    public int bgAlpha;      // 0-255
    public int strokeColor;  // RGB
    public int strokeAlpha;  // 0-255
    public float strokeWidth;// dp, 0=无描边
    public String textMode;
    public int textColor;    // RGB（字体不透明，避免调到看不见）
    public int widthPct;     // 屏宽百分比，75-100
    public int heightDp;     // 0=auto

    /** 读取某类卡片的样式设置 */
    public static CardStyle load(Context ctx, String prefix) {
        CardStyle s = new CardStyle();
        s.enabled = SettingsHelper.get(ctx, prefix + "enabled", false);
        s.bgMode = SettingsHelper.getString(ctx, prefix + "bg_mode", MODE_AUTO);
        s.bgColor = SettingsHelper.getInt(ctx, prefix + "bg_color", 0xFFFFFF);
        s.bgAlpha = SettingsHelper.getInt(ctx, prefix + "bg_alpha", 20);
        s.strokeColor = SettingsHelper.getInt(ctx, prefix + "stroke_color", 0xFFFFFF);
        s.strokeAlpha = SettingsHelper.getInt(ctx, prefix + "stroke_alpha", 60);
        s.strokeWidth = SettingsHelper.getFloat(ctx, prefix + "stroke_width", 1f);
        s.textMode = SettingsHelper.getString(ctx, prefix + "text_mode", MODE_AUTO);
        s.textColor = SettingsHelper.getInt(ctx, prefix + "text_color", 0xFFFFFF);
        s.widthPct = SettingsHelper.getInt(ctx, prefix + "width_pct", 100);
        s.heightDp = SettingsHelper.getInt(ctx, prefix + "height_dp", 0);
        return s;
    }

    public int bgArgb() {
        return Color.argb(bgAlpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor));
    }

    public int strokeArgb() {
        return Color.argb(strokeAlpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor));
    }

    public int textArgb() {
        return textColor | 0xFF000000; // 字体不透明，避免调到看不见
    }
}