package com.leowalk.aodchange.hook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterfaceWrapper;

public class LyricHook {

    private static final Uri URI = Uri.parse("content://com.leowalk.aodchange.notifications");
    private static LinearLayout sContainer;
    private static ImageView sAppIcon;
    private static ImageView sAlbumIcon;
    private static ImageView sAlbumArt;
    private static FrameLayout sAlbumArtWrap;
    private static LinearLayout sTitleWrap;
    private static String sAlbumArtKey = "";
    private static LinearLayout sContentCol;
    private static LinearLayout sInfoCol;
    private static LinearLayout sSongRow;
    private static TextView sAlbumName;
    private static TextView sSongTitle;
    private static TextView sSongSubtitle;
    private static LinearLayout sTitleCol;
    private static LinearLayout sTitleRow;
    private static TextView sSongArtist;
    private static TextView sLyric;
    private static TextView sSubLyric;
    private static IntroDotsView sIntroDots;
    private static ViewGroup sRoot;
    private static int sAccentColor = Color.WHITE;
    private static int sMonetColor2 = Color.WHITE;
    private static int sMonetInfoColor1 = Color.WHITE;
    private static int sMonetInfoColor2 = Color.WHITE;

    public static int getAccentColor() {
        return sAccentColor;
    }
    private static int sVMedia = -1, sVLyric = -1, sVLyricFd = -1, sVCalendar = -1, sVNotif = -1, sVSettings = -1;
    /** 多行歌词行内容指纹：内容未变时跳过重建，只更新高�?滚动 */
    private static String sLastLinesKey = "";
    /** MediaSession 控制器列表缓存（2s），合并封面/播放位置查询 */
    private static java.util.List<android.media.session.MediaController> sControllers;
    private static long sControllersTime = 0;
    private static String sLastSongKey = "";
    private static FrameLayout sMask;
    private static android.widget.ScrollView sScroll;
    private static LinearLayout sMultiLine;
    private static LinearLayout sNotifIconRow;
    private static java.util.List<View> sMultiLineViews = new java.util.ArrayList<>();
    private static int sMaskRegionH = 0;
    private static int sLineHeight = 0;
    private static int sLastCtxIdx = -1;
    private static long sIntroFillTime = 0;
    private static String sAppIconKey = "";
    private static org.json.JSONObject sCachedCtx = null;
    private static String sLastStyleKey = "";
    private static String sLastSongKey2 = "";
    private static int sCurRow = -1;
    private static final java.util.List<Integer> sRowHeights = new java.util.ArrayList<>();

    /** 占位内容接口：无媒体播放时在此区域常驻显示，媒体播放时自动隐藏。 */
    public static void setPlaceholder(View v) {
        CustomContentHook.setPlaceholder(v);
    }

    public static void init(XposedInterfaceWrapper xiw, ClassLoader cl) {
        try {
            Class<?> av = Class.forName("com.miui.aod.AODView", false, cl);
            Method hm = av.getDeclaredMethod("handleUpdateView", boolean.class, boolean.class, boolean.class);
            xiw.hook(hm).intercept(chain -> {
                chain.proceed();
                sRoot = (ViewGroup) chain.getThisObject();
                // 锁屏出现：强制刷新设置缓存 + 置位强制检查自定义内容 + 立即渲染（不等轮询）
                com.leowalk.aodchange.SettingsHelper.invalidate();
                CustomContentHook.setCustomRefreshPending(true);
                registerObserverOnce();
                setup(sRoot);
                // 锁屏出现立即渲染：排到主线程队列最前（不等轮询/排队任务）
                new Handler(Looper.getMainLooper()).postAtFrontOfQueue(() -> {
                    try {
                        readAndUpdate();
                    } catch (Throwable ignored) {}
                });
                return null;
            });
        } catch (Exception ignored) {}
        startPolling();
    }

    /** 合并查询媒体会话控制器列表（2s 缓存）：updateAlbumArt �?getMediaPosition 共用 */
    private static java.util.List<android.media.session.MediaController> getControllers() {
        try {
            if (sRoot == null) return null;
            long now = android.os.SystemClock.elapsedRealtime();
            if (sControllers == null || now - sControllersTime > 2000) {
                sControllersTime = now;
                android.media.session.MediaSessionManager mgr = (android.media.session.MediaSessionManager)
                        sRoot.getContext().getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
                sControllers = mgr != null ? mgr.getActiveSessions(null) : null;
            }
            return sControllers;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * AODView 视图树重建时调用：清空所�?内容指纹/状态缓�?�?
     * 这些缓存是静态的，�?sContainer/sPlaceholder 等视图实例会被重建，
     * 若缓存不重置会导致新空视图命中旧缓存而拒绝渲染（空白/白框）�?
     */
    private static void resetContentCaches() {
        CustomContentHook.resetCaches();
        sLastLinesKey = "";
        sLastCtxIdx = -1;
        sLastSongKey = "";
        sLastSongKey2 = "";
        sLastStyleKey = "";
    }

    /** 半透明白色圆角矩形线框（自适应宽高） */
    private static void applyFrameBackground(View v, float d) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(20, 255, 255, 255));
        bg.setStroke((int)(1f * d), Color.argb(60, 255, 255, 255));
        bg.setCornerRadius(14 * d);
        v.setBackground(bg);
        v.setPadding((int)(14 * d), (int)(8 * d), (int)(14 * d), (int)(8 * d));
    }

    private static void setup(ViewGroup root) {        if (sContainer != null && sContainer.getParent() == root) return;
        // AODView 视图树重建（新实例）：清空全部内容缓存，保证新视图首次必渲染
        resetContentCaches();
        float d = root.getResources().getDisplayMetrics().density;

        boolean albumArtEnabled = com.leowalk.aodchange.SettingsHelper.get(root.getContext(), "info_show_albumart", false);

        sContainer = new LinearLayout(root.getContext());
        sContainer.setOrientation(LinearLayout.VERTICAL);
        sContainer.setGravity(Gravity.CENTER);

        // 歌曲信息行（横向）：封面（左�? 右列（歌�?歌手/专辑），整体居中
        LinearLayout songRow = new LinearLayout(root.getContext());
        songRow.setOrientation(LinearLayout.HORIZONTAL);
        songRow.setGravity(Gravity.CENTER);
        sSongRow = songRow;
        applyFrameBackground(songRow, d);
        // 左侧封面（开关开启时显示，运行时 updateAlbumArt 控制显隐�?
        // �?FrameLayout 包住：图标可叠加显示在封面右下角
        sAlbumArtWrap = new FrameLayout(root.getContext());
        sAlbumArtWrap.setVisibility(View.GONE);
        sAlbumArt = new ImageView(root.getContext());
        sAlbumArt.setVisibility(View.VISIBLE);
        sAlbumArt.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        // 专辑封面圆角处理（跟随卡片圆角）
        try {
            final float artRadius = 5 * d;
            sAlbumArt.setClipToOutline(true);
            sAlbumArt.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(android.view.View v, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), artRadius);
                }
            });
        } catch (Throwable ignored) {}
        // 专辑图大小与歌名/副标�?歌手/专辑总高一致（歌名行含 18dp 图标�?
        float sd = root.getResources().getDisplayMetrics().scaledDensity;
        int titleH = Math.max((int)(16f * sd), (int)(18 * d));
        int subH = Math.max((int)(9f * sd), (int)(10 * d));
        int artSize = subH + titleH + (int)(11f * sd) + (int)(11f * sd) + (int)(6 * d);
        LinearLayout.LayoutParams artlp = new LinearLayout.LayoutParams(artSize, artSize);
        artlp.rightMargin = (int)(16*d);
        sAlbumArtWrap.addView(sAlbumArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        songRow.addView(sAlbumArtWrap, artlp);

        // 右列：歌�?歌手/专辑
        sInfoCol = new LinearLayout(root.getContext());
        sInfoCol.setOrientation(LinearLayout.VERTICAL);
        sInfoCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams infolp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        songRow.addView(sInfoCol, infolp);

        LinearLayout titleWrap = new LinearLayout(root.getContext());
        titleWrap.setOrientation(LinearLayout.HORIZONTAL);
        titleWrap.setGravity(Gravity.CENTER_VERTICAL);
        sTitleWrap = titleWrap;

        // 标题列：图标+主标题一行在上，副标题在下（副标题显�?隐藏不影响主标题行位置）
        sTitleCol = new LinearLayout(root.getContext());
        sTitleCol.setOrientation(LinearLayout.VERTICAL);
        sTitleCol.setGravity(Gravity.LEFT);

        // 图标+主标题行（横向）
        sTitleRow = new LinearLayout(root.getContext());
        sTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        sTitleRow.setGravity(Gravity.CENTER_VERTICAL);

        // 标题图标：固定在主标题行左侧（只�?标题图标"开关控制，与封面无关）
        sAppIcon = new ImageView(root.getContext());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int)(18*d), (int)(18*d));
        sTitleRow.addView(sAppIcon, 0, iconLp);

        // 封面图标：固定在封面右下角（只由"封面图标"开关控制，与标题无关）
        sAlbumIcon = new ImageView(root.getContext());
        FrameLayout.LayoutParams albumIconLp = new FrameLayout.LayoutParams((int)(18*d), (int)(18*d));
        albumIconLp.gravity = Gravity.BOTTOM | Gravity.END;
        albumIconLp.rightMargin = (int)(2*d);
        albumIconLp.bottomMargin = (int)(2*d);
        sAlbumArtWrap.addView(sAlbumIcon, albumIconLp);

        sSongTitle = new TextView(root.getContext());
        sSongTitle.setTextSize(16);
        sSongTitle.setTextColor(sAccentColor);
        sSongTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sSongTitle.setSingleLine(true);
        sSongTitle.setEllipsize(TextUtils.TruncateAt.END);
        sSongTitle.setIncludeFontPadding(false);
        sSongTitle.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sTitleRow.addView(sSongTitle, stlp);

        sTitleCol.addView(sTitleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 副标题（主标题下方）
        sSongSubtitle = new TextView(root.getContext());
        sSongSubtitle.setTextSize(9);
        sSongSubtitle.setTextColor(Color.argb(160,255,255,255));
        sSongSubtitle.setSingleLine(true);
        sSongSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        sSongSubtitle.setIncludeFontPadding(false);
        sSongSubtitle.setGravity(Gravity.LEFT);
        sSongSubtitle.setVisibility(View.GONE);
        sTitleCol.addView(sSongSubtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        titleWrap.addView(sTitleCol, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams twlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sInfoCol.addView(titleWrap, twlp);

        sSongArtist = new TextView(root.getContext());
        sSongArtist.setTextSize(11);
        sSongArtist.setTextColor(Color.argb(160,255,255,255));
        sSongArtist.setSingleLine(true);
        sSongArtist.setEllipsize(TextUtils.TruncateAt.END);
        sSongArtist.setIncludeFontPadding(false);
        sSongArtist.setGravity(Gravity.LEFT);
        sSongArtist.setVisibility(View.GONE);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = (int)(2*d);
        sInfoCol.addView(sSongArtist, alp);

        // 专辑名（歌手下方，显示封面时开启）
        sAlbumName = new TextView(root.getContext());
        sAlbumName.setTextSize(11);
        sAlbumName.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(
                root.getContext(), com.leowalk.aodchange.CardStyle.P_SONG, Color.argb(120,255,255,255)));
        sAlbumName.setSingleLine(true);
        sAlbumName.setEllipsize(TextUtils.TruncateAt.END);
        sAlbumName.setIncludeFontPadding(false);
        sAlbumName.setGravity(Gravity.LEFT);
        sAlbumName.setVisibility(View.GONE);
        LinearLayout.LayoutParams albp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        albp.topMargin = (int)(2*d);
        sInfoCol.addView(sAlbumName, albp);

        sContainer.addView(songRow);

        // 前奏占位符：3 个圆圈，随播放进度填�?
        sIntroDots = new IntroDotsView(root.getContext());
        sIntroDots.setVisibility(View.GONE);
        LinearLayout.LayoutParams idlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(26 * d));
        idlp.topMargin = (int)(6*d);
        sContainer.addView(sIntroDots, idlp);

        sLyric = new TextView(root.getContext());
        sLyric.setTextSize(20);
        sLyric.setTextColor(sAccentColor);
        sLyric.setTypeface(Typeface.DEFAULT_BOLD);
        sLyric.setGravity(Gravity.CENTER);
        sLyric.setIncludeFontPadding(false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = (int)(6*d);
        sContainer.addView(sLyric, llp);

        sSubLyric = new TextView(root.getContext());
        sSubLyric.setTextSize(16);
        sSubLyric.setTextColor(sAccentColor);
        sSubLyric.setGravity(Gravity.CENTER);
        sSubLyric.setIncludeFontPadding(false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = (int)(2*d);
        sContainer.addView(sSubLyric, slp);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        flp.topMargin = (int)(280*d);
        flp.leftMargin = (int)(30*d);
        flp.rightMargin = (int)(30*d);
        root.addView(sContainer, flp);
        ElementSyncHook.register(sContainer);
        CustomContentHook.setup(root, sContainer);
        setupMask(root);
    }

    private static void setupMask(ViewGroup root) {
        if (sMask != null && sMask.getParent() == root) return;
        float d = root.getResources().getDisplayMetrics().density;

        sMask = new FrameLayout(root.getContext());
        sMask.setBackgroundColor(Color.argb(235, 0, 0, 0));
        sMask.setVisibility(View.GONE);

        sScroll = new android.widget.ScrollView(root.getContext());
        sScroll.setVerticalScrollBarEnabled(false);
        sScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sScroll.setClipChildren(true);
        sScroll.setClipToPadding(false);

        sMultiLine = new LinearLayout(root.getContext());
        sMultiLine.setOrientation(LinearLayout.VERTICAL);
        sMultiLine.setGravity(Gravity.LEFT);
        sMultiLine.setClipChildren(false);
        sMultiLine.setClipToPadding(false);
        sMultiLineViews.clear();

        android.widget.FrameLayout.LayoutParams sclp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sclp.leftMargin = (int)(30*d);
        sclp.rightMargin = (int)(30*d);
        sScroll.addView(sMultiLine, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sMask.addView(sScroll, sclp);

        // 多行模式通知图标行（sMask 下沿之下，横排最�?个）
        sNotifIconRow = new LinearLayout(root.getContext());
        sNotifIconRow.setOrientation(LinearLayout.HORIZONTAL);
        String igi = com.leowalk.aodchange.SettingsHelper.getString(root.getContext(), "multi_icon_row_gravity", "left");
        int igiGrav = "center".equals(igi) ? Gravity.CENTER_HORIZONTAL
                : "right".equals(igi) ? Gravity.RIGHT : Gravity.LEFT;
        sNotifIconRow.setGravity(igiGrav | Gravity.CENTER_VERTICAL);
        sNotifIconRow.setVisibility(View.GONE);
        root.addView(sNotifIconRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams maskLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        maskLp.gravity = Gravity.TOP;
        maskLp.topMargin = (int)(335 * d);
        int bottomMargin = com.leowalk.aodchange.SettingsHelper.getInt(root.getContext(), "multi_bottom_margin", 165);
        maskLp.bottomMargin = (int)(bottomMargin * d);
        root.addView(sMask, maskLp);
        ElementSyncHook.register(sMask);
        ElementSyncHook.register(sNotifIconRow);
        android.util.Log.i("AodChange", "setupMask d=" + d + " topMargin=" + maskLp.topMargin
                + " bottomMargin=" + maskLp.bottomMargin
                + " rootH=" + root.getHeight() + " rootW=" + root.getWidth()
                + " screenH=" + root.getResources().getDisplayMetrics().heightPixels);
    }

    private static int resolveAccentColor(android.content.Context ctx, int albumColor) {
        try {
            String mode = com.leowalk.aodchange.SettingsHelper.getString(ctx, "lyric_color_mode", "white");
            if ("album".equals(mode)) {
                return albumColor != 0 ? albumColor : Color.WHITE;
            }
            if ("custom".equals(mode)) {
                int c = com.leowalk.aodchange.SettingsHelper.getInt(ctx, "lyric_custom_color", 0);
                return c != 0 ? c : Color.WHITE;
            }
            if ("preset".equals(mode)) {
                int c = com.leowalk.aodchange.SettingsHelper.getInt(ctx, "lyric_custom_color", 0);
                return c != 0 ? c : albumColor != 0 ? albumColor : Color.WHITE;
            }
        } catch (Throwable ignored) {}
        return Color.WHITE;
    }

    private static void updateAlbumName(String album) {
        try {
            if (sAlbumName == null) return;
            boolean enabled = sRoot != null
                    && com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "info_show_albumname", false);
            if (!enabled || album == null || album.isEmpty()) {
                sAlbumName.setVisibility(View.GONE);
                adjustLyricTopMargin(0);
                return;
            }
            sAlbumName.setText(album);
            sAlbumName.setVisibility(View.VISIBLE);
            adjustLyricTopMargin((int)(sAlbumName.getResources().getDisplayMetrics().density * 16));
        } catch (Throwable ignored) {}
    }

    private static void adjustLyricTopMargin(int extraDp) {
        try {
            if (sLyric == null) return;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) sLyric.getLayoutParams();
            float d = sLyric.getResources().getDisplayMetrics().density;
            lp.topMargin = (int)(6 * d) + extraDp;
            sLyric.setLayoutParams(lp);
        } catch (Throwable ignored) {}
    }

    private static void applyLyricStyle(android.content.Context ctx) {
        try {
            if (sLyric == null) return;
            float lyricSize = com.leowalk.aodchange.SettingsHelper.getFloat(ctx, "lyric_text_size", 20f);
            int lyricMaxLines = com.leowalk.aodchange.SettingsHelper.getInt(ctx, "lyric_max_lines", 2);
            int transMaxLines = com.leowalk.aodchange.SettingsHelper.getInt(ctx, "translation_max_lines", 1);
            if (lyricMaxLines < 0) lyricMaxLines = 2;
            if (transMaxLines < 0) transMaxLines = 1;
            String gravityStr = com.leowalk.aodchange.SettingsHelper.getString(ctx, "lyric_gravity", "center");
            int widthPercent = com.leowalk.aodchange.SettingsHelper.getInt(ctx, "lyric_width_percent", 100);

            int g = "left".equals(gravityStr) ? Gravity.LEFT
                    : "right".equals(gravityStr) ? Gravity.RIGHT : Gravity.CENTER;

            sLyric.setTextSize(lyricSize);
            sLyric.setGravity(g);
            sLyric.setMaxLines(lyricMaxLines);
            sLyric.setSingleLine(lyricMaxLines <= 1);
            sLyric.setEllipsize(lyricMaxLines <= 1 ? TextUtils.TruncateAt.END : null);

            sSubLyric.setTextSize(lyricSize * 0.8f);
            sSubLyric.setGravity(g);
            sSubLyric.setMaxLines(transMaxLines);
            sSubLyric.setSingleLine(transMaxLines <= 1);
            sSubLyric.setEllipsize(transMaxLines <= 1 ? TextUtils.TruncateAt.END : null);

            if (sLyric.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) sLyric.getLayoutParams();
                if (widthPercent >= 100) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                } else {
                    int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                    lp.width = Math.min(screenW * widthPercent / 100,
                            screenW - (int)(60 * ctx.getResources().getDisplayMetrics().density));
                }
                sLyric.setLayoutParams(lp);
            }
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "applyLyricStyle fail", t);
        }
    }

    /** 轻量封面指纹�?x4 网格 16 个抽样像素哈希，判断是否换图（快，同歌稳定） */
    private static long artFingerprint(android.graphics.Bitmap bmp) {
        try {
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w <= 0 || h <= 0) return 0;
            long hash = 0;
            for (int gy = 0; gy < 4; gy++) {
                for (int gx = 0; gx < 4; gx++) {
                    int px = bmp.getPixel(w * (2 * gx + 1) / 8, h * (2 * gy + 1) / 8);
                    hash = hash * 31 + (px & 0xFFFFFF);
                }
            }
            return hash;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void updateAlbumArt(android.content.Context ctx) {
        try {
            if (sAlbumArt == null) return;
            boolean artEnabled = com.leowalk.aodchange.SettingsHelper.get(ctx, "info_show_albumart", false);
            if (!artEnabled) {
                sAlbumArt.setImageDrawable(null);
                if (sAlbumArtWrap != null) sAlbumArtWrap.setVisibility(View.GONE);
                sAlbumArtKey = "";
                return;
            }
            String pkg = "";
            android.media.session.MediaSessionManager mgr = (android.media.session.MediaSessionManager)
                    ctx.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
            android.graphics.Bitmap art = null;
            if (mgr != null) {
                java.util.List<android.media.session.MediaController> cs = getControllers();
                if (cs != null) {
                    for (android.media.session.MediaController c : cs) {
                        if (c == null) continue;
                        pkg = c.getPackageName();
                        android.media.MediaMetadata meta = c.getMetadata();
                        if (meta != null) {
                            art = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
                            if (art == null) art = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
                            if (art == null) art = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                        }
                        if (art != null) break;
                    }
                }
            }
            if (art == null) {
                if (sAlbumArtWrap != null) sAlbumArtWrap.setVisibility(View.GONE);
                return;
            }
            // 换图判断：像素指纹（切歌必更新，同歌稳定不重设）
            String key = pkg + "|" + artFingerprint(art);
            boolean changed = !key.equals(sAlbumArtKey);
            if (changed) {
                sAlbumArtKey = key;
                sAlbumArt.setImageBitmap(art);
                sAlbumArt.invalidateOutline();
            }
            if (sAlbumArtWrap == null) return;
            if (sAlbumArtWrap.getVisibility() != View.VISIBLE) {
                // 封面曾被隐藏（无�?占位）：恢复时强制重设内容再显示
                if (!changed) {
                    sAlbumArt.setImageBitmap(art);
                }
                sAlbumArtWrap.setVisibility(View.VISIBLE);
            }
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "updateAlbumArt fail", t);
        }
    }

    private static void startPolling() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    readAndUpdate();
                    new Handler(Looper.getMainLooper()).postDelayed(this, 200);
                } catch (Throwable t) {
                    new Handler(Looper.getMainLooper()).postDelayed(this, 1000);
                }
            }
        }, 500);
    }

    private static long sLastVersionsCheck = 0;
    private static String sLastLyricJson = "{}";
    private static String sLastMediaJson = "{}";
    private static volatile boolean sDataDirty = false;
    private static boolean sObserverRegistered = false;

    /** 注册 ContentObserver：数据变化事件驱动（替代高频版本轮询，及时且省 binder） */
    private static void registerObserverOnce() {
        if (sObserverRegistered || sRoot == null) return;
        sObserverRegistered = true;
        try {
            android.database.ContentObserver ob = new android.database.ContentObserver(
                    new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) {
                    sDataDirty = true;
                }
            };
            sRoot.getContext().getContentResolver().registerContentObserver(URI, true, ob);
        } catch (Throwable ignored) {}
    }

    /** 外部数据就绪后请求刷新（后台任务完成回调用） */
    public static void requestRefresh() {
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    readAndUpdate();
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void readAndUpdate() {
        try {
            if (sRoot == null) return;

            // 事件驱动：数据变化（ContentObserver 置 dirty）或锁屏 pending 立即处理；
            // 无变化时 5s 兜底检查一次（防丢事件），其余轮询走内存快路径（本地，无 binder）
            long now = android.os.SystemClock.elapsedRealtime();
            if (!sDataDirty && !CustomContentHook.isCustomRefreshPending()
                    && now - sLastVersionsCheck < 5000) {
                refreshCurrentLineFromCache();
                return;
            }
            sDataDirty = false;
            sLastVersionsCheck = now;

            // 版本号优化：数据未变化时用缓存数据高频刷新当前行/滚动（不跨进程）
            int newVMedia = -1, newVLyric = -1, newVLyricFd = -1, newVSettings = -1, newVCalendar = -1;
            try {
                Bundle vb = sRoot.getContext().getContentResolver().call(URI, "versions", null, null);
                if (vb != null) {
                    newVMedia = vb.getInt("media", -1);
                    newVLyric = vb.getInt("lyric", -1);
                    newVLyricFd = vb.getInt("lyricfd", -1);
                    newVSettings = vb.getInt("settings", -1);
                    newVCalendar = vb.getInt("calendar", -1);
                    if (newVMedia == sVMedia && newVLyric == sVLyric && newVLyricFd == sVLyricFd
                            && newVSettings == sVSettings && !CustomContentHook.isCustomRefreshPending()) {
                        refreshCurrentLineFromCache();
                        return;
                    }
                }
            } catch (Throwable ignored) {}

            try {
                doReadAndUpdate(newVMedia, newVLyric, newVLyricFd, newVSettings, newVCalendar);
            } catch (Throwable t) {
                // 处理失败：不记录版本号，下次轮询重试
                android.util.Log.w("AodChange", "readAndUpdate fail", t);
            }
        } catch (Throwable ignored) {}
    }

    /** 数据未变化时：用缓存歌词 + 播放位置高频刷新当前行（换行实时，不跨进程） */
    private static void refreshCurrentLineFromCache() {
        try {
            if (sRoot == null || sCachedCtx == null) return;
            org.json.JSONArray linesArr = sCachedCtx.optJSONArray("lines");
            if (linesArr == null || linesArr.length() == 0) return;
            int advance = com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "lyric_advance_ms", 200);
            long pos = getMediaPosition() + advance;
            int curIdx = -1;
            for (int i = 0; i < linesArr.length(); i++) {
                org.json.JSONObject o = linesArr.optJSONObject(i);
                if (o != null && o.optLong("tm", Long.MAX_VALUE) <= pos) curIdx = i;
            }
            int oldIdx = sCachedCtx.optInt("idx", -1);
            if (curIdx == oldIdx) return; // 当前行未�?
            // 更新缓存 idx �?isCur，走 applyMask 缓存分支更新滚动/高亮
            sCachedCtx.put("idx", curIdx);
            for (int i = 0; i < linesArr.length(); i++) {
                org.json.JSONObject o = linesArr.optJSONObject(i);
                if (o != null) o.put("isCur", i == curIdx);
            }
            applyMask(true, sCachedCtx, sRoot, false, 0f);
        } catch (Throwable ignored) {}
    }

    private static void doReadAndUpdate(int newVMedia, int newVLyric, int newVLyricFd, int newVSettings, int newVCalendar) {
        try {
            if (sRoot == null) return;
            // 记录旧版本：无媒体分支需要判断 calendar/settings 是否有变化
            int oldVSettings = sVSettings;
            int oldVCalendar = sVCalendar;
            int oldVLyric = sVLyric;
            int oldVLyricFd = sVLyricFd;
            int oldVMedia = sVMedia;
            sVMedia = newVMedia;
            sVLyric = newVLyric;
            sVLyricFd = newVLyricFd;
            sVSettings = newVSettings;

            applyLyricStyle(sRoot.getContext());
            applyInfoAlignment();
            updateAlbumArt(sRoot.getContext());

            Bundle mb = null;
            if (oldVMedia != newVMedia) {
                // 媒体小 JSON：仅在 media 版本变化时重新读取（缓存，避免每秒跨进程）
                mb = sRoot.getContext().getContentResolver().call(URI, "media", null, null);
                if (mb != null) {
                    String j = mb.getString("n");
                    if (j != null) sLastMediaJson = j;
                }
            }
            // 歌词数据：FD 版本变化读全量（文件，无 binder 大数据）；轻量版本变化走小 Bundle
            Bundle lb = null;
            if (oldVLyricFd != newVLyricFd) {
                try {
                    Bundle fb = sRoot.getContext().getContentResolver().call(URI, "lyric_fd", null, null);
                    android.os.ParcelFileDescriptor pfd = fb != null
                            ? (android.os.ParcelFileDescriptor) fb.getParcelable("fd") : null;
                    if (pfd != null) {
                        java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
                        fis.close();
                        pfd.close();
                        sLastLyricJson = new String(bos.toByteArray(), "UTF-8");
                    }
                } catch (Throwable ignored) {}
            }
            if (oldVLyric != newVLyric && oldVLyricFd == newVLyricFd) {
                // 轻量推送（换行 l/s）：小 Bundle 读取，保留 FD 全量里的 ctx（lines）
                lb = sRoot.getContext().getContentResolver().call(URI, "lyric", null, null);
                if (lb != null) {
                    String j = lb.getString("n");
                    if (j != null) {
                        try {
                            org.json.JSONObject old = new org.json.JSONObject(sLastLyricJson);
                            org.json.JSONObject neu = new org.json.JSONObject(j);
                            boolean emptyPush = !neu.has("l") && !neu.has("s")
                                    && !neu.has("title") && !neu.has("ctx");
                            if (emptyPush) {
                                // 切歌清空推送：彻底清除残留歌词（含 ctx/缓存），等新歌全量到达
                                sLastLyricJson = "{}";
                                sCachedCtx = null;
                                if (sMultiLine != null) sMultiLine.removeAllViews();
                                sMultiLineViews.clear();
                            } else if (!neu.has("ctx") && old.has("ctx")) {
                                neu.put("ctx", old.get("ctx"));
                                sLastLyricJson = neu.toString();
                            } else {
                                sLastLyricJson = neu.toString();
                            }
                        } catch (Throwable ignored) {
                            sLastLyricJson = j;
                        }
                    }
                }
            }

            String title = "", artist = "", lyric = "", sub = "", mjson = "", ljson = "", mPkg = "";
            boolean playing = false;

            mjson = sLastMediaJson != null ? sLastMediaJson : "{}";
            ljson = sLastLyricJson != null ? sLastLyricJson : "{}";

            org.json.JSONObject mo = new org.json.JSONObject(mjson);
            playing = mo.optInt("p", 0) != 0;
            title = mo.optString("t", "");
            artist = mo.optString("a", "");
            String album = mo.optString("al", "");
            mPkg = mo.optString("pkg", "");

            // 白名单/音乐应用判定：仅选中的应用才显示音乐信息与歌词
            boolean hasMedia = !"{}".equals(mjson);
            if (hasMedia && !isAllowedMusicApp(mPkg)) {
                CustomContentHook.applyCustomOrPlaceholder();
                return;
            }

            org.json.JSONObject lo = new org.json.JSONObject(ljson);
            lyric = lo.optString("l", "");
            sub = lo.optString("s", "");
            String lTitle = lo.optString("title", "");
            String lArtist = lo.optString("artist", "");
            org.json.JSONObject ctx = lo.optJSONObject("ctx");

            // 用播放位置自行计算当前行（不依赖 LyricFocus 推送的 idx），
            // 换行响应更快，节奏快的歌不卡顿；前奏（未到第一句）�?idx=-1
            sCachedCtx = null;
            if (ctx != null && playing) {
                org.json.JSONArray linesArr = ctx.optJSONArray("lines");
                if (linesArr != null && linesArr.length() > 0 && linesArr.optJSONObject(0).has("tm")) {
                    sCachedCtx = ctx; // 缓存供数据未变化时高频刷新滚�?当前�?
                    int advance = com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "lyric_advance_ms", 200);
                    long pos = getMediaPosition() + advance;
                    int curIdx = -1;
                    for (int i = 0; i < linesArr.length(); i++) {
                        org.json.JSONObject o = linesArr.optJSONObject(i);
                        if (o != null && o.optLong("tm", Long.MAX_VALUE) <= pos) curIdx = i;
                    }
                    if (curIdx >= 0) {
                        ctx.put("idx", curIdx);
                        for (int i = 0; i < linesArr.length(); i++) {
                            org.json.JSONObject o = linesArr.optJSONObject(i);
                            if (o != null) o.put("isCur", i == curIdx);
                        }
                    } else {
                        ctx.put("idx", -1);
                    }
                }
            }

            boolean multiLineEnabled = sRoot != null
                    && com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "multi_line", false);
            if (sRoot != null && com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "swap_lyric_translation", false)
                    && !sub.isEmpty()) {
                String tmp = lyric;
                lyric = sub;
                sub = tmp;
            }

            // 歌词归属判断：媒体标题为空视为无媒体信息（放行使能渲染）；
            // 否则要求歌词标题非空且与媒体标题一致，避免 LyricFocus 残留旧歌词
            // （title 被清空或与当前媒体不符）时单/多行歌词仍被误判匹配而残留
            boolean matchSong = title.isEmpty() || (!lTitle.isEmpty() && title.equals(lTitle));

            boolean noLyric = lyric.isEmpty() && sub.isEmpty();
            boolean showLyric = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_show", false);
            // 多行激活：有歌词上下文（lines）且播放中且歌曲匹配——
            // 前奏（idx=-1、l/s 空）也显示多行 mask（三点+未播行），不依赖单行文本；
            // matchSong 防止切到非音乐源（视频等）时旧歌词 ctx 残留继续渲染；
            // 无歌词文本(noLyric)时仅前奏(idx<0)激活，避免残留旧歌词 ctx 的文字残留
            boolean multiActive = multiLineEnabled && ctx != null && playing && matchSong
                    && (!noLyric || ctx.optInt("idx", -1) < 0);

            // 切歌检测：歌曲变化时强制重建（重置缓存索引并清空多行列表，
            // 否则新歌前奏 idx=-1 会命中缓存显示旧歌词�?
            String newSongKey = title + "|" + artist + "|" + mPkg;
            if (!newSongKey.equals(sLastSongKey2)) {
                sLastSongKey2 = newSongKey;
                sLastCtxIdx = -1;
                sCachedCtx = null;
                if (sMultiLine != null) sMultiLine.removeAllViews();
                sMultiLineViews.clear();
                if (sScroll != null) sScroll.scrollTo(0, 0);
            }
            if (!multiActive) {
                sLastCtxIdx = -1;
            }

            // 样式设置变化时强制重建多行（颜色/字号/对齐/翻译互换/宽度�?
            String styleKey = com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "lyric_color_mode", "white")
                    + "|" + com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "lyric_custom_color", 0)
                    + "|" + com.leowalk.aodchange.SettingsHelper.getFloat(sRoot.getContext(), "lyric_text_size", 20f)
                    + "|" + com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "lyric_gravity", "center")
                    + "|" + com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "lyric_width_percent", 100)
                    + "|" + com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "swap_lyric_translation", false)
                    + "|" + com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "multi_line_show_translation", true)
                    + "|" + com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "multi_line_spacing", 14);
            if (!styleKey.equals(sLastStyleKey)) {
                sLastStyleKey = styleKey;
                sLastCtxIdx = -1;
            }

            android.util.Log.i("AodChange", "multi check enabled=" + multiLineEnabled + " ctx=" + (ctx != null)
                    + " playing=" + playing + " noLyric=" + noLyric + " active=" + multiActive);
            if (com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_monet", false)) {
                int c1 = mo.optInt("c1", 0);
                int c2 = mo.optInt("c2", 0);
                sAccentColor = c1 != 0 ? c1 : Color.WHITE;
                sMonetColor2 = c2 != 0 ? c2 : sAccentColor;
                sMonetInfoColor1 = c2 != 0 ? c2 : sAccentColor;
                sMonetInfoColor2 = c1 != 0 ? c1 : sAccentColor;
            } else {
                sAccentColor = resolveAccentColor(sRoot.getContext(), mo.optInt("c1", 0));
            }

            // 前奏占位符：前奏�?7s 时显�?3 个圆�?
            // 填满时刻 = 离第一句剩 1/3 前奏处；若离第一句超�?5s 则按�?5s 处填�?
            boolean introEnabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_show_intro", false);
            long firstLineTime = 0;
            int introCtxIdx = -1;
            if (ctx != null) {
                introCtxIdx = ctx.optInt("idx", -1);
                org.json.JSONArray introLines = ctx.optJSONArray("lines");
                if (introLines != null && introLines.length() > 0) {
                    // tm = 时间�?ms)；兼容旧�?t 为数字的情况
                    firstLineTime = introLines.optJSONObject(0).optLong("tm", 0);
                    if (firstLineTime == 0) {
                        firstLineTime = introLines.optJSONObject(0).optLong("t", 0);
                    }
                }
            }
            long mediaPos = getMediaPosition();
            // 前奏判定：LyricFocus 推送的 idx<0 表示尚未到第一句（最可靠）；
            // mediaPos<firstLineTime 作为辅助（播放器位置不可靠时�?idx 为准�?
            boolean introActive = introEnabled && playing && !title.isEmpty()
                    && firstLineTime > 7000 && introCtxIdx < 0
                    && (mediaPos < firstLineTime || mediaPos <= 0);
            final boolean fIntroActive = introActive;
            final float fIntroProgress;
            if (introActive) {
                // 填满时刻 = 离第一句剩 1/3 前奏处；若该时刻离第一句超�?5s 则改为剩 5s �?
                long fillTime = firstLineTime * 2 / 3;
                if (firstLineTime - fillTime > 5000L) {
                    fillTime = firstLineTime - 5000L;
                }
                sIntroFillTime = fillTime;
                float p = fillTime > 0 ? (float) mediaPos / (float) fillTime : 0f;
                fIntroProgress = Math.max(0f, Math.min(1f, p));
            } else {
                fIntroProgress = 0f;
            }

            // 歌曲信息卡片背景跟随专辑主色（浅色半透明，保留线框）
            try {
                if (sSongRow != null) {
                    int bgc = sAccentColor;
                    float[] hbg = new float[3];
                    android.graphics.Color.colorToHSV(bgc, hbg);
                    hbg[1] = Math.min(hbg[1], 0.5f);
                    hbg[2] = 1f;
                    int base = android.graphics.Color.HSVToColor(hbg);
                    float sd = sRoot.getResources().getDisplayMetrics().density;
                    android.graphics.drawable.GradientDrawable gd = com.leowalk.aodchange.CardRenderer.background(
                            sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_SONG, base, 14f, sd);
                    if (gd == null) {
                        gd = new android.graphics.drawable.GradientDrawable();
                        gd.setColor(android.graphics.Color.argb(38, android.graphics.Color.red(base),
                                android.graphics.Color.green(base), android.graphics.Color.blue(base)));
                        gd.setStroke((int)(1f * sd), android.graphics.Color.argb(60, 255, 255, 255));
                        gd.setCornerRadius(14 * sd);
                    }
                    sSongRow.setBackground(gd);
                }
            } catch (Throwable ignored) {}
            applyMask(multiActive && showLyric, ctx, sRoot, fIntroActive, fIntroProgress);

            if (multiActive) {
                CustomContentHook.showMedia();
                final String fSongM = title;
                final String fArtistM = artist;
                final String fPkgM = mPkg;
                final int fColorM = mo.optInt("c1", 0);
                final String fAlbumM = album;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (sContainer != null) sContainer.setVisibility(View.VISIBLE);
                    if (sLyric != null) sLyric.setVisibility(View.GONE);
                    if (sSubLyric != null) sSubLyric.setVisibility(View.GONE);
                    // 多行模式下三点由 applyMask 的多行列表渲染，此处不重复显�?
                    if (sIntroDots != null) sIntroDots.setVisibility(View.GONE);
                    setAppIcon(fPkgM, fColorM);
                    if (sSongTitle != null) {
                        applyTitleBracket(fSongM.isEmpty() ? fPkgM : fSongM, fPkgM, com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_monet", false)
                                ? sMonetInfoColor1 : sAccentColor);
                        sSongTitle.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(
                                sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_SONG,
                                com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_monet", false)
                                        ? sMonetInfoColor1 : sAccentColor));
                        sSongTitle.setVisibility(View.VISIBLE);
                        if (sSongSubtitle != null && sSongSubtitle.getVisibility() == View.VISIBLE
                                && !com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "info_show_title", true)) {
                            sSongSubtitle.setVisibility(View.GONE);
                        }
                    }
                    if (sSongArtist != null) {
                        sSongArtist.setText(fArtistM);
                        sSongArtist.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_SONG,
                                com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_monet", false)
                                        ? sMonetInfoColor2 : fadeColor(sAccentColor, 0.8f)));
                        sSongArtist.setVisibility(View.VISIBLE);
                    }
                    if (sSongRow != null) sSongRow.setVisibility(View.VISIBLE);
                    updateAlbumName(fAlbumM);
                    if (!com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "info_show_card", true)) {
                        if (sSongRow != null) sSongRow.setVisibility(View.GONE);
                        if (sSongTitle != null) sSongTitle.setVisibility(View.GONE);
                        if (sSongSubtitle != null) sSongSubtitle.setVisibility(View.GONE);
                        if (sSongArtist != null) sSongArtist.setVisibility(View.GONE);
                        if (sAlbumName != null) sAlbumName.setVisibility(View.GONE);
                        if (sAppIcon != null) sAppIcon.setVisibility(View.GONE);
                        if (sAlbumIcon != null) sAlbumIcon.setVisibility(View.GONE);
                    }
                });
                return;
            }
            // 无音乐播放（媒体为空 �?歌词为空且标题为空）时显示日�?自定义�?
            // media={} 是权威的"无媒�?信号；lyric 可能有残�?title 但无实际歌词
            boolean mediaEmpty = "{}".equals(mjson);
            if (mediaEmpty) {
                // 自定义内容（日历/文字）不走实时更新：锁屏出现（pending）或
                // 数据版本变化（日历/设置）时才更新，其余轮询空转
                if (CustomContentHook.isCustomRefreshPending()
                        || oldVCalendar != newVCalendar || oldVSettings != newVSettings) {
                    CustomContentHook.setCustomRefreshPending(false);
                    sVCalendar = newVCalendar;
                    CustomContentHook.applyCustomOrPlaceholder();
                }
                return;
            }
            // 渲染歌词期间：兜底隐藏系统焦点通知容器，避免与 LyricFocus 自绘互抢
            FocusRowHook.hideIfActive(sRoot.getContext());
            boolean lyricEmpty = "{}".equals(ljson)
                    || (lo.optString("l", "").isEmpty() && lo.optString("s", "").isEmpty()
                        && lo.isNull("ctx") && lo.optString("title", "").isEmpty());
            if (lyricEmpty) { CustomContentHook.applyCustomOrPlaceholder(); return; }
            // 有媒体数据（播放或暂停）：不显示自定义组件
            CustomContentHook.showMedia();

            final String fSong = title;
            final String fArtist = artist;
            final String fLyric = lyric;
            final String fSub = sub;
            final String fPkg = mPkg;
            final boolean fPlaying = playing;
            final boolean fMatch = matchSong;
            final int fColor = mo.optInt("c1", 0);
            final String fAlbum = album;
            final boolean fShowLyric = showLyric;
            new Handler(Looper.getMainLooper()).post(() -> {
                boolean monet = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_monet", false);
                int accent = monet ? (sAccentColor != 0 ? sAccentColor : Color.WHITE) : resolveAccentColor(sRoot.getContext(), fColor);
                int subColor = monet ? sMonetColor2 : fadeColor(accent, 0.8f);
                int titleColor = monet ? sMonetInfoColor1 : accent;
                int artistColor = monet ? sMonetInfoColor2 : fadeColor(accent, 0.8f);
                sAccentColor = accent;
                setAppIcon(fPkg, fColor);
                updateAlbumName(fAlbum);
                if (!fSong.isEmpty()) {
                    applyTitleBracket(fSong, fPkg, artistColor);
                    sSongTitle.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(
                            sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_SONG, titleColor));
                    sSongTitle.setVisibility(View.VISIBLE);
                    if (!fArtist.isEmpty()) {
                        sSongArtist.setText(fArtist);
                        sSongArtist.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(
                                sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_SONG, artistColor));
                        sSongArtist.setVisibility(View.VISIBLE);
                    } else { sSongArtist.setVisibility(View.GONE); }
                } else {
                    sSongTitle.setVisibility(View.GONE);
                    if (sSongSubtitle != null) sSongSubtitle.setVisibility(View.GONE);
                    sSongArtist.setVisibility(View.GONE);
                }
                if (fIntroActive) {
                    // 前奏占位符：三点自驱动填充动画，隐藏歌词
                    if (sIntroDots != null) {
                        sIntroDots.setDotColor(accent);
                        sIntroDots.startIntro(sIntroFillTime);
                        sIntroDots.setVisibility(fShowLyric ? View.VISIBLE : View.GONE);
                    }
                    sLyric.setVisibility(View.GONE);
                    sSubLyric.setVisibility(View.GONE);
                } else {
                    if (sIntroDots != null) {
                        sIntroDots.stopIntro();
                        sIntroDots.setVisibility(View.GONE);
                    }
                    sLyric.setText(fLyric);
                    sLyric.setTextColor(accent);
                    sLyric.setVisibility((fShowLyric && fPlaying && !fLyric.isEmpty() && fMatch) ? View.VISIBLE : View.GONE);
                    sSubLyric.setText(fSub);
                    sSubLyric.setTextColor(subColor);
                    sSubLyric.setVisibility((fShowLyric && fPlaying && !fSub.isEmpty() && fMatch) ? View.VISIBLE : View.GONE);
                }

                String songKey = fPkg + "|" + fSong + "|" + fArtist;
                if (!songKey.equals(sLastSongKey)) {
                    sLastSongKey = songKey;
                    fadeInMedia();
                }
                applySongInfoMode();
            });
        } catch (Exception ignored) {}
    }

    /** 从当前媒体会话获取播放位置（ms�?*/
    private static long getMediaPosition() {
        try {
            if (sRoot == null) return 0;
            java.util.List<android.media.session.MediaController> cs = getControllers();
            if (cs == null) return 0;
            for (android.media.session.MediaController c : cs) {
                if (c == null) continue;
                android.media.session.PlaybackState st = c.getPlaybackState();
                if (st == null) continue;
                int state = st.getState();
                if (state == android.media.session.PlaybackState.STATE_PLAYING
                        || state == android.media.session.PlaybackState.STATE_BUFFERING) {
                    long pos = st.getPosition();
                    if (pos > 0) {
                        // 播放器不常推 PlaybackState（网易云约 5s 一次）：
                        // 用 lastPositionUpdateTime 外推实时位置，避免快句（<1s/句）整段跳行
                        long lastT = st.getLastPositionUpdateTime();
                        if (lastT > 0) {
                            long delta = android.os.SystemClock.elapsedRealtime() - lastT;
                            if (delta > 0) pos += delta;
                        }
                        return pos;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static void applyInfoAlignment() {
        try {
            if (sInfoCol == null) return;
            // 独立排版设置：info_gravity (left/center/right)
            String gravity = com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "info_gravity", "");
            int g;
            int rowG;
            if ("left".equals(gravity)) {
                g = Gravity.LEFT;
                rowG = Gravity.LEFT;
            } else if ("right".equals(gravity)) {
                g = Gravity.RIGHT;
                rowG = Gravity.RIGHT;
            } else if ("center".equals(gravity)) {
                g = Gravity.CENTER_HORIZONTAL;
                rowG = Gravity.CENTER;
            } else {
                // 自动：封面开启时文字靠左（整体居中），否则文字居�?
                boolean artEnabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "info_show_albumart", false);
                g = artEnabled ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL;
                rowG = Gravity.CENTER;
            }
            sInfoCol.setGravity(g);
            if (sTitleCol != null) sTitleCol.setGravity(g);
            if (sSongTitle != null) sSongTitle.setGravity(g);
            if (sSongSubtitle != null) sSongSubtitle.setGravity(g);
            if (sSongArtist != null) sSongArtist.setGravity(g);
            if (sAlbumName != null) sAlbumName.setGravity(g);
            if (sSongRow != null) sSongRow.setGravity(rowG);
        } catch (Throwable ignored) {}
    }

    private static void applySongInfoMode() {
        try {
            boolean showCard = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "info_show_card", true);
            if (!showCard) {
                if (sSongRow != null) sSongRow.setVisibility(View.GONE);
                if (sSongTitle != null) sSongTitle.setVisibility(View.GONE);
                if (sSongSubtitle != null) sSongSubtitle.setVisibility(View.GONE);
                if (sSongArtist != null) sSongArtist.setVisibility(View.GONE);
                if (sAlbumName != null) sAlbumName.setVisibility(View.GONE);
                if (sAppIcon != null) sAppIcon.setVisibility(View.GONE);
                if (sAlbumIcon != null) sAlbumIcon.setVisibility(View.GONE);
                return;
            }
            boolean showTitle = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "info_show_title", true);
            boolean showArtist = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "info_show_artist", true);
            if (sSongTitle != null && sSongTitle.getVisibility() == View.VISIBLE) {
                sSongTitle.setVisibility(showTitle ? View.VISIBLE : View.GONE);
            }
            if (sSongSubtitle != null && sSongSubtitle.getVisibility() == View.VISIBLE) {
                sSongSubtitle.setVisibility(showTitle ? View.VISIBLE : View.GONE);
            }
            if (sSongArtist != null && sSongArtist.getVisibility() == View.VISIBLE) {
                sSongArtist.setVisibility(showArtist ? View.VISIBLE : View.GONE);
            }
            // 有可见歌曲信息时恢复 songRow（含线框�?
            if (sSongRow != null) {
                boolean anyVisible = (sSongTitle != null && sSongTitle.getVisibility() == View.VISIBLE)
                        || (sSongSubtitle != null && sSongSubtitle.getVisibility() == View.VISIBLE)
                        || (sSongArtist != null && sSongArtist.getVisibility() == View.VISIBLE);
                sSongRow.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {}
    }

    private static final java.util.regex.Pattern BRACKET_RE = java.util.regex.Pattern.compile("[\uFF08(]([^\uFF08\uFF09()]*)[\uFF09)]");

    private static String[] splitBrackets(String title) {
        if (title == null) return new String[]{"", ""};
        java.util.regex.Matcher m = BRACKET_RE.matcher(title);
        StringBuilder main = new StringBuilder();
        StringBuilder sub = new StringBuilder();
        int last = 0;
        while (m.find()) {
            main.append(title, last, m.start());
            String inner = m.group(1).trim();
            if (!inner.isEmpty()) {
                if (sub.length() > 0) sub.append(' ');
                sub.append(inner);
            }
            last = m.end();
        }
        main.append(title.substring(last));
        return new String[]{main.toString().replaceAll("\\s+", " ").trim(), sub.toString()};
    }

    private static CharSequence shrinkTitle(String title) {
        android.text.SpannableString ss = new android.text.SpannableString(title);
        java.util.regex.Matcher m = BRACKET_RE.matcher(title);
        while (m.find()) {
            ss.setSpan(new android.text.style.RelativeSizeSpan(0.6f), m.start(), m.end(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    private static boolean isMusicApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        // 白名单中的应用视为音乐应用（用户显式选择�?
        if (isInWhitelist(pkg)) return true;
        String lp = pkg.toLowerCase();
        // 包名�?video 的默认不识别为音乐应�?
        if (lp.contains("video")) return false;
        // 包名�?music 的优先识别为音乐应用
        if (lp.contains("music")) return true;
        // 其他软件默认不识别为音乐应用
        return false;
    }

    private static boolean isInWhitelist(String pkg) {
        if (pkg == null || pkg.isEmpty() || sRoot == null) return false;
        String raw = com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "music_whitelist", "");
        if (raw == null || raw.isEmpty()) return false;
        for (String p : raw.split(",")) {
            if (p != null && p.trim().equalsIgnoreCase(pkg)) return true;
        }
        return false;
    }

    /** 是否允许显示音乐信息与歌词：白名单关闭时所有应用都允许，开启时仅白名单内应�?*/
    private static boolean isAllowedMusicApp(String pkg) {
        if (pkg == null || pkg.isEmpty() || sRoot == null) return false;
        if (com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "music_whitelist_enabled", false)) {
            return isInWhitelist(pkg);
        }
        return true;
    }

    /** 封面图标大小（固�?title_icon_size，父容器�?sAlbumArtWrap/FrameLayout�?*/
    private static void updateAlbumIconSpan() {
        try {
            if (sAlbumIcon == null || sRoot == null) return;
            float d = sRoot.getResources().getDisplayMetrics().density;
            int iconSize = (int)(com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "title_icon_size", 18) * d);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sAlbumIcon.getLayoutParams();
            if (lp != null && (lp.width != iconSize || lp.height != iconSize)) {
                lp.width = iconSize;
                lp.height = iconSize;
                lp.gravity = Gravity.BOTTOM | Gravity.END;
                lp.rightMargin = (int)(4 * d);
                lp.bottomMargin = (int)(4 * d);
                sAlbumIcon.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {}
    }

    /** 标题图标大小（固�?title_icon_size，父容器�?sTitleRow/LinearLayout�?*/
    private static void updateIconSpan() {
        try {
            if (sAppIcon == null || sRoot == null) return;
            float d = sRoot.getResources().getDisplayMetrics().density;
            int iconSize = (int)(com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "title_icon_size", 18) * d);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) sAppIcon.getLayoutParams();
            if (lp != null && (lp.width != iconSize || lp.height != iconSize)) {
                lp.width = iconSize;
                lp.height = iconSize;
                sAppIcon.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {}
    }

    private static void setSubtitleVisible(boolean show) {
        if (sSongSubtitle == null) return;
        sSongSubtitle.setVisibility(show ? View.VISIBLE : View.GONE);
        updateIconSpan();
    }

    private static void applyTitleBracket(String title, String pkg, int titleColor) {
        if (sSongTitle == null || sRoot == null) return;
        try {
            boolean music = isMusicApp(pkg);
            String mode = com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "title_bracket_mode", "default");
            android.util.Log.i("AodChange", "titleBracket title=" + title + " pkg=" + pkg + " music=" + music + " mode=" + mode);
            if (!music || "default".equals(mode) || title == null || title.isEmpty()) {
                sSongTitle.setText(title == null ? "" : title);
                setSubtitleVisible(false);
                return;
            }
            String[] parts = splitBrackets(title);
            String main = parts[0];
            String sub = parts[1];
            android.util.Log.i("AodChange", "titleBracket main=[" + main + "] sub=[" + sub + "]");
            if ("remove".equals(mode)) {
                sSongTitle.setText(main.isEmpty() ? title : main);
                setSubtitleVisible(false);
            } else if ("shrink".equals(mode)) {
                sSongTitle.setText(shrinkTitle(title));
                setSubtitleVisible(false);
            } else { // line
                sSongTitle.setText(main.isEmpty() ? title : main);
                if (!sub.isEmpty()) {
                    sSongSubtitle.setText(sub);
                    // 副标题跟随主标题颜色，透明?50%
                    sSongSubtitle.setTextColor(com.leowalk.aodchange.CardRenderer.textColor(
                            sRoot.getContext(), com.leowalk.aodchange.CardStyle.P_SONG,
                            Color.argb(128, Color.red(titleColor), Color.green(titleColor), Color.blue(titleColor))));
                    setSubtitleVisible(true);
                } else {
                    setSubtitleVisible(false);
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "applyTitleBracket fail", t);
        }
    }


    /** 多行歌词行内容指纹：不含当前行索引（isCur），换行时内容不变即可跳过重�?*/
    private static String buildLinesKey(org.json.JSONArray lines, boolean showMultiTrans, boolean swapTrans,
                                        int widthPercent, float multiSize, int lineSpacing, String gravStr,
                                        int accent, boolean introActive) {
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append(widthPercent).append('|').append(multiSize).append('|').append(lineSpacing)
                    .append('|').append(gravStr).append('|').append(accent)
                    .append('|').append(showMultiTrans).append('|').append(swapTrans)
                    .append('|').append(introActive);
            int n = Math.min(lines.length(), 500); // 防极端长歌词
            for (int i = 0; i < n; i++) {
                org.json.JSONObject o = lines.optJSONObject(i);
                if (o == null) {
                    sb.append("|_");
                    continue;
                }
                sb.append('|').append(o.optString("t", "")).append('/').append(o.optString("r", ""));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void applyMask(boolean active, org.json.JSONObject ctx, ViewGroup root,
                                  boolean introActive, float introProgress) {
        try {
            if (sMask == null) {
                if (root != null) setupMask(root);
                if (sMask == null) return;
            }
            if (!active || ctx == null) {
                if (sMask.getVisibility() != View.GONE) {
                    sMask.setVisibility(View.GONE);
                    NotificationCardHook.setOverlayVisible(true);
                }
                if (sNotifIconRow != null) sNotifIconRow.setVisibility(View.GONE);
                return;
            }
            org.json.JSONArray lines = ctx.optJSONArray("lines");
            if (lines == null) {
                sMask.setVisibility(View.GONE);
                NotificationCardHook.setOverlayVisible(true);
                if (sNotifIconRow != null) sNotifIconRow.setVisibility(View.GONE);
                return;
            }
            if (sContainer != null && sContainer.getBottom() > 0) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sMask.getLayoutParams();
                float sd = sMask.getResources().getDisplayMetrics().density;
                int target = sContainer.getBottom() + (int)(12 * sd);
                if (lp != null && lp.topMargin != target) {
                    lp.topMargin = target;
                    sMask.setLayoutParams(lp);
                }
            }
            // 多行下沿位置：读 multi_bottom_margin，变化时更新
            float md = sMask.getResources().getDisplayMetrics().density;
            int bottomMarginSetting = com.leowalk.aodchange.SettingsHelper.getInt(sMask.getContext(), "multi_bottom_margin", 165);
            FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) sMask.getLayoutParams();
            if (blp != null && blp.bottomMargin != (int)(bottomMarginSetting * md)) {
                blp.bottomMargin = (int)(bottomMarginSetting * md);
                sMask.setLayoutParams(blp);
            }
            updateNotifIconRow();

            int ctxIdx = ctx.optInt("idx", -1);
            boolean hasRows = sMultiLine != null && sMultiLine.getChildCount() > 0;
            if (ctxIdx == sLastCtxIdx && hasRows) {
                if (com.leowalk.aodchange.hook.ElementSyncHook.isAodVisible()) {
                    sMask.setVisibility(View.VISIBLE);
                    NotificationCardHook.setOverlayVisible(false);
                }
                // 前奏中：三点自驱动动画，无需外部刷新进度
                if (introActive && !sMultiLineViews.isEmpty() && sMultiLineViews.get(0) instanceof IntroDotsView) {
                    sCurRow = 0;
                    rescrollToCurrent(sRoot);
                    return;
                }
                // 更新当前行视图索引（idx 推进但内容不变时�?
                updateCurRowFromCtx(lines);
                rescrollToCurrent(sRoot);
                return;
            }
            sLastCtxIdx = ctxIdx;

            int accent = sAccentColor;
            float d = sMask.getResources().getDisplayMetrics().density;
            sMaskRegionH = sMask.getHeight();
            if (sMaskRegionH <= 0) sMaskRegionH = sMask.getResources().getDisplayMetrics().heightPixels - (int)(335 * d);
            sLineHeight = (int)(30 * d);
            boolean showMultiTrans = com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "multi_line_show_translation", true);
            float multiSize = com.leowalk.aodchange.SettingsHelper.getFloat(sMask.getContext(), "lyric_text_size", 20f);
            boolean swapTrans = com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "swap_lyric_translation", false);
            int widthPercent = com.leowalk.aodchange.SettingsHelper.getInt(sMask.getContext(), "lyric_width_percent", 100);
            int lineSpacing = com.leowalk.aodchange.SettingsHelper.getInt(sMask.getContext(), "multi_line_spacing", 14);
            float lineSpacingPx = lineSpacing * d;
            int sMaskW = sMask.getWidth() > 0 ? sMask.getWidth() : sMask.getResources().getDisplayMetrics().widthPixels;
            int contentW = Math.max(sMaskW - (int)(60 * d), (int)(40 * d));
            int multiWidthPx = widthPercent >= 100 ? contentW
                    : Math.max(contentW * widthPercent / 100, (int)(40 * d));
            String gravStr = com.leowalk.aodchange.SettingsHelper.getString(sMask.getContext(), "lyric_gravity", "center");
            int multiGravity = "left".equals(gravStr) ? Gravity.LEFT
                    : "right".equals(gravStr) ? Gravity.RIGHT : Gravity.CENTER;
            int containerGravity = "left".equals(gravStr) ? Gravity.LEFT
                    : "right".equals(gravStr) ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL;
            sMultiLine.setGravity(containerGravity);

            // 行内容指纹：歌词文本/样式未变时跳过重建，只更新高�?样式与滚动（换行实时不降�?
            String linesKey = buildLinesKey(lines, showMultiTrans, swapTrans, widthPercent,
                    multiSize, lineSpacing, gravStr, accent, introActive);
            if (linesKey.equals(sLastLinesKey) && sMultiLine.getChildCount() > 0) {
                updateCurRowFromCtx(lines);
                applyCurRowStyles(lines, introActive);
                rescrollToCurrent(sRoot);
                return;
            }
            sLastLinesKey = linesKey;

            // ScrollView 滚动方案：动态创建所有歌词行，当前行滚动居中
            sMultiLine.removeAllViews();
            sMultiLineViews.clear();
            int curRow = -1;
            int regionH = sMaskRegionH;
            int row = 0;

            // 前奏占位：三点作为当前行（高亮），下方继续显示未播歌�?
            if (introActive) {
                IntroDotsView dots = new IntroDotsView(sMask.getContext());
                dots.setDotColor(accent);
                dots.startIntro(sIntroFillTime);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        multiWidthPx, (int)(34 * d));
                dlp.topMargin = (int) lineSpacingPx;
                sMultiLine.addView(dots, dlp);
                sMultiLineViews.add(dots);
                curRow = row;
                row++;
            }

            for (int i = 0; i < lines.length(); i++) {
                org.json.JSONObject o = lines.optJSONObject(i);
                if (o == null) continue;
                String text = o.optString("t", "").trim();
                String trans = o.optString("r", "").trim();
                boolean isCur = o.optBoolean("isCur", false);
                // 前奏中：三点是当前行，所有歌词行都按未播（灰色）显示
                if (introActive) isCur = false;
                // 翻译互换
                if (swapTrans && !trans.isEmpty()) {
                    String tmp = text;
                    text = trans;
                    trans = tmp;
                }
                // 翻译与原文相同视为无翻译，避免误显翻译行
                if (!trans.isEmpty() && trans.equals(text)) trans = "";
                if (text.isEmpty() && trans.isEmpty()) continue;

                TextView tv = new TextView(sMask.getContext());
                tv.setText(text);
                tv.setGravity(multiGravity);
                if (isCur) {
                    tv.setTextSize(multiSize);
                    tv.setTextColor(accent);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setSingleLine(false);
                    tv.setMaxLines(3);
                    tv.setEllipsize(null);
                } else {
                    tv.setTextSize(15);
                    tv.setTextColor(Color.argb(170, 255, 255, 255));
                    tv.setTypeface(Typeface.DEFAULT);
                    tv.setSingleLine(false);
                    tv.setMaxLines(2);
                    tv.setEllipsize(null);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        multiWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = (int) lineSpacingPx;
                sMultiLine.addView(tv, lp);
                sMultiLineViews.add(tv);
                if (isCur) curRow = row;
                row++;

                if (!trans.isEmpty() && showMultiTrans) {
                    TextView tvr = new TextView(sMask.getContext());
                    tvr.setText(trans);
                    tvr.setGravity(multiGravity);
                    if (isCur) {
                        tvr.setTextSize(multiSize * 0.62f);
                        tvr.setTextColor(com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "lyric_monet", false)
                                ? sMonetColor2 : fadeColor(accent, 0.8f));
                        tvr.setSingleLine(false);
                        tvr.setMaxLines(2);
                        tvr.setEllipsize(null);
                    } else {
                        tvr.setTextSize(11);
                        tvr.setTextColor(Color.argb(110, 255, 255, 255));
                        tvr.setSingleLine(false);
                        tvr.setMaxLines(2);
                        tvr.setEllipsize(null);
                    }
                    tvr.setTypeface(Typeface.DEFAULT);
                    LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(
                            multiWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
                    trp.topMargin = (int)(2 * d);
                    sMultiLine.addView(tvr, trp);
                    sMultiLineViews.add(tvr);
                    row++;
                }
            }

            // 底部占位：一整页空行，让最后几句也能滚动到顶部固定位置
            View botPad = new View(sMask.getContext());
            sMultiLine.addView(botPad, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, regionH));
            android.util.Log.i("AodChange", "mask build lines=" + lines.length() + " views=" + sMultiLineViews.size()
                    + " idx=" + ctxIdx + " intro=" + introActive);

            sCurRow = curRow;

            // 滚动：当前行固定对准区域顶部第一行（歌手底下），已播歌词不显示，未播向上滚动
            final int fCurRow = curRow;
            final int fRegionH = sMaskRegionH;
            final int fMaskTop = sMask.getTop();
            final java.util.List<View> fViews = new java.util.ArrayList<>(sMultiLineViews);
            sMultiLine.post(() -> {
                try {
                    if (fCurRow < 0 || fCurRow >= fViews.size() || sScroll == null) return;
                    int top = fViews.get(fCurRow).getTop();
                    int h = fViews.get(fCurRow).getHeight();
                    if (top == 0 && h == 0) return;
                    // 当前行固定对准区域顶部第一行（歌手底下），已播歌词不显示，未播向上滚动
                    int scrollY = top;
                    scrollY = Math.max(0, scrollY);
                    sScroll.scrollTo(0, scrollY);
                    int childH = sScroll.getChildAt(0) == null ? 0 : sScroll.getChildAt(0).getHeight();
                    int maxScroll = Math.max(0, childH - sScroll.getHeight());
                    android.util.Log.i("AodChange", "mask scroll curRow=" + fCurRow + " scrollY=" + scrollY
                            + " maxScroll=" + maxScroll + " childH=" + childH + " viewportH=" + sScroll.getHeight()
                            + " regionH=" + fRegionH + " rows=" + sMultiLineViews.size());
                } catch (Throwable ignored) {}
            });

            if (com.leowalk.aodchange.hook.ElementSyncHook.isAodVisible()) {
                sMask.setVisibility(View.VISIBLE);
                NotificationCardHook.setOverlayVisible(false);
            }
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "applyMask fail", t);
        }
    }

    // �?ctx.lines 找当前行（isCur）的视图索引（含翻译行计数），更�?sCurRow
    private static void updateCurRowFromCtx(org.json.JSONArray lines) {
        try {
            if (lines == null || sMultiLineViews == null || sMultiLineViews.isEmpty()) return;
            boolean showMultiTrans = com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "multi_line_show_translation", true);
            boolean swapTrans = com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "swap_lyric_translation", false);
            int row = 0;
            for (int i = 0; i < lines.length(); i++) {
                org.json.JSONObject o = lines.optJSONObject(i);
                if (o == null) continue;
                String text = o.optString("t", "").trim();
                String trans = o.optString("r", "").trim();
                if (swapTrans && !trans.isEmpty()) {
                    String tmp = text;
                    text = trans;
                    trans = tmp;
                }
                if (!trans.isEmpty() && trans.equals(text)) trans = "";
                if (text.isEmpty() && trans.isEmpty()) continue;
                boolean isCur = o.optBoolean("isCur", false);
                if (isCur) { sCurRow = row; return; }
                row++;
                if (!trans.isEmpty() && showMultiTrans) row++;
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 行内容未变跳过重建时：同步更新每行视图的当前行样式（高亮/字号/颜色），
     * 与重建路径的样式逻辑保持一致�?
     */
    private static void applyCurRowStyles(org.json.JSONArray lines, boolean introActive) {
        try {
            if (lines == null || sMultiLineViews == null || sMultiLineViews.isEmpty()) return;
            boolean showMultiTrans = com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "multi_line_show_translation", true);
            boolean swapTrans = com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "swap_lyric_translation", false);
            int accent = sAccentColor;
            float multiSize = com.leowalk.aodchange.SettingsHelper.getFloat(sMask.getContext(), "lyric_text_size", 20f);
            boolean monet = com.leowalk.aodchange.SettingsHelper.get(sMask.getContext(), "lyric_monet", false);
            int transColor = monet ? sMonetColor2 : fadeColor(accent, 0.8f);
            int viewIdx = introActive ? 1 : 0; // 前奏占位（IntroDotsView）占用视图索�?0
            for (int i = 0; i < lines.length(); i++) {
                org.json.JSONObject o = lines.optJSONObject(i);
                if (o == null) continue;
                String text = o.optString("t", "").trim();
                String trans = o.optString("r", "").trim();
                if (swapTrans && !trans.isEmpty()) {
                    String tmp = text;
                    text = trans;
                    trans = tmp;
                }
                if (!trans.isEmpty() && trans.equals(text)) trans = "";
                if (text.isEmpty() && trans.isEmpty()) continue;
                boolean isCur = o.optBoolean("isCur", false);
                if (viewIdx >= sMultiLineViews.size()) return;
                View v = sMultiLineViews.get(viewIdx);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    if (isCur) {
                        tv.setTextSize(multiSize);
                        tv.setTextColor(accent);
                        tv.setTypeface(Typeface.DEFAULT_BOLD);
                        tv.setSingleLine(false);
                        tv.setMaxLines(3);
                        tv.setEllipsize(null);
                    } else {
                        tv.setTextSize(15);
                        tv.setTextColor(Color.argb(170, 255, 255, 255));
                        tv.setTypeface(Typeface.DEFAULT);
                        tv.setSingleLine(false);
                        tv.setMaxLines(2);
                        tv.setEllipsize(null);
                    }
                }
                viewIdx++;
                if (!trans.isEmpty() && showMultiTrans) {
                    if (viewIdx >= sMultiLineViews.size()) return;
                    View v2 = sMultiLineViews.get(viewIdx);
                    if (v2 instanceof TextView) {
                        TextView tvr = (TextView) v2;
                        if (isCur) {
                            tvr.setTextSize(multiSize * 0.62f);
                            tvr.setTextColor(transColor);
                            tvr.setTypeface(Typeface.DEFAULT_BOLD);
                            tvr.setSingleLine(false);
                            tvr.setMaxLines(2);
                            tvr.setEllipsize(null);
                        } else {
                            tvr.setTextSize(11);
                            tvr.setTextColor(Color.argb(110, 255, 255, 255));
                            tvr.setTypeface(Typeface.DEFAULT);
                            tvr.setSingleLine(false);
                            tvr.setMaxLines(2);
                            tvr.setEllipsize(null);
                        }
                    }
                    viewIdx++;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void rescrollToCurrent(ViewGroup root) {
        try {
            if (sScroll == null || sMultiLine == null || sCurRow < 0) return;
            if (sCurRow >= sMultiLineViews.size()) return;
            final int fCurRow = sCurRow;
            final int fMaskTop = sMask.getTop();
            final java.util.List<View> fViews = new java.util.ArrayList<>(sMultiLineViews);
            sMultiLine.post(() -> {
                try {
                    if (fCurRow < 0 || fCurRow >= fViews.size() || sScroll == null) return;
                    int top = fViews.get(fCurRow).getTop();
                    int h = fViews.get(fCurRow).getHeight();
                    if (top == 0 && h == 0) return;
                    // 当前行固定对准区域顶部第一行（与 applyMask 一致）
                    int scrollY = top;
                    scrollY = Math.max(0, scrollY);
                    sScroll.scrollTo(0, scrollY);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static String sLastCustomKey = "";

    private static String readProvider(String key) {
        try {
            Bundle b = sRoot.getContext().getContentResolver().call(URI, key, null, null);
            String s = (b != null) ? b.getString("n") : null;
            return s != null ? s : "";
        } catch (Throwable ignored) { return ""; }
    }

    private static void updateNotifIconRow() {
        try {
            if (sNotifIconRow == null) return;
            boolean enabled = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "multi_show_notif_icons", false);
            if (!enabled) {
                sNotifIconRow.setVisibility(View.GONE);
                return;
            }
            String json = readProvider("get");
            java.util.List<org.json.JSONObject> notifs = new java.util.ArrayList<>();
            if (!json.isEmpty() && !"[]".equals(json)) {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                // 排序：priority 高优先，其次最新（pt 大优先）�?第一个即最重要最�?
                java.util.List<org.json.JSONObject> all = new java.util.ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String p = o.optString("p", "");
                    if (p.isEmpty()) continue;
                    all.add(o);
                }
                all.sort((a, b) -> {
                    int ap = a.optInt("pr", 0), bp = b.optInt("pr", 0);
                    if (ap != bp) return Integer.compare(bp, ap);
                    return Long.compare(b.optLong("pt", 0), a.optLong("pt", 0));
                });
                java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
                for (org.json.JSONObject o : all) {
                    String p = o.optString("p", "");
                    if (!seen.add(p)) continue;
                    notifs.add(o);
                    if (notifs.size() >= 5) break;
                }
            }
            sNotifIconRow.removeAllViews();
            float d = sRoot.getResources().getDisplayMetrics().density;
            if (notifs.isEmpty()) {
                sNotifIconRow.setVisibility(View.GONE);
                return;
            }
            // 第一个通知：图�?+ 主标题（最重要最新，标题过长省略号）
            org.json.JSONObject first = notifs.get(0);
            String firstPkg = first.optString("p", "");
            String firstTitle = first.optString("t", "");
            try {
                android.graphics.drawable.Drawable ic = sRoot.getContext().getPackageManager().getApplicationIcon(firstPkg);
                if (ic != null) {
                    android.widget.ImageView iv = new android.widget.ImageView(sRoot.getContext());
                    iv.setImageDrawable(ic);
                    LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams((int)(24*d), (int)(24*d));
                    vlp.setMargins((int)(3*d), 0, (int)(6*d), 0);
                    sNotifIconRow.addView(iv, vlp);
                }
            } catch (Throwable ignored) {}
            if (firstTitle != null && !firstTitle.isEmpty()) {
                TextView tv = new TextView(sRoot.getContext());
                tv.setText(firstTitle);
                tv.setTextSize(14);
                tv.setTextColor(Color.argb(220, 255, 255, 255));
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setIncludeFontPadding(false);
                tv.setMaxWidth((int)(180 * d)); // 太长省略�?
                LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tvlp.rightMargin = (int)(12 * d); // 与后续图标保持距�?
                sNotifIconRow.addView(tv, tvlp);
            }
            // 后面最�?个纯图标
            for (int i = 1; i < notifs.size(); i++) {
                String pkg = notifs.get(i).optString("p", "");
                try {
                    android.graphics.drawable.Drawable ic = sRoot.getContext().getPackageManager().getApplicationIcon(pkg);
                    if (ic == null) continue;
                    android.widget.ImageView iv = new android.widget.ImageView(sRoot.getContext());
                    iv.setImageDrawable(ic);
                    LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams((int)(24*d), (int)(24*d));
                    vlp.setMargins((int)(3*d), 0, (int)(3*d), 0);
                    sNotifIconRow.addView(iv, vlp);
                } catch (Throwable ignored) {}
            }
            // 带背景圆角容器（线框风格�?
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(android.graphics.Color.argb(20, 255, 255, 255));
            bg.setStroke((int)(1 * d), android.graphics.Color.argb(60, 255, 255, 255));
            bg.setCornerRadius(12 * d);
            sNotifIconRow.setPadding((int)(10*d), (int)(5*d), (int)(10*d), (int)(5*d));
            sNotifIconRow.setBackground(bg);
            sNotifIconRow.setVisibility(View.VISIBLE);
            // 定位�?sMask 下沿之下
            if (sNotifIconRow.getVisibility() == View.VISIBLE) {
                int rootH = sRoot.getHeight();
                if (rootH <= 0) rootH = sRoot.getResources().getDisplayMetrics().heightPixels;
                int bm = com.leowalk.aodchange.SettingsHelper.getInt(sRoot.getContext(), "multi_bottom_margin", 165);
                String iga = com.leowalk.aodchange.SettingsHelper.getString(sRoot.getContext(), "multi_icon_row_gravity", "left");
                int igaGrav = "center".equals(iga) ? Gravity.CENTER_HORIZONTAL
                        : "right".equals(iga) ? Gravity.RIGHT : Gravity.LEFT;
                FrameLayout.LayoutParams nirp = (FrameLayout.LayoutParams) sNotifIconRow.getLayoutParams();
                nirp.gravity = Gravity.TOP | igaGrav;
                nirp.topMargin = rootH - (int)(bm * d) + (int)(8 * d);
                nirp.leftMargin = igaGrav == Gravity.LEFT ? (int)(30 * d) : 0;
                sNotifIconRow.setLayoutParams(nirp);
            }
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "updateNotifIconRow fail", t);
        }
    }

    public static void hideMediaViews() {
        View[] views = {sAlbumArtWrap, sSongTitle, sSongSubtitle, sSongArtist, sLyric, sSubLyric, sIntroDots};
        for (View v : views) {
            if (v == null) continue;
            v.setVisibility(View.GONE);
        }
        if (sSongRow != null) sSongRow.setVisibility(View.GONE);
        if (sMask != null) sMask.setVisibility(View.GONE);
        // sContainer 显隐�?ElementSyncHook 跟随时钟控制，这里不强制显示
    }

    public static void fadeOutMedia() {
        View[] views = {sSongTitle, sSongSubtitle, sSongArtist, sLyric, sSubLyric};
        for (View v : views) {
            if (v == null) continue;
            if (v.getVisibility() != View.VISIBLE) continue;
            v.animate().alpha(0f).setDuration(200L)
                    .withEndAction(() -> { try { v.setVisibility(View.GONE); } catch (Throwable ignored) {} })
                    .start();
        }
    }

    public static void fadeInMedia() {
        View[] views = {sSongTitle, sSongSubtitle, sSongArtist, sLyric, sSubLyric};
        for (View v : views) {
            if (v == null) continue;
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(300L).start();
        }
    }

    private static int fadeColor(int color, float factor) {
        return Color.argb((int)(255 * factor), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void setAppIcon(String pkg, int color) {
        try {
            if (sRoot == null) return;
            boolean titleIcon = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "title_icon_title", false);
            boolean albumIcon = com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "title_icon_albumart", false);
            boolean showAny = titleIcon || albumIcon;
            if (pkg == null || pkg.isEmpty() || !showAny) {
                if (sAppIcon != null) sAppIcon.setVisibility(View.GONE);
                if (sAlbumIcon != null) sAlbumIcon.setVisibility(View.GONE);
                return;
            }
            int accent = sAccentColor != 0 ? sAccentColor : Color.WHITE;
            if (com.leowalk.aodchange.SettingsHelper.get(sRoot.getContext(), "lyric_monet", false)) {
                accent = sMonetInfoColor1 != 0 ? sMonetInfoColor1 : accent;
            }
            // 图标内容（与位置无关，两个图标共用同一 drawable�?
            // 切歌/AODView 重建�?iconKey 可能相同�?drawable 为空（新 View），需强制重设
            // 两个图标各自的自定义取色（未开启自定义则沿用专辑主色）
            android.content.Context ctx = sRoot.getContext();
            String titleMode = com.leowalk.aodchange.SettingsHelper.getString(ctx, "title_icon_title_mode", "auto");
            String albumMode = com.leowalk.aodchange.SettingsHelper.getString(ctx, "title_icon_albumart_mode", "auto");
            boolean titleCustom = "custom".equals(titleMode);
            boolean albumCustom = "custom".equals(albumMode);
            // 自动取色：按专辑主色（入参 color 即媒体元数据的专辑主色）；无专辑主色时沿用歌词强调色
            int autoTint = color != 0 ? color : accent;
            int titleTint = titleCustom
                    ? com.leowalk.aodchange.SettingsHelper.getInt(ctx, "title_icon_title_color", 0xFFFFFF) : autoTint;
            int albumTint = albumCustom
                    ? com.leowalk.aodchange.SettingsHelper.getInt(ctx, "title_icon_albumart_color", 0xFFFFFF) : autoTint;

            String iconKey = pkg + "|" + titleTint + "|" + albumTint;
            boolean needDrawable = !iconKey.equals(sAppIconKey)
                    || (sAppIcon != null && sAppIcon.getDrawable() == null)
                    || (sAlbumIcon != null && sAlbumIcon.getDrawable() == null);
            if (needDrawable) {
                sAppIconKey = iconKey;
                int iconRes = resolveMusicIcon(pkg);
                boolean preset = iconRes != 0;
                Drawable base = null;
                if (iconRes != 0) {
                    try {
                        android.content.Context mc = sRoot.getContext().createPackageContext("com.leowalk.aodchange", 0);
                        base = mc.getResources().getDrawable(iconRes, null);
                    } catch (Exception ignored) {}
                }
                if (base == null) {
                    try {
                        base = sRoot.getContext().getPackageManager().getApplicationIcon(pkg);
                    } catch (Exception ignored) {}
                }
                if (base == null) {
                    if (sAppIcon != null) sAppIcon.setVisibility(View.GONE);
                    if (sAlbumIcon != null) sAlbumIcon.setVisibility(View.GONE);
                    return;
                }
                // 仅预设内置图标支持取色；其他应用图标保持原样（不着色）
                Drawable titleD = base.getConstantState() != null ? base.getConstantState().newDrawable() : base.mutate();
                if (preset) titleD.setTint(0xFF000000 | (titleTint & 0xFFFFFF));
                if (sAppIcon != null) sAppIcon.setImageDrawable(titleD);
                Drawable albumD = base.getConstantState() != null ? base.getConstantState().newDrawable() : base.mutate();
                if (preset) albumD.setTint(0xFF000000 | (albumTint & 0xFFFFFF));
                if (sAlbumIcon != null) sAlbumIcon.setImageDrawable(albumD);
            }
            // 标题图标：只在标题行，与封面无关
            if (sAppIcon != null) {
                sAppIcon.setVisibility(titleIcon ? View.VISIBLE : View.GONE);
                if (titleIcon) {
                    sAppIcon.animate().cancel();
                    sAppIcon.setAlpha(1f);
                    updateIconSpan();
                }
            }
            // 封面图标：只在封面右下角，与标题无关
            if (sAlbumIcon != null) {
                sAlbumIcon.setVisibility(albumIcon ? View.VISIBLE : View.GONE);
                if (albumIcon) {
                    sAlbumIcon.animate().cancel();
                    sAlbumIcon.setAlpha(1f);
                    updateAlbumIconSpan();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int resolveMusicIcon(String pkg) {
        if (pkg == null) return 0;
        String lp = pkg.toLowerCase();
        if (lp.contains("netease")) return com.leowalk.aodchange.R.drawable.ic_app_icon_netease;
        if (lp.contains("qqmusic") || lp.contains("tencent") || lp.contains("miui.player")) return com.leowalk.aodchange.R.drawable.ic_app_icon_qq;
        if (lp.contains("kugou")) return com.leowalk.aodchange.R.drawable.ic_app_icon_kugou;
        if (lp.contains("kuwo")) return com.leowalk.aodchange.R.drawable.ic_app_icon_kuwo;
        if (lp.contains("luna.music")) return com.leowalk.aodchange.R.drawable.ic_app_icon_qishui;
        if (lp.contains("wenyu.bodian")) return com.leowalk.aodchange.R.drawable.ic_app_icon_bodian;
        if (lp.contains("spotify")) return com.leowalk.aodchange.R.drawable.ic_app_icon_spotify;
        if (lp.contains("apple") || lp.contains("itunes")) return com.leowalk.aodchange.R.drawable.ic_app_icon_apple;
        return 0;
    }

    /** 前奏占位符：3 个空心圆圈，随歌曲播放位置填充（一首歌一次性，填满后保持） */
    private static class IntroDotsView extends android.view.View {
        private float progress = 0f; // 0..1
        private int color = Color.WHITE;
        private final android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private long fillTimeMs = 0;
        private long lastPos = -1;

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (progress >= 1f) return; // 已填满，保持
                // 进度跟随歌曲播放位置（不依赖 View 出现时刻，进�?AOD 不重新计时）
                long pos = LyricHook.getMediaPosition();
                if (pos == lastPos && lastPos >= 0) {
                    // 位置没变（可能暂停），继续轮询等恢复
                    handler.postDelayed(this, 100L);
                    return;
                }
                lastPos = pos;
                float p = fillTimeMs > 0 ? (float) pos / (float) fillTimeMs : 0f;
                progress = Math.max(0f, Math.min(1f, p));
                invalidate();
                handler.postDelayed(this, 100L);
            }
        };

        IntroDotsView(android.content.Context c) {
            super(c);
        }

        /** 启动填充：从当前播放位置开始，fillTimeMs 时填满（幂等：同一首歌不重启） */
        void startIntro(long fillTimeMs) {
            if (this.fillTimeMs == fillTimeMs && running()) return;
            this.fillTimeMs = fillTimeMs;
            lastPos = -1;
            handler.removeCallbacks(tick);
            handler.post(tick);
            invalidate();
        }

        private boolean running() {
            return fillTimeMs > 0;
        }

        /** 停止动画 */
        void stopIntro() {
            handler.removeCallbacks(tick);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            handler.removeCallbacks(tick);
        }

        void setProgress(float p) {
            p = Math.max(0f, Math.min(1f, p));
            if (p != progress) {
                progress = p;
                invalidate();
            }
        }

        void setDotColor(int c) {
            if (c != color) {
                color = c;
                invalidate();
            }
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float d = getResources().getDisplayMetrics().density;
            float radius = 6 * d;
            float gap = 26 * d;
            float cy = getHeight() / 2f;
            float cx = getWidth() / 2f - gap;
            for (int i = 0; i < 3; i++) {
                float x = cx + i * gap;
                // 空心�?
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setColor(color);
                canvas.drawCircle(x, cy, radius, paint);
                // 填充：第 i 个圆的填充度 = progress*3 - i，裁剪到 0..1
                float fill = Math.max(0f, Math.min(1f, progress * 3f - i));
                if (fill > 0.01f) {
                    paint.setStyle(android.graphics.Paint.Style.FILL);
                    paint.setColor(color);
                    android.graphics.RectF rect = new android.graphics.RectF(
                            x - radius, cy - radius, x + radius, cy + radius);
                    canvas.drawArc(rect, -90f, 360f * fill, true, paint);
                }
            }
        }
    }
}
