package mfi.files.responsibles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;
import mfi.files.annotation.Responsible;
import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.MapSortHelper;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.ButtonBar;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.logic.MetaTagParser;
import mfi.files.model.Condition;
import mfi.files.model.Model;
import mfi.files.model.SplittedLineGroups;
import mfi.files.model.TextFileMetaTag;
import mfi.files.model.TextFileMetaTagName;

@Component
public class TextDateiStatistik extends AbstractResponsible {

	@Responsible(conditions = { Condition.TXT_STATISTIC_MENU })
	public void fjStatistikMenu(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		List<String> kategorien = new LinkedList<String>();
		List<String> zeilen = model.lookupConversation().getEditingFile().readIntoLines();
		for (String zeile : zeilen) {
			if (StringUtils.startsWith((StringUtils.trimToEmpty(zeile)), "==")) {
				kategorien.add(StringUtils.remove(zeile, "=").trim());
			}
		}

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Anzeigen", Condition.FS_VIEW_FILE));
		buttonBar.getButtons().add(new Button("Statistik berechnen", Condition.TXT_STATISTIC_CALC));
		sb.append(HTMLUtils.buildMenuNar(model, "Statistik Parameter", true, buttonBar, false));

		HTMLTable table = new HTMLTable();
		table.addTD(model.lookupConversation().getEditingFile().dateiNameKlartext(), 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();

		MetaTagParser metaTagParser = new MetaTagParser();
		List<TextFileMetaTag> formatTags = metaTagParser.parseTags(zeilen, true);

		if (formatTags.size() == 0) {
			table.addTD("Keine Meta-Tags gefunden.", 2, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			sb.append(table.buildTable(model));
			return;
		}

		if (kategorien.size() > 1) {
			table.addTDSource(HTMLUtils.buildCheckBox("Je Kategorie einzeln", "STAT_CATEGORY", true, null), 2, null);
			table.addNewRow();
		}

		table.addTD("Aufteilen...", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();

		table.addTDSource(HTMLUtils.buildRadioButton("STAT_SPLIT", "Nicht aufteilen", "#", true), 2, null);
		table.addNewRow();

		for (TextFileMetaTag tag : formatTags) {
			if (tag.getTextFileMetaTagName() == TextFileMetaTagName.DATE) {
				table.addTDSource(HTMLUtils.buildRadioButton("STAT_SPLIT", tag.getCaption() + " nach Jahr", "YEAR#" + tag.getId(), false),
						2, null);
				table.addNewRow();
				table.addTDSource(HTMLUtils.buildRadioButton("STAT_SPLIT", tag.getCaption() + " nach Monat", "MONTH#" + tag.getId(), false),
						2, null);
				table.addNewRow();
			}
			if (tag.getTextFileMetaTagName() == TextFileMetaTagName.CHOICE) {
				table.addTDSource(
						HTMLUtils.buildRadioButton("STAT_SPLIT", tag.getCaption() + " nach Werten", "VALUES#" + tag.getId(), false), 2,
						null);
				table.addNewRow();
			}
		}

		table.addTD("Zusammenfassen nach...", 2, HTMLTable.TABLE_HEADER);
		table.addNewRow();

		table.addTDSource(HTMLUtils.buildCheckBox("Anzahl gesamt", "STAT_COUNT#", false, null), 2, null);
		table.addNewRow();

		for (TextFileMetaTag tag : formatTags) {
			if (tag.getTextFileMetaTagName() == TextFileMetaTagName.CHOICE && tag.argumentsAreNumbers()) {
				table.addTDSource(HTMLUtils.buildCheckBox("Summe von " + tag.getCaption(), "STAT_SUMM#" + tag.getId(), false, null), 2,
						null);
				table.addNewRow();
				table.addTDSource(HTMLUtils.buildCheckBox("Mittelwert von " + tag.getCaption(), "STAT_AVG#" + tag.getId(), false, null), 2,
						null);
				table.addNewRow();
			}
			if (tag.getTextFileMetaTagName() == TextFileMetaTagName.CHOICE || tag.getTextFileMetaTagName() == TextFileMetaTagName.TEXT
					|| tag.getTextFileMetaTagName() == TextFileMetaTagName.NUMBER) {
				table.addTDSource(HTMLUtils.buildCheckBox("Verteilung von " + tag.getCaption(), "STAT_VALS#" + tag.getId(), false, null), 2,
						null);
				table.addNewRow();
			}
		}

		sb.append(table.buildTable(model));

		return;
	}

	@Responsible(conditions = { Condition.TXT_STATISTIC_CALC })
	public void fjStatistikCalc(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		List<String> kategorien = new LinkedList<String>();
		List<String> zeilen = model.lookupConversation().getEditingFile().readIntoLines();
		for (String zeile : zeilen) {
			if (StringUtils.startsWith((StringUtils.trimToEmpty(zeile)), "==")) {
				kategorien.add(StringUtils.remove(zeile, "=").trim());
			}
		}

		MetaTagParser metaTagParser = new MetaTagParser();

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Anzeigen", Condition.FS_VIEW_FILE));
		buttonBar.getButtons().add(new Button("Parameter ändern", Condition.TXT_STATISTIC_MENU));
		sb.append(HTMLUtils.buildMenuNar(model, "Statistik", true, buttonBar, false));

		boolean optCategory = kategorien.size() > 1
				&& StringUtils.equalsIgnoreCase(parameters.get("STAT_CATEGORY"), Boolean.TRUE.toString());

		List<TextFileMetaTag> captions = metaTagParser.parseCaptions(zeilen);

		HTMLTable table = new HTMLTable();

		if (optCategory) {
			for (String category : kategorien) {
				// JE KATEGORIE
				List<List<String>> parsedLinesAll = metaTagParser.parseLines(zeilen, category);
				verarbeiteStatistik(sb, model, metaTagParser, category, parsedLinesAll, parameters, captions, table);
			}
		} else {
			// ALLE KATEGORIEN
			List<List<String>> parsedLinesAll = metaTagParser.parseLines(zeilen, null);
			String title = kategorien.size() == 1 ? kategorien.get(0) : model.lookupConversation().getEditingFile().dateiNameKlartext();
			verarbeiteStatistik(sb, model, metaTagParser, title, parsedLinesAll, parameters, captions, table);
		}

		sb.append(table.buildTable(model));

	}

	private void verarbeiteStatistik(StringBuilder sb, Model model, MetaTagParser metaTagParser, String category,
			List<List<String>> parsedLinesAll, Map<String, String> parameters, List<TextFileMetaTag> captions, HTMLTable table) {

		String splitType = null;
		Integer splitColumn = null;
		TextFileMetaTag splitTag = null;

		List<String> dispTagId = new LinkedList<String>();
		List<String> dispElement = new LinkedList<String>();
		List<Integer> dispColumn = new LinkedList<Integer>();
		List<TextFileMetaTag> dispTag = new LinkedList<TextFileMetaTag>();

		for (String name : parameters.keySet()) {
			if (name.startsWith("STAT_")) {
				String key = StringUtils.removeStart(name, "STAT_").trim(); // DROPDOWN_id1nn;
				String element;
				String value;
				String tagid;

				if (name.startsWith("STAT_SPLIT")) {
					element = StringUtils.substringBefore(key, "_").trim(); // DROPDOWN
					value = StringUtils.substringBefore(parameters.get(name), "#");
					tagid = StringUtils.substringAfter(parameters.get(name), "#");
				} else {
					element = StringUtils.substringBefore(key, "#");
					value = parameters.get(name);
					tagid = StringUtils.substringAfter(key, "#");
				}

				int statItemCol = 0;
				TextFileMetaTag tag = null;
				for (TextFileMetaTag caption : captions) {
					if (StringUtils.equals(caption.getId(), tagid)) {
						tag = caption;
						break;
					} else {
						statItemCol++;
					}
				}
				if (tag == null) { // Bei Anzahl Gesamt gibt es keine TagID
					statItemCol = 0;
				}

				if (StringUtils.equals(element, "SPLIT")) {
					splitType = StringUtils.trimToNull(value);
					splitColumn = statItemCol;
					splitTag = tag;
				} else if (!StringUtils.equals(element, "CATEGORY")) {
					if (Boolean.valueOf(value)) {
						dispTagId.add(element);
						dispElement.add(element);
						dispColumn.add(statItemCol);
						dispTag.add(tag);
					}
				}

			}
		}

		SplittedLineGroups splittedLineGroups = new SplittedLineGroups();
		if (splitType == null) {
			splittedLineGroups.put(null, parsedLinesAll);
		} else {
			splitLineGroups(parsedLinesAll, splitTag, splitType, splitColumn, splittedLineGroups);
		}

		for (String error : metaTagParser.getErrors()) {
			model.lookupConversation().getMeldungen().add(error);
		}

		if (dispElement.isEmpty()) {
			zeigeTabelle(table, metaTagParser, category, splittedLineGroups, captions);
		} else {
			SplittedLineGroups dispersion = new SplittedLineGroups();
			dispersion.assumeLabelsFrom(splittedLineGroups);
			dispersionGroups(dispersion, splittedLineGroups, dispTagId, dispElement, dispColumn, dispTag);
			zeigeTabelle(table, metaTagParser, category, dispersion, null);
		}

	}

	private void dispersionGroups(SplittedLineGroups dispersion, Map<String, List<List<String>>> splittedLineGroups, List<String> dispTagId,
			List<String> dispElement, List<Integer> dispColumn, List<TextFileMetaTag> dispTag) {

		for (String orderKey : splittedLineGroups.keySet()) {
			if (!dispersion.containsKey(orderKey)) {
				dispersion.put(orderKey, new LinkedList<List<String>>());
			}
			for (int i = 0; i < dispElement.size(); i++) {

				BigDecimal summ = new BigDecimal(0);
				if (StringUtils.equals(dispElement.get(i), "SUMM") || StringUtils.equals(dispElement.get(i), "AVG")) {
					for (List<String> line : splittedLineGroups.get(orderKey)) {
						int col = dispColumn.get(i);
						BigDecimal bd = Hilfsklasse.parseBigDecimal(line.get(col));
						summ = summ.add(bd);
					}
				}

				switch (dispElement.get(i)) {
				case "COUNT":
					List<String> resultZeileCount = new LinkedList<String>();
					resultZeileCount.add("Anzahl gesamt :");
					resultZeileCount.add(Integer.toString(splittedLineGroups.get(orderKey).size()));
					dispersion.get(orderKey).add(resultZeileCount);
					break;
				case "SUMM":
					List<String> resultZeileSumm = new LinkedList<String>();
					resultZeileSumm.add("Summe von " + dispTag.get(i).getCaption() + ":");
					resultZeileSumm.add(summ.toString());
					dispersion.get(orderKey).add(resultZeileSumm);
					break;
				case "AVG":
					List<String> resultZeileAvg = new LinkedList<String>();
					resultZeileAvg.add("Mittelwert von " + dispTag.get(i).getCaption() + ":");
					if (splittedLineGroups.get(orderKey).size() > 0) {
						resultZeileAvg.add(
								summ.divide(new BigDecimal(splittedLineGroups.get(orderKey).size()), 2, RoundingMode.HALF_UP).toString());
					} else {
						resultZeileAvg.add("0");
					}
					dispersion.get(orderKey).add(resultZeileAvg);
					break;
				case "VALS":
                    Map<String, Integer> vals = new LinkedHashMap<>();
					for (List<String> line : splittedLineGroups.get(orderKey)) {
						int col = dispColumn.get(i);
						String val = line.get(col);
						if (vals.containsKey(val)) {
							vals.put(val, (vals.get(val)) + 1);
						} else {
                            vals.put(val, NumberUtils.INTEGER_ONE);
						}
					}
					vals = MapSortHelper.sortByValue(vals);
					for (String s : vals.keySet()) {
                        List<String> resultZeileVal = new LinkedList<>();
						resultZeileVal.add("\"" + s + "\":");
						resultZeileVal.add(vals.get(s).toString() + " x");
						dispersion.get(orderKey).add(resultZeileVal);
					}

					break;
				}
			}
		}
	}

	private void splitLineGroups(List<List<String>> parsedLinesAll, TextFileMetaTag splitTag, String splitType, Integer splitColumn,
			SplittedLineGroups splittedLineGroups) {

		if (StringUtils.equals(splitType, "VALUES")) {
			for (String key : splitTag.getArguments()) {
				splittedLineGroups.addKey(key);
			}
		}

		for (List<String> line : parsedLinesAll) {

			String label = null;
			String key = null;

			switch (splitType) {
			case "MONTH":
				label = splitTag.getCaption() + " für den Monat " + line.get(splitColumn).substring(3);
				key = label;
				break;
			case "YEAR":
				label = splitTag.getCaption() + " für das Jahr " + line.get(splitColumn).substring(6);
				key = label;
				break;
			case "VALUES":
				key = line.get(splitColumn);
				if (splittedLineGroups.getLabel(key) == null) {
					label = splitTag.getCaption() + " mit '" + Hilfsklasse.normalizedString(line.get(splitColumn)) + "'";
				}
				break;
			}

			splittedLineGroups.addWithLabel(key, label, line);
		}
	}

	private void zeigeTabelle(HTMLTable tableAll, MetaTagParser metaTagParser, String category, SplittedLineGroups splittedLineGroups,
			List<TextFileMetaTag> captions) {

		int cols = 2;
		if (captions != null && !captions.isEmpty()) {
			cols = captions.size();
		}

		if (category != null) {
			tableAll.addTD(category, cols, HTMLTable.TABLE_HEADER);
			tableAll.addNewRow();
		}

		for (String orderKey : splittedLineGroups.keySet()) {

			if (splittedLineGroups.get(orderKey) != null && !splittedLineGroups.get(orderKey).isEmpty()) {
				if (orderKey != null) {
					tableAll.addTD(splittedLineGroups.getLabel(orderKey), cols, HTMLTable.TABLE_CHAPTER);
					tableAll.addNewRow();
				}

				if (captions != null) {
					for (TextFileMetaTag caption : captions) {
						tableAll.addTD(caption.getCaption(), 1, HTMLTable.TABLE_CHAPTER);
					}
				}
				tableAll.addNewRow();

				for (List<String> line : splittedLineGroups.get(orderKey)) {
					for (String token : line) {
						tableAll.addTD(token, 1, null);
					}
					tableAll.addNewRow();
				}
			}
		}
	}

}
