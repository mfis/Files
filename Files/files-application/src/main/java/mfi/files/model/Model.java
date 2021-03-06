package mfi.files.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;

public class Model implements Serializable {

	private static final String HOME_DIRECTORY = ".homeDirectory";

    private static final String IS_CLIENT_TOUCH_DEVICE_WURDE_NICHT_INITIALISIERT = "isClientTouchDevice() wurde nicht initialisiert!";

    private static final Logger logger = LoggerFactory.getLogger(Model.class);
	private static final long serialVersionUID = 1L;

	private LoginToken loginCookieID;
	private String user;
	private List<String> verzeichnisBerechtigungen;
	private String zwischenablage;

	private Map<Integer, Conversation> conversations;

	private boolean developmentMode;
	private boolean webserverRunsBehindSSLReverseProxy;
	private String hostname;
	private String sessionID;
	private boolean deleteModelAfterRequest;
	private String userAgent;
	private Integer lastUsedConversationID = null;

	private String noteFileSuffix;
	private List<String> favoriteFolders;

	private boolean istTouchDevice;
	private boolean istTouchDeviceGeprueft;
	private boolean istTelephone;
	private boolean istTablet;
	private boolean istTouchIconFaehig;
	private boolean istWebApp;

	private boolean isBatch;

	public Model() {
		istTouchDeviceGeprueft = false;
		deleteModelAfterRequest = false;
		user = null;
		verzeichnisBerechtigungen = new LinkedList<>();
		conversations = null;
		isBatch = false;
	}

	public void initializeModelOnFirstRequest(HttpServletRequest request) {

		conversations = new HashMap<>();

		setSessionID(null);
		setUser(null);

		if (request != null) {
			setHostname(request.getHeader("host"));
			isClientTouchDevice(request);
		} else {
			isBatch = true;
		}

		if (!KVMemoryMap.getInstance().isInitialized()) {
			throw new IllegalArgumentException("KVM Initialisierung noch nicht beendet!");
		}

		setDevelopmentMode(StringUtils.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.properties.developmentMode"))
				.equalsIgnoreCase(Boolean.TRUE.toString()));

		setWebserverRunsBehindSSLReverseProxy(StringUtils
				.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.properties.webserverRunsBehindSSLReverseProxy"))
				.equalsIgnoreCase(Boolean.TRUE.toString()));

		setNoteFileSuffix(StringUtils.trim(KVMemoryMap.getInstance().readValueFromKey("application.properties.noteFileSuffix")));
	}

	public Conversation lookupConversation() {

		Integer id = ThreadLocalHelper.getConversationID();
		if (id == null) {
			if (lastUsedConversationID != null) {
				id = lastUsedConversationID;
			} else {
				synchronized (this) {
					id = lookupNextConversationID();
                    logger.debug("New conversation: {}", id);
					ThreadLocalHelper.setConversationID(id.toString());
				}
			}
		}

		if (!conversations.containsKey(id)) {
			String startVerzeichnis = null;
            FilesFile editingFile = null;
			if (isUserAuthenticated()) {
				startVerzeichnis = KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + HOME_DIRECTORY);
                editingFile = new FilesFile(startVerzeichnis);
			}
			Conversation newConversation = new Conversation(id, startVerzeichnis);
            newConversation.setEditingFile(editingFile);
			newConversation.lookupConditionForRequest(null);
			newConversation.setTextViewPush(!istTelephone);
			newConversation.setFilesystemViewDetails(!istTelephone);
			newConversation.setTextViewNumbers(false);
			conversations.put(id, newConversation);
		}
		lastUsedConversationID = id;
		return conversations.get(id);
	}

	public Conversation lookupConversation(Integer id) {

		if (id == null) {
			throw new IllegalArgumentException("Conversation ID ist null!");
		}
		if (!conversations.containsKey(id)) {
			throw new IllegalArgumentException("Conversation ID ist unbekannt!");
		}
		return conversations.get(id);
	}

	public Integer getConversationCount() {
		if (conversations == null) {
			return 0;
		}
		return conversations.size();
	}

	public int lookupNextConversationID() {
		Integer max = 0;
		if (!conversations.isEmpty()) {
			max = Collections.max(conversations.keySet());
		}
		return max + 1;
	}

	public void lookupConditionForRequest(Map<String, String> parameters) {
		lookupConversation().lookupConditionForRequest(parameters.get(HTMLUtils.CONDITION));
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getZwischenablage() {
		return zwischenablage;
	}

	public void setZwischenablage(String zwischenablage) {
		this.zwischenablage = zwischenablage;
	}

	public List<String> getFavoriteFolders() {
		return favoriteFolders;
	}

	public void setFavoriteFolders(List<String> favoriteFolders) {
		this.favoriteFolders = favoriteFolders;
	}

	public String getNoteFileSuffix() {
		return noteFileSuffix;
	}

	public void setNoteFileSuffix(String noteFileSuffix) {
		this.noteFileSuffix = noteFileSuffix;
	}

	public boolean isClientTouchDevice(HttpServletRequest request) {

		if (istTouchDeviceGeprueft) {
			return istTouchDevice;
		} else {
			userAgent = request.getHeader("user-agent");

			istTelephone = StringUtils.contains(userAgent, "iPod") || StringUtils.contains(userAgent, "iPhone")
					|| StringUtils.contains(userAgent, "Galaxy S") || StringUtils.contains(userAgent, "Android")
					|| StringUtils.contains(userAgent, "Symbian") || StringUtils.contains(userAgent, "Windows Phone")
					|| StringUtils.contains(userAgent, "ZuneWP7") || StringUtils.contains(userAgent, "WPDesktop")
					|| StringUtils.contains(userAgent, "Lumia");

			istTablet = StringUtils.contains(userAgent, "iPad") || StringUtils.contains(userAgent, "Galaxy Tab")
					|| StringUtils.contains(userAgent, "Tablet") || StringUtils.contains(userAgent, "Kindle");

			istTouchDevice = istTablet || istTelephone;

			istTouchIconFaehig = istTouchDevice;

			istTouchDeviceGeprueft = true;
			lookupConversation().setTextViewPush(!istTelephone);
			return istTouchDevice;
		}
	}

	public boolean isClientTouchDevice() {

		if (istTouchDeviceGeprueft) {
			return istTouchDevice;
		} else {
			throw new IllegalArgumentException(IS_CLIENT_TOUCH_DEVICE_WURDE_NICHT_INITIALISIERT);
		}
	}

	public boolean isPhone() {

		if (istTouchDeviceGeprueft) {
			return istTelephone;
		} else {
			throw new IllegalArgumentException(IS_CLIENT_TOUCH_DEVICE_WURDE_NICHT_INITIALISIERT);
		}
	}

	public boolean isTablet() {

		if (istTouchDeviceGeprueft) {
			return istTablet;
		} else {
			throw new IllegalArgumentException(IS_CLIENT_TOUCH_DEVICE_WURDE_NICHT_INITIALISIERT);
		}
	}

	public boolean isTouchIconFaehig() {

		if (istTouchDeviceGeprueft) {
			return istTouchIconFaehig;
		} else {
			throw new IllegalArgumentException("isTouchIconFaehig() wurde nicht initialisiert!");
		}
	}

	public boolean isUserAuthenticated() {
		return StringUtils.isNotEmpty(user);
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
		readUserProperties();
	}

	private void readUserProperties() {
		if (StringUtils.isNotEmpty(user)) {

			// Favorites
			StringTokenizer tokenizerFav = new StringTokenizer(
					KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".favoriteFolders"), ",");
			setFavoriteFolders(new LinkedList<>());
			while (tokenizerFav.hasMoreElements()) {
				getFavoriteFolders().add(((String) tokenizerFav.nextElement()).trim());
			}

			// Berechtigungen
			StringTokenizer tokenizerBer = new StringTokenizer(
					KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".allowedDirectory"), ",");
			setVerzeichnisBerechtigungen(new LinkedList<>());
			while (tokenizerBer.hasMoreElements()) {
				String ber = ((String) tokenizerBer.nextElement()).trim();
				if (ber.length() > 1 && StringUtils.endsWith(ber, "/")) {
					// bei laenge 1 muss / stehen bleiben, um Zugriff auf root konfigurieren zu koennen
					ber = (StringUtils.removeEnd(ber, "/"));
				}
				getVerzeichnisBerechtigungen().add(ber);
			}

			// Home
			if (!isBatch) {
				lookupConversation().setVerzeichnis(
						KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + HOME_DIRECTORY));
                lookupConversation().setEditingFile(new FilesFile(lookupConversation().getVerzeichnis()));
			}

		} else {
			lookupConversation().setVerzeichnis(null);
            lookupConversation().setEditingFile(null);
			setFavoriteFolders(null);
			setVerzeichnisBerechtigungen(new LinkedList<>());
		}
	}

	public String getSessionID() {
		return sessionID;
	}

	public void setSessionID(String sessionID) {
		this.sessionID = sessionID;
	}

	public boolean isDeleteModelAfterRequest() {
		return deleteModelAfterRequest;
	}

	public void setDeleteModelAfterRequest(boolean deleteModelAfterRequest) {
		this.deleteModelAfterRequest = deleteModelAfterRequest;
	}

	public boolean isDevelopmentMode() {
		return developmentMode;
	}

	public void setDevelopmentMode(boolean developmentMode) {
		this.developmentMode = developmentMode;
	}

	public LoginToken getLoginCookieID() {
		return loginCookieID;
	}

	public void setLoginCookieID(LoginToken loginCookieID) {
		this.loginCookieID = loginCookieID;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public boolean isIstWebApp() {
		return istWebApp;
	}

	public void setIstWebApp(boolean istWebApp) {
		this.istWebApp = istWebApp;
	}

	public List<String> getVerzeichnisBerechtigungen() {
		return verzeichnisBerechtigungen;
	}

	public void setVerzeichnisBerechtigungen(List<String> verzeichnisBerechtigungen) {
		this.verzeichnisBerechtigungen = verzeichnisBerechtigungen;
	}

	public boolean isWebserverRunsBehindSSLReverseProxy() {
		return webserverRunsBehindSSLReverseProxy;
	}

	public void setWebserverRunsBehindSSLReverseProxy(boolean webserverRunsBehindSSLReverseProxy) {
		this.webserverRunsBehindSSLReverseProxy = webserverRunsBehindSSLReverseProxy;
	}

}
