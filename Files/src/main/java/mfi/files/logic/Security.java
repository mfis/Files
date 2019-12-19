package mfi.files.logic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.ServletHelper;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Condition.AllowedFor;
import mfi.files.model.Model;

public class Security {

	private static final String COOKIE_NAME = "FILESLOGIN";
	public static final String KVDB_KEY_BLACKLIST = "temporary.day.login.blacklist.";
	public static final String KVDB_KEY_COOKIES = "temporary.manual.cookies.";
	public static final String KVDB_KEY_COOKIES_DELIMITER = " <##> ";
	private static final String COOKIE_ID_PREFIX = "fjc";

	private static Logger getLogger() {
		final Logger logger = LoggerFactory.getLogger(Security.class);
		return logger;
	}

	public static void checkSecurityForRequest(Model model, HttpServletRequest request, String sid, boolean emptyParameters) {

		// FIXME: CLEAN-UP THIS METHOD !!

		if (model.isInitialRequest()) {
			if (request.getParameterMap() != null && request.getParameterMap().size() > 0) {
				if (model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY) {
					model.lookupConversation().getMeldungen()
							.add("Du bist nicht angemeldet oder hattest einen Session-Timeout. Bitte die Seite neu aufrufen.");
					// logoffUser(model);
					model.lookupConversation().setCondition(Condition.NULL);
				}
			}
		} else {
			if (model.lookupConversation().getCondition() != null
					&& (model.lookupConversation().getCondition().getAllowedFor() == AllowedFor.ANYBODY)) {
				// Beim Login darf der User und die Session noch leer sein
			} else {
				if (StringUtils.isEmpty(model.getUser()) || !KVMemoryMap.getInstance().containsKey("user." + model.getUser())) {
					model.lookupConversation().getMeldungen().add("Bitte melde dich an.");
					logoffUser(model);
				}
				if (StringUtils.isEmpty(sid) || StringUtils.isEmpty(model.getSessionID())
						|| !StringUtils.equals(sid, model.getSessionID())) {
					if (model.lookupConversation().getMeldungen().isEmpty()) {
						model.lookupConversation().getMeldungen().add("Bitte melde dich zuerst an.");
					}
					getLogger().warn("Die SessionID ist ungueltig:" + sid + "/" + model.getSessionID());
					logoffUser(model);
				}
			}
		}

		if (!model.isDevelopmentMode() && !isSSLVerbindung(request) && !model.isWebserverRunsBehindSSLReverseProxy()) {
			model.lookupConversation().getMeldungen().add("ACHTUNG: Unverschlüsselte Verbindung!");
			if (model.lookupConversation().getCondition() != null) {
				model.lookupConversation().setCondition(Condition.SSL_NOTICE);
			}
		}

		if (model.getConversationCount() > 100) {
			throw new SecurityException("Hohe Anzahl Conversations. DOS-Angriff?");
		}

		if (emptyParameters
				|| (!model.isUserAuthenticated() && model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY)) {
			model.lookupConversation().setCondition(Condition.NULL);
		}

		if (StringUtils.isNotEmpty(model.getUser())) {
			if (isBlockeyByBlacklist(model.getUser())) {
				getLogger().warn("User " + model.getUser() + " sperren wegen zu hoher Blacklist-Eintraege");
				logoffUser(model);
			}
		}

		if (model.lookupConversation().getCondition() != null) {

			switch (model.lookupConversation().getCondition().getAllowedFor()) {
			case NOOONE:
				throw new IllegalStateException("Diese Methode wurde voruebergehend deaktiviert.");
			case ANYBODY:
				break;
			case USER:
				if (!model.isUserAuthenticated()) {
					throw new SecurityException("Das Ausfuehren dieser Methode erfordert einen angemeldeten Benutzer.");
				}
				break;
			case ADMIN:
				boolean admin = StringUtils.equalsIgnoreCase(
						KVMemoryMap.getInstance().readValueFromKey("user." + model.getUser() + ".isAdmin"), Boolean.toString(true));
				if (!admin) {
					throw new SecurityException("Das Ausfuehren dieser Methode erfordert einen Admin Account.");
				}
				break;
			default:
				break;
			}
		}

	}

	public static void cookieRead(Model model, Map<String, String> parameters) throws Exception {

		if (model.isInitialRequest() && model.lookupConversation().getCondition().equals(Condition.NULL)) {
			// Check for cookie
			String cookieID = null;
			if (model.lookupConversation().getCookiesReadFromRequest() != null) {
				for (Cookie cookieReadFromRequest : model.lookupConversation().getCookiesReadFromRequest()) {
					String cookieName = cookieReadFromRequest.getName();
					if (cookieName.equals(COOKIE_NAME) && StringUtils.startsWith(cookieReadFromRequest.getValue(), COOKIE_ID_PREFIX)) {
						cookieID = cookieReadFromRequest.getValue();
					}
				}
			}
			// getLogger().info("Session-Cookie bei initialRequest:" + cookieID);

			if (cookieID != null) {
				if (KVMemoryMap.getInstance().containsKey(KVDB_KEY_COOKIES + cookieID)) {
					// Found cookie
					String userFromCookie = KVMemoryMap.getInstance().readValueFromKey(KVDB_KEY_COOKIES + cookieID);
					if (StringUtils.contains(userFromCookie, KVDB_KEY_COOKIES_DELIMITER)) {
						userFromCookie = StringUtils.substringBefore(userFromCookie, KVDB_KEY_COOKIES_DELIMITER).trim();
					}
					// login
					authenticateUser(model, userFromCookie, null,
							KVMemoryMap.getInstance().readValueFromKey("user." + userFromCookie + ".pass"), parameters, true);
					if (model.isUserAuthenticated()) {
						// forwarding to standard condition
						model.lookupConversation().setCondition(Condition.AUTOLOGIN_FROM_COOKIE);
						cookieRenew(model, cookieID);
						// getLogger().info("Re-Login fuer: " + userFromCookie);
					} else {
						KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + cookieID);
						getLogger().error("Re-Login ueber Session-Cookie war NICHT erfolgreich:" + cookieID + " / " + userFromCookie);
						throw new SecurityException("Re-Login ueber Session-Cookie war nicht erfolgreich!");
					}
				} else {
					getLogger().error("Login-Cookie loeschen, da nicht auf DB gefunden:" + cookieID);
					cookieDelete(model);
				}
			}
		}
	}

	private static void cookieWrite(Model model) throws Exception {

		if (!model.isInitialRequest() && model.lookupConversation().getCondition().equals(Condition.LOGIN)) {
			// db: cookieID / user
			// cookie: cookie_name / uuid
			String cookieID = COOKIE_ID_PREFIX + UUID.randomUUID().toString();

			writeCookieIdentifierToKVDB(model, cookieID);

			model.setLoginCookieID(cookieID);

			Cookie cookie = new Cookie(COOKIE_NAME, cookieID);
			cookie.setMaxAge(60 * 60 * 24 * 92);
			model.lookupConversation().getCookiesToWriteToResponse().put(COOKIE_NAME, cookie);
		}
	}

	private static void writeCookieIdentifierToKVDB(Model model, String cookieID) {

		String userAgent = model.getUserAgent();
		if (StringUtils.contains(userAgent, "(") && StringUtils.contains(userAgent, ")")) {
			String[] tokens = StringUtils.substringsBetween(userAgent, "(", ")");
			userAgent = tokens[0];
		}

		String cookieValue = model.getUser() + KVDB_KEY_COOKIES_DELIMITER + Hilfsklasse.zeitstempelAlsString() + " @ " + userAgent;

		KVMemoryMap.getInstance().writeKeyValue(KVDB_KEY_COOKIES + cookieID, cookieValue, true);
	}

	private static void cookieRenew(Model model, String cookieID) throws Exception {

		writeCookieIdentifierToKVDB(model, cookieID);

		model.setLoginCookieID(cookieID);

		Cookie cookie = new Cookie(COOKIE_NAME, cookieID);
		cookie.setMaxAge(60 * 60 * 24 * 92);
		model.lookupConversation().getCookiesToWriteToResponse().put(COOKIE_NAME, cookie);
	}

	private static void cookieDelete(Model model) {

		if (StringUtils.isNotEmpty(model.getLoginCookieID())) {
			KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + model.getLoginCookieID());
		}

		Cookie cookie = new Cookie(COOKIE_NAME, model.getLoginCookieID());
		cookie.setMaxAge(0);
		model.lookupConversation().getCookiesToWriteToResponse().put(COOKIE_NAME, cookie);
		model.setLoginCookieID(null);
	}

	public static boolean isSSLVerbindung(HttpServletRequest request) {

		if (request.getAttributeNames() != null) {
			if (StringUtils.isNotEmpty((String) request.getAttribute("javax.servlet.request.ssl_session"))) {
				return true;
			}
			if (StringUtils.isNotEmpty((String) request.getAttribute("javax.servlet.request.cipher_suite"))) {
				return true;
			}
		}
		if (StringUtils.endsWith(request.getHeader("host"), ":8443")) {
			return true;
		}
		return false;
	}

	public static void authenticateUser(Model model, String user, String pass, String passHash, Map<String, String> parameters,
			boolean isReLogin) throws Exception {

		if (isBlockeyByBlacklist(user) || isBlockeyByBlacklist(parameters.get(ServletHelper.SERVLET_REMOTE_IP))) {
			logoffUser(model);
			getLogger().warn(
					"Ungueltiger Anmeldeversuch wegen Blacklisting mit " + user + " / " + parameters.get(ServletHelper.SERVLET_REMOTE_IP));
			addCounterToBlacklist(user);
			addCounterToBlacklist(parameters.get(ServletHelper.SERVLET_REMOTE_IP));
			model.lookupConversation().getMeldungen().add("Wer bist Du denn?");
		} else if (!isUserActive(user)) {
			model.lookupConversation().getMeldungen().add("Der Account ist inaktiv. Bitte den Admin benachrichtigen.");
		} else {
			if (passHash == null) {
				passHash = Crypto.encryptLoginCredentials(user, pass);
			}
			if (KVMemoryMap.getInstance().containsKey("user." + user)
					&& StringUtils.equals(KVMemoryMap.getInstance().readValueFromKey("user." + user + ".pass"), passHash)) {
				model.setUser(user);
				model.setSessionID(parameters.get(ServletHelper.SERVLET_SESSION_ID));
				cookieWrite(model);
				// if (!isReLogin) {
				// getLogger().info("Erfolgreiches Login fuer: " + user);
				// }
			} else {
				logoffUser(model);
				getLogger().warn("Ungueltiger Anmeldeversuch fuer User=" + user);
				addCounterToBlacklist(user);
				addCounterToBlacklist(parameters.get(ServletHelper.SERVLET_REMOTE_IP));
				model.lookupConversation().getMeldungen().add("Wer bist Du denn?");
			}
		}
	}

	public static boolean checkUserCredentials(String user, String pass) {

		if (isBlockeyByBlacklist(user)) {
			return false;
		} else {
			String passHash = Crypto.encryptLoginCredentials(user, pass);
			if (KVMemoryMap.getInstance().containsKey("user." + user)
					&& StringUtils.equals(KVMemoryMap.getInstance().readValueFromKey("user." + user + ".pass"), passHash)) {
				return true;
			} else {
				addCounterToBlacklist(user);
				return false;
			}
		}
	}

	public static void logoffUser(Model model) {

		cookieDelete(model);

		model.setUser(null);
		model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
		model.setDeleteModelAfterRequest(true);

	}

	public static void addCounterToBlacklist(String itemToCount) {

		String key = KVDB_KEY_BLACKLIST + itemToCount;
		String value = "1";

		if (KVMemoryMap.getInstance().containsKey(key)) {
			long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
			value = String.valueOf(++actualValue);
		}

		KVMemoryMap.getInstance().writeKeyValue(key, value, true);
		getLogger().warn("Schreibe Blacklist fuer " + key + " = " + value);
	}

	private static boolean isBlockeyByBlacklist(String itemToCheck) {

		String key = KVDB_KEY_BLACKLIST + itemToCheck;

		if (KVMemoryMap.getInstance().containsKey(key)) {
			long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
			if (actualValue > 9L) {
				getLogger().warn("Blockiert laut Blacklist: " + key + " = " + actualValue);
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public static boolean isUserActive(String user) {

		String key = "user." + user;

		if (KVMemoryMap.getInstance().containsKey(key)) {
			String value = KVMemoryMap.getInstance().readValueFromKey(key);
			if (StringUtils.endsWithIgnoreCase(value, "true")) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public static boolean isDirectoryAllowedForUser(Model model, String directoryToCheck) {

		if (StringUtils.isEmpty(directoryToCheck)) {
			return false;
		}

		if (model.getVerzeichnisBerechtigungen() == null || model.getVerzeichnisBerechtigungen().size() == 0) {
			getLogger().warn("Berechtigungen sind nicht gesetzt fuer User:" + model.getUser());
			return false;
		}

		boolean berechtigt = false;
		for (String ber : model.getVerzeichnisBerechtigungen()) {
			if (StringUtils.startsWith(directoryToCheck, ber)) {
				berechtigt = true;
			}
		}

		return berechtigt;
	}

	public static boolean isFileAllowedForUser(Model model, File file) {

		if (model == null || file == null) {
			return false;
		} else {
			String path = file.getParentFile().getAbsolutePath();
			return isDirectoryAllowedForUser(model, path);
		}
	}

	public static String generateModelPasswordForSession(Model model) {
		return model.getSessionID() + "#" + model.getLoginCookieID() + "#" + model.getUserAgent();
	}

	public static String generateVerificationString() {
		String uuid = UUID.randomUUID().toString();
		String b32 = new String(new Base32(0).encode(uuid.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
		return StringUtils.left(b32, 4).trim();
	}
}
