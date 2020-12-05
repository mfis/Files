package mfi.files.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.SatzEinfuegenHelper;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.logic.Crypto;
import mfi.files.logic.DateiBackup;
import mfi.files.logic.Filter;
import mfi.files.logic.Filter.IgnoreCase;
import mfi.files.logic.Filter.Preset;
import mfi.files.logic.MetaTagParser;
import mfi.files.logic.Security;
import mfi.files.maps.FileMap;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.CategoryMarker;
import mfi.files.model.FileLockException;
import mfi.files.model.Model;
import mfi.files.model.TextFileMetaTagName;

public class FilesFile extends File {

    public static final String APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY =
        "application.properties.cipherFileNameCryptoKey";

    private static final long serialVersionUID = 1L;

	public static final String VERSION_DATE_PATTERN = "yyyy_MM_dd-HHmmssSSS";
	public final static int BLOCK_SIZE = 8192;

	private static Object fileLockMonitor = new Object();
	private static final Logger logger = LoggerFactory.getLogger(FilesFile.class);

	public static final String TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE = "_";

	private final static String KVM_PRE_LOCK = "temporary.manual.filelock.";
	private final static String KVM_SUF_LOCK_USER = ".user";
	private final static String KVM_SUF_LOCK_TIME = ".timestamp";

	private static List<String> editableFiles;
	private static List<String> viewableImages;
	private static List<String> browserCompatibleFiles;

	private String nameDecrypted = null;

	private String pendingPassword = null; // aktuelles Passwort
	private String prospectivePassword = null; // zukuenftiges Passwort
	private boolean clientKnowsPassword = false;

	// Fuers Polling auf Dateiaenderung letztenBekannten Hash merken;
	private String lastKnownHash;

	public FilesFile(URI uri) throws Exception {
		super(uri);
		throw new Exception("Method not implemented");
	}

	public FilesFile(String parent, String child) throws Exception {
		super(parent, child);
		throw new Exception("Method not implemented");
	}

	private FilesFile(File parent, String child) throws Exception {
		super(parent, child);
		throw new Exception("Method not implemented");
	}

	public FilesFile(String pathname) {
		super((pathname));
	}

	public String dateiNameKlartext() {
        try {
		if (isServerCryptedDirectPassword()) {
			return lookupNameDecryptedServerDirectPassword();
		} else if (isServerCryptedHashedPassword()) {
			return lookupNameDecryptedServerHashedPassword();
		} else if (isClientCrypted()) {
			return lookupNameDecryptedClient();
		} else {
			return getName();
		}
    } catch (Exception e) {
        logger.error("Fehler bei dateiNameKlartext():", e);
        return null;
    }
	}

	public boolean isFileSystemRoot() {
		return getAbsolutePath().equals("/");
	}

	public long dateiGroesseEntschluesselt() throws IOException {

		if (isServerCryptedDirectPassword() || isServerCryptedHashedPassword()) {
			BufferedInputStream inputstream = new BufferedInputStream(new FileInputStream(this));
			String pendingKlartext = Crypto.decryptString(pendingPassword, ThreadLocalHelper.getModelPassword());
			return Crypto.sizeOfDecryptedStream(inputstream, pendingKlartext);
		} else {
			return this.length();
		}
	}

	public String dateiNameUndPfadKlartext() {

		String fullpath = this.getAbsolutePath();
		String[] parts = StringUtils.split(fullpath, File.separatorChar);

		String decrypted = "";
		for (String string : parts) {
			if (StringUtils.equalsIgnoreCase(lookupCipherFileSuffixServerDirectPassword(), StringUtils.substringAfterLast(string, "."))) {
				decrypted = decrypted + File.separatorChar
						+ Crypto.decryptDateiName(string,
								KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
								lookupCipherFileSuffixServerDirectPassword());
			} else if (StringUtils.equalsIgnoreCase(lookupCipherFileSuffixServerHashedPassword(),
					StringUtils.substringAfterLast(string, "."))) {
				decrypted = decrypted + File.separatorChar
						+ Crypto.decryptDateiName(string,
								KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
								lookupCipherFileSuffixServerHashedPassword());
			} else if (StringUtils.equalsIgnoreCase(lookupCipherFileSuffixClient(), StringUtils.substringAfterLast(string, "."))) {
				decrypted = decrypted + File.separatorChar
						+ Crypto.decryptDateiName(string,
								KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
								lookupCipherFileSuffixClient());
			} else {
				decrypted = decrypted + File.separatorChar + string;
			}
		}
		return decrypted;
	}

	public String dateiNameUndPfadEscaped() {
		String rep = dateiNameUndPfadKlartext();
        rep = StringUtils.replace(rep, "/", "_");
        rep = StringUtils.replace(rep, ".", "-");
		rep = StringUtils.replace(rep, "=", "-");
        rep = Security.cleanUpKvSubKey(rep);
		return rep;
	}

	public String getChiffre() {
		if (isServerCryptedDirectPassword() || isServerCryptedHashedPassword() || isClientCrypted()) {
			return StringUtils.left(getName(), 5) + "-" + StringUtils.substringAfterLast(getName(), ".");
		} else {
			return null;
		}
	}

	public boolean isFileWriteableForApplication() {

		boolean writeFlag = this.canWrite();
		boolean filesystemWriteable = java.nio.file.Files.isWritable(java.nio.file.Paths.get(this.getAbsolutePath()));

		return writeFlag && filesystemWriteable;
	}

	public boolean isParentOf(String file) {
		if (StringUtils.isBlank(file)) {
			return false;
		} else {
			return isParentOf(new FilesFile(file));
		}
	}

	public boolean isParentOf(File file) {
		if (file == null) {
			return false;
		}
		return StringUtils.startsWith(file.getAbsolutePath(), this.getAbsolutePath());
	}

	@Override
	public FilesFile[] listFiles() {
		File[] fileArray = super.listFiles();
		FilesFile[] filesArray = new FilesFile[fileArray.length];
		for (int i = 0; i < fileArray.length; i++) {
			filesArray[i] = new FilesFile(fileArray[i].getAbsolutePath());
		}
		return filesArray;
	}

	public static FilesFile createTempFile(String prefix, String suffix) throws IOException {
		File file = File.createTempFile(prefix, suffix);
		FilesFile filesFile = new FilesFile(file.getAbsolutePath());
		return filesFile;
	}

	@Override
	public boolean renameTo(File dest) {

		if (!(dest instanceof FilesFile)) {
			throw new RuntimeException("Umbenennen: Uebergebenes File ist keine Instanz von FilesFile");
		}

		boolean altIsServerCrypted = isServerCryptedDirectPassword() || isServerCryptedHashedPassword();
		boolean neuIsServerCrypted = isServerCryptedDirectPassword(new FilesFile(dest.getAbsolutePath()))
				|| isServerCryptedHashedPassword(new FilesFile(dest.getAbsolutePath()));
		boolean neuIsClientCrypted = isClientCrypted(new FilesFile(dest.getAbsolutePath()));

		// alt und neu ohne Verschluesselung - der einfachste Fall
		if (!altIsServerCrypted && !neuIsServerCrypted) {
			return super.renameTo(dest);
		}

		try {
			if (altIsServerCrypted && neuIsServerCrypted) {
				boolean ok = super.renameTo(dest);
				((FilesFile) dest).forgetPasswords();
				this.forgetPasswords();
				return ok;
			}
			if (altIsServerCrypted && neuIsClientCrypted) {
				boolean ok = super.renameTo(dest);
				((FilesFile) dest).forgetPasswords();
				this.forgetPasswords();
				return ok;
			}

		} catch (Exception e) {
			logger.error("Fehler beim Umbenennen:", e);
			return false;
		}
		// duerfte nie vorkommen
		return false;
	}

	public static FilesFile lookupServerCryptoFileNameForFileDirectPassword(FilesFile file) {

		FilesFile cryptedDest = null;
		if (file.isServerCryptedDirectPassword()) {
			cryptedDest = file;
		} else {
			String nameVerschluesselt = Crypto.encryptDateiName(file.getName(),
					KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
					lookupCipherFileSuffixServerDirectPassword());
			cryptedDest = new FilesFile(file.getParent() + File.separator + nameVerschluesselt);
		}
		return cryptedDest;
	}

	public static FilesFile lookupServerCryptoFileNameForFileHashedPassword(FilesFile file) {

		FilesFile cryptedDest = null;
		if (file.isServerCryptedHashedPassword()) {
			cryptedDest = file;
		} else {
			String nameVerschluesselt = Crypto.encryptDateiName(file.getName(),
					KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
					lookupCipherFileSuffixServerHashedPassword());
			cryptedDest = new FilesFile(file.getParent() + File.separator + nameVerschluesselt);
		}
		return cryptedDest;
	}

	public static FilesFile lookupClientCryptoFileNameForFile(FilesFile file) {

		FilesFile cryptedDest = null;
		if (file.isClientCrypted()) {
			cryptedDest = file;
		} else {
			String nameVerschluesselt = Crypto.encryptDateiName(file.getName(),
					KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
					lookupCipherFileSuffixClient());
			cryptedDest = new FilesFile(file.getParent() + File.separator + nameVerschluesselt);
		}
		return cryptedDest;
	}

	public static String lookupServerCryptoPathNameForFile(FilesFile file) {

		String fullpath = file.getAbsolutePath();
		String[] parts = StringUtils.split(fullpath, File.separatorChar);

		String crypto = "";
		for (String string : parts) {
			String lookupCipherFileSuffix = null;
			if (file.isServerCryptedDirectPassword()) {
				lookupCipherFileSuffix = lookupCipherFileSuffixServerDirectPassword();
			} else if (file.isServerCryptedHashedPassword()) {
				lookupCipherFileSuffix = lookupCipherFileSuffixServerHashedPassword();
			} else if (file.isClientCrypted()) {
				lookupCipherFileSuffix = lookupCipherFileSuffixClient();
			}
			if (StringUtils.equalsIgnoreCase(lookupCipherFileSuffix, StringUtils.substringAfterLast(string, "."))) {
				crypto = crypto + File.separatorChar + string;
			} else {
				crypto = crypto + File.separatorChar
						+ Crypto.encryptDateiName(string,
								KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
								lookupCipherFileSuffix);
			}
		}
		return crypto;

	}

	public FilesFile verschluesseleDateiServerseitigMitBasisKey() {

		String key = KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY);
		prospectivePassword(key);

		return verschluesseleDateiServerseitigDirectPassword(); // hier IMMER DirectPassword !

	}

	public FilesFile clientseitigeVerschluesselungNachbereiten(String clientCryptedFileContent) {

		String nameVerschluesselt = Crypto.encryptDateiName(dateiNameKlartext(),
				KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
				lookupCipherFileSuffixClient());

		try {
			FilesFile dest = new FilesFile(getParent() + File.separator + nameVerschluesselt);
			if (clientCryptedFileContent != null) {
				dest.write(clientCryptedFileContent);
			}
			FileUtils.touch(dest);
			if (this.exists()) {
				this.delete();
			}
			return dest;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei Nachbereitung zur Client-seitigen Verschluesselung", e);
		}
	}

	public FilesFile verschluesseleDateiServerseitigDirectPassword() {

		String nameVerschluesselt = Crypto.encryptDateiName(getName(),
				KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
				lookupCipherFileSuffixServerDirectPassword());

		return verschluesseleDateiServerseitigInternal(nameVerschluesselt);
	}

	public FilesFile verschluesseleDateiServerseitigHashedPassword() {

		String nameVerschluesselt = Crypto.encryptDateiName(getName(),
				KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
				lookupCipherFileSuffixServerHashedPassword());

		return verschluesseleDateiServerseitigInternal(nameVerschluesselt);
	}

	private FilesFile verschluesseleDateiServerseitigInternal(String nameVerschluesselt) {

		try {
			String prospectiveKlartext = Crypto.decryptString(prospectivePassword, ThreadLocalHelper.getModelPassword());
			FilesFile dest = new FilesFile(getParent() + File.separator + nameVerschluesselt);
			FileUtils.touch(dest);

			InputStream inputstream;
			if (this.exists()) {
				inputstream = new BufferedInputStream(new FileInputStream(this));
			} else {
				inputstream = new ByteArrayInputStream(" ".getBytes(StandardCharsets.UTF_8));
			}
			BufferedOutputStream outputstream = new BufferedOutputStream(new FileOutputStream(dest));
			Crypto.encrypt(inputstream, outputstream, prospectiveKlartext);

			// das zukuenftige Passwort wird hiermit zum aktuellen Passwort
			dest.pendingPassword = prospectivePassword;
			if (this.exists()) {
				this.delete();
			}
			return dest;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei Dateiverschluesselung", e);
		}
	}

	public FilesFile entschluesseleDateiServerseitig() {

		String nameEntschluesselt = null;
		if (isServerCryptedDirectPassword()) {
			nameEntschluesselt = lookupNameDecryptedServerDirectPassword();
		} else if (isServerCryptedHashedPassword()) {
			nameEntschluesselt = lookupNameDecryptedServerHashedPassword();
		} else {
			throw new IllegalStateException("Ungueltiger Verschluesselungstyp:" + getName());
		}
		nameDecrypted = null;

		try {
			String pendingKlartext = Crypto.decryptString(pendingPassword, ThreadLocalHelper.getModelPassword());
			FilesFile dest = new FilesFile(getParent() + File.separator + nameEntschluesselt);
			FileUtils.touch(dest);

			BufferedInputStream inputstream = new BufferedInputStream(new FileInputStream(this));
			BufferedOutputStream outputstream = new BufferedOutputStream(new FileOutputStream(dest));
			Crypto.decrypt(inputstream, outputstream, pendingKlartext);

			this.forgetPasswords();
			this.delete();
			return dest;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei Dateientschluesselung", e);
		}

	}

	public void copyFile(Model model, FilesFile dest, boolean schreibeAenderer) throws IOException {
		copyFile(model, new FilesFile(dest.getParent()), dest.getName(), schreibeAenderer);
	}

	public FilesFile copyFile(Model model, FilesFile copyToDirectory, String newFileName, boolean schreibeAenderer) throws IOException {

		FilesFile newFile = null;
		if (this.exists() && this.isFile()) {
			if (copyToDirectory != null && copyToDirectory.exists() && copyToDirectory.isDirectory()) {
				if (StringUtils.isEmpty(newFileName)) {
					newFileName = this.getName();
				}
				FilesFile dest = new FilesFile(copyToDirectory.getAbsolutePath() + FilesFile.separator + newFileName);
				if (this.isServerCryptedDirectPassword() && !newFileName.endsWith("." + lookupCipherFileSuffixServerDirectPassword())) {
					dest = lookupServerCryptoFileNameForFileDirectPassword(dest);
				}
				if (this.isServerCryptedHashedPassword() && !newFileName.endsWith("." + lookupCipherFileSuffixServerHashedPassword())) {
					dest = lookupServerCryptoFileNameForFileHashedPassword(dest);
				}
				if (this.isClientCrypted() && !newFileName.endsWith("." + lookupCipherFileSuffixClient())) {
					dest = lookupClientCryptoFileNameForFile(dest);
				}
				if (dest.exists()) {
					if (model != null) {
						model.lookupConversation().getMeldungen().add("Zieldatei existiert bereits! - " + dest.dateiNameKlartext());
					} else {
						throw new RuntimeException("Zieldatei existiert bereits! - " + dest.dateiNameKlartext());
					}
				} else {
					FileUtils.copyFile(this, dest);
					newFile = dest;
					if (schreibeAenderer && model != null) {
						dest.aktualisiereAenderer(model.getUser());
					}
				}
			} else {
				if (model != null) {
					model.lookupConversation().getMeldungen()
							.add("Zielverzeichnis zum Kopieren ist ungültig! - " + copyToDirectory.getAbsolutePath());
				} else {
					throw new RuntimeException("Zielverzeichnis zum Kopieren ist ungültig! - " + copyToDirectory.getAbsolutePath());
				}
			}
		} else {
			if (model != null) {
				model.lookupConversation().getMeldungen().add("Quelldatei zum Kopieren ist ungültig! - " + this.dateiNameKlartext());
			} else {
				throw new RuntimeException("Quelldatei zum Kopieren ist ungültig! - " + this.dateiNameKlartext());
			}
		}
		return newFile;
	}

	public FilesFile moveFile(Model model, FilesFile moveToDirectory, boolean schreibeAenderer) throws IOException {

		FilesFile newFile = null;
		if (this.exists() && this.isFile()) {
			if (moveToDirectory != null && moveToDirectory.exists() && moveToDirectory.isDirectory()) {
				FilesFile dest = new FilesFile(moveToDirectory.getAbsolutePath() + FilesFile.separator + this.getName());
				if (dest.exists()) {
					if (model != null) {
						model.lookupConversation().getMeldungen().add("Zieldatei existiert bereits! - " + dest.dateiNameKlartext());
					} else {
						throw new RuntimeException("Zieldatei existiert bereits! - " + dest.dateiNameKlartext());
					}
				} else {
					FileUtils.moveFile(this, dest);
					newFile = dest;
					if (schreibeAenderer && model != null) {
						dest.aktualisiereAenderer(model.getUser());
					}
				}
			} else {
				if (model != null) {
					model.lookupConversation().getMeldungen()
							.add("Zielverzeichnis zum Verschieben ist ungültig! - " + moveToDirectory.getAbsolutePath());
				} else {
					throw new RuntimeException("Zielverzeichnis zum Verschieben ist ungültig! - " + moveToDirectory.getAbsolutePath());
				}
			}
		} else {
			if (model != null) {
				model.lookupConversation().getMeldungen().add("Quelldatei zum Verschieben ist ungültig! - " + this.dateiNameKlartext());
			} else {
				throw new RuntimeException("Quelldatei zum Verschieben ist ungültig! - " + this.dateiNameKlartext());
			}
		}
		return newFile;
	}

	@Override
	public boolean createNewFile() throws IOException {
		boolean ok = super.createNewFile();
		write(" ");
		return ok;
	}

	private String lookupNameDecryptedServerDirectPassword() {
		if (nameDecrypted == null) {
			nameDecrypted = Crypto.decryptDateiName(getName(),
					KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
					lookupCipherFileSuffixServerDirectPassword());
		}
		return nameDecrypted;
	}

	private String lookupNameDecryptedServerHashedPassword() {
		if (nameDecrypted == null) {
			nameDecrypted = Crypto.decryptDateiName(getName(),
					KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
					lookupCipherFileSuffixServerHashedPassword());
		}
		return nameDecrypted;
	}

	private String lookupNameDecryptedClient() {
		if (nameDecrypted == null) {
			nameDecrypted = Crypto.decryptDateiName(getName(),
					KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY),
					lookupCipherFileSuffixClient());
		}
		return nameDecrypted;
	}

	private static String lookupCipherFileSuffixServerDirectPassword() {
		return KVMemoryMap.getInstance().readValueFromKey("application.properties.cipherFileSuffix");
	}

	private static String lookupCipherFileSuffixServerHashedPassword() {
		return KVMemoryMap.getInstance().readValueFromKey("application.properties.cipherFileSuffixHashedPassword");
	}

	public static String lookupCipherFileSuffixClient() {
		return KVMemoryMap.getInstance().readValueFromKey("application.properties.cipherFileSuffixClient");
	}

	private boolean checkPassword(String password) {
		byte[] dateiinhalt = new byte[Crypto.DECODING_CHUNK_SIZE];
		byte[] bytes;
		try {
			BufferedInputStream inputstream = new BufferedInputStream(new FileInputStream(this));
			int len = inputstream.read(dateiinhalt);
			bytes = ArrayUtils.subarray(dateiinhalt, 0, len);
			inputstream.close();
		} catch (IOException e) {
			throw new RuntimeException("Datei konnte nicht gelesen werden", e);
		}
		return Crypto.checkDecryptionPassword(bytes, password);
	}

	public boolean pendingPassword(String password) {
		// hier wird das eingegebene Passwort auf Richtigkeit geprueft und vorgehalten, um den Dateiinhalt in einem spaeteren Schritt zu
		// lesen
		// Das vorgehaltene Passwort wird dabei selbst nochmal verschluesselt, um nicht im Klartext im Model zu stehen
		if (checkPassword(password)) {
			pendingPassword = Crypto.encryptString(password, ThreadLocalHelper.getModelPassword());
			return true;
		} else {
			return false;
		}
	}

	public void prospectivePassword(String password) {
		// hier wird das eingegebene zukuenftige Passwort (Neue Datei oder Passwort-Wechsel oder Datei umbenennen) vorgehalten.
		// Das vorgehaltee Passwort wird dabei selbst nochmal verschluesselt, um nicht im Klartext im Modenn zu stehen
		prospectivePassword = Crypto.encryptString(password, ThreadLocalHelper.getModelPassword());
	}

	public void forgetPasswords() {
		pendingPassword = null;
		prospectivePassword = null;
		clientKnowsPassword = false;
	}

	public void passwordsFromOtherFile(FilesFile otherFile) {
		pendingPassword = otherFile.pendingPassword;
		prospectivePassword = otherFile.prospectivePassword;
		clientKnowsPassword = otherFile.clientKnowsPassword;
	}

	public boolean isReadable() {
		// lesbar bei nicht verschluesselten Dateien immer und bei verschluesselten, wenn ein Passwort eingegeben wurde
		if (isServerCryptedDirectPassword() || isServerCryptedHashedPassword()) {
			return pendingPassword != null;
		} else if (isClientCrypted()) {
			return false;
		} else {
			return true;
		}
	}

	public boolean isPasswordPending() {
		return prospectivePassword != null;
	}

	// public Condition isRenameableWithNewName(FilesFile newFile, Condition conditionNewEncryptionPassword,
	// Condition conditionPendingPassword) {
	//
	// // umbenennbar bei nicht verschluesselten Dateien immer und bei verschluesselten, wenn ein Passwort eingegeben wurde
	//
	// boolean altIsCrypted = isServerCryptedDirectPassword();
	// boolean neuIsServerCrypted = isServerCryptedDirectPassword(newFile);
	//
	// if (!altIsCrypted && !neuIsServerCrypted) {
	// return null;
	// }
	// if (altIsCrypted && neuIsServerCrypted) {
	// return null;
	// }
	// if (!altIsCrypted && neuIsServerCrypted) {
	// if (prospectivePassword != null) {
	// return null;
	// } else {
	// return conditionNewEncryptionPassword;
	// }
	// }
	// if (altIsCrypted && !neuIsServerCrypted) {
	// if (pendingPassword != null) {
	// return null;
	// } else {
	// return conditionPendingPassword;
	// }
	// }
	// return null;
	// }

	public boolean deleteQuietly() {
		boolean ok = FileUtils.deleteQuietly(this);

		if (!ok) {
			throw new RuntimeException("Datei konnte nicht geloescht werden:" + (this.getAbsolutePath()));
		}
		return ok;
	}

	public void aktualisiereAenderer(String user) {
		synchronized (fileLockMonitor) {
			FileMap.getInstance().updateEditor(this, user);
		}
	}

	public void refreshHashValue() {
		lastKnownHash = "*" + lastModified() + "#" + length() + "*";
	}

	public String calculateActualHashValue() {
		return "*" + lastModified() + "#" + length() + "*";
	}

	public String getLastKnownHash() {
		return lastKnownHash;
	}

	public boolean isServerCryptedDirectPassword() {
		return isServerCryptedDirectPassword(this);
	}

	public boolean isServerCryptedHashedPassword() {
		return isServerCryptedHashedPassword(this);
	}

	public boolean isClientCrypted() {
		return isClientCrypted(this);
	}

	public boolean isServerBaseCrypted() { // hier IMMER DirectPassword
		return isServerCryptedDirectPassword()
				&& pendingPassword(KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY));
	}

	private static boolean isServerCryptedDirectPassword(FilesFile otherFile) {
		// Achtung, hier NICHT dateiNamenSuffix aufrufen, da hier a) decodeter Suffix und b) StackOverflow
		return StringUtils.equalsIgnoreCase(lookupCipherFileSuffixServerDirectPassword(),
				StringUtils.substringAfterLast(otherFile.getName(), "."));
	}

	private static boolean isServerCryptedHashedPassword(FilesFile otherFile) {
		// Achtung, hier NICHT dateiNamenSuffix aufrufen, da hier a) decodeter Suffix und b) StackOverflow
		return StringUtils.equalsIgnoreCase(lookupCipherFileSuffixServerHashedPassword(),
				StringUtils.substringAfterLast(otherFile.getName(), "."));
	}

	private static boolean isClientCrypted(FilesFile otherFile) {
		// Achtung, hier NICHT dateiNamenSuffix aufrufen, da hier a) decodeter Suffix und b) StackOverflow
		return StringUtils.equalsIgnoreCase(lookupCipherFileSuffixClient(), StringUtils.substringAfterLast(otherFile.getName(), "."));
	}

	public boolean isDownloadedCryptoFile() {
		return StringUtils.equalsIgnoreCase(TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE + lookupCipherFileSuffixServerDirectPassword(),
				StringUtils.substringAfterLast(getName(), "."))
				|| StringUtils.equalsIgnoreCase(TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE + lookupCipherFileSuffixServerHashedPassword(),
						StringUtils.substringAfterLast(getName(), "."))
				|| StringUtils.equalsIgnoreCase(TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE + lookupCipherFileSuffixClient(),
						StringUtils.substringAfterLast(getName(), "."));
	}

	public FilesFile lookupCryptoFileNameFromDownloadedCryptoFile() {

		String end = StringUtils.substringAfterLast(getName(), "." + TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE);
		String filename = StringUtils.removeEnd(getName(), "." + TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE + end);

		String nameVerschluesselt = Crypto.encryptDateiName(filename,
				KVMemoryMap.getInstance().readValueFromKey(APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY), end);
		return new FilesFile(getParent() + File.separator + nameVerschluesselt);

	}

	public String readIntoString() throws IOException {
		byte[] bytes = FileUtils.readFileToByteArray(this);
		if (isServerCryptedDirectPassword() || isServerCryptedHashedPassword()) {
			String pendingKlartext = Crypto.decryptString(pendingPassword, ThreadLocalHelper.getModelPassword());
			bytes = Crypto.decrypt(bytes, pendingKlartext);
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public String readIntoBase64String() throws IOException {
		byte[] bytes = FileUtils.readFileToByteArray(this);
		if (isServerCryptedDirectPassword() || isServerCryptedHashedPassword()) {
			String pendingKlartext = Crypto.decryptString(pendingPassword, ThreadLocalHelper.getModelPassword());
			bytes = Crypto.decrypt(bytes, pendingKlartext);
		}
		String content = new String(Base64.encodeBase64(bytes), StandardCharsets.UTF_8);
		return content;
	}

	public List<String> readIntoLines() {

		try {
			return stringToLines(readIntoString());
		} catch (IOException e) {
			throw new RuntimeException("Datei konnte nicht gelesen werden.");
		}
	}

	public void readIntoOutputStream(OutputStream outputStream) throws IOException {

		BufferedInputStream inputstream = new BufferedInputStream(new FileInputStream(this));

		if ((isServerCryptedDirectPassword() || isServerCryptedHashedPassword()) && isReadable()) {
			String pendingKlartext = Crypto.decryptString(pendingPassword, ThreadLocalHelper.getModelPassword());
			Crypto.decrypt(inputstream, outputStream, pendingKlartext);
		} else {
			streamToStreamCopy(inputstream, outputStream);
		}
	}

	public void writeFromInputStream(InputStream inputStream, boolean forceRawDataWriting) throws IOException {

		BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(this));

		if ((isServerCryptedDirectPassword() || isServerCryptedHashedPassword()) && !forceRawDataWriting) {
			String pendingKlartext = Crypto.decryptString(pendingPassword, ThreadLocalHelper.getModelPassword());
			Crypto.encrypt(inputStream, outputStream, pendingKlartext);
		} else if (isPasswordPending()) {
			String pendingKlartext = Crypto.decryptString(prospectivePassword, ThreadLocalHelper.getModelPassword());
			Crypto.encrypt(inputStream, outputStream, pendingKlartext);
		} else {
			streamToStreamCopy(inputStream, outputStream);
		}
	}

	public void write(String string) throws IOException {
		byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
		if (isServerCryptedDirectPassword() || isServerCryptedHashedPassword()) {
			String pendingKlartext = Crypto.decryptString(pendingPassword, ThreadLocalHelper.getModelPassword());
			bytes = Crypto.encrypt(bytes, pendingKlartext);
		}
		FileUtils.writeByteArrayToFile(this, bytes);
	}

	private List<String> stringToLines(String string) {

		List<String> lines = new LinkedList<String>();
		if (StringUtils.contains(string, "\r\n")) {
			string = StringUtils.replace(string, "\r\n", "\n");
		}
		String[] stringArray = StringUtils.splitPreserveAllTokens(string, '\n');
		for (String line : stringArray) {
			lines.add(line);
		}
		return lines;
	}

	public static void streamToStreamCopy(InputStream in, OutputStream out) {

		int read = 0;
		byte[] bytesIn = new byte[BLOCK_SIZE];
		try {
			while ((read = in.read(bytesIn)) != -1) {
				out.write(bytesIn, 0, read);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Fehler bei streamToStreamCopy:", e);
		} finally {
			try {
				in.close();
				try {
					out.flush();
				} catch (Exception eflush) {
					logger.debug("could not flush outputstream:", eflush);
				}
				out.close();
			} catch (IOException e) {
				throw new IllegalStateException("Fehler bei streamToStreamCopy/close:", e);
			}
		}
	}

	public static FilesFile lookupFileIgnoreCase(String path, boolean startsWith, String rootForFallbackSearch) {

		String[] tokens = StringUtils.splitByWholeSeparator(path, "/");

		File target = null;
		int tokenCount = 0;
		String lastTokenString = null;
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i];
			boolean lastToken = (i + 1 == tokens.length);
			if (lastToken) {
				lastTokenString = token;
			}
			if (target == null) {
				target = new File("/");
			}
			File[] files = target.listFiles();
			for (File file : files) {
				if (lastToken && startsWith) {
					if (StringUtils.startsWithIgnoreCase(file.getName(), token)) {
						target = file;
						tokenCount++;
						break;
					}
				} else {
					if (StringUtils.equalsIgnoreCase(file.getName(), token)) {
						target = file;
						tokenCount++;
						break;
					}
				}
			}
		}

		if (tokenCount == tokens.length && target != null && new File(target.getAbsolutePath()).isFile()) {
			return new FilesFile(target.getAbsolutePath());
		}

		if (rootForFallbackSearch == null) {
			return null;
		}

		// Fallback - Suche ueber alle Files unterhalb des root directories
		List<File> list = (List<File>) FileUtils.listFiles(new File(rootForFallbackSearch), FileFilterUtils.trueFileFilter(),
				FileFilterUtils.trueFileFilter());
		if (list != null && lastTokenString != null) {
			for (File file : list) {
				if (startsWith) {
					if (StringUtils.startsWithIgnoreCase(file.getName(), lastTokenString)) {
						return new FilesFile(file.getAbsolutePath());
					}
				} else {
					if (StringUtils.equalsIgnoreCase(file.getName(), lastTokenString)) {
						return new FilesFile(file.getAbsolutePath());
					}
				}
			}
		}
		return null;
	}

	public String dateinamenSuffix() {
		return dateinamenSuffix(this);
	}

	public static String dateinamenSuffix(FilesFile file) {
		String name = file.dateiNameKlartext();
		int i = name.lastIndexOf('.');
		if (i != -1) {
			return StringUtils.substringAfterLast(name, ".").toLowerCase();
		} else {
			return "";
		}
	}

	public static String dateinameOhneSuffix(File file) {
		int i = file.getName().lastIndexOf('.');
		if (i != -1) {
			return StringUtils.substringBeforeLast(file.getName(), ".");
		} else {
			return file.getName();
		}
	}

	public boolean isSupportingClientCrypto() {
		return (isEditableFileType() || isViewableImageType() || isBrowserCompatibleFileType()) && ((this.length() / 1024) <= (1024 * 30)); // FIXME:
																																			// Abfrage
																																			// auch
																																			// in
		// fileupload.js!!
	}

	public boolean isEditableFileType() {

		if (editableFiles == null) {
			editableFiles = new LinkedList<String>();
			StringTokenizer tokenizer = new StringTokenizer(
					KVMemoryMap.getInstance().readValueFromKey("application.properties.editableFiles").toLowerCase(), ",");
			while (tokenizer.hasMoreElements()) {
				editableFiles.add(((String) tokenizer.nextElement()).toLowerCase().trim());
			}
		}

		return !isDirectory() && editableFiles.contains(dateinamenSuffix());
	}

	public boolean isBrowserCompatibleFileType() {

		if (browserCompatibleFiles == null) {
			browserCompatibleFiles = new LinkedList<String>();
			StringTokenizer tokenizer = new StringTokenizer(
					KVMemoryMap.getInstance().readValueFromKey("application.properties.browserCompatibleFiles").toLowerCase(), ",");
			while (tokenizer.hasMoreElements()) {
				browserCompatibleFiles.add(((String) tokenizer.nextElement()).toLowerCase().trim());
			}
		}

		return !isDirectory() && browserCompatibleFiles.contains(dateinamenSuffix());
	}

	public boolean isViewableImageType() {

		if (viewableImages == null) {
			viewableImages = new LinkedList<String>();
			StringTokenizer tokenizer = new StringTokenizer(
					KVMemoryMap.getInstance().readValueFromKey("application.properties.viewableImages").toLowerCase(), ",");
			while (tokenizer.hasMoreElements()) {
				viewableImages.add(((String) tokenizer.nextElement()).toLowerCase().trim());
			}
		}

		return !isDirectory() && viewableImages.contains(dateinamenSuffix());
	}

	public boolean isSameAs(FilesFile other) {
		return StringUtils.equals(this.getAbsolutePath(), other.getAbsolutePath());
	}

	public static String linesToString(List<String> zeilen) {

        String separator = System.lineSeparator();
		int len = 0;
		for (String string : zeilen) {
			len = len + string.length();
		}
		len = len + (separator.length() * zeilen.size());

		StringBuilder neueDatei = new StringBuilder(len);
		boolean ersteZeile = true;
		for (String string : zeilen) {
			if (ersteZeile) {
				ersteZeile = false;
			} else {
				neueDatei.append(separator);
			}
			neueDatei.append(string);
		}
		return neueDatei.toString();
	}

	// null, wenn kein Lock, sonst String[]
	// [0] = UserName
	// [1] = Zeitstempel
	public String[] fileLockVorhanden() {
		synchronized (fileLockMonitor) {
			return fileLockVorhandenDirtyRead();
		}
	}

	public String[] fileLockVorhandenDirtyRead() {

		if (KVMemoryMap.getInstance().containsKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER)) {
			String user = KVMemoryMap.getInstance().readValueFromKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER);
			String time = KVMemoryMap.getInstance().readValueFromKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_TIME);
			return new String[] { user, time };
		} else {
			return null;
		}
	}

	// Versucht, ein File Lock anhand Filename und User/Timestamp zu setzen.
	// Return true, wenn erfolgreich, false, wenn schon ein Lock vorhanden
	public boolean setzeFileLock(String user) {
		synchronized (fileLockMonitor) {
			if (KVMemoryMap.getInstance().containsKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER)) {
				return false;
			} else {
				boolean okUser = KVMemoryMap.getInstance().writeKeyValue(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER, user,
						false);
				boolean okTime = KVMemoryMap.getInstance().writeKeyValue(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_TIME,
						Long.toString(System.currentTimeMillis()), false);
				return okUser && okTime;
			}
		}
	}

	public void loescheFileLock(String user) {
		synchronized (fileLockMonitor) {
			if (KVMemoryMap.getInstance().containsKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER)) {
				String lockUser = KVMemoryMap.getInstance().readValueFromKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER);
				if (StringUtils.equals(lockUser, user)) {
					KVMemoryMap.getInstance().deleteKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER);
					KVMemoryMap.getInstance().deleteKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_TIME);
				}
			}
		}
	}

	public void loescheFileLock() {
		synchronized (fileLockMonitor) {
			if (KVMemoryMap.getInstance().containsKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER)) {
				KVMemoryMap.getInstance().deleteKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_USER);
				KVMemoryMap.getInstance().deleteKey(KVM_PRE_LOCK + dateiNameUndPfadEscaped() + KVM_SUF_LOCK_TIME);
			}
		}
	}

	public boolean createNewCategory(String categoryName) {

		if (!isReadable() || !isEditableFileType()) {
			return false;
		}
		if (fileLockVorhandenDirtyRead() != null) {
			throw new FileLockException();
		}
		boolean lock = false;
		if (setzeFileLock("System")) {
			lock = true;
		} else {
			throw new FileLockException();
		}
		try {
			categoryName = StringUtils.trimToNull(categoryName);
			if (categoryName == null) {
				return false;
			}
			if (hasCategory(categoryName, false) != null) {
				return true;
			}

			List<String> zeilen = readIntoLines();

			MetaTagParser metaTagParser = new MetaTagParser();
			metaTagParser.parseTags(zeilen, false);

			int insertPos = metaTagParser.getMetaTagLineCount();

			zeilen.add(insertPos, "== " + categoryName);

			String neu = FilesFile.linesToString(zeilen);
			schreibeFileMitArchivierung(neu, null, "System");
		} catch (IOException e) {
			throw new RuntimeException("Fehler beim Kategorie schreiben:" + dateiNameUndPfadKlartext(), e);
		} finally {
			if (lock) {
				loescheFileLock("System");
			}
		}

		return true;
	}

	// null: false, all other: found category name
	public String hasCategory(String category, boolean startsWithIgnoreCase) {

		if (!isEditableFileType()) {
			return null;
		}

		CategoryMarker marker = findCategoryMarker(category, readIntoLines(), true, 0, startsWithIgnoreCase);
		return marker.categoryName;
	}

	public static CategoryMarker findCategoryMarker(String noteCategory, List<String> zeilen, boolean insertTop, int i,
			boolean startsWithIgnoreCase) {

		CategoryMarker marker = new CategoryMarker();
		// -1=noch nicht gefunden,-2=nicht relevant
		marker.categoryLine = -1;
		marker.firstEntry = -1;
		marker.lastEntry = insertTop ? -2 : -1;

		if (zeilen == null || zeilen.size() == 0) {
			return marker;
		}

		boolean weitersuchen = true;
		while (weitersuchen) {
			String aktuelleZeile = StringUtils.trimToEmpty(zeilen.get(i));
			boolean istEOF = MetaTagParser.isEndOfFileTagLine(aktuelleZeile);
			boolean istLetzteTextZeile = i + 1 == zeilen.size() || MetaTagParser.isEndOfFileTagLine(zeilen.get(i + 1));
			boolean istKategorie = StringUtils.startsWith(aktuelleZeile, "==");
			// Kategorie-Eintrag finden
			if (startsWithIgnoreCase) {
				if (istKategorie && StringUtils.startsWithIgnoreCase(StringUtils.remove(aktuelleZeile, "=").trim(),
						StringUtils.trim(noteCategory))) {
					marker.categoryLine = i;
					marker.categoryName = StringUtils.remove(aktuelleZeile, "=").trim();
				}
			} else {
				if (istKategorie && StringUtils.equalsIgnoreCase(StringUtils.remove(aktuelleZeile, "=").trim(), noteCategory)) {
					marker.categoryLine = i;
					marker.categoryName = StringUtils.remove(aktuelleZeile, "=").trim();
				}
			}
			// Ersten Eintrag finden
			if (marker.categoryLine != -1 && marker.categoryLine != i && marker.firstEntry == -1 && StringUtils.isNotBlank(aktuelleZeile)
					&& !istKategorie && !istEOF) {
				marker.firstEntry = i;
			}
			// Letzten Eintrag finden
			if (marker.firstEntry > -1 && marker.lastEntry == -1) {
				// Letzte Dateizeile ist immer auch letzter Eintrag
				if (istLetzteTextZeile) {
					marker.lastEntry = i;
				}
				// Leere Zeile oder Start einer anderen Kategorie ist auch Kategorieende -> letzte Zeile also eine davor
				if (StringUtils.isBlank(aktuelleZeile) || istKategorie) {
					marker.lastEntry = i - 1;
				}
			}

			// Folgekategorie gefunden? Dann nicht weiter suchen
			if (marker.categoryLine != -1 && istKategorie && marker.categoryLine != i) {
				weitersuchen = false;
			}
			if (marker.firstEntry != -1 && marker.lastEntry != -1) {
				weitersuchen = false;
			}
			if (i + 1 < zeilen.size()) {
				i++;
			} else {
				weitersuchen = false;
			}

		}
		return marker;
	}

	public void fuegeNeueNoteZeileEinFuerBatch(String user, String category, String zeile) {

		if (fileLockVorhandenDirtyRead() != null) {
			throw new FileLockException();
		}
		boolean lock = false;
		if (setzeFileLock("System")) {
			lock = true;
		} else {
			throw new FileLockException();
		}
		try {

			MetaTagParser metaTagParser = new MetaTagParser();
			List<String> zeilen = readIntoLines();
			metaTagParser.parseTags(zeilen, false);

			fuegeNeueNoteZeileEin(zeile, category, metaTagParser.hasTag(TextFileMetaTagName.NEW_ITEM_ON_TOP), null);

		} catch (IOException e) {
			throw new RuntimeException("Fehler beim File schreiben:" + dateiNameUndPfadKlartext(), e);
		} finally {
			if (lock) {
				loescheFileLock("System");
			}
		}

	}

	public void fuegeNeueNoteZeileEin(String zeile, String noteCategory, boolean insertTop, Model model) throws IOException {

		List<String> zeilen = readIntoLines();
		int insertPosition = -1;

		if (StringUtils.isBlank(noteCategory)) {
			insertPosition = SatzEinfuegenHelper.ermittleFallbackEinfuegePosition(zeilen, insertTop);
		} else {

			int startzeile = 0;
			CategoryMarker marker = FilesFile.findCategoryMarker(noteCategory, zeilen, insertTop, startzeile, false);

			// Nach Zeilenindizes wurde gesucht. Jetzt daraus die Position zum einfuegen ermitteln.
			if (marker.categoryLine != -1) {
				if (insertTop) {
					if (marker.firstEntry == -1) {
						insertPosition = marker.categoryLine + 1;
					} else {
						insertPosition = marker.firstEntry;
					}
				} else {
					if (marker.lastEntry == -1) {
						insertPosition = marker.categoryLine + 1;
					} else {
						insertPosition = marker.lastEntry + 1;
						// Duerfte nie vorkommen
						if (insertPosition > zeilen.size()) {
							insertPosition = zeilen.size();
						}
					}
				}
			}
		}

		if (insertPosition == -1) {
			insertPosition = SatzEinfuegenHelper.ermittleFallbackEinfuegePosition(zeilen, insertTop);
			logger.warn("Positionsermittlung war fehlerhaft:" + dateiNameUndPfadKlartext() + " -- " + noteCategory);
		}

		// Neue Zeile hinzufuegen
		zeilen.add(insertPosition, zeile);

		String neu = FilesFile.linesToString(zeilen);
		schreibeFileMitArchivierung(neu, model, model != null ? model.getUser() : null);
	}

	public void schreibeFileMitAsynchronerArchivierung(String data, Model model, String user) throws IOException {

		write(data);
		aktualisiereAenderer(model != null ? model.getUser() : user);
		KVMemoryMap.getInstance().writeToValueList("temp.asyncbackuparchive.key", getAbsolutePath(), false);

	}

	public void schreibeFileMitArchivierung(String data, Model model, String user) throws IOException {

		write(data);
		aktualisiereAenderer(model != null ? model.getUser() : user);
		backupFile(model);

	}

	private static String erzeugeZielPfadeAusEinzelPfaden(FilesFile file1, FilesFile file2) throws IOException {

		String pfad = "";

		if (file1 != null) {
			String pfad1 = file1.getAbsolutePath() + FilesFile.separator;
			pfad = pfad.concat(pfad1);
		}

		if (file2 != null) {
			String pfad2 = file2.getAbsolutePath() + FilesFile.separator;
			pfad = pfad.concat(pfad2);
		}

		pfad = StringUtils.replace(pfad, FilesFile.separator + FilesFile.separator, FilesFile.separator);
		pfad = StringUtils.replace(pfad, ":", "");

		FilesFile archiv = new FilesFile(pfad);

		if (!archiv.exists()) {
			boolean anlageOK = archiv.mkdirs();
			if (!anlageOK) {
				throw new IOException("Archivverzeichnis konnte nicht erstellt werden: " + pfad);
			}
		}
		return pfad;
	}

	public void backupFile(Model model) throws IOException {

		String backupVerzeichnis = StringUtils.trimToNull(KVMemoryMap.getInstance().readValueFromKey("application.properties.backupLocal"));

		String[] asyncExternBackup = StringUtils.split(
				StringUtils.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.properties.backupAsyncExtern")), ',');

		if (StringUtils.isEmpty(backupVerzeichnis)) {
			return;
		}

		if (this.isDirectory()) {
			List<File> innerFiles = (List<File>) FileUtils.listFiles(this, FileFilterUtils.trueFileFilter(),
					FileFilterUtils.trueFileFilter());
			for (File inner : innerFiles) {
				new FilesFile(inner.getAbsolutePath()).backupFile(model);
			}
			return;
		}

		boolean isFileAlreadyInBackupPath = false;
		if (Filter.matches(this.getAbsolutePath(), backupVerzeichnis, Preset.STARTS_WITH, IgnoreCase.NO)) {
			// File ist bereits im oder unterhalb des Backup Pfades
			// Dann kein zusaetzliches lokales Backup, nur Extern
			// Nutzung z.Z. fuer FileMap
			isFileAlreadyInBackupPath = true;
		}

		if (!DateiBackup.isInBackupPfad(this) && !isFileAlreadyInBackupPath) {
			return;
		}

		SimpleDateFormat sdf = Hilfsklasse.lookupSimpleDateFormat(VERSION_DATE_PATTERN);
		String timestamp = "fjbackupversion_" + sdf.format(new Date());

		try {

			FilesFile backupFile = null;
			FilesFile file1 = null;
			FilesFile file2 = null;

			if (isFileAlreadyInBackupPath) {
				file2 = new FilesFile(this.getAbsolutePath() + "_fjbackups" + FilesFile.separator + timestamp + FilesFile.separator);

			} else {
				file1 = new FilesFile(backupVerzeichnis);
				file2 = new FilesFile(this.getAbsolutePath() + FilesFile.separator + timestamp + FilesFile.separator);
			}

			String backupPfadName = erzeugeZielPfadeAusEinzelPfaden(file1, file2);

			backupFile = new FilesFile(backupPfadName + getName());

			copyFile(model, backupFile, false);

			if (asyncExternBackup != null) {
				for (String dest : asyncExternBackup) {
					if (StringUtils.isNoneBlank(dest)) {
						KVMemoryMap.getInstance().writeToValueList("temp.backup.key." + StringUtils.trim(dest),
								backupFile.getAbsolutePath(), false);
					}
				}
			}

		} catch (Exception e) {
			throw new IOException("Kopieren in Backup-Verzeichnis nicht erfolgreich:" + dateiNameUndPfadKlartext(), e);
		}
	}

	public String getPendingPassword() {
		return pendingPassword;
	}

	public void setPendingPassword(String pendingPassword) {
		this.pendingPassword = pendingPassword;
	}

	public boolean isClientKnowsPassword() {
		return clientKnowsPassword;
	}

	public void setClientKnowsPassword(boolean clientKnowsPassword) {
		this.clientKnowsPassword = clientKnowsPassword;
	}
}
