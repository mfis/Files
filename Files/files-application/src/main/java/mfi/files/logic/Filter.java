package mfi.files.logic;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class Filter {

	public static boolean matches(String item, Set<String> includes, Set<String> excludes, Preset preset, IgnoreCase ignoreCase) {

		List<String> items = new LinkedList<String>();
		items.add(item);
		Collection<String> filter = filter(items, includes, excludes, preset, ignoreCase);
		return filter.size() == 1;

	}

	/**
	 * empty include list includes all, exclude list entries confuting previously included entries
	 */
	public static List<String> filter(Collection<String> items, Set<String> includes, Set<String> excludes, Preset preset,
			IgnoreCase ignoreCase) {

		List<String> matchList = new LinkedList<String>();
		items = items == null ? new LinkedList<String>() : items;
		includes = includes == null ? new HashSet<String>() : includes;
		excludes = excludes == null ? new HashSet<String>() : excludes;

		for (String item : items) {

			boolean isIn = includes.isEmpty();
			for (String include : includes) {
				if (matches(item, include, preset, ignoreCase)) {
					isIn = true;
					break;
				}
			}

			if (isIn) {
				for (String exclude : excludes) {
					if (matches(item, exclude, preset, ignoreCase)) {
						isIn = false;
						break;
					}
				}
			}

			if (isIn) {
				matchList.add(item);
			}

		}

		return matchList;
	}

	public static boolean matches(String item, String criterion, Preset preset, IgnoreCase ignoreCase) {
		switch (preset) {
		case STARTS_WITH:
			return ignoreCase == IgnoreCase.YES ? StringUtils.startsWithIgnoreCase(item, criterion) : StringUtils.startsWith(item,
					criterion);
		case ENDS_WITH:
			return ignoreCase == IgnoreCase.YES ? StringUtils.endsWithIgnoreCase(item, criterion) : StringUtils.endsWith(item, criterion);
		case CONTAINS:
			return ignoreCase == IgnoreCase.YES ? StringUtils.containsIgnoreCase(item, criterion) : StringUtils.contains(item, criterion);
		default:
			throw new IllegalArgumentException("Unknown Preset: " + preset);
		}
	}

	public static enum IgnoreCase {
		YES, NO;
	}

	public static enum Preset {
		STARTS_WITH, ENDS_WITH, CONTAINS
	}

}