package com.leowalk.aodchange.hook;

/**
 * 农历/节日/节气查询：基于 cn.6tail.lunar（lunar-java，与 lunar-javascript 同源同算法）。
 * 纯 Java 实现，无 JS 引擎依赖，主线程安全（轻量计算）。
 */
public class LunarBridge {

    /** 查询某天（公历年月日）的节日与节气名列表（逗号分隔），失败返回 null */
    public static String festivalsOf(android.content.Context ctx, int y, int m, int d) {
        try {
            java.util.List<String> list = new java.util.ArrayList<>();
            com.nlf.calendar.Solar solar = com.nlf.calendar.Solar.fromYmd(y, m, d);
            com.nlf.calendar.Lunar lunar = solar.getLunar();
            // 农历节日
            try {
                java.util.List<String> fs = lunar.getFestivals();
                if (fs != null) {
                    for (String f : fs) {
                        if (f != null && !f.trim().isEmpty()) list.add(f.trim());
                    }
                }
            } catch (Throwable ignored) {}
            // 节气
            try {
                String jq = lunar.getJieQi();
                if (jq != null && !jq.trim().isEmpty()) list.add(jq.trim());
            } catch (Throwable ignored) {}
            // 公历节日
            try {
                java.util.List<String> sf = solar.getFestivals();
                if (sf != null) {
                    for (String f : sf) {
                        if (f != null && !f.trim().isEmpty()) list.add(f.trim());
                    }
                }
            } catch (Throwable ignored) {}
            if (list.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (String s : list) {
                if (sb.length() > 0) sb.append(',');
                sb.append(s);
            }
            return sb.toString();
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "lunar query fail", t);
            return null;
        }
    }
}
