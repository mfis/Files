package mfi.files.helper;

import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.htmlgen.HTMLUtils;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.RequestValidationException;

public class ServletHelper {

	private static Logger logger = LoggerFactory.getLogger(ServletHelper.class);

	public static final String SYSTEM_PROPERTY_CATALINA_HOME = "catalina.home";
	public static final String SYSTEM_PROPERTY_CATALINA_BASE = "catalina.base";
	public static final String SERVLET_SESSION_ID = "servlet_session_ID";
	public static final String SERVLET_ACCEPT_ENCODING = "servlet_accept_encoding";
	public static final String SERVLET_ACCEPT_LANG = "servlet_accept_lang";
	public static final String SERVLET_REMOTE_IP = "servlet_remote_ip";
	public static final String HTTP_PORT = "8080";
	public static final String HTTPS_PORT = "8443";
	public static final String STRING_ENCODING_UTF8 = "UTF-8";
	public static final String CONTENT_TYPE_HTML = "text/html";
	public static final String REQUEST_TYPE_PARAMETER = "requestType";
	public static final char REQUEST_TYPE_SUBMIT = 's';
	public static final char REQUEST_TYPE_AJAX = 'a';
	public static final int HTTP_STATUS_CODE_NON_AUTHORITATIVE_RESPONSE = 203;
	public static final String NON_BREAKING_SPACE = "\u00A0";

	public static boolean isLocalNetworkClient(Map<String, String> parameters) {
		if (parameters.get(SERVLET_REMOTE_IP).equals("0:0:0:0:0:0:0:1")) {
			return true;
		}
		if (parameters.get(SERVLET_REMOTE_IP).startsWith("192.168.")) {
			return true;
		}
		if (parameters.get(SERVLET_REMOTE_IP).startsWith("10.")) {
			return true;
		}
		if (parameters.get(SERVLET_REMOTE_IP).startsWith("172.")) {
			String b = StringUtils.left(parameters.get(SERVLET_REMOTE_IP), 7);
			if (b.equals("172.16.") || b.equals("172.17.") || b.equals("172.18.") || b.equals("172.19.") || b.equals("172.20.")
					|| b.equals("172.21.") || b.equals("172.22.") || b.equals("172.23.") || b.equals("172.24.") || b.equals("172.25.")
					|| b.equals("172.26.") || b.equals("172.27.") || b.equals("172.28.") || b.equals("172.29.") || b.equals("172.30.")
					|| b.equals("172.31.")) {
				return true;
			}
			return true;
		}
		return false;
	}

	public static boolean lookupUseGzip(Map<String, String> parameters) {

		if (parameters == null) {
			return false;
		}

		boolean gzipConfigSwitch = Boolean.valueOf(KVMemoryMap.getInstance().readValueFromKey("application.properties.useGzip"));

		String accept = parameters.get(SERVLET_ACCEPT_ENCODING);
		boolean gzipBrowserSupport = accept != null && accept.indexOf("gzip") >= 0;

		return gzipConfigSwitch && gzipBrowserSupport && !isLocalNetworkClient(parameters);
	}

	public static boolean lookupUseAjax() {

		boolean ajaxConfigSwitch = Boolean.valueOf(KVMemoryMap.getInstance().readValueFromKey("application.properties.useAjax"));

		return ajaxConfigSwitch;
	}

	public static char lookupAjaxSubmitConfig() {

		if (lookupUseAjax()) {
			return REQUEST_TYPE_AJAX;
		} else {
			return REQUEST_TYPE_SUBMIT;
		}

	}

	public static boolean lookupIsCurrentRequestTypeAjax(Map<String, String> parameters) {

		return Boolean.valueOf(
				StringUtils.equals(parameters.get(ServletHelper.REQUEST_TYPE_PARAMETER), String.valueOf(ServletHelper.REQUEST_TYPE_AJAX)));

	}

	public static char lookupCurrentRequestType(Map<String, String> parameters) {

		if (parameters.containsKey(ServletHelper.REQUEST_TYPE_PARAMETER)) {
			return parameters.get(ServletHelper.REQUEST_TYPE_PARAMETER).charAt(0);
		} else {
			logger.error("ServletHelper.REQUEST_TYPE_PARAMETER ist nicht gesetzt!");
			return ' ';
		}

	}

	public static Map<String, String> parseRequest(HttpServletRequest request, HttpSession session)
			throws RequestValidationException, UnsupportedEncodingException {

		Map<String, Integer> lengthQuota = new HashMap<String, Integer>();
		Map<String, Integer> lengthActual = new HashMap<String, Integer>();

		Enumeration<String> e = request.getParameterNames();
		Map<String, String> parameters = new HashMap<String, String>();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			String value = request.getParameter(key);
			int valueLength = value != null ? value.length() : 0;
			value = StringUtils.remove(value, NON_BREAKING_SPACE);
			value = StringUtils.trimToEmpty(value);

			if (StringUtils.endsWith(key, HTMLUtils.SECURE_ATTRIBUTE + "__length")) {
				String keyName = StringUtils.removeEnd(key, HTMLUtils.SECURE_ATTRIBUTE + "__length");
				lengthQuota.put(keyName, StringUtils.isNumeric(value) ? Integer.parseInt(value) : 0);
			} else if (StringUtils.endsWith(key, HTMLUtils.SECURE_ATTRIBUTE)) {
				key = StringUtils.removeEnd(key, HTMLUtils.SECURE_ATTRIBUTE);
				parameters.put(key, value);
				lengthActual.put(key, valueLength);
			} else {
				parameters.put(key, value);
			}
		}

		if (lengthActual.size() != lengthQuota.size()) {
			throw new RequestValidationException("Different Entries in Size Maps:" + lengthActual.size() + " / " + lengthQuota.size());
		} else {
			for (String keyActual : lengthActual.keySet()) {
				if (!lengthActual.get(keyActual).equals(lengthQuota.get(keyActual))) {
					throw new RequestValidationException("Different lengths in Size Maps for '" + keyActual + "':"
							+ lengthActual.get(keyActual) + " / " + lengthQuota.get(keyActual));
				}
			}
		}

		if (session != null) {
			parameters.put(ServletHelper.SERVLET_SESSION_ID, session.getId());
		}
		parameters.put(ServletHelper.SERVLET_ACCEPT_ENCODING, request.getHeader("Accept-Encoding"));
		parameters.put(ServletHelper.SERVLET_ACCEPT_LANG, request.getHeader("Accept-Language"));
		parameters.put(ServletHelper.SERVLET_REMOTE_IP, request.getRemoteAddr());

		return parameters;
	}

	public static String getMimeTypeForFileSuffix(String suffix) {
		String mimeType = KVMemoryMap.getInstance().readValueFromKey("application.properties.mimetypes." + suffix);
		if (mimeType == null) {
			mimeType = KVMemoryMap.getInstance().readValueFromKey("application.properties.mimetypes.fallback");
		}
		return mimeType;
	}

}
