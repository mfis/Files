package mfi.files.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;

public class LoginToken implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final char SEPARATOR_CHAR = '*';

	private String user;

	private LocalDateTime timestamp;

	private String userSecretHash;

	private String value;

	private LoginToken() {
		super();
	}

	public static LoginToken createNew(String user) {

		String secret = KVMemoryMap.getInstance().readValueFromKey("user." + user + ".loginTokenSecret");
		String hash = Integer.toHexString(secret.hashCode());

		LoginToken newToken = new LoginToken();
		newToken.user = user;
		newToken.timestamp = LocalDateTime.now();
		newToken.userSecretHash = hash;
		newToken.value = generateTokenValue();
		return newToken;
	}

	public void refreshValue() {
		timestamp = LocalDateTime.now();
		value = generateTokenValue();
	}

	public static LoginToken fromCombinedValue(String value) {

		if (StringUtils.isAllBlank(value)) {
			return null;
		}

		String[] strings = StringUtils.split(value, SEPARATOR_CHAR);
		if (strings.length != 4) {
			return null;
		}

		LoginToken newToken = new LoginToken();
		newToken.user = strings[0];
		newToken.timestamp = LocalDateTime.parse(strings[1], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		newToken.userSecretHash = strings[2];
		newToken.value = strings[3];
		return newToken;
	}

	public String toKvDbValue() {
		String tsString = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		return StringUtils.join(new String[] { user, tsString, userSecretHash, value }, SEPARATOR_CHAR);
	}

    @Override
    public String toString() {
        String tsString = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return StringUtils.join(new String[] {user, tsString, userSecretHash, StringUtils.left(value, 10)}, SEPARATOR_CHAR);
    }

	public boolean checkToken(String user, String application, String device) {
		return checkUser(user) && checkUserSecretHash(user) && checkValue(user, application, device);
	}

	private boolean checkUser(String user) {
        return StringUtils.equals(user, this.user) && Security.isUserActive(user);
	}

	private boolean checkValue(String user, String application, String device) {
		String key = KVMemoryMap.KVDB_KEY_LOGINTOKEN + user + "." + application + "." + device;
        String actualValueNew = KVMemoryMap.getInstance().readValueFromKey(key + KVMemoryMap.KVDB_NEW_TOKEN_IDENTIFIER);
        if (StringUtils.isNotBlank(actualValueNew) && StringUtils.equals(actualValueNew, toKvDbValue())) {
            return true;
        }
        String actualValueOld = KVMemoryMap.getInstance().readValueFromKey(key);
        return StringUtils.isNotBlank(actualValueOld) && StringUtils.equals(actualValueOld, toKvDbValue());
	}

	private boolean checkUserSecretHash(String user) {
		String secret = KVMemoryMap.getInstance().readValueFromKey("user." + user + ".loginTokenSecret");
		String actualHash = Integer.toHexString(secret.hashCode());
		return StringUtils.isNotBlank(actualHash) && StringUtils.equals(actualHash, userSecretHash);
	}

	private static String generateUUID() {
		return UUID.randomUUID().toString().replaceAll("[^a-zA-Z0-9]", "");
	}

	private static String generateTokenValue() {
		return generateUUID() + new RandomStringGenerator.Builder().withinRange('0', 'z')
				.filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS).build().generate(3600);
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public String getUserSecretHash() {
		return userSecretHash;
	}

	public String getValue() {
		return value;
	}

	public String getUser() {
		return user;
	}
}
