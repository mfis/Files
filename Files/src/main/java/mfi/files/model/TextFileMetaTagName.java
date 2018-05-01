package mfi.files.model;

public enum TextFileMetaTagName {

	META(false, null, null), PUSH_OFF(false, null, null), NEW_ITEM_ON_TOP(false, null, null), NO_CATEGORIES(false, null, null), //
	BULLET(true, null, "*"), NEWLINE(true, null, "\n"), SPACE(true, null, " "), DATE(true, "Datum", "dd.MM.yyyy"), FORMAT(true, null,
			null), TEXT(true, "Eintrag", null), NUMBER(true, "Wert", null), CHOICE(true, "Auswahl", null), //
	BOF(false, null, null), EOF(false, null, null); // backward compatiblity

	private boolean formatTag;

	private String standardName;

	private String fixContent;

	private TextFileMetaTagName(boolean formatTag, String standardName, String fixContent) {
		this.formatTag = formatTag;
		this.standardName = standardName;
		this.fixContent = fixContent;
	}

	public boolean isFormatTag() {
		return formatTag;
	}

	public String getStandardName() {
		return standardName;
	}

	public String getFixContent() {
		return fixContent;
	}
}