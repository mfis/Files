package mfi.files.logic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/*
 * UNMAINTAINABLE CLASS - DO NOT EDIT ANYMORE -> REWRITE !!
 */
@Deprecated
public class Security {

	private static final String COOKIE_NAME = "FILESLOGIN";
	public static final String KVDB_KEY_BLACKLIST = "temporary.day.login.blacklist.";
	public static final String KVDB_KEY_COOKIES = "temporary.manual.cookies.";
	public static final String KVDB_KEY_COOKIES_DELIMITER = " <##> ";
	public static final String BLACKLIST_UNKNOWN_SESSION = "UnknownSession";
	public static final String BLACKLIST_UNKNOWN_COOKIE = "UnknownCookie";
	public static final String BLACKLIST_SESSION_WITHOUT_LOGIN = "SessionWithoutLogin";
	private static final String COOKIE_ID_PREFIX = "fjc";

	private static Logger getLogger() {
		final Logger logger = LoggerFactory.getLogger(Security.class);
		return logger;
	}

	public static void checkSecurityForRequest(Model model, Map<String, String> parameters) {

		String sid = parameters.get(ServletHelper.SERVLET_SESSION_ID);
		boolean emptyParameters = parameters.isEmpty();

		if (model.isInitialRequest()) {

			if (model.lookupConversation().getCondition() == Condition.FILE_UPLOAD
					&& parameters.containsKey(ServletHelper.UPLOAD_TICKET_PARAM)
					&& StringUtils.isNotBlank(parameters.get(ServletHelper.UPLOAD_TICKET_PARAM))) {
				boolean ticketOkay = false;
				String ticket = parameters.get(ServletHelper.UPLOAD_TICKET_PARAM);
				if (KVMemoryMap.getInstance().containsKey(ServletHelper.UPLOAD_TICKET_PARAM + "." + ticket)) {
					String user = KVMemoryMap.getInstance().readValueFromKey(ServletHelper.UPLOAD_TICKET_PARAM + "." + ticket);
					if (StringUtils.isNotBlank(user)) {
						ticketOkay = authenticateUser(model, user, null,
								KVMemoryMap.getInstance().readValueFromKey("user." + user + ".pass"), parameters, true);
					}
				}
				if (ticketOkay) {
					model.setUploadTicket(true);
				} else {
					List<String[]> ticketEntries = KVMemoryMap.getInstance().readListWithPartKey(ServletHelper.UPLOAD_TICKET_PARAM + ".");
					Set<String> blacklisted = new HashSet<>();
					for (String[] ticketEntry : ticketEntries) {
						String ticketUser = ticketEntry[1];
						if (!blacklisted.contains(ticketUser)) {
							addCounter(ticketUser);
							blacklisted.add(ticketUser);
						}
					}
				}

			} else if (!emptyParameters && model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY) {
				model.lookupConversation().getMeldungen()
						.add("Du bist nicht angemeldet oder hattest einen Session-Timeout. Bitte die Seite neu aufrufen.");
				model.lookupConversation().setCondition(Condition.NULL);
				addCounter(BLACKLIST_UNKNOWN_SESSION);
				if (isBlocked(BLACKLIST_UNKNOWN_SESSION)) {
					KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
					getLogger().warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs");
					logoffUser(model);
					resetCounter(BLACKLIST_UNKNOWN_SESSION);
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
					addCounter(BLACKLIST_SESSION_WITHOUT_LOGIN);
					if (isBlocked(BLACKLIST_SESSION_WITHOUT_LOGIN)) {
						KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
						getLogger().warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs (4)");
						resetCounter(BLACKLIST_SESSION_WITHOUT_LOGIN);
					}
				}
				if (StringUtils.isEmpty(sid) || StringUtils.isEmpty(model.getSessionID())
						|| !StringUtils.equals(sid, model.getSessionID())) {
					if (model.lookupConversation().getMeldungen().isEmpty()) {
						model.lookupConversation().getMeldungen().add("Bitte melde dich zuerst an.");
					}
					getLogger().warn("Die SessionID ist ungueltig:" + sid + "/" + model.getSessionID());
					logoffUser(model);
					addCounter(BLACKLIST_UNKNOWN_SESSION);
					if (isBlocked(BLACKLIST_UNKNOWN_SESSION)) {
						KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
						getLogger().warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs (4)");
						resetCounter(BLACKLIST_UNKNOWN_SESSION);
					}
				}
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
			if (isBlocked(model.getUser())) {
				getLogger().warn("User " + model.getUser() + " sperren wegen zu hoher Blacklist-Eintraege");
				logoffUser(model);
			}
		}

		if (model.isUploadTicket() && model.lookupConversation().getCondition() != null
				&& model.lookupConversation().getCondition() != Condition.FILE_UPLOAD
				&& model.lookupConversation().getCondition() != Condition.LOGOFF) {
			model.lookupConversation().setCondition(Condition.FILE_UPLOAD);
			model.lookupConversation().getMeldungen().add("Mit diesem Anmeldungs-Ticket ist nur der Datei-Upload erlaubt.");
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

	public static Model lookupModelFromSession(HttpSession session, HttpServletRequest request) {

		Model model;
		model = (Model) session.getAttribute(FilesMainServlet.SESSION_ATTRIBUTE_MODEL);

		if (model != null) {
			String cookieID = lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest());
			if (StringUtils.isBlank(cookieID)) {
				if (request.getCookies() != null) {
					cookieID = lookupLoginCookie(Arrays.asList(request.getCookies()));
				}
			}
			if (StringUtils.isBlank(cookieID)
					|| StringUtils.isBlank(KVMemoryMap.getInstance().readValueFromKey(KVDB_KEY_COOKIES + cookieID))) {
				model.setUser(null);
				model.setVerzeichnisBerechtigungen(new LinkedList<>());
				if (StringUtils.isNotBlank(cookieID)) {
					addCounter(BLACKLIST_SESSION_WITHOUT_LOGIN);
					if (isBlocked(BLACKLIST_SESSION_WITHOUT_LOGIN)) {
						KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
						getLogger().warn("Loeschen aller Session Cookies aufgrund moegliches BruteForce Angriffs (3)");
						resetCounter(BLACKLIST_SESSION_WITHOUT_LOGIN);
					}
				}
			}
		}

		return model;
	}

	public static void cookieRead(Model model, Map<String, String> parameters) throws Exception {

		boolean init = false;
		if (model.isInitialRequest() && model.lookupConversation().getCondition().equals(Condition.NULL)) {
			init = true;
		}
		String cookieID = lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest());

		if (cookieID != null) {
			if (KVMemoryMap.getInstance().containsKey(KVDB_KEY_COOKIES + cookieID)) {
				if (init) {
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
					} else {
						KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + cookieID);
						getLogger().error("Re-Login ueber Session-Cookie war NICHT erfolgreich:" + cookieID + " / " + userFromCookie);
						throw new SecurityException("Re-Login ueber Session-Cookie war nicht erfolgreich!");
					}
				}
			} else {
				getLogger().error("Login-Cookie loeschen, da nicht auf DB gefunden:" + cookieID);
				logoffUser(model);
				model.lookupConversation().setCondition(Condition.NULL);
				addCounter(BLACKLIST_UNKNOWN_COOKIE);
				if (isBlocked(BLACKLIST_UNKNOWN_COOKIE)) {
					KVMemoryMap.getInstance().deleteKeyRangeStartsWith(KVDB_KEY_COOKIES);
					getLogger().warn("Loeschen aller Cookies aufgrund moegliches BruteForce Angriffs (2)");
					resetCounter(BLACKLIST_UNKNOWN_COOKIE);
				}
			}
		}
		// }
	}

	private static String lookupLoginCookie(List<Cookie> cookies) {

		// Check for cookie
		String cookieID = null;
		if (cookies != null) {
			for (Cookie cookieReadFromRequest : cookies) {
				String cookieName = cookieReadFromRequest.getName();
				if (cookieName.equals(COOKIE_NAME) && StringUtils.startsWith(cookieReadFromRequest.getValue(), COOKIE_ID_PREFIX)) {
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

	private static void cookieRenew(Model model, String cookieID) {

		writeCookieIdentifierToKVDB(model, cookieID);

		model.setLoginCookieID(cookieID);

		Cookie cookie = new Cookie(COOKIE_NAME, cookieID);
		cookie.setMaxAge(60 * 60 * 24 * 92);
		model.lookupConversation().getCookiesToWriteToResponse().put(COOKIE_NAME, cookie);
	}

	private static void cookieDelete(Model model) {

		if (StringUtils.isNotEmpty(model.getLoginCookieID())) {
			KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + model.getLoginCookieID());
			Cookie cookie = new Cookie(COOKIE_NAME, model.getLoginCookieID());
			cookie.setMaxAge(0);
			model.lookupConversation().getCookiesToWriteToResponse().put(COOKIE_NAME, cookie);
			model.setLoginCookieID(null);
		}

		String cookieID = lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest());
		if (StringUtils.isNotEmpty(cookieID)) {
			KVMemoryMap.getInstance().deleteKey(KVDB_KEY_COOKIES + cookieID);
			Cookie cookie = new Cookie(COOKIE_NAME, cookieID);
			cookie.setMaxAge(0);
			model.lookupConversation().getCookiesToWriteToResponse().put(COOKIE_NAME, cookie);
			model.setLoginCookieID(null);
		}
	}

	public static boolean authenticateUser(Model model, String user, String pass, String passHash, Map<String, String> parameters,
			boolean isReLogin) {

		if (isBlocked(user) || isBlocked(parameters.get(ServletHelper.SERVLET_REMOTE_IP))) {
			logoffUser(model);
			getLogger().warn(
					"Ungueltiger Anmeldeversuch wegen Blacklisting mit " + user + " / " + parameters.get(ServletHelper.SERVLET_REMOTE_IP));
			addCounter(user);
			addCounter(parameters.get(ServletHelper.SERVLET_REMOTE_IP));
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
				return true;
			} else {
				logoffUser(model);
				getLogger().warn("Ungueltiger Anmeldeversuch fuer User=" + user);
				addCounter(user);
				addCounter(parameters.get(ServletHelper.SERVLET_REMOTE_IP));
				model.lookupConversation().getMeldungen().add("Wer bist Du denn?");
			}
		}
		return false;
	}

	public static boolean checkUserCredentials(String user, String pass) {

		if (isBlocked(user)) {
			return false;
		} else {
			String passHash = Crypto.encryptLoginCredentials(user, pass);
			if (KVMemoryMap.getInstance().containsKey("user." + user) && KVMemoryMap.getInstance().containsKey("user." + user + ".pass")
					&& StringUtils.equals(KVMemoryMap.getInstance().readValueFromKey("user." + user + ".pass"), passHash)) {
				return true;
			} else {
				addCounter(user);
				return false;
			}
		}
	}

	public static boolean checkPin(String user, String pin) {

		if (isBlocked(user)) {
			return false;
		} else {
			String passHash = Crypto.encryptLoginCredentials(user, pin);
			if (KVMemoryMap.getInstance().containsKey("user." + user) && KVMemoryMap.getInstance().containsKey("user." + user + ".pin")
					&& StringUtils.equals(KVMemoryMap.getInstance().readValueFromKey("user." + user + ".pin"), passHash)) {
				return true;
			} else {
				addCounter(user);
				return false;
			}
		}
	}

	public static String createToken(String user, String pass, String application, String device) {

		application = cleanUpSubKey(application);
		device = cleanUpSubKey(device);
		if (checkUserCredentials(user, pass)) {
			String uuid = application + "#" + UUID.randomUUID().toString() + "#" + user.hashCode();
			String token = new String(new Base32(0).encode(uuid.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8).replace("=", "");
			String encryptedToken = Crypto.encryptLoginCredentials(user, token);
			KVMemoryMap.getInstance().writeKeyValue("user." + user + "." + application + "#" + device + ".token", encryptedToken, true);
			return token;
		}
		return null;
	}

	public static boolean checkToken(String user, String token, String application, String device) {

		if (isBlocked(user)) {
			return false;
		} else {
			application = cleanUpSubKey(application);
			device = cleanUpSubKey(device);
			String encryptedToken = Crypto.encryptLoginCredentials(user, token);
			String key = "user." + user + "." + application.replace('.', '_') + "#" + device + ".token";
			if (KVMemoryMap.getInstance().containsKey("user." + user) && KVMemoryMap.getInstance().containsKey(key)
					&& StringUtils.equals(KVMemoryMap.getInstance().readValueFromKey(key), encryptedToken)) {
				return true;
			} else {
				addCounter(user);
				return false;
			}
		}
	}

	private static String cleanUpSubKey(String subKey) {
		subKey = subKey.replace(" ", "");
		subKey = subKey.replace(".", "_");
		subKey = subKey.replace("=", "_");
		return subKey;
	}

	public static void logoffUser(Model model) {

		cookieDelete(model);

		model.setUser(null);
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
		getLogger().warn("Schreibe Blacklist fuer " + key + " = " + value);
	}

	public static void resetCounter(String itemToCount) {

		String key = KVDB_KEY_BLACKLIST + itemToCount;
		String value = "1";

		KVMemoryMap.getInstance().writeKeyValue(key, value, true);
		getLogger().warn("Schreibe Blacklist fuer " + key + " = " + value);
	}

	private static boolean isBlocked(String itemToCheck) {

		String key = KVDB_KEY_BLACKLIST + itemToCheck;

		if (KVMemoryMap.getInstance().containsKey(key)) {
			long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
			if (actualValue > 6L) {
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
