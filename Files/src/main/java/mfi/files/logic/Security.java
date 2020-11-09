package mfi.files.logic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.ServletHelper;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.CheckTokenResult;
import mfi.files.model.Condition;
import mfi.files.model.Condition.AllowedFor;
import mfi.files.model.LoginToken;
import mfi.files.model.Model;
import mfi.files.servlet.FilesMainServlet;

/*
 * DO NOT EDIT ANYMORE -> REWRITE !!
 * REPLACE WITH SPRING SECURITY OR COMPARABLE LIBRARY
 */
public class Security {

	private static final String FILES_APPLICATION = "de_fimatas_files";
	private static final String ANMELDEDATEN_SIND_FEHLERHAFT = "Anmeldedaten sind fehlerhaft.";
	private static final String KVDB_PASS_IDENTIFIER = ".pass";
	private static final String SESSION_COOKIE_NAME = "JSESSIONID";
	private static final String LOGIN_COOKIE_NAME = "FILESLOGIN";
	private static final String BLACKLIST_CORRUPT_LOGIN = "CorruptLogin";
	private static final Logger logger = LoggerFactory.getLogger(Security.class);

	private Security() {
		super();
	}

	public static void checkSecurityForRequest(Model model, Map<String, String> parameters) {

		model.lookupConditionForRequest(parameters);
		checkCookieAndSessionBeforeLoginFromCookie(model);

		cookieRead(model, parameters);

		ckeckUserLogin(model);

		if (model.getConversationCount() > 100) {
			throw new SecurityException("Hohe Anzahl Conversations. DOS-Angriff?");
		}

		if (parameters.isEmpty()
				|| (!model.isUserAuthenticated() && model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY)) {
			model.lookupConversation().setCondition(Condition.NULL);
		}

		if (StringUtils.isNotEmpty(model.getUser()) && isBlocked(model.getUser())) {
			logger.warn("User {} sperren wegen zu hoher Blacklist-Eintraege", model.getUser());
			logoffUser(model);
		}

		ckeckUserRole(model);
	}

	public static void ckeckUserRole(Model model) {

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
						KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + model.getUser() + ".isAdmin"),
						Boolean.toString(true));
				if (!admin) {
					throw new SecurityException("Das Ausfuehren dieser Methode erfordert einen Admin Account.");
				}
				break;
			default:
				break;
			}
		}
	}

	public static void ckeckUserLogin(Model model) {

		if (model.lookupConversation().getCondition() != null
				&& (model.lookupConversation().getCondition().getAllowedFor() == AllowedFor.ANYBODY)) {
			// Beim Login darf der User und die Session noch leer sein
		} else {
			if (StringUtils.isEmpty(model.getUser())
					|| !KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + model.getUser())) {
				// Login Cookie nicht gefunden
				logoffUser(model);
				handleCorruptLogin(model);
			}
		}
	}

	private static void checkCookieAndSessionBeforeLoginFromCookie(Model model) {

		LoginToken token = LoginToken.fromCombinedValue(lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest()));
		if (token != null && !token.checkToken(token.getUser(), FILES_APPLICATION, deviceFromUserAgent(model))) {
			token = null;
		}

		// Pruefung: Model in der Session gefunden, aber kein Login-Cookie
		if (model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY && model.isUserAuthenticated()
				&& token == null) {
			logoffUser(model);
			if (StringUtils.isNotBlank(lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest()))) {
				handleCorruptLogin(model);
			}
		}

		// Pruefung: Model aus der Session gehoert nicht dem User laut Login-Cookie (Abgleich der beiden Cookies)
		if (model.isUserAuthenticated() && token != null) {
			if (!StringUtils.equalsIgnoreCase(token.getUser(), model.getUser())) { // NOSONAR
				logoffUser(model);
				handleCorruptLogin(model);
			}
		}
	}

	public static void handleCorruptLogin(Model model) {

		addCounter(BLACKLIST_CORRUPT_LOGIN);
		model.lookupConversation().setCondition(Condition.NULL);
		if (isBlocked(BLACKLIST_CORRUPT_LOGIN)) {
			KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVMemoryMap.KVDB_KEY_LOGINTOKEN);
			logger.warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs.");
		}
	}

	public static Model lookupModelFromSession(HttpSession session, HttpServletRequest request) {

		Model model = null;
		if (session != null) {
			model = (Model) session.getAttribute(FilesMainServlet.SESSION_ATTRIBUTE_MODEL);
		}

		if (model == null) {
			model = new Model();
			model.initializeModelOnFirstRequest(request);
			if (model.isDevelopmentMode()) {
				logger.info("Initializing new Model");
			}
		}

		model.lookupConversation().setCookiesReadFromRequestConvenient(request.getCookies());

		return model;
	}

	private static void cookieRead(Model model, Map<String, String> parameters) {

		String cookieID = lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest());
		if (StringUtils.isBlank(cookieID)) {
			return;
		}

		LoginToken token = LoginToken.fromCombinedValue(cookieID);
		if (token == null || !token.checkToken(token.getUser(), FILES_APPLICATION, deviceFromUserAgent(model))) {
			return;
		}

		String keyForCookieToken = kvKeyForCookieToken(token, model);

		if (KVMemoryMap.getInstance().containsKey(keyForCookieToken)) {
			// Found cookie
			String userFromCookie = token.getUser();
			// login
			boolean wasAlreadyAuthenticated = model.isUserAuthenticated();
			boolean authenticated = authenticateUser(model, userFromCookie, null,
					KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + userFromCookie + KVDB_PASS_IDENTIFIER),
					parameters);
			if (authenticated) {
				if (!wasAlreadyAuthenticated) {
					// forwarding to standard condition
					model.lookupConversation().setCondition(Condition.AUTOLOGIN_FROM_COOKIE);
				}
				cookieRenew(model, token);
			} else {
				KVMemoryMap.getInstance().deleteKey(keyForCookieToken);
				logger.error("Re-Login ueber Session-Cookie war NICHT erfolgreich: {} / {}", cookieID, userFromCookie);
				throw new SecurityException("Re-Login ueber Session-Cookie war nicht erfolgreich!");
			}
		}
	}

	private static String lookupLoginCookie(List<Cookie> cookies) {

		// Check for cookie
		String cookieID = null;
		if (cookies != null) {
			for (Cookie cookieReadFromRequest : cookies) {
				String cookieName = cookieReadFromRequest.getName();
				if (cookieName.equals(LOGIN_COOKIE_NAME)) {
					cookieID = cookieReadFromRequest.getValue();
				}
			}
		}
		cookieID = cleanUpKvValue(cookieID);
		return cookieID;
	}

	private static void cookieWrite(Model model) {

		if (model.lookupConversation().getCondition().equals(Condition.LOGIN)) {

			LoginToken token = LoginToken.createNew(model.getUser());
			writeCookieIdentifierToKVDB(model, token);
			model.setLoginCookieID(token);

			Cookie cookie = new Cookie(LOGIN_COOKIE_NAME, token.toKvDbValue());
			cookie.setMaxAge(60 * 60 * 24 * 92);
			cookie.setHttpOnly(true);
			model.lookupConversation().getCookiesToWriteToResponse().put(LOGIN_COOKIE_NAME, cookie);
		}
	}

	private static void writeCookieIdentifierToKVDB(Model model, LoginToken token) {
		KVMemoryMap.getInstance().writeKeyValue(kvKeyForCookieToken(token, model), token.toKvDbValue(), true);
	}

	private static String kvKeyForCookieToken(LoginToken token, Model model) {
		return KVMemoryMap.KVDB_KEY_LOGINTOKEN + token.getUser() + "." + FILES_APPLICATION + "." + deviceFromUserAgent(model);
	}

	private static String deviceFromUserAgent(Model model) {
		String device = cleanUpKvSubKey(model.getUserAgent()).replaceAll("[0-9]", "");
		device = StringUtils.replaceEach(device, new String[] { "_", "." }, new String[] { "", "" });

		return device;
	}

	private static void cookieRenew(Model model, LoginToken token) {

		if (model.lookupConversation().getCondition() == Condition.NULL
				|| model.lookupConversation().getCondition() == Condition.FS_NAVIGATE
				|| model.lookupConversation().getCondition() == Condition.FS_CANCEL_EDITED_FILE) {
			token.refreshValue();
		}

		writeCookieIdentifierToKVDB(model, token);

		model.setLoginCookieID(token);

		Cookie cookie = new Cookie(LOGIN_COOKIE_NAME, token.toKvDbValue());
		cookie.setMaxAge(60 * 60 * 24 * 92);
		cookie.setHttpOnly(true);
		model.lookupConversation().getCookiesToWriteToResponse().put(LOGIN_COOKIE_NAME, cookie);
	}

	private static void cookieDelete(Model model) {

		if (model.getLoginCookieID() != null) {
			// Loeschen anhand mitgeschicktem Cookie
			KVMemoryMap.getInstance().deleteByValue(model.getLoginCookieID().toKvDbValue(), KVMemoryMap.KVDB_KEY_LOGINTOKEN);
			model.setLoginCookieID(null);
			// Loeschen anhand user-agent
			String key = KVMemoryMap.KVDB_KEY_LOGINTOKEN + model.getUser() + "." + FILES_APPLICATION + "." + deviceFromUserAgent(model);
			if (KVMemoryMap.getInstance().containsKey(key)) {
				KVMemoryMap.getInstance().deleteKey(key);
			}
		}

		Cookie logincookie = new Cookie(LOGIN_COOKIE_NAME, StringUtils.EMPTY);
		logincookie.setMaxAge(0);
		model.lookupConversation().getCookiesToWriteToResponse().put(LOGIN_COOKIE_NAME, logincookie);

		Cookie sessioncookie = new Cookie(SESSION_COOKIE_NAME, StringUtils.EMPTY);
		sessioncookie.setMaxAge(0);
		model.lookupConversation().getCookiesToWriteToResponse().put(SESSION_COOKIE_NAME, sessioncookie);
	}

	public static boolean authenticateUser(Model model, String user, String pass, String passHash, Map<String, String> parameters) { // NOSONAR

		user = cleanUpKvSubKey(user);
		if (isBlocked(user) || isBlocked(parameters.get(ServletHelper.SERVLET_REMOTE_IP))) {
			logoffUser(model);
			logger.warn("Ungueltiger Anmeldeversuch wegen Blacklisting mit {} / {}", user, parameters.get(ServletHelper.SERVLET_REMOTE_IP));
			addCounter(user);
			addCounter(parameters.get(ServletHelper.SERVLET_REMOTE_IP));
			model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
		} else if (!isUserActive(user)) {
			if (KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user)) {
				model.lookupConversation().getMeldungen().add("Der Account ist inaktiv. Bitte den Admin benachrichtigen.");
			} else {
				model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
			}
		} else {
			if (passHash == null) {
				passHash = Crypto.encryptLoginCredentials(user, pass);
			}
			if (KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user) && StringUtils.equals(
					KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + KVDB_PASS_IDENTIFIER), passHash)) {
				if (!StringUtils.equals(model.getUser(), user)) {
					model.setUser(user);
				}
				cookieWrite(model);
				return true;
			} else {
				logoffUser(model);
				logger.warn("Ungueltiger Anmeldeversuch fuer User={}", user);
				addCounter(user);
				addCounter(parameters.get(ServletHelper.SERVLET_REMOTE_IP).replaceAll("[^a-zA-Z0-9]", "_"));
				model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
			}
		}
		return false;
	}

	public static boolean checkUserCredentials(String user, String pass) {

		user = cleanUpKvSubKey(user);
		if (isBlocked(user)) {
			return false;
		} else {
			String passHash = Crypto.encryptLoginCredentials(user, pass);
			if (KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user)
					&& KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + KVDB_PASS_IDENTIFIER)
					&& StringUtils.equals(
							KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + KVDB_PASS_IDENTIFIER),
							passHash)) {
				return true;
			} else {
				addCounter(user);
				return false;
			}
		}
	}

	public static boolean checkPin(String user, String pin) {

		user = cleanUpKvSubKey(user);
		pin = cleanUpKvSubKey(pin);
		if (isBlocked(user)) {
			return false;
		} else {
			String passHash = Crypto.encryptLoginCredentials(user, pin);
			if (KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user)
					&& KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".pin") && StringUtils.equals(
							KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".pin"), passHash)) {
				return true;
			} else {
				addCounter(user);
				return false;
			}
		}
	}

	public static String createToken(String user, String pass, String application, String device) {

		user = cleanUpKvSubKey(user);
		application = cleanUpKvSubKey(application);
		device = cleanUpKvSubKey(device);

		if (checkUserCredentials(user, pass)) {
			LoginToken token = LoginToken.createNew(user);
			String key = KVMemoryMap.KVDB_KEY_LOGINTOKEN + user + "." + application + "." + device;
			KVMemoryMap.getInstance().writeKeyValue(key, token.toKvDbValue(), true);
			logger.debug("created token for key : {}", key);
			logger.debug("created token value : {}", logger.isDebugEnabled() ? StringUtils.left(token.toKvDbValue(), 100) : "");
			return token.toKvDbValue();
		}
		return null;
	}

	public static CheckTokenResult checkToken(String user, String tokenToCheck, String application, String device, boolean refresh) {

		user = cleanUpKvSubKey(user);
		application = cleanUpKvSubKey(application);
		device = cleanUpKvSubKey(device);
		tokenToCheck = cleanUpKvValue(tokenToCheck);

		if (isBlocked(user)) {
			return new CheckTokenResult(false, null);
		} else {
			LoginToken token = LoginToken.fromCombinedValue(tokenToCheck);
			if (token.checkToken(user, application, device)) {
				String tokenToReturn = null;
				if (refresh) {
					token.refreshValue();
					tokenToReturn = token.toKvDbValue();
					String key = KVMemoryMap.KVDB_KEY_LOGINTOKEN + user + "." + application + "." + device;
					KVMemoryMap.getInstance().writeKeyValue(key, tokenToReturn, true);
				}
				return new CheckTokenResult(true, tokenToReturn);
			} else {
				addCounter(user);
				if (logger.isInfoEnabled()) {
					logger.info("token to ckeck  : {}", StringUtils.left(tokenToCheck, 100));
				}
				return new CheckTokenResult(false, null);
			}
		}
	}

	public static String cleanUpKvKey(String subKey) {
		if (StringUtils.isAllBlank(subKey)) {
			throw new IllegalArgumentException("key must not be empty");
		}
		String[] strings = StringUtils.split(subKey, '.');
		for (int i = 0; i < strings.length; i++) {
			strings[i] = cleanUpKvSubKey(strings[i]);
		}
		return StringUtils.join(strings, '.');
	}

	public static String cleanUpKvSubKey(String subKey) {
		// hex: _ [ ] -
		return subKey == null ? StringUtils.EMPTY : subKey.replaceAll("[^a-zA-Z0-9\\x5f\\x5b\\x5d\\x2d]", "").trim();
	}

	public static String cleanUpKvValue(String subKey) {
		// erlaubt: a-zA-Z0-9_
		// zusaetzlich: /.,()[]{}<>!@"*+#- SPACE
		// NICHT =\
		return subKey == null ? StringUtils.EMPTY : subKey.replaceAll("[^\\x20-\\x3c\\x3e-\\x5b\\x5d-\\x7e]", "").trim();
	}

	public static void logoffUser(Model model) {

		cookieDelete(model);

		model.setUser(null);
		model.lookupConversation().setEditingFile(null);
		model.setVerzeichnisBerechtigungen(new LinkedList<>());
		model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
		model.setDeleteModelAfterRequest(true);

	}

	public static void addCounter(String itemToCount) {

		String key = KVMemoryMap.KVDB_KEY_BLACKLIST + itemToCount;
		String value = "1";

		if (KVMemoryMap.getInstance().containsKey(key)) {
			long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
			value = String.valueOf(++actualValue);
		}

		KVMemoryMap.getInstance().writeKeyValue(key, value, true);
		logger.warn("Schreibe Blacklist fuer {} = {}", key, value);
	}

	private static boolean isBlocked(String itemToCheck) {

		String key = KVMemoryMap.KVDB_KEY_BLACKLIST + itemToCheck;

		if (KVMemoryMap.getInstance().containsKey(key)) {
			long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
			if (actualValue > 6L) {
				logger.warn("Blockiert laut Blacklist: {} = {}", key, actualValue);
				if (actualValue == 7L) {
					try {
						Hilfsklasse.sendPushMessage("Blacklisted key: " + itemToCheck);
					} catch (Exception e) {
						logger.error(e.getLocalizedMessage(), e);
					}
				}
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public static boolean isUserActive(String user) {

		String key = KVMemoryMap.KVDB_USER_IDENTIFIER + user;

		if (KVMemoryMap.getInstance().containsKey(key)) {
			String value = KVMemoryMap.getInstance().readValueFromKey(key);
			return (StringUtils.endsWithIgnoreCase(value, "true"));
		} else {
			return false;
		}
	}

	public static boolean isDirectoryAllowedForUser(Model model, String directoryToCheck) {

		if (StringUtils.isEmpty(directoryToCheck)) {
			return false;
		}

		if (model.getVerzeichnisBerechtigungen() == null || model.getVerzeichnisBerechtigungen().isEmpty()) {
			logger.warn("Berechtigungen sind nicht gesetzt fuer User: {}", model.getUser());
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
			String path = null;
			if (file.getParentFile() == null) {
				path = file.getAbsolutePath();
			} else {
				path = file.getParentFile().getAbsolutePath();
			}
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
