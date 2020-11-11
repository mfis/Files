package mfi.files.helper;

import java.util.List;

import mfi.files.logic.MetaTagParser;

public class SatzEinfuegenHelper {

	public static int ermittleFallbackEinfuegePosition(List<String> zeilen, boolean insertTop) {

		int insertPosition;

		MetaTagParser metaTagParser = new MetaTagParser();
		metaTagParser.parseTags(zeilen, false);

		if (insertTop) {
			insertPosition = metaTagParser.getMetaTagLineCount();
		} else {
			insertPosition = metaTagParser.isEofTag() ? zeilen.size() - 1 : zeilen.size();
		}
		return insertPosition;
	}

}
