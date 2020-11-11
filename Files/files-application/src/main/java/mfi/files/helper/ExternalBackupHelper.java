package mfi.files.helper;

import org.apache.commons.lang3.StringUtils;

import mfi.files.io.FilesFile;

public class ExternalBackupHelper {

	public static synchronized String lookupFilename(String filename) {

		// Robustheit bei vorherigem Abbruch - ist die Verschluesselung bereits durchgelaufen, hier bei nicht gefundenem File
		// nach verschluesselter Version suchen
		if (!new FilesFile(filename).exists()) {
			FilesFile fileEnc = FilesFile.lookupServerCryptoFileNameForFileDirectPassword(new FilesFile(filename)); // hier
																																		// IMMER
																																		// DirectPassword
			if (fileEnc.exists()) {
				filename = fileEnc.getAbsolutePath();
			}
		}

		// Falls noch nicht verschluesselt, hier verschluesseln
		if (!(new FilesFile(filename).isServerCryptedDirectPassword())
				&& !(new FilesFile(filename).isServerCryptedHashedPassword()) && !(new FilesFile(filename).isClientCrypted())) {
			filename = new FilesFile(filename).verschluesseleDateiServerseitigMitBasisKey().getAbsolutePath();
		}
		return filename;
	}

	public static synchronized String ermittleDateiNamen(String backupRoot, String environmentName, String filename) {

		String uploadFileName = StringUtils.removeStart(filename, backupRoot);
		uploadFileName = FilesFile.lookupServerCryptoPathNameForFile(new FilesFile(uploadFileName));
		if (!StringUtils.startsWith(uploadFileName, FilesFile.separator)) {
			uploadFileName = FilesFile.separator + uploadFileName;
		}

		uploadFileName = FilesFile.separator + environmentName + uploadFileName;
		return uploadFileName;
	}
}
