package com.atguigu.tingshu.common.util;

/**
 * 获取当前用户信息帮助类
 */
public class AuthContextHolder {

    private static ThreadLocal<Long> userId = new ThreadLocal<Long>();
    private static ThreadLocal<String> username = new ThreadLocal<String>();

    public static void setUserId(Long _userId) {
        userId.set(_userId);
    }

    /**
     * 获取当前登录用户ID
     * <p>
     * 取自登录切面（GuiGuAspect）写入的登录态：接口上标了 @GuiGuLogin 才会有值，
     * 未登录时切面已直接响应208，正常不会走到这里拿到 null
     *
     * @return 当前登录用户ID
     */
    public static Long getUserId() {
        return userId.get();
    }

    public static void removeUserId() {
        userId.remove();
    }

    public static void setUsername(String _username) {
        username.set(_username);
    }

    public static String getUsername() {
        return username.get();
    }

    public static void removeUsername() {
        username.remove();
    }

}
