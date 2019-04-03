package mfi.files.logic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import junit.framework.TestCase;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;

public class FilesFileTest extends TestCase {

	long start = 0;
	long stop = 0;
	static long summary = 0;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		System.out.println("====== SETUP ======");
	}

	@Override
	protected void tearDown() throws Exception {
		long diff = stop - start;
		summary += diff;
		double sec = diff / (double) 1000;
		double secSum = summary / (double) 1000;
		System.out.println("====== TEARDOWN ======");
		System.out.println("Time for Test:" + sec);
		System.out.println("Time Summary :" + secSum);
		super.tearDown();
	}

	@Test
	public void testStringVariousLength() throws IOException {

		KVMemoryMap.getInstance().reset();

		for (int i = 0; i < 500; i = i + 50) {
			String s = RandomStringUtils.randomAlphanumeric((i * 2000));
			FilesFile file = FilesFile.createTempFile("test", "test");
			file.write(s);
			ByteArrayOutputStream o = new ByteArrayOutputStream();

			start = System.currentTimeMillis();

			file.readIntoOutputStream(o);

			stop = System.currentTimeMillis();
			assertEquals(file.length(), o.size());
			file.deleteOnExit();
		}
	}

	@Test
	public void testLookupCryptoPathNameForFile() throws IOException {

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().writeKeyValue("application.properties.cipherFileNameCryptoKey", "secret", true);
		KVMemoryMap.getInstance().writeKeyValue("application.properties.cipherFileSuffix", "suffix", true);

		FilesFile file = new FilesFile("/one/two/three");
		String c = FilesFile.lookupServerCryptoPathNameForFile(file);
		System.out.println(c);
		assertTrue(c != null && c.length() > file.getAbsolutePath().length());
		assertEquals(StringUtils.countMatches(file.getAbsolutePath(), File.separator), StringUtils.countMatches(c, File.separator));

	}

}
