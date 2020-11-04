package mfi.files.helper;

import javax.servlet.http.Cookie;

public class CookieHelper {

	public static Cookie cloneCookie(Cookie originalCookie) {

		Cookie clonedCookie = new Cookie(originalCookie.getName(), originalCookie.getValue());
		clonedCookie.setMaxAge(originalCookie.getMaxAge());
		clonedCookie.setHttpOnly(originalCookie.isHttpOnly());
		return clonedCookie;
	}
}
