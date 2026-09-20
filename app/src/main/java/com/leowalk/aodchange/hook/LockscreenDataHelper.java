package com.leowalk.aodchange.hook;

import android.app.AlarmManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;

/**
 * 锁屏/AOD 小组件数据：
 * 天气优先走小米主题天气 Provider（AOD 同路径），失败再反射 DataUtils；
 * 闹钟优先 AlarmManager.getNextAlarmClock()。
 */
public final class LockscreenDataHelper {

    private static final String CLOCK_DATA_UTILS = "com.miui.clock.utils.DataUtils";
    private static final String HEALTH_BEAN = "com.miui.clock.module.HealthBean";
    private static final String NEXT_ALARM_CLOCK = "next_alarm_clock_formatted";
    private static final String NEXT_ALARM_CLOCK_LONG = "next_alarm_clock_long";
    private static final Uri WEATHER_URI =
            Uri.parse("content://weather/actualWeatherData/2");
    private static final int HEALTH_STEPS = 500;
    private static final int HEALTH_STAND = 504;

    public static final class WeatherSnapshot {
        public String temperature = "--°";
        public String condition = "--";
        public String city = "--";
        public String highLow = "H: --°  L: --°";
        public Drawable icon;
        public int sunrise;
        public int sunriseTomorrow;
        public int sunset;
        public String humidity = "--";
        public String aqi = "--";
        public String feelsLike = "--°";
        public Drawable feelsLikeIcon;
        public String wind = "--";
        public Drawable windIcon;
    }

    /** 下一闹钟：展示文案 + 触发时间（用于紧迫性着色） */
    public static final class AlarmSnapshot {
        public String display;
        public long triggerAt;
    }

    private LockscreenDataHelper() {}

    public static WeatherSnapshot readWeather(Context ctx) {
        WeatherSnapshot fromProvider = readWeatherFromProvider(ctx);
        if (fromProvider != null) return fromProvider;
        return readWeatherFromDataUtils(ctx);
    }

    public static Integer readSteps(Context ctx) {
        return readHealthCount(ctx, HEALTH_STEPS, "getStepCountNow");
    }

    public static Integer readStand(Context ctx) {
        return readHealthCount(ctx, HEALTH_STAND, "getStandCountNow");
    }

    public static AlarmSnapshot readNextAlarm(Context ctx) {
        // 小米把用户闹钟和更早的系统闹钟分开存：
        // next_alarm_formatted / getNextAlarmClock() 往往是更近的一条（例如 00:00），
        // 锁屏时钟模板实际用的是 next_alarm_clock_long（例如 07:00）。
        long trigger = readLongSetting(ctx, NEXT_ALARM_CLOCK_LONG);
        String formatted = readStringSetting(ctx, NEXT_ALARM_CLOCK);
        if (trigger > System.currentTimeMillis()) {
            AlarmSnapshot out = new AlarmSnapshot();
            out.triggerAt = trigger;
            out.display = formatAlarmTrigger(trigger);
            android.util.Log.i("AodChange", "alarm clock long=" + trigger + " show=" + out.display
                    + " formatted=" + formatted);
            return out;
        }
        if (formatted != null && !formatted.trim().isEmpty()) {
            AlarmSnapshot out = new AlarmSnapshot();
            out.display = extractAlarmTimeText(formatted.trim());
            out.triggerAt = estimateTriggerFromFormatted(formatted);
            android.util.Log.i("AodChange", "alarm clock formatted=" + formatted);
            return out;
        }
        try {
            AlarmManager am = ctx.getSystemService(AlarmManager.class);
            if (am != null) {
                AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
                if (info != null && info.getTriggerTime() > System.currentTimeMillis()) {
                    AlarmSnapshot out = new AlarmSnapshot();
                    out.triggerAt = info.getTriggerTime();
                    out.display = formatAlarmTrigger(out.triggerAt);
                    android.util.Log.i("AodChange", "alarm manager fallback " + out.display);
                    return out;
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "AlarmManager next fail", t);
        }
        return null;
    }

    public static String formatMinute(int minuteOfDay) {
        if (minuteOfDay < 0 || minuteOfDay >= 24 * 60) return "--:--";
        return String.format(Locale.getDefault(), "%02d:%02d",
                minuteOfDay / 60, minuteOfDay % 60);
    }

    /** 小米天气 sunrise/sunset：距纪元的毫秒，按本地时区取时分（官方主题文档） */
    public static int millisOfDayToMinute(long msFromEpoch) {
        if (msFromEpoch <= 0) return 0;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(msFromEpoch);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static WeatherSnapshot readWeatherFromProvider(Context ctx) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(WEATHER_URI, null, null, null, null);
            if (c == null || !c.moveToFirst()) {
                android.util.Log.i("AodChange", "weather provider empty");
                return null;
            }
            // day=1 为今天；找不到则用第一行
            int dayIdx = c.getColumnIndex("day");
            boolean foundToday = false;
            if (dayIdx >= 0) {
                do {
                    if (c.getInt(dayIdx) == 1) {
                        foundToday = true;
                        break;
                    }
                } while (c.moveToNext());
                if (!foundToday) c.moveToFirst();
            }

            WeatherSnapshot out = new WeatherSnapshot();
            out.city = colString(c, "city_name", "天气");
            out.condition = colString(c, "description", "--");
            if (out.condition.isEmpty()) out.condition = "--";
            String temp = colString(c, "temperature", "--");
            out.temperature = normalizeTemp(temp);
            String feels = colString(c, "feel_temperature", "");
            if (feels.isEmpty()) feels = colString(c, "somatosensory", "");
            out.feelsLike = feels.isEmpty() ? "--°" : normalizeTemp(feels);

            String high = colString(c, "tmphighs", "");
            String low = colString(c, "tmplows", "");
            if (!high.isEmpty() || !low.isEmpty()) {
                out.highLow = "H: " + stripUnit(high.isEmpty() ? "--" : high)
                        + "°  L: " + stripUnit(low.isEmpty() ? "--" : low) + "°";
            }

            int humidity = colInt(c, "humidity", -1);
            out.humidity = humidity >= 0 ? humidity + "%" : "--";
            int aqi = colInt(c, "aqilevel", -1);
            out.aqi = aqi >= 0 ? String.valueOf(aqi) : "--";

            String wind = colString(c, "wind", "");
            if (!wind.isEmpty()) {
                out.wind = wind.replace(',', ' ').replace('，', ' ').trim();
            }

            long sunriseMs = colLong(c, "sunrise", 0);
            long sunsetMs = colLong(c, "sunset", 0);
            out.sunrise = millisOfDayToMinute(sunriseMs);
            out.sunset = millisOfDayToMinute(sunsetMs);

            android.util.Log.i("AodChange", "weather provider ok city=" + out.city
                    + " temp=" + out.temperature + " cond=" + out.condition
                    + " sun=" + out.sunrise + "/" + out.sunset);
            return out;
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "weather provider fail", t);
            return null;
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) {}
        }
    }

    private static WeatherSnapshot readWeatherFromDataUtils(Context ctx) {
        try {
            ClassLoader cl = resolveClassLoader(ctx);
            Class<?> dataUtils = Class.forName(CLOCK_DATA_UTILS, false, cl);
            java.lang.reflect.Method getter = dataUtils.getMethod(
                    "getWeatherBean", String.class, WeakReference.class);
            Object bean = null;
            for (String type : new String[]{"2", "1"}) {
                try {
                    bean = getter.invoke(null, type, new WeakReference<>(ctx));
                } catch (Throwable ignored) {}
                if (bean != null) break;
            }
            if (bean == null) {
                android.util.Log.i("AodChange", "DataUtils weather bean null");
                return null;
            }

            WeatherSnapshot out = new WeatherSnapshot();
            boolean valid = boolOr(call(bean, "getTemperatureValid"), true);
            out.temperature = valid
                    ? ((Number) numOr(call(bean, "getTemperature"), 0)).intValue() + "°"
                    : "--°";
            out.condition = strOr(call(bean, "getDescription"), "晴");
            if (out.condition.isEmpty()) out.condition = "晴";
            out.city = strOr(call(bean, "getCityName"), "天气");
            if (out.city.isEmpty()) out.city = "天气";
            Integer high = asInt(call(bean, "getHighestTemperature"));
            Integer low = asInt(call(bean, "getLowestTemperature"));
            out.highLow = "H: " + (high != null ? high : "--") + "°  L: "
                    + (low != null ? low : "--") + "°";

            Calendar cal = Calendar.getInstance();
            int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            out.sunrise = intOr(call(bean, "getSunriseMinuteTime"), 0);
            out.sunriseTomorrow = parseTimeOfDay(strOr(call(bean, "getSunriseTomorrowTimeString"), null));
            if (out.sunriseTomorrow <= 0) {
                out.sunriseTomorrow = intOr(call(bean, "getSunriseTomorrowMinuteTime"), 0);
            }
            out.sunset = intOr(call(bean, "getSunsetMinuteTime"), 0);
            boolean night = out.sunrise > 0 && out.sunset > 0
                    && (now < out.sunrise || now > out.sunset);
            Integer iconId = asInt(call(bean, "getIconResId", night, false));
            if (iconId != null && iconId != 0) {
                try { out.icon = ctx.getDrawable(iconId); } catch (Throwable ignored) {}
            }

            Integer humidity = asInt(call(bean, "getHumidity"));
            out.humidity = (humidity != null && humidity >= 0 && humidity <= 100)
                    ? humidity + "%" : "--";
            if (boolOr(call(bean, "isAQIDateValid"), false)) {
                out.aqi = strOr(call(bean, "getAQILevel"), "--");
                if (out.aqi.isEmpty()) out.aqi = "--";
            }
            boolean feelsValid = boolOr(call(bean, "getFeelTemperatureValid"), false);
            out.feelsLike = feelsValid
                    ? ((Number) numOr(call(bean, "getSomatosensoryTemperature"), 0)).intValue() + "°"
                    : "--°";
            Integer feelsIconId = asInt(call(bean, "getSomatosensoryResId", 0));
            if (feelsValid && feelsIconId != null && feelsIconId != 0) {
                try { out.feelsLikeIcon = ctx.getDrawable(feelsIconId); } catch (Throwable ignored) {}
            }
            Integer windIconId = asInt(call(bean, "getWindIconResId"));
            if (windIconId != null && windIconId != 0) {
                try { out.windIcon = ctx.getDrawable(windIconId); } catch (Throwable ignored) {}
            }
            Integer windDirRes = asInt(call(bean, "getWindDescResIdFull"));
            String windDir = "";
            if (windDirRes != null && windDirRes != 0) {
                try { windDir = ctx.getString(windDirRes); } catch (Throwable ignored) {}
            }
            String windStrength = strOr(call(bean, "getWindStrength"), "");
            out.wind = (!windDir.isEmpty() && !windStrength.isEmpty())
                    ? windDir + " " + windStrength + "级" : "--";
            android.util.Log.i("AodChange", "DataUtils weather ok temp=" + out.temperature);
            return out;
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "readWeather DataUtils fail", t);
            return null;
        }
    }

    private static Integer readHealthCount(Context ctx, int type, String getter) {
        try {
            ClassLoader cl = resolveClassLoader(ctx);
            Class<?> dataUtils = Class.forName(CLOCK_DATA_UTILS, false, cl);
            Class<?> healthBean = Class.forName(HEALTH_BEAN, false, cl);
            java.lang.reflect.Method method = null;
            for (java.lang.reflect.Method m : dataUtils.getMethods()) {
                if (!"getHealthBean".equals(m.getName()) || m.getParameterCount() != 3) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts[0] == WeakReference.class
                        && pts[1] == int.class
                        && pts[2].isAssignableFrom(healthBean)) {
                    method = m;
                    break;
                }
            }
            if (method == null) return null;
            Object bean = method.invoke(null, new WeakReference<>(ctx), type, null);
            if (bean == null) return null;
            Object steps = call(bean, getter);
            if (!(steps instanceof Number)) return null;
            int v = ((Number) steps).intValue();
            return v >= 0 ? v : null;
        } catch (Throwable t) {
            android.util.Log.w("AodChange", "readHealth " + type + " fail", t);
            return null;
        }
    }

    private static String readStringSetting(Context ctx, String key) {
        try {
            String s = Settings.System.getString(ctx.getContentResolver(), key);
            if (s != null && !s.trim().isEmpty()) return s.trim();
        } catch (Throwable ignored) {}
        try {
            String s = Settings.Global.getString(ctx.getContentResolver(), key);
            if (s != null && !s.trim().isEmpty()) return s.trim();
        } catch (Throwable ignored) {}
        return null;
    }

    private static long readLongSetting(Context ctx, String key) {
        String s = readStringSetting(ctx, key);
        if (s == null) return 0;
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String formatAlarmTrigger(long triggerAt) {
        Calendar now = Calendar.getInstance();
        Calendar ec = Calendar.getInstance();
        ec.setTimeInMillis(triggerAt);
        java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        if (now.get(Calendar.YEAR) == ec.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == ec.get(Calendar.DAY_OF_YEAR)) {
            return hm.format(ec.getTime());
        }
        now.add(Calendar.DAY_OF_YEAR, 1);
        if (now.get(Calendar.YEAR) == ec.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == ec.get(Calendar.DAY_OF_YEAR)) {
            return "明天 " + hm.format(ec.getTime());
        }
        java.text.SimpleDateFormat md = new java.text.SimpleDateFormat("M/d HH:mm", Locale.getDefault());
        return md.format(ec.getTime());
    }

    /** 从「周日22:27」「Tue 7:00 AM」等格式抽出时间；整串过长则保留时间部分 */
    private static String extractAlarmTimeText(String raw) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d{1,2}\\s*[:：]\\s*\\d{2}(?:\\s*(?:[AaPp][Mm]|上午|下午))?)").matcher(raw);
        if (m.find()) return m.group(1).replace('：', ':').replaceAll("\\s+", "");
        return raw.length() > 12 ? raw.substring(0, 12) + "…" : raw;
    }

    /** 粗估触发时间（仅用于紧迫性；优先仍用 AlarmManager） */
    private static long estimateTriggerFromFormatted(String raw) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d{1,2})\\s*[:：]\\s*(\\d{2})").matcher(raw);
        if (!m.find()) return 0;
        try {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, min);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            return cal.getTimeInMillis();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String normalizeTemp(String t) {
        if (t == null || t.isEmpty() || "--".equals(t)) return "--°";
        String s = t.trim().replace("℃", "°").replace("°C", "°").replace("°c", "°");
        if (!s.endsWith("°")) s = s + "°";
        return s;
    }

    private static String stripUnit(String t) {
        if (t == null) return "--";
        return t.replace("℃", "").replace("°C", "").replace("°c", "").replace("°", "").trim();
    }

    private static String colString(Cursor c, String col, String def) {
        int i = c.getColumnIndex(col);
        if (i < 0) return def;
        try {
            String s = c.getString(i);
            return s != null ? s : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static int colInt(Cursor c, String col, int def) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return def;
        try {
            return c.getInt(i);
        } catch (Throwable ignored) {
            try {
                return (int) Double.parseDouble(c.getString(i));
            } catch (Throwable ignored2) {
                return def;
            }
        }
    }

    private static long colLong(Cursor c, String col, long def) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return def;
        try {
            return c.getLong(i);
        } catch (Throwable ignored) {
            try {
                return Long.parseLong(c.getString(i));
            } catch (Throwable ignored2) {
                return def;
            }
        }
    }

    private static ClassLoader resolveClassLoader(Context ctx) {
        ClassLoader cl = ctx.getClassLoader();
        if (canLoad(cl, CLOCK_DATA_UTILS)) return cl;
        try {
            Context sysui = ctx.createPackageContext("com.android.systemui",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ClassLoader scl = sysui.getClassLoader();
            if (canLoad(scl, CLOCK_DATA_UTILS)) return scl;
        } catch (Throwable ignored) {}
        ClassLoader parent = cl != null ? cl.getParent() : null;
        if (canLoad(parent, CLOCK_DATA_UTILS)) return parent;
        return cl;
    }

    private static boolean canLoad(ClassLoader cl, String name) {
        if (cl == null) return false;
        try {
            Class.forName(name, false, cl);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object call(Object bean, String name, Object... args) {
        try {
            for (java.lang.reflect.Method m : bean.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    return m.invoke(bean, args);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int parseTimeOfDay(String value) {
        if (value == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d{1,2}):(\\d{2})$").matcher(value.trim());
        if (!m.matches()) return 0;
        try {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (h < 0 || h > 23 || min < 0 || min > 59) return 0;
            return h * 60 + min;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String strOr(Object o, String def) {
        return o instanceof String ? (String) o : def;
    }

    private static boolean boolOr(Object o, boolean def) {
        return o instanceof Boolean ? (Boolean) o : def;
    }

    private static Object numOr(Object o, Number def) {
        return o instanceof Number ? o : def;
    }

    private static int intOr(Object o, int def) {
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    private static Integer asInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : null;
    }
}
