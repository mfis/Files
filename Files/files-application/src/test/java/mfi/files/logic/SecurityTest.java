package mfi.files.logic;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import junit.framework.TestCase;
import mfi.files.api.TokenResult;
import mfi.files.maps.KVMemoryMap;

public class SecurityTest extends TestCase {

    private static final String USER = "user";

    private static final String PASS = "pass";

    private static final String APPLICATION = "application";

    private static final String DEVICE = "device";

    private static final String TOKEN_KEY = KVMemoryMap.KVDB_KEY_LOGINTOKEN + USER + "." + APPLICATION + "." + DEVICE;

    @Test
	public void testCleanUpKvKey() {
		assertEquals("a.b.c.d", Security.cleanUpKvKey("a.\nb.=c . d"));
	}

	@Test
	public void testCleanUpKvSubKey() {
		assertEquals("", Security.cleanUpKvSubKey(null));
		assertEquals("", Security.cleanUpKvSubKey(""));
		assertEquals("abcd", Security.cleanUpKvSubKey("abcd"));
		assertEquals("abcd", Security.cleanUpKvSubKey("a.b c=d\\öäü"));
		assertEquals("abcd", Security.cleanUpKvSubKey("a\rb\nc\r\nd"));
		assertEquals("abcd", Security.cleanUpKvSubKey("a\tbcd"));
		assertEquals("abcd", Security.cleanUpKvSubKey("a==b^cd="));
	}

	@Test
	public void testCleanUpKvValue() {
		assertEquals("", Security.cleanUpKvValue(null));
		assertEquals("", Security.cleanUpKvValue(""));
		assertEquals("ABCabc123.,-+ /<>(){}#*?@_", Security.cleanUpKvValue("ABCabc123.,-+ /<>(){}#*?@_"));
		assertEquals("", Security.cleanUpKvValue("\\\n\r="));
	}

    @Test
    public void testCreateUserInactive() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, false);
        Assert.assertFalse(Security.isUserActive(USER));
    }

    @Test
    public void testCreateUserActive() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, true);
        Assert.assertTrue(Security.isUserActive(USER));
    }

    @Test
    public void testCreateUserActiveButLoginNotAllowed() {
        prepareKvMap(false);
        Security.createNewUser(USER, PASS, true);
        Assert.assertFalse(Security.isUserActive(USER));
    }

    @Test
    public void testCreateTokenOK() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, true);
        TokenResult tokenResult = Security.createToken(USER, PASS, APPLICATION, DEVICE);
        Assert.assertTrue(tokenResult.isCheckOk());
        Assert.assertTrue(StringUtils.isNotBlank(tokenResult.getNewToken()));
    }

    @Test
    public void testCreateTokenInvalidPassword() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, true);
        for (int i = 0; i < 5; i++) {
            TokenResult tokenResult = Security.createToken(USER, "abc", APPLICATION, DEVICE);
            Assert.assertFalse(tokenResult.isCheckOk());
            Assert.assertNull(tokenResult.getNewToken());
        }
        // user is blocked now
        TokenResult tokenResultCorrectPassword = Security.createToken(USER, PASS, APPLICATION, DEVICE);
        Assert.assertFalse(tokenResultCorrectPassword.isCheckOk());
        Assert.assertNull(tokenResultCorrectPassword.getNewToken());
    }

    @Test
    public void testCheckTokenOK() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, true);
        TokenResult tokenResultCreate = Security.createToken(USER, PASS, APPLICATION, DEVICE);
        TokenResult tokenResultCheck = Security.checkToken(USER, tokenResultCreate.getNewToken(), APPLICATION, DEVICE, false);
        Assert.assertTrue(tokenResultCheck.isCheckOk());
    }

    @Test
    public void testCheckTokenInvalidToken() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, true);
        TokenResult tokenResultCreate = Security.createToken(USER, PASS, APPLICATION, DEVICE);
        Assert.assertTrue(tokenResultCreate.isCheckOk());
        Assert.assertTrue(StringUtils.isNotBlank(tokenResultCreate.getNewToken()));
        for (int i = 0; i < 5; i++) {
            TokenResult tokenResultCheck =
                Security.checkToken(USER, "myInvalidToken", APPLICATION, DEVICE, false);
            Assert.assertFalse(tokenResultCheck.isCheckOk());
            Assert.assertNull(tokenResultCheck.getNewToken());
        }
        // user is blocked now
        TokenResult tokenResultCorrectToken =
            Security.checkToken(USER, tokenResultCreate.getNewToken(), APPLICATION, DEVICE, false);
        Assert.assertFalse(tokenResultCorrectToken.isCheckOk());
        Assert.assertNull(tokenResultCorrectToken.getNewToken());
    }

    @Test
    public void testCheckTokenRefreshOK() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, true);
        TokenResult tokenResultCreate = Security.createToken(USER, PASS, APPLICATION, DEVICE);
        TokenResult tokenResultRefresh = Security.checkToken(USER, tokenResultCreate.getNewToken(), APPLICATION, DEVICE, true);
        Assert.assertTrue(tokenResultRefresh.isCheckOk());
        Assert.assertNotNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY));
        Assert.assertNotNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY + KVMemoryMap.KVDB_NEW_TOKEN_IDENTIFIER));
        Assert.assertNotEquals(tokenResultCreate.getNewToken(), tokenResultRefresh.getNewToken());
        TokenResult tokenResultCheckRefresh =
            Security.checkToken(USER, tokenResultRefresh.getNewToken(), APPLICATION, DEVICE, false);
        Assert.assertTrue(tokenResultCheckRefresh.isCheckOk());
        Assert.assertNotNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY));
        Assert.assertEquals(tokenResultRefresh.getNewToken(), KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY));
        Assert.assertNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY + KVMemoryMap.KVDB_NEW_TOKEN_IDENTIFIER));
    }

    @Test
    public void testCheckTokenRefreshCkeckWithUncommitedValue() {
        prepareKvMap(true);
        Security.createNewUser(USER, PASS, true);
        TokenResult tokenResultCreate = Security.createToken(USER, PASS, APPLICATION, DEVICE);
        TokenResult tokenResultRefresh = Security.checkToken(USER, tokenResultCreate.getNewToken(), APPLICATION, DEVICE, true);
        Assert.assertTrue(tokenResultRefresh.isCheckOk());
        Assert.assertNotNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY));
        Assert.assertNotNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY + KVMemoryMap.KVDB_NEW_TOKEN_IDENTIFIER));
        Assert.assertNotEquals(tokenResultCreate.getNewToken(), tokenResultRefresh.getNewToken());
        TokenResult tokenResultCheckRefresh =
            Security.checkToken(USER, tokenResultCreate.getNewToken(), APPLICATION, DEVICE, false);
        Assert.assertTrue(tokenResultCheckRefresh.isCheckOk());
        Assert.assertNotNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY));
        Assert.assertEquals(tokenResultCreate.getNewToken(), KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY));
        Assert.assertNull(KVMemoryMap.getInstance().readValueFromKey(TOKEN_KEY + KVMemoryMap.KVDB_NEW_TOKEN_IDENTIFIER));
    }

    private void prepareKvMap(boolean allowsLogin) {
        KVMemoryMap.getInstance().reset();
        KVMemoryMap.getInstance().writeKeyValue("application.allowsLogin", Boolean.toString(allowsLogin), true);
    }

}
