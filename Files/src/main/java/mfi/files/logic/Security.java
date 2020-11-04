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
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.ServletHelper;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Condition.AllowedFor;
import mfi.files.model.Model;
import mfi.files.servlet.FilesMainServlet;
import net.pushover.client.MessagePriority;
import net.pushover.client.PushoverClient;
import net.pushover.client.PushoverException;
import net.pushover.client.PushoverMessage;
import net.pushover.client.PushoverRestClient;
import net.pushover.client.Status;

/*
 * DO NOT EDIT ANYMORE -> REWRITE !!
 * REPLACE WITH SPRING SECURITY OR COMPARABLE LIBRARY
 */
public class Security {

	public static final String KVDB_KEY_BLACKLIST = "temporary.day.login.blacklist.";
	public static final String KVDB_KEY_COOKIES = "temporary.manual.cookies.";
	public static final String KVDB_KEY_COOKIES_DELIMITER = " <##> ";
	public static final String KVDB_USER_IDENTIFIER = "user.";

	private static final String ANMELDEDATEN_SIND_FEHLERHAFT = "Anmeldedaten sind fehlerhaft.";
	private static final String KVDB_PASS_IDENTIFIER = ".pass";
	private static final String SESSION_COOKIE_NAME = "JSESSIONID";
	private static final String LOGIN_COOKIE_NAME = "FILESLOGIN";
	private static final String BLACKLIST_CORRUPT_LOGIN = "CorruptLogin";
	private static final String COOKIE_ID_PREFIX = "fjc";
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

		if (model.isUploadTicket() && model.lookupConversation().getCondition() != null
				&& model.lookupConversation().getCondition() != Condition.FILE_UPLOAD
				&& model.lookupConversation().getCondition() != Condition.LOGOFF) {
			model.lookupConversation().setCondition(Condition.FILE_UPLOAD);
			model.lookupConversation().getMeldungen().add("Mit diesem Anmeldungs-Ticket ist nur der Datei-Upload erlaubt.");
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
						KVMemoryMap.getInstance().readValueFromKey(KVDB_USER_IDENTIFIER + model.getUser() + ".isAdmin"),
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
			if (StringUtils.isEmpty(model.getUser()) || !KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + model.getUser())) {
				// Login Cookie nicht gefunden
				logoffUser(model);
				addCounter(BLACKLIST_CORRUPT_LOGIN);
				model.lookupConversation().setCondition(Condition.NULL);
				if (isBlocked(BLACKLIST_CORRUPT_LOGIN)) {
					KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
					logger.warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs (4)");
					// resetCounter(BLACKLIST_CORRUPT_LOGIN);
				}
			}
		}
	}

	private static void checkCookieAndSessionBeforeLoginFromCookie(Model model) {

		String cookieID = lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest());
		cookieID = cleanUpSubKey(cookieID);

		String userFromLoginCookie = KVMemoryMap.getInstance().readValueFromKey(KVDB_KEY_COOKIES + StringUtils.trimToNull(cookieID));
		userFromLoginCookie = StringUtils.substringBefore(userFromLoginCookie, KVDB_KEY_COOKIES_DELIMITER);
		userFromLoginCookie = StringUtils.trimToNull(userFromLoginCookie);

		// Pruefung: Model in der Session gefunden, aber kein Login-Cookie
		if (model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY && model.isUserAuthenticated()
				&& (StringUtils.isBlank(cookieID) || StringUtils.isBlank(userFromLoginCookie))) {
			logoffUser(model);
			if (StringUtils.isNotBlank(cookieID)) {
				addCounter(BLACKLIST_CORRUPT_LOGIN);
				model.lookupConversation().setCondition(Condition.NULL);
				if (isBlocked(BLACKLIST_CORRUPT_LOGIN)) {
					KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
					logger.warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs (!)");
					// resetCounter(BLACKLIST_CORRUPT_LOGIN);
				}
			}
		}

		// Pruefung: Model aus der Session gehoert nicht dem User laut Login-Cookie (Abgleich der beiden Cookies)
		if (model.isUserAuthenticated()
				&& StringUtils.isNotEmpty(lookupSessionCookie(model.lookupConversation().getCookiesReadFromRequest()))) {
			// Model aus Session
			if (!StringUtils.equalsIgnoreCase(userFromLoginCookie, model.getUser())) { // NOSONAR
				logoffUser(model);
				addCounter(BLACKLIST_CORRUPT_LOGIN);
				model.lookupConversation().setCondition(Condition.NULL);
				if (isBlocked(BLACKLIST_CORRUPT_LOGIN)) {
					KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
					logger.warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs (!!)");
					// resetCounter(BLACKLIST_CORRUPT_LOGIN);
				}
			}
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
		cookieID = cleanUpSubKey(cookieID);

		if (StringUtils.isNotBlank(cookieID) && KVMemoryMap.getInstance().containsKey(KVDB_KEY_COOKIES + cookieID)) {
			// Found cookie
			String userFromCookie = KVMemoryMap.getInstance().readValueFromKey(KVDB_KEY_COOKIES + StringUtils.trimToNull(cookieID));
			if (StringUtils.contains(userFromCookie, KVDB_KEY_COOKIES_DELIMITER)) {
				userFromCookie = StringUtils.substringBefore(userFromCookie, KVDB_KEY_COOKIES_DELIMITER).trim();
			}
			// login
			boolean wasAlreadyAuthenticated = model.isUserAuthenticated();
			boolean authenticated = authenticateUser(model, userFromCookie, null,
					KVMemoryMap.getInstance().readValueFromKey(KVDB_USER_IDENTIFIER + userFromCookie + KVDB_PASS_IDENTIFIER), parameters);
			if (authenticated) {
				if (!wasAlreadyAuthenticated) {
					// forwarding to standard condition
					model.lookupConversation().setCondition(Condition.AUTOLOGIN_FROM_COOKIE);
				}
				cookieRenew(model, cookieID);
			} else {
				KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + cookieID);
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
				if (cookieName.equals(LOGIN_COOKIE_NAME) && StringUtils.startsWith(cookieReadFromRequest.getValue(), COOKIE_ID_PREFIX)) {
					cookieID = cookieReadFromRequest.getValue();
				}
			}
		}
		return cookieID;
	}

	private static String lookupSessionCookie(List<Cookie> cookies) {

		// Check for cookie
		String cookieID = null;
		if (cookies != null) {
			for (Cookie cookieReadFromRequest : cookies) {
				String cookieName = cookieReadFromRequest.getName();
				if (cookieName.equals(SESSION_COOKIE_NAME)) {
					cookieID = cookieReadFromRequest.getValue();
				}
			}
		}
		return cookieID;
	}

	private static void cookieWrite(Model model) {

		if (model.lookupConversation().getCondition().equals(Condition.LOGIN)) {
			// db: cookieID / user
			// cookie: cookie_name / uuid

			String cookieID = COOKIE_ID_PREFIX + UUID.randomUUID().toString().hashCode() + "__" + new RandomStringGenerator.Builder()
					.withinRange('0', 'z').filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS).build().generate(3600);
			cookieID = cleanUpSubKey(cookieID);

			writeCookieIdentifierToKVDB(model, cookieID);

			model.setLoginCookieID(cookieID);

			Cookie cookie = new Cookie(LOGIN_COOKIE_NAME, cookieID);
			cookie.setMaxAge(60 * 60 * 24 * 92);
			cookie.setHttpOnly(true);
			model.lookupConversation().getCookiesToWriteToResponse().put(LOGIN_COOKIE_NAME, cookie);
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

	private static void cookieRenew(Model model, String cookieID) {

		writeCookieIdentifierToKVDB(model, cookieID);

		model.setLoginCookieID(cookieID);

		Cookie cookie = new Cookie(LOGIN_COOKIE_NAME, cookieID);
		cookie.setMaxAge(60 * 60 * 24 * 92);
		cookie.setHttpOnly(true);
		model.lookupConversation().getCookiesToWriteToResponse().put(LOGIN_COOKIE_NAME, cookie);
	}

	private static void cookieDelete(Model model) {

		if (StringUtils.isNotEmpty(model.getLoginCookieID())) {
			KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + model.getLoginCookieID());
			Cookie cookie = new Cookie(LOGIN_COOKIE_NAME, model.getLoginCookieID());
			cookie.setMaxAge(0);
			model.lookupConversation().getCookiesToWriteToResponse().put(LOGIN_COOKIE_NAME, cookie);
			model.setLoginCookieID(null);
		}

		String cookieID = lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest());
		cookieID = cleanUpSubKey(cookieID);
		if (StringUtils.isNotEmpty(cookieID)) {
			KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + cookieID);
			Cookie cookie = new Cookie(LOGIN_COOKIE_NAME, cookieID);
			cookie.setMaxAge(0);
			model.lookupConversation().getCookiesToWriteToResponse().put(LOGIN_COOKIE_NAME, cookie);
			model.setLoginCookieID(null);
		}

		if (StringUtils.isNotEmpty(model.getSessionID())) {
			Cookie cookie = new Cookie(SESSION_COOKIE_NAME, model.getSessionID());
			cookie.setMaxAge(0);
			model.lookupConversation().getCookiesToWriteToResponse().put(SESSION_COOKIE_NAME, cookie);
			model.setLoginCookieID(null);
		}
	}

	public static boolean authenticateUser(Model model, String user, String pass, String passHash, Map<String, String> parameters) { // NOSONAR

		user = cleanUpSubKey(user);
		if (isBlocked(user) || isBlocked(parameters.get(ServletHelper.SERVLET_REMOTE_IP))) {
			logoffUser(model);
			logger.warn("Ungueltiger Anmeldeversuch wegen Blacklisting mit {} / {}", user, parameters.get(ServletHelper.SERVLET_REMOTE_IP));
			addCounter(user);
			addCounter(parameters.get(ServletHelper.SERVLET_REMOTE_IP));
			model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
		} else if (!isUserActive(user)) {
			if (KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + user)) {
				model.lookupConversation().getMeldungen().add("Der Account ist inaktiv. Bitte den Admin benachrichtigen.");
			} else {
				model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
			}
		} else {
			if (passHash == null) {
				passHash = Crypto.encryptLoginCredentials(user, pass);
			}
			if (KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + user) && StringUtils
					.equals(KVMemoryMap.getInstance().readValueFromKey(KVDB_USER_IDENTIFIER + user + KVDB_PASS_IDENTIFIER), passHash)) {
				if (!StringUtils.equals(model.getUser(), user)) {
					model.setUser(user);
				}
				cookieWrite(model);
				return true;
			} else {
				logoffUser(model);
				logger.warn("Ungueltiger Anmeldeversuch fuer User={}", user);
				addCounter(user);
				addCounter(parameters.get(ServletHelper.SERVLET_REMOTE_IP));
				model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
			}
		}
		return false;
	}

	public static boolean checkUserCredentials(String user, String pass) {

		user = cleanUpSubKey(user);
		if (isBlocked(user)) {
			return false;
		} else {
			String passHash = Crypto.encryptLoginCredentials(user, pass);
			if (KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + user)
					&& KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + user + KVDB_PASS_IDENTIFIER) && StringUtils.equals(
							KVMemoryMap.getInstance().readValueFromKey(KVDB_USER_IDENTIFIER + user + KVDB_PASS_IDENTIFIER), passHash)) {
				return true;
			} else {
				addCounter(user);
				return false;
			}
		}
	}

	public static boolean checkPin(String user, String pin) {

		user = cleanUpSubKey(user);
		pin = cleanUpSubKey(pin);
		if (isBlocked(user)) {
			return false;
		} else {
			String passHash = Crypto.encryptLoginCredentials(user, pin);
			if (KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + user)
					&& KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + user + ".pin")
					&& StringUtils.equals(KVMemoryMap.getInstance().readValueFromKey(KVDB_USER_IDENTIFIER + user + ".pin"), passHash)) {
				return true;
			} else {
				addCounter(user);
				return false;
			}
		}
	}

	public static String createToken(String user, String pass, String application, String device) {

		user = cleanUpSubKey(user);
		application = cleanUpSubKey(application);
		device = cleanUpSubKey(device);
		if (checkUserCredentials(user, pass)) {
			String uuid = application + "#" + UUID.randomUUID().toString().replace("-", "") + "_" + user.hashCode() + "_";
			String more = new RandomStringGenerator.Builder().withinRange('0', 'z')
					.filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS).build().generate(3600);
			String token = uuid + more;
			String key = KVDB_USER_IDENTIFIER + user + "." + application + "#" + device + ".token";
			KVMemoryMap.getInstance().writeKeyValue(key, token, true);
			logger.debug("created token for key : {}", key);
			logger.debug("created token value : {}", logger.isDebugEnabled() ? StringUtils.left(token, 100) : "");
			token = cleanUpSubKey(token);
			return token;
		}
		return null;
	}

	public static boolean checkToken(String user, String tokenToCheck, String application, String device) {

		if (isBlocked(user)) {
			return false;
		} else {
			user = cleanUpSubKey(user);
			tokenToCheck = cleanUpSubKey(tokenToCheck);
			application = cleanUpSubKey(application);
			device = cleanUpSubKey(device);
			String key = KVDB_USER_IDENTIFIER + user + "." + application.replace('.', '_') + "#" + device + ".token";
			String kvdbToken = null;
			if (KVMemoryMap.getInstance().containsKey(KVDB_USER_IDENTIFIER + user) && KVMemoryMap.getInstance().containsKey(key)) {
				kvdbToken = KVMemoryMap.getInstance().readValueFromKey(key);
			}
			if (StringUtils.isNotBlank(kvdbToken) && StringUtils.equals(kvdbToken, tokenToCheck)) {
				return true;
			} else {
				addCounter(user);
				if (logger.isInfoEnabled()) {
					logger.info("token key : {}", key);
					logger.info("token to ckeck  : {}", StringUtils.left(tokenToCheck, 100));
					logger.info("token from kvdb : {}", StringUtils.left(kvdbToken, 100));
				}
				return false;
			}
		}
	}

	public static String cleanUpKvSubKey(String subKey) {
		// nur a-zA-Z0-9_
		subKey = subKey.replace(" ", "");
		subKey = subKey.replace(".", "_");
		subKey = subKey.replace("=", "_");
		return subKey;
	}

	public static String cleanUpKyValue(String subKey) {
		// erlaubt: a-zA-Z0-9_
		// zusaetzlich: /.,()[]{}<>!@"*+#- SPACE
		subKey = subKey.replace(" ", "");
		subKey = subKey.replace(".", "_");
		subKey = subKey.replace("=", "_");
		return subKey;
	}

	public static void logoffUser(Model model) {

		cookieDelete(model);

		model.setUser(null);
		model.setVerzeichnisBerechtigungen(new LinkedList<>());
		model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
		model.setDeleteModelAfterRequest(true);

	}

	public static void addCounter(String itemToCount) {

		String key = KVDB_KEY_BLACKLIST + itemToCount;
		String value = "1";

		if (KVMemoryMap.getInstance().containsKey(key)) {
			long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
			value = String.valueOf(++actualValue);
		}

		KVMemoryMap.getInstance().writeKeyValue(key, value, true);
		logger.warn("Schreibe Blacklist fuer {} = {}", key, value);
	}

	private static void resetCounter(String itemToCount) {

		String key = KVDB_KEY_BLACKLIST + itemToCount;
		String value = "1";

		KVMemoryMap.getInstance().writeKeyValue(key, value, true);
		logger.warn("Schreibe Blacklist fuer {} = {}", key, value);
	}

	private static boolean isBlocked(String itemToCheck) {

		String key = KVDB_KEY_BLACKLIST + itemToCheck;

		if (KVMemoryMap.getInstance().containsKey(key)) {
			long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
			if (actualValue > 6L) {
				logger.warn("Blockiert laut Blacklist: {} = {}", key, actualValue);
				if (actualValue == 7L) {
					try {
						sendMessage("Blacklisted key: " + itemToCheck, new PushoverRestClient());
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

	private static void sendMessage(String text, PushoverClient pushClient) throws PushoverException {

		String apiToken = KVMemoryMap.getInstance().readValueFromKey("application.pushService.apiToken");
		String userID = KVMemoryMap.getInstance().readValueFromKey("application.pushService.userID");
		String clientName = KVMemoryMap.getInstance().readValueFromKey("application.pushService.clientName");
		String environmentName = KVMemoryMap.getInstance().readValueFromKey("application.environment.name");

		if (StringUtils.isAnyBlank(apiToken, userID, clientName)) {
			return;
		}

		PushoverMessage message = PushoverMessage.builderWithApiToken(apiToken) //
				.setUserId(userID) //
				.setDevice(clientName) //
				.setMessage(text) //
				.setPriority(MessagePriority.HIGH) //
				.setTitle(environmentName + " - Files") //
				.build();

		Status status = null;
		status = pushClient.pushMessage(message);
		if (status != null && status.getStatus() > 1) {
			throw new IllegalStateException("Pushover client status=" + status);
		}
	}

	public static boolean isUserActive(String user) {

		String key = KVDB_USER_IDENTIFIER + user;

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
