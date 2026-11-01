package io.github.op7418.guizangtv;

final class BootStartPolicy {
    private BootStartPolicy() { }
    /** Xiaomi also invokes its cached AutoLaunchApp when start_3rd_app is empty. */
    static boolean tv6TargetAllowed(String target,String packageName) {
        return target==null || target.isEmpty() || packageName.equals(target);
    }
    static String tv6SkipReason(boolean enabled, boolean targetAllowed, long elapsed, long last) {
        if (!enabled) return "开机自动进入已关闭";
        if (!targetAllowed) return "系统启动目标已变更，已跳过";
        if (last >= 0 && elapsed >= last && elapsed - last < 2000)
            return "刚刚已处理系统启动入口，已跳过重复调用";
        return null;
    }
    static String skipReason(boolean supported, boolean enabled, int boot, int attempted, long elapsed) {
        if (!supported) return "当前机型未适配，已跳过";
        if (!enabled) return "开机自动进入已关闭";
        if (boot < 0) return "无法确认本次启动，已跳过";
        if (boot == attempted) return "本次开机已尝试，未重复打开";
        if (elapsed < 0 || elapsed > 10 * 60_000L) return "启动通知过晚，已跳过以免打断使用";
        return null;
    }
}
