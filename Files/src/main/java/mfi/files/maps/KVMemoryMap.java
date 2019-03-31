package mfi.files.maps;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.ApplicationUtil;
import mfi.files.io.FilesFile;
import mfi.files.logic.Crypto;

public class KVMemoryMap {

	private static KVMemoryMap instance;

	private static Logger logger = LoggerFactory.getLogger(KVMemoryMap.class);
	private static final Object monitor = new Object();
	private static final String FILE_ENCODING = "UTF-8";
	private static final String LIST_START = ".[";
	private static final String LIST_END = "]";

	public static final String PREFIX_TEMPORARY = "temporary.";
	public static final String PREFIX_CRYPTO_ENTRY_ENC = "secureentry_";
	public static final String PREFIX_CRYPTO_ENTRY_DEC = "secureentry.";

	private Map<String, String> kvMap;

	private boolean changeSinceLastSave;

	private boolean initialized;

	private FilesFile dbFilePermanent;

	private String passwordForCryptoEntrys;

	static {
		instance = new KVMemoryMap();
	}

	private Map<String, String> buildNewMap() {
		return Collections.synchronizedMap(new TreeMap<String, String>());
	}

	public boolean isInitialized() {
		return initialized;
	}

	public static KVMemoryMap getInstance() {
		if (instance == null) {
			synchronized (monitor) {
				if (instance == null) {
					instance = new KVMemoryMap();
					logger.info("RE-Initializing KVMemoryMap Singleton");
				}
			}
		}
		return instance;
	}

	public List<String> dumpAsList() {
		List<String> list = new LinkedList<String>();
		Object[] keys = kvMap.keySet().toArray();
		for (Object key : keys) {
			list.add((String) key + " = " + readValueFromKey((String) key));
		}
		return list;
	}

	public void dumpMapToLog() {
		Logger logger = LoggerFactory.getLogger(KVMemoryMap.class);
		Object[] keys = kvMap.keySet().toArray();
		for (Object key : keys) {
			logger.info((String) key + " = " + readValueFromKey((String) key));
		}
	}

	public void reset() {
		synchronized (monitor) {
			kvMap = buildNewMap();
			changeSinceLastSave = true;

		}
	}

	public int countEntries() {
		return kvMap.size();
	}

	public boolean isChangeSinceLastSave() {
		return changeSinceLastSave;
	}

	public boolean load(FilesFile permanent) throws IOException {
		synchronized (monitor) {
			kvMap = buildNewMap();
			boolean successPerm = loadInternal(permanent);
			if (successPerm) {
				dbFilePermanent = permanent;
				initialized = true;
			}
			passwordForCryptoEntrys = Crypto.decryptDateiName(ApplicationUtil.getApplicationProperties().getProperty("entries"),
					readValueFromKey("application.properties.cipherFileNameCryptoKey"), "");
			return successPerm;
		}
	}

	public boolean save() throws IOException {
		synchronized (monitor) {
			saveInternal();
			return true;
		}
	}

	public boolean saveAndReload() throws IOException {
		synchronized (monitor) {
			if (isChangeSinceLastSave()) {
				saveInternal();
				// loadInternal(dbFile);
				return true;
			} else {
				return false;
			}
		}
	}

	private boolean loadInternal(FilesFile file) throws IOException {

		boolean success = true;
		List<?> lines = null;
		try {
			lines = FileUtils.readLines(file, FILE_ENCODING);
		} catch (IOException e) {
			lines = new LinkedList<String>();
			success = false;
			throw new IOException("Error loading KVMemoryMap:", e);
		}

		for (Object line : lines) {
			String lineString = (String) line;
			if (StringUtils.startsWith(lineString, "//") || StringUtils.isBlank(lineString)) {
				// ignore line, it's empty or comment
			} else {
				String[] keyValue = splitFileFormatLineInKeyValue(lineString);
				kvMap.put(keyValue[0], keyValue[1]);
			}
		}
		return success;
	}

	public static String[] splitFileFormatLineInKeyValue(String lineString) {

		String[] token = StringUtils.split(lineString, '=');
		String[] keyValue = new String[2];

		if (token.length == 1) {
			keyValue[0] = token[0].trim();
			keyValue[1] = null;
		} else if (token.length == 2) {
			keyValue[0] = token[0].trim();
			keyValue[1] = token[1].trim();
		} else {
			keyValue[0] = token[0].trim();
			keyValue[1] = StringUtils.substring(lineString, StringUtils.indexOf(lineString, '=') + 1).trim();
		}

		return keyValue;
	}

	private void saveInternal() throws IOException {
		List<String> linesToWritePermanent = new LinkedList<String>();
		Object[] keys = kvMap.keySet().toArray();
		for (Object key : keys) {
			String line = StringUtils.trimToEmpty(((String) key)) + " = " + StringUtils.trimToEmpty(readValueFromKey((String) key));
			linesToWritePermanent.add(line);
		}
		FileUtils.writeLines(dbFilePermanent, FILE_ENCODING, linesToWritePermanent);
		changeSinceLastSave = false;
	}

	public boolean containsKey(String key) {
		return kvMap.containsKey(key);
	}

	public boolean containsKeyIgnoreCase(String key) {
		for (String e : kvMap.keySet()) {
			if (StringUtils.equalsIgnoreCase(key, e)) {
				return true;
			}
		}
		return false;
	}

	public String readValueFromKey(String key) {

		if (StringUtils.startsWith(key, PREFIX_CRYPTO_ENTRY_DEC)) {
			String passwordDecrypted = Crypto.decryptDateiName(passwordForCryptoEntrys,
					readValueFromKeyInternal("application.properties.cipherFileNameCryptoKey"), null);
			String keyWithoutPrefix = StringUtils.removeStart(key, PREFIX_CRYPTO_ENTRY_DEC);
			String keyCryptedForRead = KVMemoryMap.PREFIX_CRYPTO_ENTRY_ENC
					+ Crypto.encryptDateiName(keyWithoutPrefix, passwordDecrypted, null);
			String valueCrypted = readValueFromKeyInternal(keyCryptedForRead);
			String valueDecrypted = Crypto.decryptDateiName(valueCrypted, passwordDecrypted, null);
			return valueDecrypted;
		} else {
			return readValueFromKeyInternal(key);
		}
	}

	private String readValueFromKeyInternal(String keyToReadWith) {

		if (containsKey(keyToReadWith)) {
			return kvMap.get(keyToReadWith);
		} else {
			return null;
		}
	}

	public boolean writeKeyValue(String key, String value, boolean overwrite) {

		synchronized (monitor) {
			if (containsKey(key)) {
				if (overwrite) {
					kvMap.put(key, value);
					changeSinceLastSave = true;
					// logger.info("flag true durch:" + key + " / " + value);
					return true;
				} else {
					return false;
				}
			} else {
				kvMap.put(key, value);
				// logger.info("flag true durch:" + key + " / " + value);
				changeSinceLastSave = true;
				return true;
			}
		}
	}

	private boolean isKeyEntryPartOfList(String keyEntry, String listName) {
		if (StringUtils.startsWith(keyEntry, listName)) {
			String rest = StringUtils.removeStart(keyEntry, listName);
			if (StringUtils.startsWith(rest, LIST_START) && StringUtils.endsWith(rest, LIST_END)) {
				return true;
			}
		}
		return false;
	}

	public List<String> readValueList(String key) {

		List<String> values = new LinkedList<String>();
		Object[] keys = kvMap.keySet().toArray();

		for (Object string : keys) {
			if (isKeyEntryPartOfList((String) string, key)) {
				values.add(readValueFromKey((String) string));
			}
		}
		return values;
	}

	public List<String[]> readListWithPartKey(String partKey) {

		List<String[]> values = new LinkedList<String[]>();
		Object[] keys = kvMap.keySet().toArray();

		for (Object string : keys) {
			if (StringUtils.startsWith((String) string, partKey)) {
				String restKey = StringUtils.removeStart((String) string, partKey);
				values.add(new String[] { restKey, readValueFromKey((String) string) });
			}
		}
		return values;
	}

	public List<String[]> readListWithPartKey(String partKeyStart, String partKeyEnd) {

		List<String[]> values = new LinkedList<String[]>();
		Object[] keys = kvMap.keySet().toArray();

		for (Object string : keys) {
			if (StringUtils.startsWith((String) string, partKeyStart) && StringUtils.endsWith((String) string, partKeyEnd)) {
				String restKey = StringUtils.removeStart((String) string, partKeyStart);
				restKey = StringUtils.removeEnd(restKey, partKeyEnd);
				values.add(new String[] { restKey, readValueFromKey((String) string) });
			}
		}
		return values;
	}

	public boolean deleteValueFromList(String key, String value) {

		Object[] keys = kvMap.keySet().toArray();

		for (Object string : keys) {
			if (isKeyEntryPartOfList((String) string, key) && readValueFromKey((String) string).equals(value)) {
				return deleteKey((String) string);
			}
		}
		return false;
	}

	public boolean writeToValueList(String key, String value, boolean allowDuplicates) {

		boolean write = true;
		if (!allowDuplicates) {
			List<String> values = readValueList(key);
			write = !values.contains(value);
		}
		if (write) {
			writeKeyValue(key + LIST_START + UUID.randomUUID() + LIST_END, value, false);
		}
		return true;
	}

	public boolean deleteKey(String key) {

		if (containsKey(key)) {
			synchronized (monitor) {
				kvMap.remove(key);
				changeSinceLastSave = true;
				// logger.info("flag true durch delete:" + key);
			}
			return true;
		} else {
			return false;
		}
	}

	public void deleteKeyRangeStartsWith(String keyRange) {

		Object[] keys = kvMap.keySet().toArray();
		for (Object string : keys) {
			if (StringUtils.startsWith((String) string, keyRange)) {
				deleteKey((String) string);
			}
		}
	}

}
