package com.leowalk.aodchange.hook;

/**
 * 节日节气查询：基于 lunar-javascript（Rhino 桥接）——
 * 农历节日（春节/中秋等）、24 节气、公历节日。
 * 全部在后台线程执行并缓存，主线程只读缓存（避免 Rhino 初始化/JS 执行阻塞渲染）。
 */
public class FestivalUtil {

    public static class Festival {
        public String name;
        public long time;
        public boolean isTerm;
    }

    private static volatile java.util.List<Festival> sCache;
    private static volatile String sCacheKey = "";
    private static volatile boolean sLoading;
    private static volatile android.content.Context sCtx;

    /** 锁屏等时机调用：后台预加载未来 7 天节日/节气（非阻塞） */
    public static void ensureLoad(android.content.Context ctx) {
        try {
            if (sLoading) return;
            String key = dayKey() + "|7";
            if (key.equals(sCacheKey)) return;
            sLoading = true;
            sCtx = ctx.getApplicationContext();
            new Thread(() -> {
                try {
                    java.util.List<Festival> r = queryUpcomingSync(sCtx, System.currentTimeMillis(), 7, 10);
                    sCache = r;
                    sCacheKey = dayKey() + "|7";
                    android.util.Log.i("AodChange", "festivals cached=" + (r == null ? 0 : r.size()));
                    // 通知刷新：缓存就绪后重新渲染（节日进入视图）
                    LyricHook.requestRefresh();
                } catch (Throwable t) {
                    android.util.Log.w("AodChange", "festival query fail", t);
                }
                sLoading = false;
            }, "festival-query").start();
        } catch (Throwable ignored) {}
    }

    /** 主线程读取缓存（非阻塞；未就绪返回空列表，锁屏期间后台加载完成后下次更新） */
    public static java.util.List<Festival> getCached() {
        java.util.List<Festival> c = sCache;
        return c != null ? c : new java.util.ArrayList<>();
    }

    private static String dayKey() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault());
        return f.format(new java.util.Date());
    }

    private static java.util.List<Festival> queryUpcomingSync(android.content.Context ctx, long now, int days, int max) {
        java.util.List<Festival> list = new java.util.ArrayList<>();
        try {
            java.util.Calendar base = java.util.Calendar.getInstance();
            base.setTimeInMillis(now);
            int y = base.get(java.util.Calendar.YEAR);
            int m = base.get(java.util.Calendar.MONTH) + 1;
            int d = base.get(java.util.Calendar.DAY_OF_MONTH);
            for (int i = 0; i <= days; i++) {
                String[] names = null;
                try {
                    String r = LunarBridge.festivalsOf(ctx, y, m, d);
                    if (r != null && !r.isEmpty()) names = r.split(",");
                } catch (Throwable ignored) {}
                if (names != null) {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.clear();
                    c.set(y, m - 1, d, 0, 0, 0);
                    long t = c.getTimeInMillis();
                    for (String name : names) {
                        if (name == null || name.trim().isEmpty()) continue;
                        Festival f = new Festival();
                        f.name = name.trim();
                        f.time = t;
                        f.isTerm = isTermName(f.name);
                        list.add(f);
                    }
                }
                java.util.Calendar nc = java.util.Calendar.getInstance();
                nc.clear();
                nc.set(y, m - 1, d);
                nc.add(java.util.Calendar.DAY_OF_YEAR, 1);
                y = nc.get(java.util.Calendar.YEAR);
                m = nc.get(java.util.Calendar.MONTH) + 1;
                d = nc.get(java.util.Calendar.DAY_OF_MONTH);
            }
            list.sort((a, b) -> Long.compare(a.time, b.time));
            if (list.size() > max) list = new java.util.ArrayList<>(list.subList(0, max));
        } catch (Throwable ignored) {}
        return list;
    }

    private static boolean isTermName(String name) {
        return "小寒".equals(name) || "大寒".equals(name) || "立春".equals(name) || "雨水".equals(name)
                || "惊蛰".equals(name) || "春分".equals(name) || "清明".equals(name) || "谷雨".equals(name)
                || "立夏".equals(name) || "小满".equals(name) || "芒种".equals(name) || "夏至".equals(name)
                || "小暑".equals(name) || "大暑".equals(name) || "立秋".equals(name) || "处暑".equals(name)
                || "白露".equals(name) || "秋分".equals(name) || "寒露".equals(name) || "霜降".equals(name)
                || "立冬".equals(name) || "小雪".equals(name) || "大雪".equals(name) || "冬至".equals(name);
    }
}
