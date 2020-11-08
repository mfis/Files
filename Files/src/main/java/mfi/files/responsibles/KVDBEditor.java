package mfi.files.responsibles;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import mfi.files.annotation.Responsible;
import mfi.files.helper.Hilfsklasse;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.ButtonBar;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.logic.Filter;
import mfi.files.logic.Filter.IgnoreCase;
import mfi.files.logic.Filter.Preset;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Model;

@Component
public class KVDBEditor extends AbstractResponsible {

	@Responsible(conditions = { Condition.KVDB_EDIT_START, Condition.KVDB_EDIT, Condition.KVDB_DELETE, Condition.KVDB_INSERT,
			Condition.KVDB_RESET })
	public void backup(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		String posFilter = StringUtils.trimToEmpty(parameters.get("pos_filter"));
		String negFilter = StringUtils.trimToEmpty(parameters.get("neg_filter"));
		String selKey = StringUtils.trimToEmpty(parameters.get("sel_key"));
		String selValue = StringUtils.trimToEmpty(parameters.get("sel_value"));
		List<String> inhalt = KVMemoryMap.getInstance().dumpAsList();
		List<String> filtered = filter(inhalt, posFilter, negFilter);

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_RESET)) {
			posFilter = "";
			negFilter = "";
			selKey = "";
			selValue = "";
			inhalt = KVMemoryMap.getInstance().dumpAsList();
			filtered = filter(inhalt, posFilter, negFilter);
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_EDIT)) {
			if (!filtered.isEmpty() && filtered.size() == 1) {
				String[] keyValue = KVMemoryMap.splitFileFormatLineInKeyValue(filtered.get(0));
				if (StringUtils.endsWithIgnoreCase(keyValue[0], selKey)) {
					writeLog("EDIT " + keyValue[0] + " = " + KVMemoryMap.getInstance().readValueFromKey(keyValue[0]) + " -> " + selValue);
					KVMemoryMap.getInstance().writeKeyValue(keyValue[0], selValue, true);
				} else {
					model.lookupConversation().getMeldungen()
							.add("Auswahl zum ändern ist nicht eindeutig. " + keyValue[0] + " / " + selKey);
				}
			} else {
				model.lookupConversation().getMeldungen().add("Keine Auswahl zum ändern gefunden.");
			}
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_DELETE)) {
			if (!filtered.isEmpty()) {
				for (String line : filtered) {
					String[] keyValue = KVMemoryMap.splitFileFormatLineInKeyValue(line);
					writeLog("DLET " + keyValue[0] + " = " + keyValue[1]);
					KVMemoryMap.getInstance().deleteKey(keyValue[0]);
				}
			} else {
				model.lookupConversation().getMeldungen().add("Keine Auswahl zum löschen gefunden.");
			}
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_INSERT)) {
			if (StringUtils.isNotBlank(selKey) && !KVMemoryMap.getInstance().containsKey(selKey)) {
				writeLog("ISRT " + selKey.trim() + " = " + selValue);
				KVMemoryMap.getInstance().writeKeyValue(selKey.trim(), StringUtils.trimToEmpty(selValue), false);
			} else {
				model.lookupConversation().getMeldungen().add("Kein Satz zum einfügen gefunden oder Key nicht vorhanden oder doppelt.");
			}
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_EDIT)
				|| model.lookupConversation().getCondition().equals(Condition.KVDB_INSERT)
				|| model.lookupConversation().getCondition().equals(Condition.KVDB_DELETE)) {
			KVMemoryMap.getInstance().save();
		}

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Filtern / Aktualisieren", Condition.KVDB_EDIT_START));
		buttonBar.getButtons().add(new Button("Reset", Condition.KVDB_RESET));
		sb.append(HTMLUtils.buildMenuNar(model, "KVDB Editor", true, buttonBar, false));

		HTMLTable tableFilter = new HTMLTable();
		tableFilter.addTD("Filter", 2, HTMLTable.TABLE_HEADER);
		tableFilter.addNewRow();
		tableFilter.addTD("Positiv: ", 1, null);
		tableFilter.addTDSource(HTMLUtils.buildTextField("pos_filter", posFilter, 30, Condition.KVDB_EDIT_START), 1, null);
		HTMLUtils.setFocus("pos_filter", model);
		tableFilter.addNewRow();
		tableFilter.addTD("Negativ: ", 1, null);
		tableFilter.addTDSource(HTMLUtils.buildTextField("neg_filter", negFilter, 30, Condition.KVDB_EDIT_START), 1, null);
		tableFilter.addNewRow();
		sb.append(tableFilter.buildTable(model));

		if (!filtered.isEmpty() && filtered.size() == 1) {
			String[] keyValue = KVMemoryMap.splitFileFormatLineInKeyValue(filtered.get(0));
			HTMLTable tableAuswahl = new HTMLTable();
			tableAuswahl.addTD("Auswahl (einzeln)", 2, HTMLTable.TABLE_HEADER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Key: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField("sel_key", keyValue[0], 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Value: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField("sel_value", keyValue[1], 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("ändern", Condition.KVDB_EDIT, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("löschen", Condition.KVDB_DELETE, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			sb.append(tableAuswahl.buildTable(model));
		}

		if (!filtered.isEmpty() && filtered.size() > 1 && (filtered.size() < inhalt.size())) {
			HTMLTable tableAuswahl = new HTMLTable();
			tableAuswahl.addTD("Auswahl (Liste)", 2, HTMLTable.TABLE_HEADER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("alle löschen", Condition.KVDB_DELETE, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			sb.append(tableAuswahl.buildTable(model));
		}

		if (StringUtils.isBlank(posFilter) && StringUtils.isBlank(negFilter) && filtered.size() == inhalt.size()) {
			HTMLTable tableAuswahl = new HTMLTable();
			tableAuswahl.addTD("Einfügen", 2, HTMLTable.TABLE_HEADER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Key: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField("sel_key", "", 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Value: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField("sel_value", "", 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("einfügen", Condition.KVDB_INSERT, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			sb.append(tableAuswahl.buildTable(model));
		}

		inhalt = KVMemoryMap.getInstance().dumpAsList();
		filtered = filter(inhalt, posFilter, negFilter);

		sb.append(HTMLUtils.contentBreak());

		String count = filtered.size() + (filtered.size() == 1 ? " Eintrag" : " Einträge");

		StringBuilder lines = new StringBuilder();
		for (String zeile : filtered) {
			String[] strings = StringUtils.split(zeile, '=');
			String key = strings[0];
			String val = StringUtils.startsWith(strings[0], KVMemoryMap.KVDB_KEY_LOGINTOKEN) ? StringUtils.left(strings[1], 100) + "..."
					: strings[1];
			lines.append(key + " = " + val + "\n");
		}

		buildTextviewTable("KVDB - " + count, sb, model, lines.toString(), model.lookupConversation().isTextViewNumbers(), true, true);

	}

	private List<String> filter(List<String> list, String posFilter, String negFilter) {

		Set<String> in = new HashSet<String>();
		if (StringUtils.isNotBlank(posFilter)) {
			in.add(posFilter);
		}
		Set<String> ex = new HashSet<String>();
		if (StringUtils.isNotBlank(negFilter)) {
			ex.add(negFilter);
		}

		List<String> filtered = Filter.filter(list, in, ex, Preset.CONTAINS, IgnoreCase.YES);

		return filtered;
	}

	private void writeLog(String s) {

		FilesFile logFile = new FilesFile(KVMemoryMap.getInstance().readValueFromKey("application.kvdblog"));
		try {
			FileUtils.writeStringToFile(logFile, Hilfsklasse.zeitstempelAlsString() + " " + s + "\n", "UTF-8", true);
		} catch (IOException e) {
			throw new IllegalStateException("Could not write to KV Log:", e);
		}
	}

}
