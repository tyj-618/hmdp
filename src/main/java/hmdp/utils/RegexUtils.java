package hmdp.utils;

import cn.hutool.core.util.StrUtil;

public class RegexUtils {
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    public static boolean isPhoneInvalid(String phone) {
        return mismatch(phone, PHONE_REGEX);
    }

    private static boolean mismatch(String str, String regex) {
        if ((StrUtil.isBlank(str))) {
            return true;
        }
        return !str.matches(regex);
    }
}
