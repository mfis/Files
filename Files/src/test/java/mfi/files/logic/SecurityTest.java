package mfi.files.logic;

import org.junit.Test;

import junit.framework.TestCase;

public class SecurityTest extends TestCase {

	@Test
	public void testCleanUpSubKey() {

		assertEquals(null, Security.cleanUpSubKey(null));
		assertEquals("abcd", Security.cleanUpSubKey("abcd"));
		assertEquals("a_bc_d", Security.cleanUpSubKey("a.b c=d"));
		assertEquals("abcd", Security.cleanUpSubKey("a\rb\nc\r\nd"));
		assertEquals("abcd", Security.cleanUpSubKey("a\tbcd"));
	}

}
