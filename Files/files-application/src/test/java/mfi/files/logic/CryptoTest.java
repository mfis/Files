package mfi.files.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import junit.framework.TestCase;

public class CryptoTest extends TestCase {

	long start = 0;
	long stop = 0;
	static long summary = 0;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		System.out.println("====== SETUP ======");
		start = System.currentTimeMillis();
	}

	@Override
	protected void tearDown() throws Exception {
		stop = System.currentTimeMillis();
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
	public void testDateiname() {

		String pass = "abcde";

		System.out.println("---- dateiname ----");
		String dateiname = "hallowelt.cnote";
		System.out.println("original       = " + dateiname);
		String enc = Crypto.encryptDateiName(dateiname, pass, "ganzSicher");
		System.out.println("verschluesselt = " + enc);
		String dec = Crypto.decryptDateiName(enc, pass, "ganzSicher");
		System.out.println("entschluesselt = " + dec);

		assertEquals(dateiname, dec);
	}

	@Test
	public void testString() {

		String pass = "abcde";

		System.out.println("---- string ----");
		String inhalt = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcÄÖÜäöüß!§$%&/()=";
		System.out.println("original       = " + inhalt);
		String enc = Crypto.encryptString(inhalt, pass);
		String dec = Crypto.decryptString(enc, pass);

		assertEquals(inhalt, dec);

	}

	@Test
	public void testCompatiblityEncoding() {

		String pass = "abcde";

		System.out.println("---- compatiblity encoding ----");
		String enc = Crypto.encryptString(getText(), pass);

		assertEquals(getEncryptedTextAsBase64(), enc);
	}

	@Test
	public void testCompatiblityDecoding() {

		String pass = "abcde";

		System.out.println("---- compatiblity decoding ----");
		String dec = Crypto.decryptString(getEncryptedTextAsBase64(), pass);

		assertEquals(getText(), dec);
	}

	@Test
	public void testStringChunks() {

		String pass = "abcde";

		System.out.println("---- string chunks ----");

		String inhalt = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcÄÖÜäöüß!§$%&/()=";
		while (inhalt.length() < Crypto.DECODING_CHUNK_SIZE * 3) {
			inhalt = inhalt + inhalt;
		}
		System.out.println("length = " + inhalt.length());
		String enc = Crypto.encryptString(inhalt, pass);
		String dec = Crypto.decryptString(enc, pass);

		assertEquals(inhalt, dec);

	}

	@Test
	public void testStringEmpty() {

		String pass = "abcde";

		System.out.println("---- dateiinhalt leer----");
		String inhalt = "";
		System.out.println("original       = " + inhalt);
		String enc = Crypto.encryptString(inhalt, pass);
		String dec = Crypto.decryptString(enc, pass);
		System.out.println("entschluesselt = " + dec);

		assertEquals(inhalt, dec);

	}

	@Test
	public void testStringVariousLength() {

		System.out.println("---- string various length ----");

		String pass = "abcdec6sr5ans54.,&$§";
		for (int i = 0; i < 30000; i = i + 2177) {
			String s = RandomStringUtils.randomAlphanumeric((i));
			String enc = Crypto.encryptString(s, pass);
			String dec = Crypto.decryptString(enc, pass);
			assertEquals(s, dec);
		}

	}

	@Test
	public void testBytes() {

		System.out.println("---- bytes ----");

		int[] sizes = new int[] { 0, 4, 20, 500, Crypto.ENCODING_CHUNK_SIZE - 1, Crypto.ENCODING_CHUNK_SIZE, Crypto.ENCODING_CHUNK_SIZE + 1,
				Crypto.ENCODING_CHUNK_SIZE * 3, 500000, Crypto.DECODING_CHUNK_SIZE - 1, Crypto.DECODING_CHUNK_SIZE,
				Crypto.DECODING_CHUNK_SIZE + 1, Crypto.DECODING_CHUNK_SIZE * 3 };

		Random random = new Random();
		for (int i : sizes) {

			byte[] bytes = new byte[i];
			random.nextBytes(bytes);
			String pwString = "bascauubcjabscshj";
			byte[] enc = Crypto.encrypt(bytes, pwString);
			byte[] dec = Crypto.decrypt(enc, pwString);

			assertTrue(Arrays.equals(bytes, dec));
		}

	}

	@Test
	public void testBytesFromString() throws UnsupportedEncodingException {

		System.out.println("---- bytes from string ----");

		String pass = "abcdec6sr5ans54.,&$§";
		for (int i = 0; i < 30000; i = i + 2177) {
			String s = RandomStringUtils.randomAlphanumeric((i));
			byte[] enc = Crypto.encrypt(s.getBytes("UTF-8"), pass);
			byte[] dec = Crypto.decrypt(enc, pass);
			assertEquals(s, new String(dec, "UTF-8"));
		}

	}

	@Test
	public void testPass() {

		System.out.println("---- pass ----");

		byte[] bytes = new byte[500];
		new Random().nextBytes(bytes);
		String pwString = "bascauubcjabscshj";

		byte[] enc = Crypto.encrypt(bytes, pwString);

		assertTrue(Crypto.checkDecryptionPassword(enc, pwString));
		assertFalse(Crypto.checkDecryptionPassword(enc, "zzzz"));

	}

	@Test
	public void testBytesEcactChunk() {

		System.out.println("---- bytes exact chunk ----");

		Random random = new Random();
		for (int i = 0; i < 20; i++) {

			byte[] bytes = new byte[Crypto.ENCODING_CHUNK_SIZE];
			random.nextBytes(bytes);
			String pwString = "bascauubcjabscshj";
			byte[] enc = Crypto.encrypt(bytes, pwString);
			byte[] dec = Crypto.decrypt(enc, pwString);

			assertTrue(Arrays.equals(bytes, dec));
		}

	}

	@Test
	public void testByteStream() {

		System.out.println("---- bytes stream ----");

		String pwString = "bascauubcjabscshj";
		byte[] bytes = new byte[Crypto.ENCODING_CHUNK_SIZE * 150];
		new Random().nextBytes(bytes);

		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		Crypto.encrypt(in, out, pwString);
		ByteArrayOutputStream out2 = new ByteArrayOutputStream();
		Crypto.decrypt(new ByteArrayInputStream(out.toByteArray()), out2, pwString);

		assertTrue(Arrays.equals(bytes, out2.toByteArray()));

	}

	@Test
	public void testBytesLength() {

		System.out.println("---- bytes exact length ----");

		byte[] bytes = new byte[Crypto.ENCODING_CHUNK_SIZE];
		new Random().nextBytes(bytes);
		String pwString = "bascauubcjabscshj";
		byte[] enc = Crypto.encrypt(bytes, pwString);
		assertEquals(enc.length, Crypto.DECODING_CHUNK_SIZE);

		byte[] bytes2 = new byte[Crypto.ENCODING_CHUNK_SIZE * 2];
		new Random().nextBytes(bytes2);
		String pwString2 = "bascauubcjabscshj";
		byte[] enc2 = Crypto.encrypt(bytes2, pwString2);
		assertEquals(Crypto.DECODING_CHUNK_SIZE * 2, enc2.length);

	}

	@Test
	public void testSalt() throws DecoderException {

		String pass = "abcdefgh((0))!?==";

		System.out.println("---- salt ----");
		String dec = Crypto.saltPasswort(pass);
		System.out.println(dec);

		assertEquals("abcdefgh((0))!?==30611ABCDEFGH12138", dec);
	}

	@Test
	public void testPasswordTo256bitKey() throws DecoderException {

		String pass = "aBcdefgH((0))!?==";

		System.out.println("---- passwordTo256bitKey ----");
		byte[] x = Crypto.passwordTo256bitKey(pass);
		String base64String = new String(Hex.encodeHex(x));
		System.out.println(base64String);

		assertEquals("4d769c49e179c0f27bfe009479b9c7bd3b28d65611e27a4ed99084e10b3f3e90", base64String);
	}

	@Test
	public void testPasswordTo128bitKey() throws DecoderException {

		String pass = "aBcdefgH((0))!?==";

		System.out.println("---- passwordTo256bitKey ----");
		byte[] x = Crypto.passwordTo128bitKey(pass);
		String base64String = new String(Hex.encodeHex(x));
		System.out.println(base64String);

		assertEquals("c67f025bb90e46e917e37cea61adae34", base64String);
	}

	private String getText() {
		return "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.   Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.   Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.   Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat.   Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis.   At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, At accusam aliquyam diam diam dolore dolores duo eirmod eos erat, et nonumy sed tempor et et invidunt justo labore Stet clita ea et gubergren, kasd magna no rebum. sanctus sea sed takimata ut vero voluptua. est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat.   Consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.   Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.   Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.   Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat.   Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis.   At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, At accusam aliquyam diam diam dolore dolores duo eirmod eos erat, et nonumy sed tempor et et invidunt justo labore Stet clita ea et gubergren, kasd magna no rebum. sanctus sea sed takimata ut vero voluptua. est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat.   Consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.   Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.   Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.   Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat.   Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis.   At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, At accusam aliquyam diam diam dolore dolores duo eirmod eos erat, et nonumy sed tempor et et invidunt justo labore Stet clita ea et gubergren, kasd magna no rebum. sanctus sea sed takimata ut vero voluptua. est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat.   Consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et ";
	}

	private String getEncryptedTextAsBase64() {
		return "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";
	}
}
