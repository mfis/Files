package mfi.files.htmlgen;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import mfi.files.model.Model;

public class HTMLTable extends HTMLUtils {

	private static final String END_TABLE = "</table>";
	private static final String END_TR = "</tr>";
	private static final String END_TD = "</td>";
	private static final String END_A = "</a>";
	private static final String A_OFFEN = "<a";
	private static final String _BLANK = "_blank";
	private static final String TARGET = "target";
	private static final String HREF = "href";
	private static final String KL_ZU = ">";
	private static final String COLSPAN = "colspan";
	private static final String TD_OFFEN = "<td";
	private static final String TR = "<tr>";

	public static final String ALIGN_CENTER = HTMLUtils.buildAttribute("align", "center");
	public static final String TABLE_HEADER = HTMLUtils.buildAttribute("class", "tableheader");
	public static final String TABLE_HEADER_TOP = HTMLUtils.buildAttribute("class", "tableheader top");
	public static final String TABLE_TOP = HTMLUtils.buildAttribute("class", "top");
	public static final String TABLE_CHAPTER = HTMLUtils.buildAttribute("class", "chapter");
	public static final String TABLE_ALT = HTMLUtils.buildAttribute("class", "alt");
	public static final String TABLE_ALT_TOP = HTMLUtils.buildAttribute("class", "alt top");
	public static final String NO_BORDER = HTMLUtils.buildAttribute("class", "noborder");

	private int currentCols;

	private final List<TableData> tds;

	private final List<Integer> colCounts;

	private String id = null;

	private boolean widthTo100Percent = false;

	private boolean newLinesForRows = true;
	private boolean newLinesAfterTableEnd = true;

	public HTMLTable() {
		currentCols = 0;
		tds = new LinkedList<TableData>();
		colCounts = new LinkedList<Integer>();
		id = null;
		widthTo100Percent = false;
	}

	public HTMLTable(String tableID) {
		currentCols = 0;
		tds = new LinkedList<TableData>();
		colCounts = new LinkedList<Integer>();
		id = tableID;
		widthTo100Percent = false;
	}

	private String checkNewLineForRows() {
		if (newLinesForRows) {
			return newLine();
		} else {
			return "";
		}
	}

	private String checkNewLineForTableEnd() {
		if (newLinesAfterTableEnd) {
			return newLine();
		} else {
			return "";
		}
	}

	public void addTD(String text, String attributes) {

		addTD(text, 1, attributes);
	}

	public void addTD(String text, int colspan, String attributes) {

		tds.add(new TableData(text, null, null, colspan, attributes));
		currentCols++;
	}

	public void addTDLinkExtern(String text, String link, int colspan, String attributes) {

		tds.add(new TableData(text, link, null, colspan, attributes));
		currentCols++;
	}

	public void addTDSource(String source, int colspan, String attributes) {

		tds.add(new TableData(null, null, source, colspan, attributes));
		currentCols++;
	}

	public void addNewRowIf(boolean b) {
		if (b) {
			addNewRow();
		}
	}

	public void addNewRow() {

		colCounts.add(currentCols);
		currentCols = 0;
	}

	public String buildTable(Model model) {

		if (currentCols > 0) {
			addNewRow();
		}

		StringBuilder sb = new StringBuilder(colCounts.size() * 200);
		Iterator<Integer> iterRows = colCounts.iterator();
		Iterator<TableData> iterData = tds.iterator();

		String tablewidth = "";
		if (model != null && model.isPhone() || widthTo100Percent) {
			widthTo100Percent = true;
			tablewidth = buildAttribute("width", "100%");
		}

		sb.append("<table" + tablewidth + buildAttribute("id", id) + KL_ZU);

		boolean innerTableOpen = false;
		while (iterRows.hasNext()) {
			StringBuilder sbRow = new StringBuilder(600);
			sbRow.append(TR);
			int rows = iterRows.next();
			for (int i = 0; i < rows; i++) {
				TableData tableData = iterData.next();
				sbRow.append(TD_OFFEN);
				if (tableData.colspan != 1) {
					sbRow.append(buildAttribute(COLSPAN, tableData.colspan));
				}
				if (tableData.attributes != null) {
					sbRow.append(tableData.attributes);
				}
				sbRow.append(KL_ZU);
				if (tableData.link != null && tableData.source == null) {
					sbRow.append(A_OFFEN);
					sbRow.append(buildAttribute(HREF, tableData.link) + buildAttribute(TARGET, _BLANK));
					sbRow.append(KL_ZU);
					sbRow.append(buildValueNotEmpty(tableData.text));
					sbRow.append(END_A);
				} else {
					if (tableData.link == null && tableData.source != null) {
						sbRow.append(tableData.source);
					} else {
						if (tableData.link == null && tableData.source == null) {
							sbRow.append(A_OFFEN);
							sbRow.append(KL_ZU);
							sbRow.append(buildValueNotEmpty(tableData.text));
							sbRow.append(END_A);
						}
					}
				}
				sbRow.append(END_TD);
			}
			sbRow.append(END_TR);
			sbRow.append(checkNewLineForRows());

			String rowString = sbRow.toString();

			if (innerTableOpen) {
				sb.append(rowString);
			} else {
				// InnerTable ist geschlossen
				sb.append("<tr><td colspan='" + rows + "' class='" + NOTVISIBLE + "'><table width='100%' class='" + NOTVISIBLE + "' "
						+ buildAttribute("id", "inner_" + id) + ">" + rowString);
				innerTableOpen = true;
			}
		}

		if (innerTableOpen) {
			sb.append("</table></td></tr>");
		}

		sb.append(END_TABLE);

		sb.append(checkNewLineForTableEnd());

		if (widthTo100Percent) {
			return sb.toString();
		} else {
			return HTMLUtils.contentBreakMitTrennlinie() + sb.toString();
		}

	}

	private class TableData {

		private final String text;
		private final String link;
		private final String source;
		private final int colspan;
		private final String attributes;

		public TableData(String text, String link, String source, int colspan, String attributes) {
			this.text = text;
			this.link = link;
			this.source = source;
			this.attributes = attributes;
			this.colspan = colspan;
		}
	}

	public void setWidthTo100Percent(boolean widthTo100Percent) {
		this.widthTo100Percent = widthTo100Percent;
	}

	public void setNewLinesForRows(boolean newLinesForRows) {
		this.newLinesForRows = newLinesForRows;
	}

	public void setNewLinesAfterTableEnd(boolean newLinesAfterTableEnd) {
		this.newLinesAfterTableEnd = newLinesAfterTableEnd;
	}

}
