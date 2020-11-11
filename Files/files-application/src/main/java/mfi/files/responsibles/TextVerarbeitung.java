package mfi.files.responsibles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import mfi.files.annotation.Responsible;
import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.StringHelper;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.ButtonBar;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.logic.MetaTagParser;
import mfi.files.model.Condition;
import mfi.files.model.Model;
import mfi.files.model.TextFileMetaTag;
import mfi.files.model.TextFileMetaTagName;

@Component
public class TextVerarbeitung extends AbstractResponsible {

	@Responsible(conditions = { Condition.FS_PUSH_TEXTVIEW })
	public void fjPushSetzen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		model.lookupConversation().setTextViewPush(!model.lookupConversation().isTextViewPush());
		model.lookupConversation().setForwardCondition(Condition.SYS_FJ_OPTIONS);
		return;
	}

	@Responsible(conditions = { Condition.FS_NUMBERS_TEXTVIEW })
	public void fjLineNumbersSetzen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		model.lookupConversation().setTextViewNumbers(!model.lookupConversation().isTextViewNumbers());
		model.lookupConversation().setForwardCondition(Condition.SYS_FJ_OPTIONS);
		return;
	}

	@Responsible(conditions = { Condition.FS_VIEW_FILE, Condition.FS_SAVE_EDITED_FILE_AND_VIEW, Condition.FS_TEXTVIEW_AFTER_OPTION_CHANGE })
	public void fjTextAnzeigen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (model.lookupConversation().getCondition().equals(Condition.FS_SAVE_EDITED_FILE_AND_VIEW)) {
			String text = parameters.get("editortext");
			editierteDateiSpeichernUndLockLoeschen(text, model);
		}

		if (model.lookupConversation().getEditingFile().isDirectory()) {
			model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
			return;
		}
		if (!model.lookupConversation().getEditingFile().isReadable()) {
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
		}

		String inhalt = model.lookupConversation().getEditingFile().readIntoString();
		MetaTagParser metaTagParser = new MetaTagParser();
		List<TextFileMetaTag> tags = metaTagParser.parseTags(inhalt, false);

		if (!model.lookupConversation().getCondition().equals(Condition.FS_TEXTVIEW_AFTER_OPTION_CHANGE)) {

			boolean pushOff = metaTagParser.hasTag(TextFileMetaTagName.PUSH_OFF);
			if (pushOff) {
				model.lookupConversation().setTextViewPush(false);
			} else {
				model.lookupConversation().resetTextViewPush(model);
			}
		}

		ButtonBar buttonBar = new ButtonBar();
		if (!model.isPhone()) {
			buttonBar.getButtons().add(new Button("Optionen", Condition.FS_VIEW_OPTIONS));
		}
		buttonBar.getButtons().add(new Button("Editieren", Condition.FS_EDIT_FILE));
		if (StringUtils.equalsIgnoreCase(model.lookupConversation().getEditingFile().dateinamenSuffix(), model.getNoteFileSuffix())
				&& !model.lookupConversation().getEditingFile().isClientCrypted()) {
			buttonBar.getButtons().add(new Button("Einfügen", Condition.FS_START_NOTE_EDIT));
			if (tags.size() > 0) {
				buttonBar.getButtons().add(new Button("Statistik", Condition.TXT_STATISTIC_MENU));
			}
		}
		sb.append(HTMLUtils.buildMenuNar(model, "Datei anzeigen", true, buttonBar, true));

		sb.append(HTMLUtils.contentBreak());

		buildTextviewTable(model.lookupConversation().getEditingFile().dateiNameKlartext(), sb, model, inhalt,
				model.lookupConversation().isTextViewNumbers(), true, true);

		if (model.lookupConversation().isTextViewPush()) {
			// FIXME: model.lookupConversation().getJavaScriptOnPageLoaded().add("initPushForTextView();");
		}

		return;
	}

	@Responsible(conditions = { Condition.FS_SAVE_EDITED_FILE })
	public void fjDateiSpeichern(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		String text = parameters.get("editortext");
		editierteDateiSpeichernUndLockLoeschen(text, model);
		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_LOCK_OVERVIEW })
	public void fjLockUebersicht(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		sb.append(HTMLUtils.buildMenuNar(model, "Datei-Locking", true, null, false));
		HTMLTable table = new HTMLTable();
		table.addTD("Hinweis", 1, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTD("Die Datei ", 1, null);
		table.addNewRow();
		table.addTD(HTMLUtils.spacifyFilePath(model.lookupConversation().getEditingFile().dateiNameUndPfadKlartext(), model), 1, null);
		table.addNewRow();

		String[] lock = model.lookupConversation().getEditingFile().fileLockVorhanden();
		if (lock == null) {
			table.addTD("ist NICHT gesperrt. ", 1, null);
			table.addNewRow();
			table.addTDSource(new Button("Datei editieren", Condition.FS_EDIT_FILE).printForUseInTable(), 1, null);
			table.addNewRow();
		} else {
			table.addTD("wird gerade editiert und ist daher gesperrt.: ", 1, null);
			table.addNewRow();
			table.addTD("Lock-Informationen", 1, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			table.addTD("User: " + lock[0], 1, null);
			table.addNewRow();
			SimpleDateFormat sdf = Hilfsklasse.lookupSimpleDateFormat("dd.MM.yyyy  HH:mm:ss");
			String timestamp = sdf.format(new Date(Long.valueOf(lock[1])));
			table.addTD("Zeitstempel: " + timestamp, 1, null);
			table.addNewRow();
			table.addTD("Lock aufheben?", 1, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			table.addTDSource(new Button("Lock löschen", Condition.FS_UNLOCK_FILE).printForUseInTable(), 1, null);
			table.addNewRow();
			table.addTDSource(new Button("Lock nochmals prüfen", Condition.FS_START_NOTE_EDIT).printForUseInTable(), 1, null);
			table.addNewRow();
		}
		table.addTDSource(new Button("Datei anzeigen", Condition.FS_VIEW_FILE).printForUseInTable(), 1, null);
		table.addNewRow();

		sb.append(table.buildTable(model));
		return;
	}

	@Responsible(conditions = { Condition.FS_UNLOCK_FILE })
	public void fjLockZuruecknehmen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (model.lookupConversation().getEditingFile() != null
				&& model.lookupConversation().getEditingFile().fileLockVorhanden() != null) {
			model.lookupConversation().getEditingFile().loescheFileLock();
		}

		model.lookupConversation().setForwardCondition(Condition.FS_LOCK_OVERVIEW);
		return;
	}

	@Responsible(conditions = { Condition.FS_CANCEL_EDITED_FILE })
	public void fjDateiAendernAbbrechen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		return;
	}

	@Responsible(conditions = { Condition.FS_START_NOTE_EDIT })
	public void fjDateiSatzEinfuegen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (!model.lookupConversation().getEditingFile().isReadable()) {
			// passwort benoetigt? Dann erstmal zur Passwortmaske weiterleiten
			model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_SERVER);
			return;
		}

		String[] lock = model.lookupConversation().getEditingFile().fileLockVorhanden();
		if (lock != null) {
			model.lookupConversation().setForwardCondition(Condition.FS_LOCK_OVERVIEW);
			return;
		}

		boolean lockSchreibenErfolgreich = model.lookupConversation().getEditingFile().setzeFileLock(model.getUser());
		if (!lockSchreibenErfolgreich) {
			model.lookupConversation().setForwardCondition(Condition.FS_VIEW_FILE);
			model.lookupConversation().getMeldungen().add("Lock auf die Datei konnte nicht gesetzt werden!");
			return;
		}

		List<String> kategorien = new LinkedList<String>();
		List<String> zeilen = model.lookupConversation().getEditingFile().readIntoLines();

		MetaTagParser metaTagParser = new MetaTagParser();
		List<TextFileMetaTag> formatTags = metaTagParser.parseTags(zeilen, true);

		if (!metaTagParser.hasTag(TextFileMetaTagName.NO_CATEGORIES)) {
			for (String zeile : zeilen) {
				if (StringUtils.startsWith((StringUtils.trimToEmpty(zeile)), "==")) {
					kategorien.add(StringUtils.remove(zeile, "=").trim());
				}
			}
		}

		for (String error : metaTagParser.getErrors()) {
			model.lookupConversation().getMeldungen().add(error);
		}

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Speichern", Condition.FS_SAVE_NEW_NOTE));
		buttonBar.getButtons().add(new Button("Speichern & Weiter", Condition.FS_SAVE_NEW_NOTE_AND_START_NEXT));
		buttonBar.getButtons().add(new Button("Speichern & Anzeigen", Condition.FS_SAVE_NEW_NOTE_AND_VIEW));
		sb.append(HTMLUtils.buildMenuNar(model, "Zeile einfügen", true, buttonBar, false));

		if (kategorien.size() == 1) {
			sb.append(HTMLUtils.buildHiddenField("noteCategory", kategorien.get(0)));
		}

		int cols = model.isPhone() ? 2 : 4;
		int itemCol = model.isPhone() ? cols : cols / 2;

		HTMLTable table = new HTMLTable();
		table.addTD(model.lookupConversation().getEditingFile().dateiNameKlartext(), 4, HTMLTable.TABLE_HEADER);
		table.addNewRow();

		if (kategorien.size() > 1) {
			table.addTD("Kategorie", itemCol, HTMLTable.NO_BORDER);
			table.addNewRowIf(model.isPhone());
			table.addTDSource(HTMLUtils.buildDropDownListeSelectContains("noteCategory", kategorien, model.getUser()), itemCol,
					HTMLTable.NO_BORDER);
			table.addNewRow();
		}

		table.addTD("Hier den Inhalt der neuen Zeile eingeben:", cols, HTMLTable.NO_BORDER);
		table.addNewRow();

		for (TextFileMetaTag tag : formatTags) {

			switch (tag.getTextFileMetaTagName()) {

			case CHOICE:
				table.addTD(tag.getCaption(), itemCol, HTMLTable.NO_BORDER);
				table.addNewRowIf(model.isPhone());
				table.addTDSource(HTMLUtils.buildDropDownListeSelectContains(tag.getId(), tag.getArguments(), model.getUser()), itemCol,
						HTMLTable.NO_BORDER);
				table.addNewRow();
				break;

			case DATE:
				table.addTD(tag.getCaption(), itemCol, HTMLTable.NO_BORDER);
				table.addNewRowIf(model.isPhone());
				table.addTDSource(HTMLUtils.buildDropDownListe(tag.getId(),
						Hilfsklasse.erstelleDatumsliste(Hilfsklasse.DATE_PATTERN_STD, 21, false), null), itemCol, HTMLTable.NO_BORDER);
				table.addNewRow();
				break;

			case TEXT:
				table.addTD(tag.getCaption(), itemCol, HTMLTable.NO_BORDER);
				table.addNewRowIf(model.isPhone());
				table.addTDSource(HTMLUtils.buildTextField(tag.getId(), "", 40, null), itemCol, HTMLTable.NO_BORDER);
				table.addNewRow();
				break;

			case NUMBER:
				table.addTD(tag.getCaption(), itemCol, HTMLTable.NO_BORDER);
				table.addNewRowIf(model.isPhone());
				if (model.isClientTouchDevice()) {
					table.addTDSource(HTMLUtils.buildNumberField(tag.getId(), "", 25, null), itemCol, HTMLTable.NO_BORDER);
				} else {
					table.addTDSource(HTMLUtils.buildTextField(tag.getId(), "", 40, null), itemCol, HTMLTable.NO_BORDER);
				}
				table.addNewRow();
				break;

			default:
				// noop
			}
		}

		sb.append(table.buildTable(model));
		return;
	}

	@Responsible(conditions = { Condition.FS_SAVE_NEW_NOTE, Condition.FS_SAVE_NEW_NOTE_AND_VIEW,
			Condition.FS_SAVE_NEW_NOTE_AND_START_NEXT })
	public void fjDateiSpeichernNachSatzEinfuegen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		MetaTagParser metaTagParser = new MetaTagParser();
		List<String> zeilen = model.lookupConversation().getEditingFile().readIntoLines();
		List<TextFileMetaTag> formatTags = metaTagParser.parseTags(zeilen, true);
		StringBuilder neueZeile = new StringBuilder();

		for (TextFileMetaTag tag : formatTags) {

			switch (tag.getTextFileMetaTagName()) {

			case CHOICE:
				String choice = StringUtils.trimToEmpty(parameters.get(tag.getId()));
				neueZeile.append(choice);
				break;

			case DATE:
				String date = StringUtils.trimToEmpty(parameters.get(tag.getId()));
				Pattern pattern = Pattern.compile("[0-9]{1,2}[.]{1}[0-9]{1,2}[.]{1}[0-9]{4}");
				Matcher m = pattern.matcher(date);
				if (m.find()) {
					neueZeile.append(date.substring(m.start(), m.end()));
				} else {
					throw new IllegalArgumentException("Datum konnte nicht geparst werden:" + date);
				}
				break;

			case TEXT:
			case NUMBER:
				String text = StringUtils.trimToEmpty(parameters.get(tag.getId()));
				if (StringUtils.isEmpty(text)) {
					model.lookupConversation().getMeldungen().add("'" + tag.getCaption() + "' ist leer!");
				}
				neueZeile.append(text);
				break;

			case BULLET:
			case NEWLINE:
			case SPACE:
				neueZeile.append(tag.getTextFileMetaTagName().getFixContent());
				break;
			case FORMAT:
				for (String arg : tag.getArguments()) {
					neueZeile.append(arg);
				}
				break;

			default:
				// noop
			}

		}

		String noteCategory = metaTagParser.hasTag(TextFileMetaTagName.NO_CATEGORIES) ? null
				: StringUtils.trimToNull(parameters.get("noteCategory"));

		model.lookupConversation().getEditingFile().fuegeNeueNoteZeileEin(neueZeile.toString().trim(), noteCategory,
				metaTagParser.hasTag(TextFileMetaTagName.NEW_ITEM_ON_TOP), model);

		if (model.lookupConversation().getCondition().equals(Condition.FS_SAVE_NEW_NOTE_AND_VIEW)) {
			model.lookupConversation().setForwardCondition(Condition.FS_VIEW_FILE);
		} else if (model.lookupConversation().getCondition().equals(Condition.FS_SAVE_NEW_NOTE_AND_START_NEXT)) {
			model.lookupConversation().getMeldungen().add("Zeile wurde hinzugefügt: " + neueZeile.toString().trim());
			model.lookupConversation().setForwardCondition(Condition.FS_START_NOTE_EDIT);
		} else {
			model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
		}
		return;
	}

	@Responsible(conditions = { Condition.FS_SWITCH_TO_EDIT })
	public void fjWechselNachDateiEditieren(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		model.lookupConversation().setForwardCondition(Condition.FS_EDIT_FILE);
		return;
	}

	@Responsible(conditions = { Condition.FS_EDIT_FILE })
	public void fjDateiEditieren(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (!model.lookupConversation().getEditingFile().isReadable()) {
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
		}

		String[] lock = model.lookupConversation().getEditingFile().fileLockVorhanden();
		if (lock != null) {
			model.lookupConversation().setForwardCondition(Condition.FS_LOCK_OVERVIEW);
			return;
		}

		boolean lockSchreibenErfolgreich = model.lookupConversation().getEditingFile().setzeFileLock(model.getUser());
		if (!lockSchreibenErfolgreich) {
			model.lookupConversation().setForwardCondition(Condition.FS_VIEW_FILE);
			model.lookupConversation().getMeldungen().add("Lock auf die Datei konnte nicht gesetzt werden!");
			return;
		}

		String textarea = HTMLUtils.buildTextArea("editortext", "");
		model.lookupConversation().getJavaScriptOnPageLoaded()
				.add("document.getElementById('" + StringHelper.idFromName("editortext") + HTMLUtils.SECURE_ATTRIBUTE + "').focus();");

		String content = StringUtils.trimToEmpty(model.lookupConversation().getEditingFile().readIntoString());
		if (model.lookupConversation().getEditingFile().isClientCrypted()) {
			model.lookupConversation().getJavaScriptOnPageLoaded().add("showFileContentToEdit(true);");
		} else {
			content = new String(Base64.encodeBase64(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
			model.lookupConversation().getJavaScriptOnPageLoaded().add("showFileContentToEdit(false);");
		}
		model.lookupConversation().setFileContent(content);

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Speichern", Condition.FS_SAVE_EDITED_FILE));
		buttonBar.getButtons().add(new Button("Speichern & Anzeigen", Condition.FS_SAVE_EDITED_FILE_AND_VIEW));
		sb.append(HTMLUtils.buildMenuNar(model, "Datei editieren", true, buttonBar, true));

		HTMLTable table = new HTMLTable();
		table.addTD(model.lookupConversation().getEditingFile().dateiNameKlartext(), model.isPhone() ? 2 : 3, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTDSource(textarea, 3, HTMLTable.NO_BORDER);
		table.setWidthTo100Percent(true);
		sb.append(table.buildTable(model));
		return;
	}

	private void editierteDateiSpeichernUndLockLoeschen(String text, Model model) throws IOException {

		String[] lock = model.lookupConversation().getEditingFile().fileLockVorhanden();
		if (lock != null && !StringUtils.equalsIgnoreCase(lock[0], model.getUser())) {
			model.lookupConversation().getMeldungen().add("Fremdes Lock auf die Datei gefunden.");
			model.lookupConversation().getMeldungen().add("Änderungen wurden nicht gespeichert.");
			model.lookupConversation().getMeldungen().add("Eingaben wurden im Logfile ausgegeben.");
			logger.error("Fehler beim Speichern aufgrund vom fremden Lock:\n" + text);
			return;
		} else {
			if (lock == null) {
				model.lookupConversation().getMeldungen().add("Das Lock auf die Datei wurde durch einen anderen Anwender gelöscht.");
				model.lookupConversation().getMeldungen().add("Änderungen wurden nicht gespeichert.");
				model.lookupConversation().getMeldungen().add("Eingaben wurden im Logfile ausgegeben.");
				logger.error("Verlorene Aenderungen durch fremdes Loeschen eines Locks:\n" + text);
				return;
			}
		}

		if (!model.lookupConversation().getEditingFile().isClientCrypted()) {
			text = new String(Base64.decodeBase64(text), StandardCharsets.UTF_8);
		}
		model.lookupConversation().getEditingFile().schreibeFileMitArchivierung(text, model, model.getUser());
		model.lookupConversation().getEditingFile().loescheFileLock(model.getUser());

	}

}
