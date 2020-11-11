package mfi.files.model;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import mfi.files.helper.Hilfsklasse;

public class SplittedLineGroups extends LinkedHashMap<String, List<List<String>>> {

	private static final long serialVersionUID = 1L;
	protected final Map<String, String> labels = new TreeMap<String, String>();

	@Override
	public List<List<String>> get(Object key) {
		String keyNorm = Hilfsklasse.normalizedString(key);
		return super.get(keyNorm);
	}

	public void addKey(String key) {
		String keyNorm = Hilfsklasse.normalizedString(key);
		if (!containsKey(keyNorm)) {
			put((keyNorm), new LinkedList<List<String>>());
		}
	}

	public void addWithLabel(String key, String label, List<String> value) {
		String keyNorm = Hilfsklasse.normalizedString(key);
		if (!containsKey(keyNorm)) {
			put((keyNorm), new LinkedList<List<String>>());
		}
		get(keyNorm).add(value);
		if (label != null) {
			labels.put(keyNorm, label);
		} else {
			if (!labels.containsKey(keyNorm)) {
				labels.put(keyNorm, keyNorm);
			}
		}
	}

	public String getLabel(String key) {
		String keyNorm = Hilfsklasse.normalizedString(key);
		return labels.get(keyNorm);
	}

	public void assumeLabelsFrom(SplittedLineGroups other) {
		labels.putAll(other.labels);
	}

}
