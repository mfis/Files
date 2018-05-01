package mfi.files.cryptomap;

import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;

import mfi.files.logic.Crypto;
import mfi.files.maps.KVMemoryMap;

public class CryptoMapSwitcher {

	public static void main(String[] args) {

		System.out.println("Entry to be switched betreewn decrypted and unencrypted:");
		Scanner scanner = new Scanner(System.in);
		String original = StringUtils.trimToEmpty(scanner.nextLine());
		System.out.println("Password:");
		String password = StringUtils.trimToEmpty(scanner.nextLine());
		boolean isCrypted = StringUtils.startsWith(original, KVMemoryMap.PREFIX_CRYPTO_ENTRY_ENC);

		String keyOriginal = StringUtils.split(original, '=')[0].trim();
		keyOriginal = StringUtils.removeStart(keyOriginal, KVMemoryMap.PREFIX_CRYPTO_ENTRY_ENC);
		keyOriginal = StringUtils.removeStart(keyOriginal, KVMemoryMap.PREFIX_CRYPTO_ENTRY_DEC);
		String valueOriginal = StringUtils.split(original, '=')[1].trim();
		String keySwitched;
		String valueSwitched;

		if (isCrypted) {
			keySwitched = KVMemoryMap.PREFIX_CRYPTO_ENTRY_DEC + Crypto.decryptDateiName(keyOriginal, password, null);
			valueSwitched = Crypto.decryptDateiName(valueOriginal, password, null);
		} else {
			keySwitched = KVMemoryMap.PREFIX_CRYPTO_ENTRY_ENC + Crypto.encryptDateiName(keyOriginal, password, null);
			valueSwitched = Crypto.encryptDateiName(valueOriginal, password, null);
		}

		System.out.println("Switched to " + (isCrypted ? "un" : "") + "encrypted:");
		System.out.println(keySwitched + " = " + valueSwitched);

		scanner.close();
	}
}
