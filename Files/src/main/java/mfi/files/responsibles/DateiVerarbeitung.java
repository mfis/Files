package mfi.files.responsibles;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import mfi.files.annotation.Responsible;
import mfi.files.helper.ServletHelper;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.htmlgen.KeyCodes;
import mfi.files.io.FilesFile;
import mfi.files.logic.AjaxFillIn;
import mfi.files.logic.DateiBackup;
import mfi.files.logic.DateiZugriff;
import mfi.files.logic.Security;
import mfi.files.model.Condition;
import mfi.files.model.Model;

public class DateiVerarbeitung extends AbstractResponsible {

	@Responsible(conditions = { Condition.FS_VIEW_OPTIONS })
	public void fjFileSystemOptionen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		int breiteEingabefeld = model.isPhone() ? 30 : 50;

		Condition backCondition = null;
		if (model.lookupConversation().getStepBackCondition() != null && model.lookupConversation().getEditingFile() != null) {
			backCondition = model.lookupConversation().getStepBackCondition();
		} else {
			backCondition = Condition.FS_CANCEL_EDITED_FILE;
		}

		if (model.lookupConversation().getEditingFile().isDirectory()) {
			sb.append(HTMLUtils.buildMenuNar(model, "Verzeichnis-Eigenschaften", backCondition, null, false));
		} else {
			sb.append(HTMLUtils.buildMenuNar(model, "Datei-Eigenschaften", backCondition, null, false));
		}

		HTMLTable table = new HTMLTable();
		table.addTD(HTMLUtils.spacifyFilePath(model.lookupConversation().getEditingFile().dateiNameUndPfadKlartext(), model), 2,
				HTMLTable.TABLE_HEADER);
		table.addNewRow();
		if (model.lookupConversation().getEditingFile().isDirectory() && ServletHelper.lookupUseAjax()) {
			table.addTDSource(HTMLUtils.buildAjaxFillInText(AjaxFillIn.DIR_SIZE_EDITING, null, false, model, null), 2, null);
		} else {
			table.addTD("Größe: " + DateiZugriff.fileGroesseFormatieren(model.lookupConversation().getEditingFile()), 2, null);
		}

		table.addNewRow();
		boolean backup = DateiBackup.isInBackupPfad(model.lookupConversation().getEditingFile());
		table.addTD("Backup: " + (backup ? "Ja" : "Nein"), 2, null);
		table.addNewRow();

		String geaendertVon = "";
		if (DateiZugriff.leseDateiAenderer(model.lookupConversation().getEditingFile(), model) != null) {
			geaendertVon = " von " + DateiZugriff.leseDateiAenderer(model.lookupConversation().getEditingFile(), model);
		}

		table.addTD("Zuletzt geändert: " + DateiZugriff.formatLastModifiedDateAsString(model.lookupConversation().getEditingFile(), false)
				+ geaendertVon + ".", 2, null);
		table.addNewRow();

		if (!model.lookupConversation().getEditingFile().isDirectory()
				&& (model.lookupConversation().getEditingFile().isServerCryptedDirectPassword()
						|| model.lookupConversation().getEditingFile().isServerCryptedHashedPassword())) {
			if (model.lookupConversation().getEditingFile().isServerCryptedHashedPassword()) {
				table.addTD("Die Datei ist Punkt-zu-Punkt-verschlüsselt.", 2, null);
			} else {
				table.addTD("Die Datei ist Punkt-zu-Punkt-verschlüsselt (Veraltertes Verfahren!).", 2, null);
			}
			table.addNewRow();
			table.addTD("Chiffre: " + model.lookupConversation().getEditingFile().getChiffre(), 2, null);
			table.addNewRow();
		}

		if (!model.lookupConversation().getEditingFile().isDirectory() && model.lookupConversation().getEditingFile().isClientCrypted()) {
			table.addTD("Die Datei ist Ende-zu-Ende-verschlüsselt.", 2, null);
			table.addNewRow();
			table.addTD("Chiffre: " + model.lookupConversation().getEditingFile().getChiffre(), 2, null);
			table.addNewRow();
		}

		table.addTD("Umbenennen / Kopieren", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildTextField("rename_copy", model.lookupConversation().getEditingFile().dateiNameKlartext(),
				breiteEingabefeld, null), 2, " align='center'");
		table.addNewRow();
		String umbenennen = new Button("Umbenennen", Condition.FS_RENAME).printForUseInTable();
		String kopieren = "";
		if (model.lookupConversation().getEditingFile().isFile()) {
			kopieren = new Button("Kopieren", Condition.FS_COPY_FILE_SAME_DIR).printForUseInTable();
		}
		table.addTDSource(umbenennen + kopieren, 2, HTMLTable.NO_BORDER);
		table.addNewRow();
		if (model.lookupConversation().getEditingFile().isDirectory()) {
			table.addTD("Neu...", 2, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			table.addTDSource(HTMLUtils.buildTextField("new_file_folder_name", "", breiteEingabefeld, null), 2, " align='center'");
			table.addNewRow();
			String neuVerzeichnis = new Button("Neues Verzeichnis", Condition.FS_NEW_FOLDER).printForUseInTable();
			String neuDatei = new Button("Neue Datei", Condition.FS_NEW_FILE).printForUseInTable();
			table.addTDSource(neuVerzeichnis + neuDatei, 2, HTMLTable.NO_BORDER);
			table.addNewRow();
			String upload = new Button("Dateien hochladen", Condition.FILE_UPLOAD).printForUseInTable();
			table.addTDSource(upload, 2, HTMLTable.NO_BORDER);
			table.addNewRow();
		}
		if (model.lookupConversation().getEditingFile().isFile()) {
			table.addTD("Datei...", 2, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			String loeschen = new Button("Löschen", Condition.FS_DELETE_FILE, true).printForUseInTable();
			String clipboard = new Button("In die Zwischenablage", Condition.FS_TO_CLIPBOARD).printForUseInTable();
			table.addTDSource(loeschen + clipboard, 2, HTMLTable.NO_BORDER);
			table.addNewRow();

			String crypto;
			if (model.lookupConversation().getEditingFile().isServerCryptedDirectPassword()
					|| model.lookupConversation().getEditingFile().isServerCryptedHashedPassword()) {
				crypto = new Button("Entschlüsseln", Condition.FS_FILE_DECRYPT_SERVER, true).printForUseInTable();
				table.addTDSource(crypto, 2, HTMLTable.NO_BORDER);
				table.addNewRow();
				if (model.lookupConversation().getEditingFile().isSupportingClientCrypto()) {
					crypto = new Button("Ende-zu-Ende-verschlüsseln", Condition.PASSWORD_ASK_ENCRYPT_CLIENT, true).printForUseInTable();
					table.addTDSource(crypto, 2, HTMLTable.NO_BORDER);
					table.addNewRow();
				}
			} else if (model.lookupConversation().getEditingFile().isClientCrypted()) {
				// noop
			} else {
				if (model.lookupConversation().getEditingFile().isSupportingClientCrypto()) {
					crypto = new Button("Ende-zu-Ende-verschlüsseln", Condition.PASSWORD_ASK_ENCRYPT_CLIENT, true).printForUseInTable();
				} else {
					crypto = new Button("Punkt-zu-Punkt-verschlüsseln", Condition.FS_FILE_ENCRYPT_SERVER, true).printForUseInTable();
				}
				table.addTDSource(crypto, 2, HTMLTable.NO_BORDER);
				table.addNewRow();
			}
			String download = new Button("Herunterladen", Condition.FILE_DOWNLOAD_ORIGINAL, false).printForUseInTable();
			table.addTDSource(download, 2, HTMLTable.NO_BORDER);
			table.addNewRow();
		}
		if (model.lookupConversation().getEditingFile().isDirectory()) {
			table.addTD("Verzeichnis...", 2, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			String loeschen = new Button("Löschen", Condition.FS_DELETE_FILE, true).printForUseInTable();
			table.addTDSource(loeschen, 2, HTMLTable.NO_BORDER);
			table.addNewRow();
		}
		if (model.lookupConversation().getEditingFile().isDirectory()) {
			if (model.getZwischenablage() != null) {
				table.addTD("Aktuelle Zwischenablage:", 2, HTMLTable.TABLE_HEADER);
				table.addNewRow();
				FilesFile zwAblage = new FilesFile(model.getZwischenablage());
				table.addTD(HTMLUtils.spacifyFilePath(zwAblage.dateiNameUndPfadKlartext(), model), 2, null);
				table.addNewRow();
				String zkopieren = new Button("Hierhin kopieren", Condition.FS_COPY_FILE_CLIP).printForUseInTable();
				String zverschieben = new Button("Hierhin verschieben", Condition.FS_MOVE_FILE_CLIP).printForUseInTable();
				table.addTDSource(zkopieren + zverschieben, 2, HTMLTable.NO_BORDER);
				table.addNewRow();
			}
		}
		if (model.lookupConversation().getEditingFile().isFile() && model.lookupConversation().getEditingFile().isEditableFileType()) {
			table.addTD("Textdatei", 2, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			String anzeigen = new Button("Anzeigen", Condition.FS_VIEW_FILE).printForUseInTable();
			String editieren = new Button("Editieren", Condition.FS_EDIT_FILE).printForUseInTable();
			table.addTDSource(anzeigen + editieren, 2, HTMLTable.NO_BORDER);
			table.addNewRow();
		}
		sb.append(table.buildTable(model));
		return;
	}

	@Responsible(conditions = { Condition.FS_FILE_ENCRYPT_CLIENT_START })
	public void fjVerschluesselnClientStart(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (!model.lookupConversation().getEditingFile().isReadable()) {
			// passwort benoetigt? Dann erstmal zur Passwortmaske weiterleiten
			model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_SERVER);
			return;
		}

		sb.append(HTMLUtils.buildHiddenField("filenameForEncrypt", model.lookupConversation().getEditingFile().dateiNameKlartext()));

		String content = model.lookupConversation().getEditingFile().readIntoBase64String();
		model.lookupConversation().setFileContent(content);
		model.lookupConversation().getJavaScriptOnPageLoaded().add("encryptFileContent();");

		FilesFile renamedFile = new FilesFile(model.lookupConversation().getEditingFile().getAbsolutePath());
		boolean renameOK = model.lookupConversation().getEditingFile().renameTo(renamedFile);
		if (!renameOK) {
			model.lookupConversation().getMeldungen().add("Ursprungsdatei konnte nicht gelöscht werden.");
		}
		return;
	}

	@Responsible(conditions = { Condition.FS_FILE_ENCRYPT_CLIENT_END })
	public void fjVerschluesselnClientEnde(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		FilesFile cryptoFileNameForFile = FilesFile.lookupClientCryptoFileNameForFile(
				new FilesFile(model.lookupConversation().getEditingFile().dateiNameUndPfadKlartext()));

		if (cryptoFileNameForFile.exists()) {
			new FilesFile(model.lookupConversation().getEditingFile().getAbsolutePath()).delete();
			model.lookupConversation().getEditingFile().delete();
		}

		model.lookupConversation().setEditingFile(null);
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_FILE_ENCRYPT_SERVER })
	public void fjVerschluesselnServer(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (model.lookupConversation().getEditingFile().isPasswordPending()) {
			FilesFile newFile = model.lookupConversation().getEditingFile().verschluesseleDateiServerseitigHashedPassword();
			newFile.aktualisiereAenderer(model.getUser());
			newFile.backupFile(model);
			model.lookupConversation().setEditingFile(null);
			model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
			return;
		} else {
			model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_ENCRYPT_SERVER_HASHED_PASSWORD);
			return;
		}
	}

	@Responsible(conditions = { Condition.FS_FILE_DECRYPT_SERVER })
	public void fjEntschluesselnServer(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (model.lookupConversation().getEditingFile().isReadable()) {
			FilesFile newFile = model.lookupConversation().getEditingFile().entschluesseleDateiServerseitig();
			newFile.aktualisiereAenderer(model.getUser());
			newFile.backupFile(model);
			model.lookupConversation().setEditingFile(null);
			model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
			return;
		} else {
			model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_SERVER);
			return;
		}
	}

	@Responsible(conditions = { Condition.FS_COPY_FILE_CLIP, Condition.FS_MOVE_FILE_CLIP })
	public void fjZwischenablageVerarbeiten(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		FilesFile processSource = new FilesFile(model.getZwischenablage());
		String processTargetDir = model.lookupConversation().getEditingFile().getAbsolutePath();

		if (model.lookupConversation().getCondition().equals(Condition.FS_MOVE_FILE_CLIP)) {
			FilesFile newFile = processSource.moveFile(model, new FilesFile(processTargetDir), true);
			if (newFile != null) {
				newFile.backupFile(model);
			}
		} else {
			FilesFile newFile = processSource.copyFile(model, new FilesFile(processTargetDir), null, true);
			if (newFile != null) {
				newFile.backupFile(model);
			}
		}

		model.setZwischenablage(null);
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_COPY_FILE_SAME_DIR })
	public void fjDateiKopieren(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		String dateiname = parameters.get("rename_copy");
		FilesFile newFile = model.lookupConversation().getEditingFile().copyFile(model,
				new FilesFile(model.lookupConversation().getEditingFile().getParent()), dateiname, true);
		if (newFile != null) {
			newFile.backupFile(model);
			newFile.aktualisiereAenderer(model.getUser());
		}
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_TO_CLIPBOARD })
	public void fjZwischenablageSetzen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		model.setZwischenablage(model.lookupConversation().getEditingFile().getAbsolutePath());
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_NEW_FOLDER })
	public void fjNeuerOrdner(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (!model.lookupConversation().getEditingFile().isDirectory()) {
			model.lookupConversation().getMeldungen().add("Neues Dateisystemobjekt nur in Verzeichnissen anlegbar!");
		} else {
			String fsObject = parameters.get("new_file_folder_name").trim();
			FilesFile file = new FilesFile(model.lookupConversation().getEditingFile() + FilesFile.separator + fsObject);
			if (file.exists()) {
				model.lookupConversation().getMeldungen().add("Neues Dateisystemobjekt existiert bereits!");
			} else {
				file.mkdir();
				file.aktualisiereAenderer(model.getUser());
			}
		}

		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_NEW_FILE })
	public void fjNeueDatei(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (model.lookupConversation().getEditingFile().isDirectory()) {

			String fsObject = parameters.get("new_file_folder_name").trim();
			FilesFile file = new FilesFile(model.lookupConversation().getEditingFile() + FilesFile.separator + fsObject);
			if (!StringUtils.equals(file.getAbsolutePath(), model.lookupConversation().getEditingFile().getAbsolutePath())
					&& !StringUtils.equals(model.lookupConversation().getEditingFile().getName(), fsObject)) {
				model.lookupConversation().setEditingFile(file);
			}

		}

		if (model.lookupConversation().getEditingFile().exists()) {
			model.lookupConversation().getMeldungen()
					.add("Dateisystemobjekt '" + model.lookupConversation().getEditingFile().getName() + "' existiert bereits!");
		} else {
			model.lookupConversation().getEditingFile().createNewFile();
			model.lookupConversation().getEditingFile().aktualisiereAenderer(model.getUser());
		}
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_RENAME })
	public void fjDateiUmbenennen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		String dateiname = parameters.get("rename_copy");
		if (StringUtils.isEmpty(dateiname)) {
			model.lookupConversation().getMeldungen().add("Der neue Dateiname muss mindestens ein Zeichen lang sein.");
			return;
		}

		FilesFile newFile = new FilesFile(
				model.lookupConversation().getEditingFile().getParent() + FilesFile.separator + dateiname);
		// Condition forward = model.lookupConversation().getEditingFile().isRenameableWithNewName(newFile,
		// Condition.PASSWORD_ASK_ENCRYPT_SERVER, Condition.PASSWORD_ASK_DECRYPT_SERVER);
		// if (forward != null) {
		// model.lookupConversation().setForwardCondition(forward);
		// return;
		// }

		if (model.lookupConversation().getEditingFile().isServerCryptedDirectPassword()) {
			newFile = FilesFile.lookupServerCryptoFileNameForFileDirectPassword(newFile);
		}

		if (model.lookupConversation().getEditingFile().isServerCryptedHashedPassword()) {
			newFile = FilesFile.lookupServerCryptoFileNameForFileHashedPassword(newFile);
		}

		if (model.lookupConversation().getEditingFile().isClientCrypted()) {
			newFile = FilesFile.lookupClientCryptoFileNameForFile(newFile);
		}

		FilesFile oldName = model.lookupConversation().getEditingFile();

		model.lookupConversation().getEditingFile().renameTo(newFile);

		if (new FilesFile(model.lookupConversation().getVerzeichnis()).isSameAs(oldName)) {
			model.lookupConversation().setVerzeichnis(newFile.getAbsolutePath());
		}

		newFile.aktualisiereAenderer(model.getUser());
		newFile.backupFile(model);

		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_DELETE_FILE })
	public void fjDateiLoeschen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		try {
			if (model.lookupConversation().getEditingFile().isDirectory()) {
				FileUtils.deleteDirectory(model.lookupConversation().getEditingFile());
				String fileString = StringUtils.removeEnd(model.lookupConversation().getEditingFile().getAbsolutePath(), "/");
				String verzeichnisString = StringUtils.removeEnd(model.lookupConversation().getVerzeichnis(), "/");
				if (fileString.equals(verzeichnisString)) {
					model.lookupConversation().setVerzeichnis(model.lookupConversation().getEditingFile().getParent());
				}
			} else {
				model.lookupConversation().getEditingFile().deleteQuietly();
			}
		} catch (RuntimeException e) {
			model.lookupConversation().getMeldungen().add(e.getMessage());
		}
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_GOTO })
	public void fjDateiSystemWechselnZu(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		String gotoFolder = parameters.get("gotoFolder").trim();
		model.lookupConversation().setVerzeichnis(gotoFolder);
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_NAVIGATE })
	public void fjFileSystemNavigation(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (model.lookupConversation().getEditingFile() != null) {
			if (model.lookupConversation().getEditingFile().isDirectory()) {
				model.lookupConversation().setVerzeichnis(model.lookupConversation().getEditingFile().getAbsolutePath());
			} else {
				model.lookupConversation().setVerzeichnis(model.lookupConversation().getEditingFile().getParent());
			}
		}
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_SWITCH_VIEW_DETAILS })
	public void fjPushSetzen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		model.lookupConversation().setFilesystemViewDetails(!model.lookupConversation().isFilesystemViewDetails());
		model.lookupConversation().setForwardCondition(Condition.SYS_FJ_OPTIONS);
		return;
	}

	@Responsible(conditions = { Condition.FS_FILE_LISTING })
	public void fjFileSystemAnzeigen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		sb.append(HTMLUtils.buildMenuNar(model, "Dateisystem", false, null, false));

		boolean details = false;
		if (!model.isPhone()) {
			details = model.lookupConversation().isFilesystemViewDetails();
		}

		HTMLTable table = new HTMLTable();
		model.lookupConversation().setFsListe(DateiZugriff.lesePfadListe(model));
		int j = 0;
		fileSystemHeader(model, details, table);
		int rowID = 0;
		for (FilesFile file : model.lookupConversation().getFsListe()) {
			if (file != null && !file.isHidden() && !file.isParentOf(model.lookupConversation().getVerzeichnis())) {
				fileSystemObject(model, details, table, j, rowID, model.lookupConversation().getVerzeichnis() == null, file);
				rowID++;
			}
			j++;
		}

		if (rowID == 0) {
			table.addTDSource(HTMLUtils.buildA(null, "dark", "Keine Dateien vorhanden"), 2, null);
			table.addNewRow();
		}

		String tableString = HTMLUtils.buildElement(table.buildTable(model));
		sb.append(tableString);

		if (!model.isClientTouchDevice() && rowID > 0) {
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.UP.methodDeclarator(), "rowNavi('up', " + (rowID - 1) + ");");
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.DOWN.methodDeclarator(), "rowNavi('down', " + (rowID - 1) + ");");
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.ENTER.methodDeclarator(), "rowNavi('exe', " + (rowID - 1) + ");");
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.LEFT.methodDeclarator(), "rowNavi('left', " + (rowID - 1) + ");");
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.RIGHT.methodDeclarator(), "rowNavi('right', " + (rowID - 1) + ");");
		}
		return;
	}

	private void fileSystemHeader(Model model, boolean details, HTMLTable table) {

		List<String> iconList = new LinkedList<String>();
		String textClassContrast = "dark";

		if (model.lookupConversation().getVerzeichnis() == null) {

			String path = HTMLUtils.buildSpan(null, null, "Übersicht");
			String td2 = "";

			fileSystemRow(model, table, HTMLTable.TABLE_HEADER, path, null, iconList, td2);

		} else {

			String path = "";
			int i = 0;
			int directParentIndex = -1;
			int header = -1;
			for (FilesFile file : model.lookupConversation().getFsListe()) {

				if (file.isParentOf(new FilesFile(model.lookupConversation().getVerzeichnis()))) {

					String name = null;
					if (Security.isDirectoryAllowedForUser(model, file.getAbsolutePath())) {
						if (file.isFileSystemRoot()) {
							name = "root";
						} else {
							name = file.dateiNameKlartext();
						}
					} else {
						name = "Übersicht";
					}

					if (StringUtils.isNotBlank(name)) {
						if (path.length() > 0) {
							path = path + HTMLUtils.buildSpan(null, textClassContrast, "&thinsp;&gt;&thinsp;");
						}
						path = path + HTMLUtils.buildSpan(null, null, name, HTMLUtils.buildConditionSubmitJS(Condition.FS_NAVIGATE, i));
						directParentIndex = i;
						header = i;
					}
				} else {
					break;
				}
				i++;
			}

			directParentIndex = directParentIndex == -1 ? -1 : directParentIndex - 1;

			model.lookupConversation().getKeyPressEvents().put(KeyCodes.ESCAPE.methodDeclarator(),
					HTMLUtils.buildConditionSubmitJS(Condition.FS_NAVIGATE, directParentIndex));

			String td2 = fileSystemTD2(new FilesFile(model.lookupConversation().getVerzeichnis()), model, null, header);
			iconList.add(fileSystemIcon("more", "Mehr", HTMLUtils.buildConditionSubmitJS(Condition.FS_VIEW_OPTIONS, header),
					textClassContrast, null));

			fileSystemRow(model, table, HTMLTable.TABLE_HEADER_TOP, path, null, iconList, td2);

		}
	}

	private void fileSystemObject(Model model, boolean details, HTMLTable table, int j, int rowID, boolean isUebersicht,
			FilesFile file) {

		List<String> iconList = new LinkedList<String>();
		String cssClass = rowID % 2 != 0 ? HTMLTable.TABLE_TOP : HTMLTable.TABLE_ALT_TOP;
		boolean editable = !file.isDirectory() && file.isEditableFileType();
		boolean viewableImage = !file.isDirectory() && file.isViewableImageType();

		String name = fileSystemObjectName(model, j, isUebersicht, file, "nav" + rowID);
		Condition conditionForName = null;
		if (editable) {
			conditionForName = Condition.FS_VIEW_FILE;
		} else if (viewableImage) {
			conditionForName = Condition.IMAGE_VIEW_WITH_MENU;
		} else if (file.isDirectory()) {
			conditionForName = Condition.FS_NAVIGATE;
		} else {
			conditionForName = Condition.FILE_DOWNLOAD_ORIGINAL;
		}

		// Einfuegen, Editieren
		if (editable) {
			if (!file.isClientCrypted()) {
				iconList.add(fileSystemIcon("inserttext", "Einfügen", HTMLUtils.buildConditionSubmitJS(Condition.FS_START_NOTE_EDIT, j),
						null, "nav" + rowID + "-i"));
			}
			iconList.add(fileSystemIcon("edit", "Editieren", HTMLUtils.buildConditionSubmitJS(Condition.FS_EDIT_FILE, j), null,
					"nav" + rowID + "-e"));
		}
		// Bild anzeigen
		if (viewableImage) {
			iconList.add(fileSystemIcon("picture", "Vollbild", HTMLUtils.buildConditionSubmitJS(Condition.IMAGE_VIEW_FULLSCREEN, j), null,
					"nav" + rowID + "-f"));
		}
		// Mehr
		iconList.add(
				fileSystemIcon("more", "Mehr", HTMLUtils.buildConditionSubmitJS(Condition.FS_VIEW_OPTIONS, j), null, "nav" + rowID + "-o"));

		String td2 = fileSystemTD2(file, model, null, j);

		fileSystemRow(model, table, cssClass, name, HTMLUtils.buildConditionSubmitJS(conditionForName, j), iconList, td2);

	}

	private String fileSystemObjectName(Model model, int j, boolean isUebersicht, FilesFile file, String id) {

		String icon = "";
		String space = "";
		String name;
		try {
			if (isUebersicht) {
				name = HTMLUtils.buildSpan(id, null, file.dateiNameKlartext());
			} else {
				if (file.isDirectory()) {
					String a = HTMLUtils.buildA(id, null, file.dateiNameKlartext());
					name = HTMLUtils.buildSpan(null, null, a);
				} else {
					FilesFile fileKlartext = new FilesFile(file.dateiNameUndPfadKlartext());
					String a = HTMLUtils.buildA(id, null, FilesFile.dateinameOhneSuffix(fileKlartext));
					name = HTMLUtils.buildSpan(null, null, a);
					if (StringUtils.isNoneBlank(FilesFile.dateinamenSuffix(fileKlartext))) {
						name += HTMLUtils.buildSpan(null, "shadow", "." + FilesFile.dateinamenSuffix(fileKlartext));
					}
				}
			}
		} catch (Exception e) {
			name = HTMLUtils.buildSpan(id, null, "[Defekter Dateiname]");
		}

		if (file.isDirectory()) {
			icon = HTMLUtils.buildSpan(null, "icon folder", "");
			space = "&nbsp;";
		} else if (file.isServerCryptedDirectPassword()) {
			icon = HTMLUtils.buildSpan(null, "icon secureMedium", "");
			if (file.isServerBaseCrypted()) {
				space = "&nbsp;";
			} else {
				space = HTMLUtils.buildSpan(null, null, "<b>(!)</b>&nbsp;");
			}
		} else if (file.isServerCryptedHashedPassword()) {
			icon = HTMLUtils.buildSpan(null, "icon secureMedium", "");
			space = "&nbsp;";
		} else if (file.isClientCrypted()) {
			icon = HTMLUtils.buildSpan(null, "icon secureHigh", "");
			space = "&nbsp;";
		}

		return icon + space + name;
	}

	private void fileSystemRow(Model model, HTMLTable table, String cssClass, String title, String onclickTitle, List<String> iconList,
			String td2content) {

		String titleDiv = HTMLUtils.buildDiv(null, "left pointer", title, onclickTitle);

		StringBuilder icons = new StringBuilder(300 * iconList.size());
		for (String icon : iconList) {
			icons.append(icon);
		}

		String iconDiv = HTMLUtils.buildDiv(null, "right hmargin", icons.toString(), null);

		table.addTDSource(titleDiv + iconDiv, 1, cssClass);
		if (!model.isPhone()) {
			table.addTDSource(td2content, 1, cssClass);
		}
		table.addNewRow();

	}

	private String fileSystemIcon(String iconName, String iconLabel, String onclick, String textClass, String labelID) {

		String emptyP = HTMLUtils.buildP(null, null, "");
		String figureicon = emptyP + HTMLUtils.buildDiv(null, "figureicon " + iconName, "", null) + emptyP;
		String label = HTMLUtils.buildP(null, null, HTMLUtils.buildA(labelID, textClass, iconLabel));
		String figureDiv = HTMLUtils.buildDiv(null, "figure", figureicon + label, onclick);
		String outerDiv = HTMLUtils.buildDiv(null, "hmargin float", figureDiv, null);

		return outerDiv;
	}

	private String fileSystemTD2(FilesFile file, Model model, String textClass, int rowID) {

		String div1;
		if (file.isDirectory()) {
			div1 = HTMLUtils.buildDiv(null, null,
					HTMLUtils.buildAjaxFillInText(AjaxFillIn.DIR_SIZE_LIST, "" + rowID, true, model, textClass), null);
		} else {
			div1 = HTMLUtils.buildDiv(null, null, HTMLUtils.buildA(null, textClass, DateiZugriff.fileGroesseFormatieren(file)), null);
		}

		String edit = "";
		if (file != null) {
			edit = DateiZugriff.formatLastModifiedDateAsString(file, true);
			if (DateiZugriff.leseDateiAenderer(file, model) != null) {
				edit = edit + ", " + DateiZugriff.leseDateiAenderer(file, model);
			}

		}

		String div2 = HTMLUtils.buildDiv(null, null, HTMLUtils.buildA(null, textClass, edit), null);

		return div1 + div2;
	}

}
