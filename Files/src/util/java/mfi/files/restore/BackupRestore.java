package mfi.files.restore;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;

import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;

public class BackupRestore {

	Scanner scanner = new Scanner(System.in);

	// deviceName e.g. "cubie"
	String deviceName = null;
	// cryptoKey e.g. "sherlocked221B"
	String cryptoKey = null;
	// cryptoSuffix e.g. "fjcipher"
	String cryptoSuffix = null;
	// baseDir e.g. "/Users/mfi/Downloads/backups"
	// devicename or localBackupDir will be added automaticly
	String baseDir = null;

	private final Map<String, RestoreFile> backupFiles = new HashMap<String, RestoreFile>();
	private List<String> fileMap = new LinkedList<String>();
	private FilesFile restoreBase;

	private final Pattern versionPatternComplete = Pattern
			.compile("/fjbackupversion[_]{1}[0-9]{4}[_]{1}[0-9]{2}[_]{1}[0-9]{2}[-]{1}[0-9]{9}$");
	private final Pattern versionPatternDate = Pattern.compile("[0-9]{4}[_]{1}[0-9]{2}[_]{1}[0-9]{2}[-]{1}[0-9]{9}$");
	private final String localBackupDir = "archive";

	public static void main(String[] args) throws Exception {
		new BackupRestore().backupRestore();
	}

	public void backupRestore() throws Exception {

		askForProperties();

		writeKVMapEntries();

		readBackupFiles();

		readFileMap();

		prepareBaseDir();

		restore();

		System.out.println("OK");
	}

	private void restore() throws IOException {

		for (String path : backupFiles.keySet()) {
			if (fileMap == null || fileMap.contains(path)) {
				FilesFile restoreFile = new FilesFile(restoreBase + path);
				restoreFile.getParentFile().mkdirs();
				restoreFile = backupFiles.get(path).versionedFileDir.copyFile(null, new FilesFile(restoreFile.getParent()),
						restoreFile.getName(), false);
				if (restoreFile.isServerBaseCrypted()) {
					restoreFile = restoreFile.entschluesseleDateiServerseitig();
				}
				System.out.println("Restored: " + restoreFile.dateiNameUndPfadKlartext());
			}
		}
	}

	private void prepareBaseDir() throws IOException {

		restoreBase = new FilesFile(baseDir + "_RESTORE");
		if (restoreBase.exists()) {
			FileUtils.deleteDirectory(restoreBase);
		}
		restoreBase.mkdirs();
	}

	private void readBackupFiles() throws ParseException {

		SimpleDateFormat dirFormatterVersionDate = Hilfsklasse.lookupSimpleDateFormat(FilesFile.VERSION_DATE_PATTERN);
		List<File> partList = (List<File>) FileUtils.listFilesAndDirs(new File(baseDir), FileFilterUtils.trueFileFilter(),
				FileFilterUtils.directoryFileFilter());

		for (File file : partList) {

			FilesFile fjf = new FilesFile(file.getAbsolutePath());
			String dec = fjf.dateiNameUndPfadKlartext();
			// fjbackupversion_2014_06_20-130647639
			Matcher matcherComplete = versionPatternComplete.matcher(dec);

			if (matcherComplete.find()) {

				Matcher matcherDate = versionPatternDate.matcher(dec);
				matcherDate.find();
				String dateString = dec.substring(matcherDate.start(), matcherDate.end());

				Date date = dirFormatterVersionDate.parse(dateString);
				String backupFile = dec.substring(0, matcherComplete.start());
				backupFile = StringUtils.removeStart(backupFile, baseDir);

				if (backupFiles.containsKey(backupFile)) {
					RestoreFile restoreFile = backupFiles.get(backupFile);
					if (date.after(restoreFile.getVersionDate()) || date.equals(restoreFile.getVersionDate())) {
						backupFiles.put(backupFile, new RestoreFile(fjf, date));
					}
				} else {
					backupFiles.put(backupFile, new RestoreFile(fjf, date));
				}
			}
		}

		for (String path : backupFiles.keySet()) {
			System.out.println("Latest Backup File: " + path + " --> " + backupFiles.get(path).getVersionDate());
		}

		System.out.println("----------------------------------------");
	}

	private void readFileMap() {

		String nameOfFileMapFile = File.separator + "fileMap_" + deviceName + ".map_fjbackups";
		if (!backupFiles.containsKey(nameOfFileMapFile)) {
			// throw new IllegalArgumentException("!! FileMapFile not found: " + nameOfFileMapFile);
			fileMap = null;
			return;
		}

		FilesFile fileMapOriginalFile = backupFiles.get(nameOfFileMapFile).getVersionedFile();
		fileMapOriginalFile.isServerBaseCrypted();
		List<String> fileMapEntries = fileMapOriginalFile.readIntoLines();

		int i = 0;
		for (String mapEntry : fileMapEntries) {
			if ((i % 2 == 0) && StringUtils.isNotBlank(mapEntry)) {
				String decEntry = new FilesFile(mapEntry).dateiNameUndPfadKlartext();
				fileMap.add(decEntry);
				System.out.println("FileMap Entry: " + decEntry);
			}
			i++;
		}

		System.out.println("----------------------------------------");
	}

	private void writeKVMapEntries() {

		ThreadLocalHelper.setConversationID("0");
		ThreadLocalHelper.setModelPassword("0");

		KVMemoryMap.getInstance().writeKeyValue("application.properties.cipherFileNameCryptoKey", cryptoKey, true);
		KVMemoryMap.getInstance().writeKeyValue("application.properties.cipherFileSuffix", cryptoSuffix, true);
		KVMemoryMap.getInstance().writeKeyValue("application.properties.timezone", "CET", true);
	}

	private void askForProperties() {

		// DeviceName
		do {
			deviceName = askForString("Enter Device Name:");
		} while (!checkNotBlank(deviceName));

		// CryptoKey
		do {
			cryptoKey = askForString("Enter Crypto Key:");
		} while (!checkNotBlank(cryptoKey));

		// CryptoSuffix
		do {
			cryptoSuffix = askForString("Enter Crypto Suffix:");
		} while (!checkNotBlank(cryptoSuffix));

		// BaseDir
		do {
			baseDir = askForString("Enter Base Directory (un-encrypted part until Device Name):");
		} while (!checkBaseDir(baseDir));
	}

	private String askForString(String s) {

		System.out.println(s);
		String input = scanner.nextLine();
		return StringUtils.trimToNull(input);
	}

	private boolean checkBaseDir(String string) {

		if (!StringUtils.endsWithIgnoreCase(string, File.separator)) {
			baseDir = baseDir + File.separator;
		}

		if (!StringUtils.endsWithIgnoreCase(string, deviceName + File.separator)
				&& !StringUtils.endsWithIgnoreCase(string, localBackupDir + File.separator)) {

			if (new FilesFile(baseDir + deviceName).exists()) {
				baseDir = baseDir + deviceName;
			} else {
				baseDir = baseDir + localBackupDir;
			}
		}
		if (!new FilesFile(baseDir).exists()) {
			return false;
		}

		baseDir = StringUtils.removeEnd(baseDir, File.separator);

		return true;
	}

	private boolean checkNotBlank(String string) {
		return StringUtils.isNotBlank(string);
	}

	private class RestoreFile {

		private final FilesFile versionedFileDir;
		private final Date versionDate;

		public RestoreFile(FilesFile fjf, Date date) {
			versionedFileDir = fjf;
			versionDate = date;
		}

		public FilesFile getVersionedFile() {
			String file = versionedFileDir.getAbsolutePath();
			if (!StringUtils.endsWith(file, File.separator)) {
				file = file + File.separator;
			}
			FilesFile[] content = versionedFileDir.listFiles();
			if (content == null || content.length != 1 || !content[0].isFile()) {
				throw new IllegalArgumentException("Versioned file fot found in dir:" + file);
			}
			return content[0];
		}

		public Date getVersionDate() {
			return versionDate;
		}

	}

}
