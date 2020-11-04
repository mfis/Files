package mfi.files.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import mfi.files.io.FilesFile;

public class Crypto {

	public final static int ENCODING_CHUNK_SIZE = FilesFile.BLOCK_SIZE - 16 - 16;

	public final static int DECODING_CHUNK_SIZE = FilesFile.BLOCK_SIZE;

	private final static Charset UTF8 = StandardCharsets.UTF_8;

	public static String encryptDateiName(String dateiname, String passwort, String cryptoFileSuffix) {
		byte[] key128 = passwordTo128bitKey(passwort);
		String name = encryptAES(dateiname, key128, true);
		name = StringUtils.replace(name, "=======", "-7");
		name = StringUtils.replace(name, "======", "-6");
		name = StringUtils.replace(name, "=====", "-5");
		name = StringUtils.replace(name, "====", "-4");
		name = StringUtils.replace(name, "===", "-3");
		name = StringUtils.replace(name, "==", "-2");
		name = StringUtils.replace(name, "=", "-1");
		if (cryptoFileSuffix != null) {
			return name + "." + cryptoFileSuffix;
		} else {
			return name;
		}
	}

	public static String decryptDateiName(String dateiname, String passwort, String cryptoFileSuffix) {
		String name;
		if (cryptoFileSuffix != null) {
			name = StringUtils.removeEndIgnoreCase(dateiname, "." + cryptoFileSuffix);
		} else {
			name = dateiname;
		}
		name = StringUtils.replace(name, "-7", "=======");
		name = StringUtils.replace(name, "-6", "======");
		name = StringUtils.replace(name, "-5", "=====");
		name = StringUtils.replace(name, "-4", "====");
		name = StringUtils.replace(name, "-3", "===");
		name = StringUtils.replace(name, "-2", "==");
		name = StringUtils.replace(name, "-1", "=");
		byte[] key128 = passwordTo128bitKey(passwort);
		String dateinameKlartext = decryptAES(name, key128, true);
		return dateinameKlartext;
	}

	public static byte[] encrypt(byte[] level0, String passwort) {

		if (level0.length > ENCODING_CHUNK_SIZE) {
			ByteArrayInputStream in = new ByteArrayInputStream(level0);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			encrypt(in, out, passwort);
			return out.toByteArray();
		} else {
			byte[] key256 = passwordTo256bitKey(passwort);
			byte[] keyXor = passwordToXorKey(passwort);
			return encryptInternal(level0, key256, keyXor, level0.length);
		}
	}

	public static void encrypt(InputStream in, OutputStream out, String passwort) {

		byte[] key256 = passwordTo256bitKey(passwort);
		byte[] keyXor = passwordToXorKey(passwort);

		int read = 0;
		byte[] bytesIn = new byte[ENCODING_CHUNK_SIZE];
		try {
			while ((read = in.read(bytesIn)) != -1) {
				byte[] level2 = encryptInternal(bytesIn, key256, keyXor, read);
				out.write(level2);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Fehler bei encrypt:", e);
		} finally {
			try {
				in.close();
				out.flush();
				out.close();
			} catch (IOException e) {
				throw new IllegalStateException("Fehler bei encrypt/close:", e);
			}
		}
	}

	private static byte[] encryptInternal(byte[] bytes, byte[] key256, byte[] keyXor, int length) {

		byte[] level1 = encryptAES(bytes, key256, length);
		level1 = xor(level1, keyXor);
		byte[] level2 = encryptAES(level1, key256, level1.length);
		return level2;
	}

	public static boolean checkDecryptionPassword(byte[] level3, String passwort) {

		byte[] key256 = passwordTo256bitKey(passwort);
		byte[] keyXor = passwordToXorKey(passwort);

		try {
			int length;
			if (level3.length <= DECODING_CHUNK_SIZE) {
				length = level3.length;
			} else {
				length = DECODING_CHUNK_SIZE;
			}
			decryptInternal(level3, key256, keyXor, length);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static byte[] decrypt(byte[] level3, String passwort) {

		if (level3.length > DECODING_CHUNK_SIZE) {
			ByteArrayInputStream in = new ByteArrayInputStream(level3);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			decrypt(in, out, passwort);
			return out.toByteArray();
		} else {
			byte[] key256 = passwordTo256bitKey(passwort);
			byte[] keyXor = passwordToXorKey(passwort);
			return decryptInternal(level3, key256, keyXor, level3.length);
		}
	}

	public static void decrypt(InputStream in, OutputStream out, String passwort) {

		byte[] key256 = passwordTo256bitKey(passwort);
		byte[] keyXor = passwordToXorKey(passwort);

		int read = 0;
		byte[] bytesIn = new byte[DECODING_CHUNK_SIZE];
		try {
			while ((read = in.read(bytesIn)) != -1) {
				byte[] level1 = decryptInternal(bytesIn, key256, keyXor, read);
				out.write(level1);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Fehler bei encrypt:", e);
		} finally {
			try {
				in.close();
				out.flush();
				out.close();
			} catch (IOException e) {
				throw new IllegalStateException("Fehler bei decrypt/close:", e);
			}
		}
	}

	public static long sizeOfDecryptedStream(InputStream in, String passwort) {

		long size = 0;

		byte[] key256 = passwordTo256bitKey(passwort);
		byte[] keyXor = passwordToXorKey(passwort);

		int read = 0;
		byte[] bytesIn = new byte[DECODING_CHUNK_SIZE];
		try {
			while ((read = in.read(bytesIn)) != -1) {
				byte[] level1 = decryptInternal(bytesIn, key256, keyXor, read);
				size = size + level1.length;
			}
			return size;
		} catch (IOException e) {
			throw new IllegalStateException("Fehler bei encrypt:", e);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				throw new IllegalStateException("Fehler bei decrypt/close:", e);
			}
		}
	}

	private static byte[] decryptInternal(byte[] level3, byte[] key256, byte[] keyXor, int length) {
		byte[] level2 = decryptAES(level3, key256, length);
		level2 = xor(level2, keyXor);
		byte[] level1 = decryptAES(level2, key256, level2.length);
		return level1;
	}

	public static String encryptString(String inhalt, String passwort) {
		byte[] encBytes = encrypt(inhalt.getBytes(UTF8), passwort);
		String encString64 = new String(Base64.encodeBase64(encBytes), UTF8);
		return encString64;
	}

	public static String decryptString(String inhalt, String passwort) {

		byte[] encBytesAus64 = Base64.decodeBase64(inhalt.getBytes(UTF8));
		byte[] decBytes = decrypt(encBytesAus64, passwort);
		String decString = new String(decBytes, UTF8);
		return decString;
	}

	public static String encryptLoginCredentials(String user, String loginPasswort) {
		byte[] key256 = passwordTo256bitKey(credentialsFromUserNameAndPasswort(user, loginPasswort));
		String crypted = encryptAES(new String(Base64.encodeBase64(key256), UTF8), key256, true);
		crypted = StringUtils.remove(crypted, "=");
		return crypted;
	}

	public static String hashString(String string) {
		if (StringUtils.isEmpty(string)) {
			return "";
		}
		byte[] bytes = passwordTo256bitKey(string);
		String hash = new String(Base64.encodeBase64(bytes), UTF8);
		return hash;
	}

	private static String encryptAES(String content, byte[] key, boolean base32InsteadOfBase64) {
		try {
			byte[] bytesFromContent = content.getBytes(UTF8);
			byte[] encrypted = encryptAES(bytesFromContent, key, bytesFromContent.length);
			String encryptedString;
			if (base32InsteadOfBase64) {
				encryptedString = new String(new Base32(0).encode(encrypted), UTF8);
			} else {
				encryptedString = new String(Base64.encodeBase64(encrypted), UTF8);
			}
			return encryptedString;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei encryptAES(String)", e);
		}
	}

	private static byte[] encryptAES(byte[] content, byte[] key, int length) {
		try {
			SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(initVector(key)));
			byte[] encrypted = cipher.doFinal(content, 0, length);
			return encrypted;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei encryptAES(byte[])" + e.getMessage() + " " + e.toString(), e);
		}
	}

	private static String decryptAES(String content, byte[] key, boolean base32InsteadOfBase64) {
		try {
			byte[] contentBytes;
			if (base32InsteadOfBase64) {
				contentBytes = new Base32().decode(content.getBytes(UTF8));
			} else {
				contentBytes = Base64.decodeBase64(content.getBytes(UTF8));
			}
			byte[] decrypted = decryptAES(contentBytes, key, contentBytes.length);
			String decryptedString = new String(decrypted, UTF8);
			return decryptedString;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei decryptAES(String)", e);
		}
	}

	private static byte[] decryptAES(byte[] content, byte[] key, int length) {
		try {
			SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(initVector(key)));
			byte[] decrypted = cipher.doFinal(content, 0, length);
			return decrypted;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei decryptAES(byte[])", e);
		}
	}

	public static byte[] passwordTo128bitKey(String password) {
		try {
			password = saltPasswort(password);
			MessageDigest crypt = MessageDigest.getInstance("SHA-1");
			crypt.reset();
			crypt.update(password.getBytes(UTF8));
			byte[] sha1 = (crypt.digest());
			byte[] sha128 = Arrays.copyOfRange(sha1, 0, 16);
			// Xystem.out.println("keylaenge = " + sha128.length + "/" + sha128.length * 8);
			if (sha128.length != 128 / 8) {
				throw new RuntimeException("Ungueltige Keylaenge erzeugt:" + sha128.length * 8 + " (" + password.length() + ")");
			}
			return sha128;
		} catch (Exception e) {
			throw new RuntimeException("Fehler bei passwordTo128bitKey()", e);
		}
	}

	public static byte[] passwordTo256bitKey(String password) {
		try {
			password = saltPasswort(password);
			MessageDigest crypt = MessageDigest.getInstance("SHA-256");
			crypt.reset();
			crypt.update(password.getBytes(UTF8));
			byte[] sha256 = (crypt.digest());
			// Xystem.out.println("keylaenge = " + sha256.length + "/" + sha256.length * 8);
			if (sha256.length != 256 / 8) {
				throw new RuntimeException("Ungueltige Keylaenge erzeugt:" + sha256.length * 8 + " (" + password.length() + ")");
			}
			return sha256;
		} catch (Exception e) {
			// Bei Problemen mit der Bitlaenge: Ist die folgende Erweiterung installiert?
			// Unlimited Strength Java(TM) Cryptography Extension Policy Files
			// for the Java(TM) Platform, Standard Edition Runtime Environment 7
			throw new RuntimeException("Fehler bei passwordTo256bitKey()", e);
		}
	}

	public static byte[] passwordToXorKey(String password) {
		byte[] a = passwordTo128bitKey(password);
		byte[] b = Base64.encodeBase64(a);
		byte[] c = Base64.encodeBase64(b);
		return c;
	}

	private static byte[] xor(byte[] data, byte[] xorKey) {
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) (data[i] ^ xorKey[i % xorKey.length]);
		}
		return data;
	}

	private static byte[] initVector(byte[] in) throws Exception {
		if (in.length < 16) {
			throw new Exception("Input Array too small:" + in.length);
		}
		int j = 0;
		byte[] iv = new byte[16]; // blocksize
		for (int i = in.length - 1; i > in.length - 17; i--) {
			iv[j] = in[i];
			j++;
		}
		return iv;

	}

	public static String saltPasswort(String passwort) throws DecoderException {
		return passwort + (passwort.length() * passwort.length() + passwort.length()) + Integer.toHexString(passwort.length())
				+ passwort.toUpperCase().substring(0, passwort.length() / 2) + (passwort.length() * passwort.length() * 42);
	}

	private static String credentialsFromUserNameAndPasswort(String user, String LoginPasswort) {
		String credentials = user + LoginPasswort
				+ StringUtils.rightPad(LoginPasswort, (user.length() + LoginPasswort.length()) * 2, LoginPasswort);
		return credentials;
	}

}
