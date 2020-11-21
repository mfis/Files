package mfi.files.responsibles;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.Cookie;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import it.sauronsoftware.cron4j.Scheduler;
import mfi.files.annotation.Responsible;
import mfi.files.helper.ApplicationUtil;
import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.ServletHelper;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.ButtonBar;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.logic.Crypto;
import mfi.files.logic.DateiZugriff;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.CronSchedulers;
import mfi.files.model.Model;

@Component
public class BasicApplication extends AbstractResponsible {

	private static final String FORMULAR_FIELD_NEW_USER = "new_user";
    private static final String FORMULAR_FIELD_LOGIN_PASS = "login_pass";
    private static final String FORMULAR_FIELD_LOGIN_USER = "login_user";

    @Responsible(conditions = { Condition.NULL, Condition.LOGIN_FORMULAR })
    public void fjAnmeldeSeite(StringBuilder sb, Map<String, String> parameters, Model model) {

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Lizenzen anzeigen / View Licence Attribution", Condition.VIEW_LICENCE_ATTRIBUTION));

		if (model.isUserAuthenticated() && model.lookupConversation().getCondition().equals(Condition.NULL)) {
			// Neue Conversation
			// Weiterleiten zum normalen Menü
			model.lookupConversation().setForwardCondition(Condition.FS_NAVIGATE);
		} else {
			sb.append(HTMLUtils.buildMenuNar(model, "Files Anmeldung", false, buttonBar, false));
			HTMLTable table = new HTMLTable();
			table.addTD("Anmeldedaten", 1, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			table.addTD("Name: ", 1, null);
			table.addNewRow();
			table.addTDSource(HTMLUtils.buildTextField(FORMULAR_FIELD_LOGIN_USER, "", 20, Condition.LOGIN), 1, null);
			HTMLUtils.setFocus(FORMULAR_FIELD_LOGIN_USER, model);
			table.addNewRow();
			table.addTD("Passwort: ", 1, null);
			table.addNewRow();
			table.addTDSource(HTMLUtils.buildPasswordField(FORMULAR_FIELD_LOGIN_PASS, "", 20, Condition.LOGIN, true), 1, null);
			table.addNewRow();
			table.addTD("Cookie-Information", 1, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			String cookietext = "Diese Internetseite verwendet so genannte Cookies. <p> Um mehr zu erfahren, klicken Sie bitte hier:";
			table.addTD(cookietext, null);
			table.addNewRow();
			table.addTD(new Button("Impressum und Datenschutzerkl&auml;rung",
					KVMemoryMap.getInstance().readValueFromKey("application.linkToLawSite"), true).printForUseInTable(), null);
			table.addNewRow();
			table.addTDSource(HTMLUtils.buildCheckBox("Ich bin mit der Verwendung von Cookies einverstanden.", "COOKIE_OK", false, null), 1,
					null);
			table.addNewRow();
			table.addTD("", 1, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			table.addTDSource(new Button("Anmelden", Condition.LOGIN).printForUseInTable(), 1, null);
			table.addNewRow();
			sb.append(table.buildTable(model));

			model.lookupConversation().setForwardCondition(null);
			return;
		}
	}

	@Responsible(conditions = { Condition.LOGIN_GENERATE_CREDENTIALS_FORM })
    public void fjAccountAnlegenSeite(StringBuilder sb, Map<String, String> parameters, Model model) {

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Zur Anmeldeseite", Condition.LOGIN_FORMULAR));

		sb.append(HTMLUtils.buildMenuNar(model, "Account anlegen", false, buttonBar, false));
		HTMLTable table = new HTMLTable();
		table.addTD("Neuer Name: ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildTextField(FORMULAR_FIELD_NEW_USER, "", 20, null), 1, null);
		HTMLUtils.setFocus(FORMULAR_FIELD_NEW_USER, model);
		table.addNewRow();
		table.addTD("Neues Passwort: ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField("new_pass1", "", 20, Condition.LOGIN_GENERATE_CREDENTIALS, false), 1, null);
		table.addNewRow();
		table.addTD("Passwort wiederholen: ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField("new_pass2", "", 20, Condition.LOGIN_GENERATE_CREDENTIALS, true), 1, null);
		table.addNewRow();
		table.addTDSource(new Button("OK", Condition.LOGIN_GENERATE_CREDENTIALS).printForUseInTable(), 1, null);
		table.addNewRow();
		sb.append(table.buildTable(model));
		model.lookupConversation().setForwardCondition(null);
		return;
	}

	@Responsible(conditions = { Condition.LOGIN_GENERATE_CREDENTIALS })
    public void fjAnmeldeCredentialsGenerieren(StringBuilder sb, Map<String, String> parameters, Model model)
            throws IOException {

		String user = StringUtils.trim(parameters.get(FORMULAR_FIELD_NEW_USER));
		String pass1 = StringUtils.trim(parameters.get("new_pass1"));
		String pass2 = StringUtils.trim(parameters.get("new_pass2"));

        user = Security.cleanUpKvSubKey(user);

		if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(pass1) && StringUtils.isNotBlank(pass2)) {

			if (!StringUtils.equals(pass1, pass2)) {
				model.lookupConversation().getMeldungen().add("Die beiden eingegebenen Passwörter sind nicht identisch");
				model.lookupConversation().setForwardCondition(Condition.LOGIN_GENERATE_CREDENTIALS_FORM);
				return;
			}

            if (KVMemoryMap.getInstance().containsKeyIgnoreCase(KVMemoryMap.KVDB_USER_IDENTIFIER + user)) {
				model.lookupConversation().getMeldungen().add("Ein Account mit dem Namen existiert bereits.");
				model.lookupConversation().setForwardCondition(Condition.LOGIN_GENERATE_CREDENTIALS_FORM);
				return;
			}

			String hash = Crypto.encryptLoginCredentials(user, pass1);
			String secret = Security.cleanUpKvSubKey(UUID.randomUUID().toString());

            KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user, "FALSE", false);
            KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".pass", hash, false);
            KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".loginTokenSecret", secret,
                false);
			KVMemoryMap.getInstance().save();

			logger.info("User/Passwort generiert fuer: " + user);
			model.lookupConversation().getMeldungen().add("Der Neuer Benutzer '" + user
					+ "' wurde angelegt und muss nun noch freigeschaltet werden. Bitte hierfür den Besitzer der Anwendung kontaktieren.");
		} else {
			model.lookupConversation().getMeldungen().add("Bitte Name und Passwort eingeben.");
		}

		model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
		return;
	}

	@Responsible(conditions = { Condition.LOGIN_CHANGE_PASS_FORM })
    public void fjAccountPasswortAendernSeite(StringBuilder sb, Map<String, String> parameters, Model model) {

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Zur Anmeldeseite", Condition.LOGIN_FORMULAR));

		model.lookupConversation().getMeldungen()
				.add("Falls das alte Passwort nicht mehr bekannt ist, bitte das entsprechende Feld leer lassen. "
						+ "In diesem Fall muss der Administrator das neue Passwort erst freischalten. "
						+ "Ist das alte Passwort noch bekannt, kann es direkt durch Eingabe aller u.g. Felder geändert werden.");

		sb.append(HTMLUtils.buildMenuNar(model, "Passwort ändern", false, buttonBar, false));
		HTMLTable table = new HTMLTable();
		table.addTD("Name: ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildTextField("pw_change_user", "", 20, null), 1, null);
		HTMLUtils.setFocus("pw_change_user", model);
		table.addNewRow();
		table.addTD("Altes Passwort: ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField("pw_change_old_pw", "", 20, Condition.LOGIN_CHANGE_PASS, false), 1, null);
		table.addNewRow();
		table.addTD("Neues Passwort: ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField("pw_change_new_pass1", "", 20, Condition.LOGIN_CHANGE_PASS, false), 1, null);
		table.addNewRow();
		table.addTD("Neues Passwort wiederholen: ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField("pw_change_new_pass2", "", 20, Condition.LOGIN_CHANGE_PASS, true), 1, null);
		table.addNewRow();
		table.addTD("PIN (6-stellig numerisch): ", 1, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField("pw_change_new_pin", "", 6, Condition.LOGIN_CHANGE_PASS, true), 1, null);
		table.addNewRow();
		table.addTDSource(new Button("OK", Condition.LOGIN_CHANGE_PASS).printForUseInTable(), 1, null);
		table.addNewRow();
		sb.append(table.buildTable(model));
		model.lookupConversation().setForwardCondition(null);
	}

	@Responsible(conditions = { Condition.LOGIN_CHANGE_PASS })
    public void fjPasswortAendern(StringBuilder sb, Map<String, String> parameters, Model model) throws IOException {

		String user = StringUtils.trim(parameters.get("pw_change_user"));
		String passOld = StringUtils.trim(parameters.get("pw_change_old_pw"));
		String passNew1 = StringUtils.trim(parameters.get("pw_change_new_pass1"));
		String passNew2 = StringUtils.trim(parameters.get("pw_change_new_pass2"));
		String pin = StringUtils.trim(parameters.get("pw_change_new_pin"));

        user = Security.cleanUpKvSubKey(user);
        pin = Security.cleanUpKvSubKey(pin);

		if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(passOld) && StringUtils.isNotBlank(passNew1)
				&& StringUtils.isNotBlank(passNew2)) {

			if (!StringUtils.equals(passNew1, passNew2)) {
				model.lookupConversation().getMeldungen().add("Die beiden eingegebenen neuen Passwörter sind nicht identisch");
				model.lookupConversation().setForwardCondition(Condition.LOGIN_CHANGE_PASS_FORM);
				return;
			}

			if (!Security.checkUserCredentials(user, passOld)) {
				model.lookupConversation().getMeldungen().add("Name und altes Passwort sind nicht korrekt.");
				model.lookupConversation().setForwardCondition(Condition.LOGIN_CHANGE_PASS_FORM);
				return;
			}

			String hashPass = Crypto.encryptLoginCredentials(user, passNew1);
            KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".pass", hashPass, true);
			KVMemoryMap.getInstance().save();

			if (StringUtils.isNotBlank(pin)) {
				if (pin.length() == 6 && StringUtils.isNumeric(pin)) {
					String hashPin = Crypto.encryptLoginCredentials(user, pin);
                    KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".pin", hashPin, true);
					KVMemoryMap.getInstance().save();
					model.lookupConversation().getMeldungen().add("Pin-Änderung erfolgreich.");
				} else {
					model.lookupConversation().getMeldungen().add("Pin wurde NICHT geändert - nicht 6-stellig numerisch.");
				}
			}

			logger.info("User/Passwort geaendert fuer: " + user);
			model.lookupConversation().getMeldungen().add("Passwort-Änderung erfolgreich.");

		} else if (StringUtils.isNotBlank(user) && StringUtils.isBlank(passOld) && StringUtils.isNotBlank(passNew1)
				&& StringUtils.isNotBlank(passNew2) && StringUtils.isBlank(pin)) {

			if (!StringUtils.equals(passNew1, passNew2)) {
				model.lookupConversation().getMeldungen().add("Die beiden eingegebenen neuen Passwörter sind nicht identisch");
				model.lookupConversation().setForwardCondition(Condition.LOGIN_CHANGE_PASS_FORM);
				return;
			}

			if (!Security.isUserActive(user)) {
				model.lookupConversation().getMeldungen().add("Der Name ist nicht bekannt oder gesperrt.");
				model.lookupConversation().setForwardCondition(Condition.LOGIN_CHANGE_PASS_FORM);
				return;
			}

			String hash = Crypto.encryptLoginCredentials(user, passNew1);
			String verification = Security.generateVerificationString();

            KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".resetPass",
					hash + " # " + verification + " @ " + Hilfsklasse.zeitstempelAlsString(), true);
			KVMemoryMap.getInstance().save();

			logger.info("Passwort Reset vorbereitet fuer: " + user);
			model.lookupConversation().getMeldungen().add(
					"Das Passwort wurde gespeichert, muss aber noch vom Administrator freigeschaltet werden, da das alte Passwort nicht angegeben wurde. "
							+ "Bitte hierzu dem Administrator den Verifikationsschlüssel '" + verification + "' nennen");

		} else {
			model.lookupConversation().getMeldungen().add("Bitte erst alle benötigten Felder eingeben.");
		}

		model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
	}

	@Responsible(conditions = { Condition.ENTER_KVDB_PASSWORD, Condition.SAVE_KVDB_PASSWORD })
    public void fjKVDBPasswortSetzen(StringBuilder sb, Map<String, String> parameters, Model model) {

		if (model.lookupConversation().getCondition() == Condition.ENTER_KVDB_PASSWORD) {
			sb.append(HTMLUtils.buildMenuNar(model, "Applikation freischalten", true, null, false));
			HTMLTable table = new HTMLTable();
			table.addNewRow();
			table.addTD("Applikations-Passwort: ", 1, null);
			table.addNewRow();
			table.addTDSource(HTMLUtils.buildPasswordField("kvdbpass", "", 20, Condition.SAVE_KVDB_PASSWORD, false), 1, null);
			HTMLUtils.setFocus("kvdbpass", model);
			table.addNewRow();
			table.addTDSource(new Button("OK", Condition.SAVE_KVDB_PASSWORD).printForUseInTable(), 1, null);
			table.addNewRow();
			sb.append(table.buildTable(model));
		} else {
			String pass = StringUtils.trim(parameters.get("kvdbpass"));
			boolean successful = KVMemoryMap.getInstance().setPasswordForCryptoEntrys(pass);
			if (successful) {
				model.lookupConversation().setForwardCondition(Condition.FS_NAVIGATE);
			} else {
				model.lookupConversation().getMeldungen().add("Das eingegebene Passwort war nicht korrekt.");
				Security.addCounter(model.getUser());
				model.lookupConversation().setForwardCondition(Condition.ENTER_KVDB_PASSWORD);
			}
		}

		return;
	}

	@Responsible(conditions = { Condition.SSL_NOTICE })
    public void fjSSLHinweis(StringBuilder sb, Map<String, String> parameters, Model model) {

		sb.append(HTMLUtils.buildMenuNar(model, "Files", false, null, false));
		HTMLTable table = new HTMLTable();
		table.addTD("In der produktiven Umgebung ist der Aufruf der Anwendung nur über eine verschlüsselte Verbindung zugelassen.", 1,
				null);
		table.addNewRow();
		sb.append(table.buildTable(model));
		model.lookupConversation().setForwardCondition(null);
		return;
	}

	@Responsible(conditions = { Condition.AUTOLOGIN_FROM_COOKIE })
    public void fjAnmeldungDurchCookie(StringBuilder sb, Map<String, String> parameters, Model model) {

		model.lookupConversation().setForwardCondition(Condition.FS_NAVIGATE);
		return;
	}

	@Responsible(conditions = { Condition.LOGIN })
    public void fjAnmeldung(StringBuilder sb, Map<String, String> parameters, Model model) {

		if (!parameters.containsKey(FORMULAR_FIELD_LOGIN_USER)) {
			model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
			return;
		}

		if (parameters.get("COOKIE_OK").equals("false")) {
			model.lookupConversation().getMeldungen()
					.add("Sie m&uuml;ssen zur Anmeldung zun&auml;chst der Datenschutzerkl&auml;rung zustimmen.");
			model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
			return;
		}

		String user = parameters.get(FORMULAR_FIELD_LOGIN_USER);
		String pass = parameters.get(FORMULAR_FIELD_LOGIN_PASS);

        user = Security.cleanUpKvSubKey(user);

		Security.authenticateUser(model, user, pass, null, parameters);
		if (model.isUserAuthenticated()) {
			model.lookupConversation().setForwardCondition(Condition.FS_NAVIGATE);
		} else {
			model.lookupConversation().setForwardCondition(Condition.LOGIN_FORMULAR);
			model.setDeleteModelAfterRequest(true);
		}
		return;
	}

	@Responsible(conditions = { Condition.LOGOFF })
    public void fjAbmeldungDurchAnwender(StringBuilder sb, Map<String, String> parameters, Model model) {

		model.lookupConversation().getMeldungen().add("Du bist jetzt abgemeldet.");
		Security.logoffUser(model);
		return;
	}

	@Responsible(conditions = { Condition.PASSWORD_ASK_ENCRYPT_SERVER_DIRECT_PASSWORD,
			Condition.PASSWORD_ASK_ENCRYPT_SERVER_HASHED_PASSWORD, Condition.PASSWORD_ASK_ENCRYPT_CLIENT })
    public void fjPasswortEncryptAbfrage(StringBuilder sb, Map<String, String> parameters, Model model) {

		String namePrefix = "";
		String captionSuffix = "";
		Condition targetCondition = null;
		if (model.lookupConversation().getCondition() == Condition.PASSWORD_ASK_ENCRYPT_CLIENT) {
			namePrefix = "__clientpassword__";
			captionSuffix = " (Ende-zu-Ende-Verschlüsselung)";
			targetCondition = Condition.PASSWORD_CHECK_ENCRYPT_CLIENT;
		} else if (model.lookupConversation().getCondition() == Condition.PASSWORD_ASK_ENCRYPT_SERVER_DIRECT_PASSWORD) {
			captionSuffix = " (Punkt-zu-Punkt-Verschlüsselung, veraltet!)";
			targetCondition = Condition.PASSWORD_CHECK_ENCRYPT_SERVER_DIRECT_PASSWORD;
		} else if (model.lookupConversation().getCondition() == Condition.PASSWORD_ASK_ENCRYPT_SERVER_HASHED_PASSWORD) {
			captionSuffix = " (Punkt-zu-Punkt-Verschlüsselung)";
			targetCondition = Condition.PASSWORD_CHECK_ENCRYPT_SERVER_HASHED_PASSWORD;
			namePrefix = "__hashedpassword__";
		}

		sb.append(HTMLUtils.buildMenuNar(model, "Passwort-Abfrage" + captionSuffix, true, null, false));
		HTMLTable table = new HTMLTable();
		table.addTD("Neues Passwort", 1, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTD("Zum Verschlüsseln dieser Datei bitte ein Passwort eingeben.", 1, " align='center'");
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField(namePrefix + "inapp_pass_one", "", 30, null, false), 1, " align='center'");
		HTMLUtils.setFocus(namePrefix + "inapp_pass_one", model);
		table.addNewRow();
		table.addTD("Bestätigung: ", 1, " align='center'");
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField(namePrefix + "inapp_pass_two", "", 30, targetCondition, false), 1,
				" align='center'");
		table.addNewRow();
		table.addTDSource(new Button("Verschlüsseln", targetCondition).printForUseInTable(), 1, " align='center'");
		table.addNewRow();

		sb.append(table.buildTable(model));

		// Parameter fuer naechsten Request retten
		// Ziel-Condition steht in parameters.get(HTMLUtils.CONDITION)
		Base32 base32 = new Base32();
		for (String key : parameters.keySet()) {
			if (StringUtils.isNotEmpty(key)) {
				String valueBase32 = base32.encodeAsString(parameters.get(key).getBytes());
				sb.append(HTMLUtils.buildHiddenField("pass_routing_" + key, valueBase32));
			}
		}
		return;
	}

	@Responsible(conditions = { Condition.PASSWORD_ASK_DECRYPT_SERVER, Condition.PASSWORD_ASK_DECRYPT_CLIENT })
    public void fjPasswortDecryptAbfrage(StringBuilder sb, Map<String, String> parameters, Model model) {

		String namePrefix = "";
		String captionSuffix = "";
		Condition targetCondition = Condition.PASSWORD_CHECK_DECRYPT_SERVER;
		if (model.lookupConversation().getCondition() == Condition.PASSWORD_ASK_DECRYPT_CLIENT) {
			namePrefix = "__clientpassword__";
			captionSuffix = " (Ende-zu-Ende-Verschlüsselung)";
			targetCondition = Condition.PASSWORD_CHECK_DECRYPT_CLIENT;
		} else if (model.lookupConversation().getEditingFile().isServerCryptedDirectPassword()) {
			captionSuffix = " (Punkt-zu-Punkt-Verschlüsselung, veraltet!)";
		} else {
			namePrefix = "__hashedpassword__";
			captionSuffix = " (Punkt-zu-Punkt-Verschlüsselung)";
		}

		if (model.lookupConversation().getEditingFile().isServerBaseCrypted()) {
			Condition forward = Condition.valueOf(parameters.get(HTMLUtils.CONDITION));
			model.lookupConversation().setForwardCondition(forward);
		} else {
			sb.append(HTMLUtils.buildMenuNar(model, "Passwort-Abfrage" + captionSuffix, true, null, false));
			HTMLTable table = new HTMLTable();
			table.addTD(model.lookupConversation().getEditingFile().dateiNameKlartext(), 1, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			table.addTD("Diese Datei ist verschlüsselt.", 1, " align='center'");
			table.addNewRow();
			table.addTD("Bitte Passwort eingeben, dann geht's weiter.", 1, " align='center'");
			table.addNewRow();
			table.addTDSource(HTMLUtils.buildPasswordField(namePrefix + "inapp_pass_one", "", 30, targetCondition, false), 1,
					" align='center'");
			HTMLUtils.setFocus(namePrefix + "inapp_pass_one", model);
			table.addNewRow();
			table.addTDSource(new Button("Entschlüsseln", targetCondition).printForUseInTable(), 1, " align='center'");
			table.addNewRow();
			sb.append(table.buildTable(model));

			// Parameter fuer naechsten Request retten
			// Ziel-Condition steht in parameters.get(HTMLUtils.CONDITION)
			Base32 base32 = new Base32();
			for (String key : parameters.keySet()) {
				if (StringUtils.isNotEmpty(key)) {
					String valueBase32 = base32.encodeAsString(parameters.get(key).getBytes());
					sb.append(HTMLUtils.buildHiddenField("pass_routing_" + key, valueBase32));
				}
			}
		}

	}

	@Responsible(conditions = { Condition.PASSWORD_CHECK_ENCRYPT_CLIENT })
    public void fjPasswortPruefenEncryptClient(StringBuilder sb, Map<String, String> parameters, Model model) {
		model.lookupConversation().getJavaScriptOnPageLoaded().add("checkClientSideEncryptionPassword();");
	}

	@Responsible(conditions = { Condition.PASSWORD_CHECK_DECRYPT_CLIENT })
    public void fjPasswortPruefenDecryptClient(StringBuilder sb, Map<String, String> parameters, Model model) {

		model.lookupConversation().getEditingFile().setClientKnowsPassword(true);

		// Die via Hidden Fields durchgeschleiften Parameter uebernehmen, sofern nicht vorhanden
		Base32 base32 = new Base32();
		Object[] keys = parameters.keySet().toArray();
		for (Object key : keys) {
			String keyString = (String) key;
			if (StringUtils.startsWith(keyString, "pass_routing_")) {
				String keyOriginal = StringUtils.removeStart(keyString, "pass_routing_");
				// Alle nicht gesetzten Parameter setzen.
				// Damit wird vermieden, dass Sessionvariablen ueberschrieben werden
				// Ausnahme: Die Ziel-Condition (z.B. FILE_VIEW). Diese immer ueberschreiben, sonst geht bei >1 Versuchen der
				// Passwort-Eingabe die Ziel-Condition verloren und nach der Eingabe des korrektes Passworts wissen wir nicht mehr, wohin
				// wir zurueck springen muessen.
				if (!parameters.containsKey(keyOriginal) || StringUtils.equals(keyOriginal, HTMLUtils.CONDITION)) {
					String valueDecoded = new String(base32.decode(parameters.get(keyString)));
					parameters.put(keyOriginal, valueDecoded);
					// Xystem.out.println("durchschleifen rein:" + keyOriginal + " / " + valueDecoded);
				}
				// String valueBase32 = new String(base32.decode(parameters.get(key).getBytes()));
				// sb.append(HTMLUtils.buildHiddenField("pass_routing_" + key, valueBase32));
			}
		}

		Condition forward = Condition.valueOf(new String(base32.decode(parameters.get("pass_routing_" + HTMLUtils.CONDITION).getBytes())));
		model.lookupConversation().setForwardCondition(forward);
	}

	@Responsible(conditions = { Condition.PASSWORD_CHECK_DECRYPT_SERVER, Condition.PASSWORD_CHECK_ENCRYPT_SERVER_DIRECT_PASSWORD,
			Condition.PASSWORD_CHECK_ENCRYPT_SERVER_HASHED_PASSWORD })
    public void fjPasswortPruefenServer(StringBuilder sb, Map<String, String> parameters, Model model) {

		// Die via Hidden Fields durchgeschleiften Parameter uebernehmen, sofern nicht vorhanden
		Base32 base32 = new Base32();
		Object[] keys = parameters.keySet().toArray();
		for (Object key : keys) {
			String keyString = (String) key;
			if (StringUtils.startsWith(keyString, "pass_routing_")) {
				String keyOriginal = StringUtils.removeStart(keyString, "pass_routing_");
				// Alle nicht gesetzten Parameter setzen.
				// Damit wird vermieden, dass Sessionvariablen ueberschrieben werden
				// Ausnahme: Die Ziel-Condition (z.B. FILE_VIEW). Diese immer ueberschreiben, sonst geht bei >1 Versuchen der
				// Passwort-Eingabe die Ziel-Condition verloren und nach der Eingabe des korrektes Passworts wissen wir nicht mehr, wohin
				// wir zurueck springen muessen.
				if (!parameters.containsKey(keyOriginal) || StringUtils.equals(keyOriginal, HTMLUtils.CONDITION)) {
					String valueDecoded = new String(base32.decode(parameters.get(keyString)));
					parameters.put(keyOriginal, valueDecoded);
				}
			}
		}

		String pass1 = null;
		String pass2 = null;
		for (String key : parameters.keySet()) {
			if (StringUtils.contains(key, "inapp_pass_one")) {
				pass1 = parameters.get(key);
			}
			if (StringUtils.contains(key, "inapp_pass_two")) {
				pass2 = parameters.get(key);
			}
		}

		if (model.lookupConversation().getCondition().equals(Condition.PASSWORD_CHECK_ENCRYPT_SERVER_DIRECT_PASSWORD)) {
			// verschluesseln
			if (StringUtils.isNotEmpty(pass1) && StringUtils.equals(pass1, pass2)) {
				// Neues Passwort ist gueltig
				model.lookupConversation().getEditingFile().prospectivePassword(pass1);
			} else {
				model.lookupConversation().getMeldungen().add("Die Passwörter waren nicht gleich oder leer. Bitte nochmal eingeben.");
				// Zurueck zur Eingabe
				model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_ENCRYPT_SERVER_DIRECT_PASSWORD);
				return;
			}
		} else if (model.lookupConversation().getCondition().equals(Condition.PASSWORD_CHECK_ENCRYPT_SERVER_HASHED_PASSWORD)) {
			// verschluesseln
			if (StringUtils.isNotEmpty(pass1) && StringUtils.equals(pass1, pass2)) {
				// Neues Passwort ist gueltig
				model.lookupConversation().getEditingFile().prospectivePassword(pass1);
			} else {
				model.lookupConversation().getMeldungen().add("Die Passwörter waren nicht gleich oder leer. Bitte nochmal eingeben.");
				// Zurueck zur Eingabe
				model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_ENCRYPT_SERVER_HASHED_PASSWORD);
				return;
			}
		} else {
			// entschluesseln
			if (model.lookupConversation().getEditingFile().pendingPassword(pass1)) {
				// eingegebenes Passwort ist richtig bzw kann die Datei entschluesseln
			} else {
				// Zurueck zur Eingabe
				model.lookupConversation().getMeldungen().add("Mit dem eingegebenen Passwort konnte die Datei nicht entschlüsselt werden.");
				Security.addCounter(model.getUser());
				model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_SERVER);
				return;
			}
		}
		// Wenn wir bis hier gekommen sind, ist/sind die Passwoerter richtig und es kann zur eigentlichen Ziel-Condition weitergeleitet
		// werden.
		Condition forward = Condition.valueOf(new String(base32.decode(parameters.get("pass_routing_" + HTMLUtils.CONDITION).getBytes())));
		model.lookupConversation().setForwardCondition(forward);
		return;
	}

	@Responsible(conditions = { Condition.FS_EDIT_FILE_AFTER_RESET_CLIENT_PW, Condition.FS_VIEW_FILE_AFTER_RESET_CLIENT_PW,
			Condition.IMAGE_VIEW_FULLSCREEN_AFTER_RESET_CLIENT_PW, Condition.IMAGE_VIEW_WITH_MENU_AFTER_RESET_CLIENT_PW,
			Condition.FILE_DOWNLOAD_DECRYPTED_AFTER_RESET_CLIENT_PW })
    public void zurueckNachClientPasswortReset(StringBuilder sb, Map<String, String> parameters, Model model) {

		if (model.lookupConversation().isOriginalRequestCondition()) {
			model.lookupConversation().getEditingFile().setClientKnowsPassword(false);
			model.lookupConversation().getMeldungen().add("Mit dem eingegebenen Passwort konnte die Datei nicht entschlüsselt werden.");
			Security.addCounter(model.getUser());
		}
		switch (model.lookupConversation().getCondition()) {
		case FS_EDIT_FILE_AFTER_RESET_CLIENT_PW:
			model.lookupConversation().setForwardCondition(Condition.FS_EDIT_FILE);
			break;
		case FS_VIEW_FILE_AFTER_RESET_CLIENT_PW:
			model.lookupConversation().setForwardCondition(Condition.FS_VIEW_FILE);
			break;
		case IMAGE_VIEW_FULLSCREEN_AFTER_RESET_CLIENT_PW:
			model.lookupConversation().setForwardCondition(Condition.IMAGE_VIEW_FULLSCREEN);
			break;
		case IMAGE_VIEW_WITH_MENU_AFTER_RESET_CLIENT_PW:
			model.lookupConversation().setForwardCondition(Condition.IMAGE_VIEW_WITH_MENU);
			break;
		case FILE_DOWNLOAD_DECRYPTED_AFTER_RESET_CLIENT_PW:
			model.lookupConversation().setForwardCondition(Condition.FILE_DOWNLOAD_DECRYPTED);
			break;
		default:
			throw new IllegalArgumentException("Weiterleitung nicht moeglich fuer: " + model.lookupConversation().getCondition());
		}
	}

	@Responsible(conditions = { Condition.FILE_DOWNLOAD_ORIGINAL, Condition.FILE_DOWNLOAD_DECRYPTED })
    public void fjDownload(StringBuilder sb, Map<String, String> parameters, Model model) {

		if (model.lookupConversation().getCondition().equals(Condition.FILE_DOWNLOAD_DECRYPTED)
				&& !model.lookupConversation().getEditingFile().isReadable()) {
			// passwort benoetigt? Dann erstmal zur Passwortmaske weiterleiten
			if (model.lookupConversation().getEditingFile().isClientCrypted()) {
				if (!model.lookupConversation().getEditingFile().isClientKnowsPassword()) {
					model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_CLIENT);
					return;
				}
			} else {
				model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_SERVER);
				return;
			}
		} else if (model.lookupConversation().getCondition().equals(Condition.FILE_DOWNLOAD_ORIGINAL)) {
			model.lookupConversation().getEditingFile().forgetPasswords();
		}

		String headerText = "";
		ButtonBar buttonBar = null;
		if (model.lookupConversation().getEditingFile().isServerCryptedDirectPassword()
				|| model.lookupConversation().getEditingFile().isServerCryptedHashedPassword()
				|| model.lookupConversation().getEditingFile().isClientCrypted()) {
			buttonBar = new ButtonBar();
			if (model.lookupConversation().getCondition().equals(Condition.FILE_DOWNLOAD_ORIGINAL)) {
				headerText = "Datei verschlüsselt herunterladen";
				buttonBar.getButtons().add(new Button("Entschlüsselt laden", Condition.FILE_DOWNLOAD_DECRYPTED));
			} else {
				headerText = "Datei entschlüsselt herunterladen";
				buttonBar.getButtons().add(new Button("Verschlüsselt laden", Condition.FILE_DOWNLOAD_ORIGINAL));
			}
		} else {
			headerText = "Datei herunterladen";
		}

		sb.append(HTMLUtils.buildMenuNar(model, headerText, Condition.FS_NAVIGATE, buttonBar, false));

		String dateiname = model.lookupConversation().getEditingFile().dateiNameKlartext();
		String url = HTMLUtils.buildDownloadURLForDirectAttachmentDownload(model, model.lookupConversation().getEditingFile());
		String mimeType = ServletHelper.getMimeTypeForFileSuffix(model.lookupConversation().getEditingFile().dateinamenSuffix());

		HTMLTable table = new HTMLTable();
		table.addTD(dateiname, 1, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		String name = "Herunterladen starten";
		if (model.lookupConversation().getEditingFile().isClientCrypted()
				&& model.lookupConversation().getCondition().equals(Condition.FILE_DOWNLOAD_DECRYPTED)) {
			table.addTDSource(
					new Button(name,
							"javascript:downloadClientCryptedFile('dl', '" + url + "', '" + dateiname + "', '" + mimeType + "', '"
									+ Condition.FILE_DOWNLOAD_DECRYPTED_AFTER_RESET_CLIENT_PW.name() + "' );",
							true).printForUseInTable(),
					1, null);
		} else {
			table.addTDSource(new Button(name, url, true).printForUseInTable(), 1, null);
		}
		table.addNewRow();
		sb.append(table.buildTable(model));
		sb.append("<a id=\"dl\" target=\"_blank\"></a>");

		return;
	}

	@Responsible(conditions = { Condition.SYS_FJ_OPTIONS })
    public void fjSystemOptionen(StringBuilder sb, Map<String, String> parameters, Model model) {

		Condition backCondition = null;
		if (model.lookupConversation().getStepBackCondition() != null && model.lookupConversation().getEditingFile() != null) {
			backCondition = model.lookupConversation().getStepBackCondition();
		} else {
			backCondition = Condition.FS_NAVIGATE;
		}

		sb.append(HTMLUtils.buildMenuNar(model, "Anwendung", backCondition, null, false));

		HTMLTable table = new HTMLTable();

		if (model.isPhone()) {
			table.addTD("Allgemein", 3, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			table.addTDSource(new Button("Neues Fenster", "/", true).printForUseInTable(), 3, null);
			table.addNewRow();
			table.addTDSource(new Button(model.getUser() + " abmelden", Condition.LOGOFF).printForUseInTable(), 3, null);
			table.addNewRow();
		}

		table.addTD("Einstellungen", 3, HTMLTable.TABLE_HEADER);
		table.addNewRow();

		table.addTDSource(HTMLUtils.buildCheckBox("Zeilennummern", "LINE_NUMBERS", model.lookupConversation().isTextViewNumbers(),
				Condition.FS_NUMBERS_TEXTVIEW), 3, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildCheckBox("Datei Details", "FILE_DETAILS", model.lookupConversation().isFilesystemViewDetails(),
				Condition.FS_SWITCH_VIEW_DETAILS), 3, null);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildCheckBox("Push Nachrichten", "PUSH", model.lookupConversation().isTextViewPush(),
				Condition.FS_PUSH_TEXTVIEW), 3, null);
		table.addNewRow();

		table.addTD("Verzeichnisse", 3, HTMLTable.TABLE_HEADER);
		table.addNewRow();

		List<String> fav = new LinkedList<String>();
		fav.addAll(model.getFavoriteFolders());
		for (String ber : model.getVerzeichnisBerechtigungen()) {
			if (!fav.contains(ber)) {
				fav.add(ber);
			}
		}
		table.addTDSource(HTMLUtils.buildDropDownListe("gotoFolder", fav, null), 3, " align='center'");
		table.addNewRow();
		table.addTDSource(new Button("wechseln", Condition.FS_GOTO).printForUseInTable(), 3, HTMLTable.NO_BORDER);
		table.addNewRow();
		table.addTD("Anwendungen", 3, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTDSource(new Button("System-Info", Condition.SYS_SYSTEM_INFO).printForUseInTable(), 3, null);
		table.addNewRow();
		table.addTDSource(
				new Button("Lizenzen anzeigen / View Licence Attribution", Condition.VIEW_LICENCE_ATTRIBUTION).printForUseInTable(), 3,
				null);
		table.addNewRow();
        boolean admin = StringUtils.equalsIgnoreCase(
            KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + model.getUser() + ".isAdmin"),
				Boolean.toString(true));
		if (admin) {
			table.addTDSource(new Button("Backups", Condition.BACKUPS_START).printForUseInTable(), 3, null);
			table.addNewRow();
			table.addTDSource(new Button("KVDB Editor", Condition.KVDB_EDIT_START).printForUseInTable(), 3, null);
			table.addNewRow();
			table.addTDSource(new Button("Benutzer freischalten", Condition.NEW_USERS).printForUseInTable(), 3, null);
			table.addNewRow();
		}

		sb.append(table.buildTable(model));
		return;
	}

	@Responsible(conditions = { Condition.NEW_USERS })
    public void fjNewUsers(StringBuilder sb, Map<String, String> parameters, Model model) {

		sb.append(HTMLUtils.buildMenuNar(model, "Neue Benutzer freischalten", Condition.FS_NAVIGATE, null, false));
		HTMLTable table = new HTMLTable();
		List<String> newUsers = new LinkedList<>();
		newUsers.add("");

        List<String[]> listWithPartKey = KVMemoryMap.getInstance().readListWithPartKey(KVMemoryMap.KVDB_USER_IDENTIFIER);
		for (String[] strings : listWithPartKey) {
			if (!strings[0].contains(".") && strings[1].trim().equalsIgnoreCase(Boolean.FALSE.toString())) {
				newUsers.add(strings[0].trim());
			}
		}

		table.addTDSource(HTMLUtils.buildDropDownListe("newUser", newUsers, null), 3, " align='center'");
		table.addNewRow();
		table.addTDSource(new Button("freischalten", Condition.UNLOCK_NEW_USER).printForUseInTable(), 3, HTMLTable.NO_BORDER);
		table.addNewRow();

		sb.append(table.buildTable(model));
		return;
	}

	@Responsible(conditions = { Condition.UNLOCK_NEW_USER })
    public void fjUnlockNewUser(StringBuilder sb, Map<String, String> parameters, Model model) {

		sb.append(HTMLUtils.buildMenuNar(model, "Freischaltung", Condition.FS_NAVIGATE, null, false));
        String user = Security.cleanUpKvSubKey(parameters.get("newUser"));
		HTMLTable table = new HTMLTable();
		table.addTD("Benutzername = " + user, null);
		table.addNewRow();

		if (StringUtils.isBlank(user)) {
			model.lookupConversation().getMeldungen().add("Bitte Benutzer auswählen!");
			model.lookupConversation().setForwardCondition(Condition.NEW_USERS);
			return;
		}

		File userdir = new File(KVMemoryMap.getInstance().readValueFromKey("application.userBaseDir") + "/" + user);
		if (userdir.exists()) {
			table.addTD("Fehler! Benutzerverzeichnis besteht bereits!", null);
			table.addNewRow();
		} else {
            boolean kv1 = KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".allowedDirectory",
                userdir.getAbsolutePath(), false);
            boolean kv2 = KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".favoriteFolders",
                userdir.getAbsolutePath(), false);
            boolean kv3 = KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".homeDirectory",
                userdir.getAbsolutePath(), false);
			if (kv1 && kv2 && kv3) {
				boolean mkdirs = userdir.mkdirs();
				if (mkdirs) {
					table.addTD("Benutzerverzeichnis = " + userdir.getAbsolutePath(), null);
					table.addNewRow();
                    boolean writeKeyValue = KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.KVDB_USER_IDENTIFIER + user,
                        Boolean.TRUE.toString(), true);
					if (writeKeyValue) {
						table.addTD("Freischaltung erfolgreich!", null);
						table.addNewRow();
					} else {
						table.addTD("Benutzer konnte nicht aktiv geschaltet werden: " + user, null);
						table.addNewRow();
					}
				} else {
					table.addTD("Benutzerverzeichnis konnte nicht erstellt werden! " + userdir.getAbsolutePath(), null);
					table.addNewRow();
				}
			} else {
				table.addTD("Fehler! Kollision mit anderem User!", null);
				table.addNewRow();
			}

		}

		sb.append(table.buildTable(model));
		return;
	}

	@Responsible(conditions = { Condition.SYS_SYSTEM_INFO, Condition.SYS_EXECUTE_JOB })
    public void fjSystemInfo(StringBuilder sb, Map<String, String> parameters, Model model) {

		if (model.lookupConversation().getCondition().equals(Condition.SYS_EXECUTE_JOB)) {
			String executeJob = StringUtils.trimToEmpty(parameters.get("execute_job"));
			for (String schedulerName : CronSchedulers.getInstance().getSchedulersIDs().keySet()) {
				if (StringUtils.equalsIgnoreCase(schedulerName, executeJob)) {
					Runnable r = CronSchedulers.getInstance().getSchedulersInstances().get(schedulerName);
					r.run();
					model.lookupConversation().getMeldungen().add(r.getClass().getName() + " wurde gestartet");
					break;
				}
			}
		}

		String builddate = KVMemoryMap.getInstance().readValueFromKey("application.builddate");
        if (StringUtils.isBlank(builddate)) {
            builddate = "nicht bekannt";
		}

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Reload", Condition.SYS_SYSTEM_INFO));
		sb.append(HTMLUtils.buildMenuNar(model, "Systeminformationen", Condition.FS_NAVIGATE, buttonBar, false));

		String attributeLeftCol = model.isPhone() ? HTMLUtils.buildAttribute("width", "40%") : null;

		HTMLTable table = new HTMLTable();

		table.addTD("Anwendungsvariablen:", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTD("Build:", 1, null);
		table.addTD(builddate, 1, null);
		table.addNewRow();
		table.addTD("App Uptime:", 1, null);
		table.addTD(KVMemoryMap.getInstance().readValueFromKey("application.uptime"), 1, null);
		table.addNewRow();
		table.addTD("Zwischenablage:", 1, attributeLeftCol);
		String clip;
		if (model.getZwischenablage() != null) {
			clip = HTMLUtils.spacifyFilePath(model.getZwischenablage(), model);
		} else {
			clip = "[ leer ]";
		}
		table.addTD(clip, 1, null);
		table.addNewRow();
		table.addTD("Session:", 1, null);
		table.addTD(StringUtils.left(model.getSessionID(), 10), 1, null);
		table.addNewRow();
		table.addTD("Login:", 1, null);
		table.addTD(StringUtils.left(model.getLoginCookieID().getValue(), 10), 1, null);
		table.addNewRow();
		if (model.lookupConversation().getCookiesReadFromRequest() != null && model.isDevelopmentMode()) {
			for (Cookie cookieReadFromRequest : model.lookupConversation().getCookiesReadFromRequest()) {
				String cookieName = cookieReadFromRequest.getName();
				String cookieValue = cookieReadFromRequest.getValue();
				table.addTD("Cookie (Request):", 1, null);
				table.addTD(cookieName, 1, null);
				table.addNewRow();
				table.addTD("", 1, null);
				table.addTD(StringUtils.left(cookieValue, 10), 1, null);
				table.addNewRow();
			}
		}
		table.addTD("Conversation ID:", 1, null);
		table.addTD(model.lookupConversation().getConversationID().toString(), 1, null);
		table.addNewRow();
		table.addTD("Touch / Phone / Tablet:", 1, null);
		table.addTD(Boolean.toString(model.isClientTouchDevice()) + " / " + Boolean.toString(model.isPhone()) + " / "
				+ Boolean.toString(model.isTablet()), 1, null);
		table.addNewRow();
		table.addTD("Ist FullScreen:", 1, null);
		table.addTD(Boolean.toString(model.isIstWebApp()), 1, null);
		table.addNewRow();

		if (model.isPhone()) {
			sb.append(table.buildTable(model));
			table = new HTMLTable();
		}

		table.addTD("Java", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTD("total / max memory:", 1, attributeLeftCol);
		table.addTD(DateiZugriff.speicherGroesseFormatieren(Runtime.getRuntime().totalMemory()) + " / "
				+ DateiZugriff.speicherGroesseFormatieren(Runtime.getRuntime().maxMemory()), 1, null);
		table.addNewRow();
		table.addTD("strengthCrypto:", 1, null);
		table.addTD(KVMemoryMap.getInstance().readValueFromKey("application.unlimitedStrengthCryptoEnabled"), 1, null);
		table.addNewRow();
		table.addTD("freeMemory:", 1, null);
		table.addTD(DateiZugriff.speicherGroesseFormatieren(Runtime.getRuntime().freeMemory()), 1, null);
		table.addNewRow();
		table.addTD("catalina.base:", 1, null);
		table.addTD(HTMLUtils.spacifyFilePath(System.getProperties().getProperty(ServletHelper.SYSTEM_PROPERTY_CATALINA_BASE), model), 1,
				null);
		table.addNewRow();

		if (model.isPhone()) {
			sb.append(table.buildTable(model));
			table = new HTMLTable();
		}

		table.addTD("Hardware / Software", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTD("OS:", 1, attributeLeftCol);
		table.addTD(System.getProperty("os.name") + " " + System.getProperty("os.version"), 1, null);
		table.addNewRow();
		table.addTD("OS Uptime:", 1, null);
		table.addTD(ApplicationUtil.getSystemUptime(), 1, null);
		table.addNewRow();
		table.addTD("CPU Cores:", 1, attributeLeftCol);
		table.addTD(Runtime.getRuntime().availableProcessors() + "", 1, null);
		table.addNewRow();
		table.addTD("Architecture:", 1, null);
		table.addTD(System.getProperty("sun.arch.data.model", "") + " bit", 1, null);
		table.addNewRow();
		table.addTD("Systemzeit:", 1, null);
		table.addTD(Hilfsklasse.zeitstempelAlsString(), 1, null);
		table.addNewRow();
		table.addTD("Java Version:", 1, null);
		table.addTD(System.getProperty("java.version", ""), 1, null);
		table.addNewRow();
		table.addTD("Java VM:", 1, null);
		table.addTD(System.getProperty("java.vm.name", ""), 1, null);
		table.addNewRow();
		table.addTD("Server:", 1, null);
		table.addTD(KVMemoryMap.getInstance().readValueFromKey("application.server"), 1, null);
		table.addNewRow();

		if (model.isPhone()) {
			sb.append(table.buildTable(model));
			table = new HTMLTable();
		}

		table.addTD("Cron Jobs", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();

		int a = 0;
		List<String> jobs = new LinkedList<String>();
		jobs.add("");
		for (String schedulerName : CronSchedulers.getInstance().getSchedulersIDs().keySet()) {
			jobs.add(schedulerName);
			String cssClass = a % 2 != 0 ? attributeLeftCol : HTMLUtils.buildAttribute("class", "alt");
			Scheduler s = CronSchedulers.getInstance().getSchedulers().get(schedulerName);
			table.addTD(schedulerName, 1, cssClass);
			table.addTD(((s != null && s.isStarted()) ? "@ " : "not running")
					+ CronSchedulers.getInstance().lookupCronStringOfScheduler(schedulerName), 1, cssClass);
			table.addNewRow();
			table.addTD("", 1, cssClass);
			table.addTD(CronSchedulers.getInstance().getSchedulersInstances().get(schedulerName).status(), 1, cssClass);
			table.addNewRow();
			a++;
		}

		if (model.isPhone()) {
			sb.append(table.buildTable(model));
			table = new HTMLTable();
		}

		table.addTD("Manueller Job-Start", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildDropDownListe("execute_job", jobs, null), 2, null);
		table.addNewRow();
		String buttonJobStart = new Button("Job starten", Condition.SYS_EXECUTE_JOB).printForUseInTable();
		table.addTDSource(buttonJobStart, 2, HTMLTable.NO_BORDER);
		table.addNewRow();

		sb.append(table.buildTable(model));
		return;
	}

	@Responsible(conditions = { Condition.VIEW_LICENCE_ATTRIBUTION })
    public void fjLizenzbedingungenAnzeigen(StringBuilder sb, Map<String, String> parameters, Model model) {

		Condition back;
		if (model.lookupConversation().getStepBackCondition() != null) {
			back = model.lookupConversation().getStepBackCondition();
		} else {
			if (model.getUser() != null) {
				back = Condition.FS_NAVIGATE;
			} else {
				back = Condition.LOGIN;
			}
		}

		sb.append(HTMLUtils.buildMenuNar(model, "Lizenzen anzeigen / View Licence Attribution", back, null, true));

		try {
			String licence = "";
			URL url = this.getClass().getClassLoader().getResource("licenseattribution.txt");
			URLConnection resConn = url.openConnection();
			resConn.setUseCaches(false);
			InputStream in = resConn.getInputStream();
			StringWriter writer = new StringWriter();
			IOUtils.copy(in, writer, "UTF-8");
			licence = writer.toString();
			buildTextviewTable("Lizenzen anzeigen / View Licence Attribution", sb, model, licence,
					model.lookupConversation().isTextViewNumbers(), true, true);

		} catch (IOException e) {
			logger.error("Error loading Resource licenseattribution.txt: ", e);
		}

	}
}
