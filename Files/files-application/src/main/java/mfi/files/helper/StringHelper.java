package mfi.files.helper;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class StringHelper {

	private static String patternID = "[^A-Za-z0-9]";

	public static String idFromName(String name) {
		if (name == null) {
			return "";
		}
		return name.replaceAll(patternID, "");
	}

	public static String langenStringFuerAnzeigeAufbereiten(String string) {
		if (string == null) {
			return "";
		}
		char[] chars = string.toCharArray();
		StringBuilder sb = new StringBuilder();
		int counter = 0;
		for (char c : chars) {
			counter++;
			sb.append(c);
			if (counter > 4) {
				sb.append(' ');
				counter = 0;
			}
		}
		return sb.toString();
	}

	public static String replaceAllIgnoreCase(String original, String regex, String replacement) {
		String r = null;

		int repl = replacement.length();
		int regl = regex.length();
		int dif = repl - regl;
		int cnt = 0;
		String regLc = regex.toLowerCase();
		String buf = original.toLowerCase();
		int index;
		while ((index = buf.indexOf(regLc)) >= 0) {
			buf = buf.substring(index + regl);
			if (r != null) {
				if (dif == 0) {
					r += original.substring(r.length() + dif, r.length() + dif + index);
					r += replacement;
				} else {
					r += original.substring(r.length() - (dif * cnt), r.length() - (dif * cnt) + index);
					r += replacement;
					cnt++;
				}
			} else {
				if (index != 0) {
					r = original.substring(0, index) + replacement;
					cnt++;
				} else {
					r = replacement;
					cnt++;
				}
			}
		}
		if (dif == 0) {
			r += original.substring(r.length() + dif, original.length());
		} else {
			r += original.substring(r.length() - (dif * cnt), original.length());
		}
		return r;
	}

	public static String insertStringsBetween(String original, String target, String before, String after) {

		if (original == null || target == null || !StringUtils.containsIgnoreCase(original, target)) {
			return original;
		}

		String workOriginal = StringUtils.lowerCase(original);
		String workTarget = StringUtils.lowerCase(target);

		String[] tokenArray = StringUtils.splitByWholeSeparator(workOriginal, workTarget);
		StringBuilder returnString = new StringBuilder();
		int posInOriginal = 0;
		if (StringUtils.startsWith(workOriginal, workTarget)) {
			returnString.append(before);
			returnString.append(StringUtils.mid(original, posInOriginal, target.length()));
			posInOriginal += target.length();
			returnString.append(after);
		}
		for (int i = 0; i < tokenArray.length; i++) {
			if (tokenArray[i].length() > 0) {
				returnString.append(StringUtils.mid(original, posInOriginal, tokenArray[i].length()));
				posInOriginal += tokenArray[i].length();
				if (i < tokenArray.length - 1 || i == tokenArray.length - 1 && StringUtils.endsWith(workOriginal, workTarget)) {
					returnString.append(before);
					returnString.append(StringUtils.mid(original, posInOriginal, target.length()));
					posInOriginal += target.length();
					returnString.append(after);
				}
			}
		}
		return returnString.toString();
	}

	public static String stringFromList(List<String> list) {
		if (list == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (String string : list) {
			sb.append(string);
		}
		return sb.toString();
	}

}
