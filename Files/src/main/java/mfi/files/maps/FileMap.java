package mfi.files.maps;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.StringHelper;
import mfi.files.io.FilesFile;
import mfi.files.logic.Crypto;

public class FileMap {

	private static FileMap instance;

	private static final String FILE_ENCODING = "UTF-8";

	private static final Object monitor = new Object();

	private static Map<String, String> map;

	private static boolean initialized;

	private static FilesFile dbFile;

	private static Logger logger = LoggerFactory.getLogger(FileMap.class);

	static {
		instance = new FileMap();
	}

	private Map<String, String> buildNewMap() {
		return Collections.synchronizedMap(new TreeMap<String, String>());
	}

	private FileMap() {
		map = buildNewMap();
		initialized = false;
		dbFile = null;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public static FileMap getInstance() {
		if (instance == null) {
			synchronized (monitor) {
				if (instance == null) {
					instance = new FileMap();
					logger.info("RE-Initializing FileMap Singleton");
				}
			}
		}
		return instance;
	}

	public void reset() {
		synchronized (monitor) {
			map = buildNewMap();

		}
	}

	public boolean load(FilesFile permanent) throws IOException {
		synchronized (monitor) {
			map = buildNewMap();
			boolean successPerm = loadInternal(permanent);
			if (successPerm) {
				dbFile = permanent;
				initialized = true;
			}
			return successPerm;
		}
	}

	public boolean save() throws IOException {
		synchronized (monitor) {
			saveInternal();
			return true;
		}
	}

	private boolean loadInternal(FilesFile file) throws IOException {

		boolean success = true;
		List<String> lines = null;

		if (file == null) {
			logger.warn("No FileMap file to load defined.");
			return success;
		}

		if (!file.exists()) {
			file.createNewFile();
			return success;
		}

		try {
			lines = FileUtils.readLines(file, FILE_ENCODING);
		} catch (IOException e) {
			lines = new LinkedList<String>();
			success = false;
			throw new IOException("Error loading FileMap:", e);
		}

		Iterator<String> iter = lines.listIterator();
		while (iter.hasNext()) {
			map.put(iter.next(), iter.hasNext() ? iter.next() : null);
		}

		return success;
	}

	private void saveInternal() throws IOException {

		if (dbFile == null) {
			logger.error("No FileMap file to save defined.");
			return;
		}

		if (!dbFile.exists()) {
			dbFile.createNewFile();
		}

		List<String> linesToWritePermanent = new LinkedList<String>();
		Object[] keys = map.keySet().toArray();
		for (Object key : keys) {
			linesToWritePermanent.add(StringUtils.trimToEmpty((String) key));
			linesToWritePermanent.add(StringUtils.trimToEmpty(readEditor(new File((String) key))));
		}

		String currentSavedFileHash = Crypto.hashString(StringHelper.stringFromList(dbFile.readIntoLines()));
		String currentWorkingMapHash = Crypto.hashString(StringHelper.stringFromList(linesToWritePermanent));

		if (!StringUtils.equals(currentSavedFileHash, currentWorkingMapHash)) {
			logger.info("save()");
			FileUtils.writeLines(dbFile, FILE_ENCODING, linesToWritePermanent);
			dbFile.backupFile(null);
		}

	}

	private boolean containsKey(String key) {
		return map.containsKey(key);
	}

	public String updateEditor(File file, String user) {
		return map.put(file.getAbsolutePath(), user);
	}

	public String readEditor(File file) {
		if (containsKey(file.getAbsolutePath())) {
			return StringUtils.trimToNull(map.get(file.getAbsolutePath()));
		} else {
			return null;
		}
	}

	public void updateList(Collection<FilesFile> list) {
		synchronized (monitor) {
			for (FilesFile file : list) {
				if (!containsKey(file.getAbsolutePath())) {
					logger.info("update " + file.getAbsolutePath());
					updateEditor(file, null);
				}
			}
		}
	}

	public void deleteOrphans() {
		synchronized (monitor) {
			Object[] keys = map.keySet().toArray();
			for (Object key : keys) {
				File file = new File((String) key);
				if (!file.exists()) {
					logger.info("remove " + key);
					map.remove(key);
				}
			}
		}
	}

}
