package mfi.files.logic;

import org.junit.Test;

import junit.framework.TestCase;

public class SecurityTest extends TestCase {

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
}
