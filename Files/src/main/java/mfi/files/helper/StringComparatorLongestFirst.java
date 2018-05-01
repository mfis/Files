package mfi.files.helper;

import java.util.Comparator;

public class StringComparatorLongestFirst implements Comparator<String> {

	@Override
	public int compare(String s1, String s2) {

		if (s1 == null && s2 == null) {
			return 0;
		}
		if (s1 == null) {
			return 1;
		}
		if (s2 == null) {
			return -1;
		}
		return ((Integer) s1.length()).compareTo(s2.length()) * -1;
	}
}
