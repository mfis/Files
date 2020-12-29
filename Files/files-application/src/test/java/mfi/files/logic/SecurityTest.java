package mfi.files.logic;

import org.junit.Assert;
import org.junit.Test;
import junit.framework.TestCase;
import mfi.files.maps.KVMemoryMap;

public class SecurityTest extends TestCase {

    private static final String USER = "user";

    private static final String PASS = "pass";

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

    private void prepareKvMap(boolean allowsLogin) {
        KVMemoryMap.getInstance().reset();
        KVMemoryMap.getInstance().writeKeyValue("application.allowsLogin", Boolean.toString(allowsLogin), true);
    }

}
