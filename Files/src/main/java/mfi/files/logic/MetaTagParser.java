package mfi.files.logic;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.StringHelper;
import mfi.files.io.FilesFile;
import mfi.files.model.CategoryMarker;
import mfi.files.model.TextFileMetaTag;
import mfi.files.model.TextFileMetaTagName;

public class MetaTagParser {

	private static final String QUOTE = "\"";
	private static final String BRACE_OPEN = "{";
	private static final String BRACE_CLOSE = "}";
	private static final String TAG_PREFIX = "&&";
	private static final char COMMA = ',';
	private static final char SPACE = ' ';
	private static final String CAPTION_DELIMITER = ":";

	List<TextFileMetaTag> parsedTags = null;

	private final List<String> errors = new LinkedList<String>();

	private int metaTagLineCount = 0;

	private boolean eofTag = false;

	private int linesInFile = 0;

	public static boolean isMetaTagLine(String line) {

		if (!StringUtils.contains(line, TAG_PREFIX)) {
			return false;
		}

		line = StringUtils.remove(line, SPACE);

		return StringUtils.startsWithIgnoreCase(line, TAG_PREFIX + TextFileMetaTagName.META.name())
				|| StringUtils.startsWithIgnoreCase(line, TAG_PREFIX + TextFileMetaTagName.BOF.name()) // legacy
				|| StringUtils.startsWithIgnoreCase(line, TAG_PREFIX + TextFileMetaTagName.EOF.name()); // legacy
	}

	public boolean isMetaTagLine(int lineNumberBaseZero) {

		if (lineNumberBaseZero < metaTagLineCount) {
			return true;
		}

		return (eofTag && (lineNumberBaseZero == (linesInFile - 1)));

	}

	public static boolean isEndOfFileTagLine(String line) {

		if (!StringUtils.contains(line, TAG_PREFIX)) {
			return false;
		}

		line = StringUtils.remove(line, SPACE);

		return StringUtils.startsWithIgnoreCase(line, TAG_PREFIX + TextFileMetaTagName.EOF.name());
	}

	public List<TextFileMetaTag> parseTags(String content, boolean standardTagIfEmpty) {

		if (StringUtils.isBlank(content)) {
			List<TextFileMetaTag> tags = new LinkedList<TextFileMetaTag>();
			return tags;
		}
		content = StringUtils.replace(content, "\r\n", "\n");
		String[] linesArray = StringUtils.split(content, "\n");
		List<String> linesList = new LinkedList<String>();
		for (String string : linesArray) {
			linesList.add(string);
		}
		return parseTags(linesList, standardTagIfEmpty);
	}

	public List<TextFileMetaTag> parseTags(List<String> lines, boolean standardTagIfEmpty) {

		List<TextFileMetaTag> tags = new LinkedList<TextFileMetaTag>();
		metaTagLineCount = 0;
		eofTag = false;
		linesInFile = 0;
		errors.clear();

		if (lines == null || lines.size() == 0) {
			return tags;
		}
		linesInFile = lines.size();

		for (String line : lines) {

			if (!isMetaTagLine(line)) {
				break;
			}

			metaTagLineCount++;
			line = removeSpacesAroundToken(line, BRACE_OPEN);
			line = removeSpacesAroundToken(line, BRACE_CLOSE);
			line = removeSpacesAroundToken(line, TAG_PREFIX);

			String[] tagsStrings = StringUtils.splitByWholeSeparator(line, TAG_PREFIX);
			int id = 0;
			for (String tagString : tagsStrings) {

				String tagName = StringUtils.trim(StringUtils.substringBefore(tagString, BRACE_OPEN));
				String caption = null;
				if (StringUtils.contains(tagName, CAPTION_DELIMITER)) {
					caption = StringUtils.trim(StringUtils.substringAfter(tagName, CAPTION_DELIMITER));
					tagName = StringUtils.trim(StringUtils.substringBefore(tagName, CAPTION_DELIMITER));
				}

				String argumentsString = StringUtils.substringBetween(tagString, BRACE_OPEN, BRACE_CLOSE);
				TextFileMetaTag tag = new TextFileMetaTag(tagName);
				if (argumentsString != null && !tag.isUnknownTag()) {
					boolean isInQuote = false;
					int i = 0;
					int prev = 0;
					for (char c : argumentsString.toCharArray()) {
						if (c == QUOTE.charAt(0)) {
							isInQuote = !isInQuote;
						}
						if ((c == COMMA && !isInQuote && i != prev) || argumentsString.length() == i + 1) {
							String a = StringUtils.trim((StringUtils.substring(argumentsString, prev, i + (c == COMMA ? 0 : 1))));
							a = StringUtils.trim(a);
							a = StringUtils.removeStart(a, QUOTE);
							a = StringUtils.removeEnd(a, QUOTE);
							a = StringUtils.trim(a);
							tag.getArguments().add(a);
							prev = i + 1;
						}
						i++;
					}
				}
				if (!tag.isUnknownTag() && tag.getTextFileMetaTagName() != TextFileMetaTagName.META) {
					if (caption == null) {
						caption = tag.getTextFileMetaTagName().getStandardName();
					}
					tag.setCaption(caption);
					tag.setId("id" + StringHelper.idFromName(caption) + (++id) + tag.getTextFileMetaTagName().name());
					tags.add(tag);
				} else if (tag.isUnknownTag()) {
					errors.add("Unbekannter Tag:" + tagName);
				}
			}

		}

		parsedTags = tags;

		if (!hasFormatTags()) {
			TextFileMetaTag stdTag = new TextFileMetaTag(TextFileMetaTagName.TEXT);
			stdTag.setId("standardTextTag");
			tags.add(stdTag);
			parsedTags = tags;
		}

		eofTag = isEndOfFileTagLine(lines.get(lines.size() - 1));

		return tags;
	}

	public List<TextFileMetaTag> parseCaptions(List<String> lines) {

		List<TextFileMetaTag> formatTags = parseTags(lines, true);
		List<TextFileMetaTag> captions = new LinkedList<TextFileMetaTag>();

		for (TextFileMetaTag tag : formatTags) {
			switch (tag.getTextFileMetaTagName()) {
			case CHOICE:
			case TEXT:
			case NUMBER:
			case DATE:
				captions.add(tag);
				break;
			default:
				// noop
			}
		}

		return captions;
	}

	public List<List<String>> parseLines(List<String> lines, String category) {

		errors.clear();
		List<String> tempErrors = new LinkedList<String>();
		List<TextFileMetaTag> formatTags = parseTags(lines, true);
		List<List<String>> zeilen = new LinkedList<List<String>>();
		int parsableLinesCounter = 0;

		CategoryMarker marker = null;
		if (category != null) {
			marker = FilesFile.findCategoryMarker(category, lines, false, 0, false);
		}

		int lineNumber = 0;
		for (String line : lines) {
			boolean inMarker = marker == null || (marker != null && lineNumber >= marker.firstEntry && lineNumber <= marker.lastEntry);
			boolean isCategoryLine = StringUtils.startsWith((StringUtils.trimToEmpty(line)), "==");
			if (!isMetaTagLine(line) && inMarker && !isCategoryLine && !StringUtils.isBlank(line)) {
				int pos = 0;
				boolean error = false;
				List<String> zeile = new LinkedList<String>();
				for (TextFileMetaTag tag : formatTags) {
					switch (tag.getTextFileMetaTagName()) {
					case BULLET:
						pos += tag.getTextFileMetaTagName().getFixContent().length();
						break;
					case CHOICE:
					case TEXT:
					case NUMBER:
						boolean foundThis = false;
						boolean foundNext = false;
						String textToAdd = null;
						int spacerOffset = 0;
						for (TextFileMetaTag t : formatTags) {
							if (StringUtils.equals(t.getId(), tag.getId())) {
								foundThis = true;
							} else if (foundThis && t.getTextFileMetaTagName() == TextFileMetaTagName.SPACE) {
								spacerOffset++;
							} else if (foundThis && t.getTextFileMetaTagName() == TextFileMetaTagName.FORMAT) {
								String argString = "";
								for (String arg : t.getArguments()) {
									argString += arg;
								}
								int end = StringUtils.indexOf(line, argString, pos);
								if (end == -1) {
									error = true;
								} else {
									textToAdd = StringUtils.substring(line, pos, end);
									foundNext = true;
								}
								break;
							}
						}
						if (!foundThis || !foundNext) {
							textToAdd = StringUtils.substring(line, pos);
						}
						if (textToAdd != null) {
							if (tag.argumentsAreNumbers() && !Hilfsklasse.isNumeric(textToAdd)) {
								error = true;
							} else {
								if (tag.argumentsAreNumbers()) {
									zeile.add(Hilfsklasse.normalizedString(StringUtils.trimToEmpty(textToAdd)));
								} else {
									zeile.add(StringUtils.trimToEmpty(textToAdd));
								}
								pos += textToAdd.length() - spacerOffset;
							}
						}
						break;
					case DATE:
						String token = StringUtils.substring(line, pos, pos + tag.getTextFileMetaTagName().getFixContent().length());
						zeile.add(token);
						pos += tag.getTextFileMetaTagName().getFixContent().length();
						break;
					case FORMAT:
						for (String arg : tag.getArguments()) {
							if (StringUtils.equals(arg, StringUtils.substring(line, pos, pos + arg.length()))) {
								pos += arg.length();
							} else {
								error = true;
							}
						}
						break;
					case SPACE:
						pos += tag.getTextFileMetaTagName().getFixContent().length();
						break;
					default:
						// noop
					}
				}
				if (error || line.length() < pos - 1) {
					if (category == null) {
						tempErrors.add("Zeile konnte nicht geparst werden:" + line);
					} else {
						tempErrors.add("Zeile in Kategorie '" + category + "' konnte nicht geparst werden:" + line);
					}
					// zeilen.add(zeile); // FIXME: DELETE
				} else {
					zeilen.add(zeile);
					parsableLinesCounter++;
				}
			}
			lineNumber++;
		}

		if (parsableLinesCounter == 0) {
			if (category == null) {
				tempErrors.add("Datei konnte nicht geparst werden!");
			} else {
				errors.add("Kategorie konnte nicht geparst werden: " + category);
			}
		} else {
			errors.addAll(tempErrors);
		}

		return zeilen;
	}

	public boolean hasFormatTags() {

		if (parsedTags == null) {
			return false;
		}

		boolean formatTag = false;
		for (TextFileMetaTag tag : parsedTags) {
			if (tag.getTextFileMetaTagName().isFormatTag()) {
				formatTag = true;
				break;
			}
		}

		return formatTag;
	}

	public boolean hasTag(TextFileMetaTagName textFileMetaTagName) {

		if (parsedTags == null) {
			return false;
		}

		for (TextFileMetaTag tag : parsedTags) {
			if (tag.getTextFileMetaTagName() == textFileMetaTagName) {
				return true;
			}
		}

		return false;
	}

	private String removeSpacesAroundToken(String string, String token) {

		String a = null;
		String b = string;
		while (!StringUtils.equals(a, b)) {
			a = b;
			b = StringUtils.replace(b, SPACE + token, token);
			b = StringUtils.replace(b, token + SPACE, token);
		}
		return b;
	}

	public List<String> getErrors() {
		return errors;
	}

	public int getMetaTagLineCount() {
		return metaTagLineCount;
	}

	public boolean isEofTag() {
		return eofTag;
	}

}
