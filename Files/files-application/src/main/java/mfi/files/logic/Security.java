package mfi.files.logic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import mfi.files.api.TokenResult;
import mfi.files.helper.Hilfsklasse;
import mfi.files.maps.KVMemoryMap;
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

    private static final String UNKNOWN_USER = "UnknownUser";

    private static final String FILES_APPLICATION = "de_fimatas_files";

    private static final String ANMELDEDATEN_SIND_FEHLERHAFT = "Anmeldedaten sind fehlerhaft.";

    private static final String KVDB_PASS_IDENTIFIER = ".pass";

    private static final String SESSION_COOKIE_NAME = "JSESSIONID";

    private static final String LOGIN_COOKIE_NAME = "FILESLOGIN";

    private static final String BLACKLIST_ALLOWED_APPLICATION = "NotAllowedApplication";

    private static final long LIMIT_BLOCKED = 3;

    private static final long LIMIT_PUSH_2 = 40;

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

        if (parameters.isEmpty() || (!model.isUserAuthenticated()
            && model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY)) {
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
            if (StringUtils.isBlank(model.getUser())
                || !KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + model.getUser())) {
                // Login Cookie nicht gefunden
                addCounter(StringUtils.defaultIfBlank(model.getUser(), UNKNOWN_USER));
                logoffUser(model);
            }
        }
    }

    private static void checkCookieAndSessionBeforeLoginFromCookie(Model model) {

        if (isBlocked(UNKNOWN_USER)) {
            logoffUser(model);
            return;
        }

        LoginToken token =
            LoginToken.fromCombinedValue(lookupLoginCookie(model.lookupConversation().getCookiesReadFromRequest()));
        if (token != null && !token.checkToken(token.getUser(), FILES_APPLICATION, deviceFromUserAgent(model.getUserAgent()))) {
            addCounter(StringUtils.defaultIfBlank(token.getUser(), UNKNOWN_USER));
            token = null;
        }

        // Pruefung: Model in der Session gefunden, aber kein Login-Cookie
        if (model.lookupConversation().getCondition().getAllowedFor() != AllowedFor.ANYBODY && model.isUserAuthenticated()
            && token == null) {
            addCounter(StringUtils.defaultIfBlank(model.getUser(), UNKNOWN_USER));
            logoffUser(model);
            return;
        }

        // Pruefung: Model aus der Session gehoert nicht dem User laut Login-Cookie (Abgleich der beiden Cookies)
        if (model.isUserAuthenticated() && token != null) {
            if (!StringUtils.equalsIgnoreCase(token.getUser(), model.getUser())) { // NOSONAR
                addCounter(StringUtils.defaultIfBlank(model.getUser(), UNKNOWN_USER));
                addCounter(StringUtils.defaultIfBlank(token.getUser(), UNKNOWN_USER));
                logoffUser(model);
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
            if (logger.isDebugEnabled()) {
                logger.debug("Initializing new Model");
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
        if (token == null || !token.checkToken(token.getUser(), FILES_APPLICATION, deviceFromUserAgent(model.getUserAgent()))) {
            return;
        }

        String keyForCookieToken = kvKeyForCookieToken(token, model);

        if (KVMemoryMap.getInstance().containsKey(keyForCookieToken)) {
            // Found cookie
            String userFromCookie = token.getUser();
            // login
            boolean wasAlreadyAuthenticated = model.isUserAuthenticated();
            boolean authenticated = authenticateUser(model, userFromCookie, null, KVMemoryMap.getInstance()
                .readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + userFromCookie + KVDB_PASS_IDENTIFIER), parameters);
            if (authenticated) {
                if (!wasAlreadyAuthenticated) {
                    // forwarding to standard condition
                    model.lookupConversation().setCondition(Condition.AUTOLOGIN_FROM_COOKIE);
                }
                cookieRenew(model, token);
            } else {
                KVMemoryMap.getInstance().deleteKey(keyForCookieToken);
                logger.error("Re-Login ueber Session-Cookie war NICHT erfolgreich: {} / {}", StringUtils.left(cookieID, 100), // NOSONAR
                    userFromCookie);
                logoffUser(model);
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
        return KVMemoryMap.KVDB_KEY_LOGINTOKEN + token.getUser() + "." + FILES_APPLICATION + "."
            + deviceFromUserAgent(model.getUserAgent());
    }

    public static String deviceFromUserAgent(String userAgent) {
        String device = cleanUpKvSubKey(userAgent).replaceAll("[0-9]", "");
        device = StringUtils.replaceEach(device, new String[] {"_", "."}, new String[] {"", ""});
        device = StringUtils.defaultIfBlank(device, "UnknownBrowser");

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
            String key = KVMemoryMap.KVDB_KEY_LOGINTOKEN + model.getUser() + "." + FILES_APPLICATION + "."
                + deviceFromUserAgent(model.getUserAgent());
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

    public static boolean authenticateUser(Model model, String user, String pass, String passHash,
            Map<String, String> parameters) { // NOSONAR

        user = cleanUpKvSubKey(user);

        if (isBlocked(user)) {
            logoffUser(model);
            logger.warn("Ungueltiger Anmeldeversuch (authenticateUser) wegen Blacklisting mit User={}", user);
            addCounter(user);
            model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
        } else if (!isUserActive(user)) {
            logoffUser(model);
            logger.warn("Ungueltiger Anmeldeversuch (authenticateUser) wegen inaktivem User mit User={}", user);
            addCounter(user);
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
                KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + KVDB_PASS_IDENTIFIER),
                passHash)) {
                if (!StringUtils.equals(model.getUser(), user)) {
                    model.setUser(user);
                }
                cookieWrite(model);
                return true;
            } else {
                logoffUser(model);
                logger.warn("Ungueltiger Anmeldeversuch (authenticateUser) wegen falschem Passwort fuer User={}", user);
                addCounter(user);
                model.lookupConversation().getMeldungen().add(ANMELDEDATEN_SIND_FEHLERHAFT);
            }
        }
        return false;
    }

    public static boolean checkUserCredentials(String user, String pass) {

        user = cleanUpKvSubKey(user);

        if (StringUtils.isAnyBlank(user, pass)) {
            return false;
        }

        if (isBlocked(user)) {
            addCounter(user);
            logger.warn("Ungueltiger Anmeldeversuch (checkUserCredentials) wegen Blacklisting mit User={}", user);
            return false;
        } else if (!isUserActive(user)) {
            addCounter(user);
            logger.warn("Ungueltiger Anmeldeversuch (checkUserCredentials) wegen inaktivem User mit User={}", user);
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
                logger.warn("Ungueltiger Anmeldeversuch (checkUserCredentials) wegen falschem Passwort fuer User={}", user);
                addCounter(user);
                return false;
            }
        }
    }

    public static boolean checkAllowedApplication(String user, String application) {

        if (StringUtils.isBlank(application)) {
            return false;
        }

        String allowedApplications = StringUtils.trimToEmpty(
            KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".allowedApplications"));
        boolean applicationAllowed = Arrays.asList(StringUtils.split(allowedApplications, ",")).contains(application);
        if (!applicationAllowed) {
            logger.warn("Ungueltiger Anmeldeversuch (checkAllowedApplication) fuer User/Applikation={}/{}", user, application);
            addCounter(BLACKLIST_ALLOWED_APPLICATION);
        }
        return applicationAllowed;
    }

    public static boolean checkPin(String user, String pin) {

        user = cleanUpKvSubKey(user);
        pin = cleanUpKvSubKey(pin);

        if (StringUtils.isAnyBlank(user, pin)) {
            return false;
        }

        if (isBlocked(user)) {
            addCounter(user);
            logger.warn("Ungueltiger Anmeldeversuch (checkPin) wegen Blacklisting mit User={}", user);
            return false;
        } else if (!isUserActive(user)) {
            addCounter(user);
            logger.warn("Ungueltiger Anmeldeversuch (checkPin) wegen inaktivem User mit User={}", user);
            return false;
        } else {
            String passHash = Crypto.encryptLoginCredentials(user, pin);
            if (KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user)
                && KVMemoryMap.getInstance().containsKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".pin")
                && StringUtils.equals(
                    KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".pin"), passHash)) {
                return true;
            } else {
                addCounter(user);
                logger.warn("Ungueltiger Authentifizierungsversuch (checkPin) wegen falscher PIN fuer User={}", user);
                return false;
            }
        }
    }

    public static TokenResult createToken(String user, String pass, String application, String device) {

        user = cleanUpKvSubKey(user);
        application = cleanUpKvSubKey(application);
        device = cleanUpKvSubKey(device);

        if (StringUtils.isAnyBlank(user, application, device)) {
            return new TokenResult(false, null);
        }

        if (checkUserCredentials(user, pass)) {
            LoginToken token = LoginToken.createNew(user);
            String key = KVMemoryMap.KVDB_KEY_LOGINTOKEN + user + "." + application + "." + device;
            KVMemoryMap.getInstance().writeKeyValue(key, token.toKvDbValue(), true);
            logger.debug("created token for key : {}", key);
            logger.debug("created token value : {}", logger.isDebugEnabled() ? StringUtils.left(token.toKvDbValue(), 100) : "");
            return new TokenResult(true, token.toKvDbValue());
        }
        return new TokenResult(false, null);
    }

    public static TokenResult checkToken(String user, String tokenToCheck, String application, String device, boolean refresh) {

        user = cleanUpKvSubKey(user);
        application = cleanUpKvSubKey(application);
        device = cleanUpKvSubKey(device);
        tokenToCheck = cleanUpKvValue(tokenToCheck);

        if (StringUtils.isAnyBlank(user, application, device, tokenToCheck)) {
            return new TokenResult(false, null);
        }

        if (isBlocked(user)) {
            addCounter(user);
            logger.warn("Ungueltiger Anmeldeversuch (checkToken) wegen Blacklisting mit User={}", user);
            return new TokenResult(false, null);
        } else if (!isUserActive(user)) {
            addCounter(user);
            logger.warn("Ungueltiger Anmeldeversuch (checkToken) wegen inaktivem User mit User={}", user);
            return new TokenResult(false, null);
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
                return new TokenResult(true, tokenToReturn);
            } else {
                addCounter(user);
                if (logger.isInfoEnabled()) {
                    logger.info("token to ckeck  : {}", StringUtils.left(tokenToCheck, 100));
                }
                return new TokenResult(false, null);
            }
        }
    }

    public static void deleteToken(String user, String application, String device) {

        user = cleanUpKvSubKey(user);
        application = cleanUpKvSubKey(application);
        device = cleanUpKvSubKey(device);

        if (StringUtils.isAnyBlank(user, application, device)) {
            return;
        }

        String key = KVMemoryMap.KVDB_KEY_LOGINTOKEN + user + "." + application + "." + device;
        KVMemoryMap.getInstance().deleteKey(key);
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
        model.lookupConversation().setCondition(Condition.NULL);
        model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
        model.setDeleteModelAfterRequest(true);

    }

    public static void addCounter(String itemToCount) {

        itemToCount = StringUtils.toRootLowerCase(itemToCount);
        String key = KVMemoryMap.KVDB_KEY_BLACKLIST + itemToCount;
        long value = 1;

        if (KVMemoryMap.getInstance().containsKey(key)) {
            long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
            value = actualValue + 1;
        }

        KVMemoryMap.getInstance().writeKeyValue(key, String.valueOf(value), true);
        logger.warn("Schreibe Blacklist fuer {} = {}", key, value);

        if (value == LIMIT_BLOCKED) {
            Hilfsklasse.sendPushMessage("Blocked key: " + itemToCount);
        }
        if (value == LIMIT_PUSH_2) {
            Hilfsklasse.sendPushMessage("High login attempt count: " + itemToCount);
        }
    }

    private static boolean isBlocked(String itemToCheck) {

        itemToCheck = StringUtils.toRootLowerCase(itemToCheck);
        String key = KVMemoryMap.KVDB_KEY_BLACKLIST + itemToCheck;

        if (KVMemoryMap.getInstance().containsKey(key)) {
            long actualValue = Long.parseLong(KVMemoryMap.getInstance().readValueFromKey(key));
            if (actualValue >= LIMIT_BLOCKED) {
                logger.warn("Blockiert laut Blacklist: {} = {}", key, actualValue);
                return true;
            }
        }
        return false;
    }

    public static boolean isUserActive(String user) {

        if (StringUtils.isBlank(user)) {
            return false;
        }

        if (!Boolean
            .parseBoolean(StringUtils.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.allowsLogin")))) {
            return false;
        }

        String key = KVMemoryMap.KVDB_USER_IDENTIFIER + user;
        if (KVMemoryMap.getInstance().containsKey(key)) {
            String value = KVMemoryMap.getInstance().readValueFromKey(key);
            return (StringUtils.endsWithIgnoreCase(value, Boolean.TRUE.toString()));
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
            if (file.isDirectory()) {
                path = file.getAbsolutePath();
            } else if (file.getParentFile() == null) {
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
