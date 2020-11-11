package mfi.files.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import mfi.files.model.TextFileMetaTag;
import mfi.files.model.TextFileMetaTagName;

public class MetaTagParserTest {

	@Test
	public void testIsTagLine() {

		assertFalse(MetaTagParser.isMetaTagLine(null));
		assertFalse(MetaTagParser.isMetaTagLine(""));
		assertFalse(MetaTagParser.isMetaTagLine("&&"));

		assertTrue(MetaTagParser.isMetaTagLine("&&META"));
		assertTrue(MetaTagParser.isMetaTagLine("  &&   META "));
		assertTrue(MetaTagParser.isMetaTagLine(" && meta"));

	}

	@Test
	public void testParse() {

		MetaTagParser parser = new MetaTagParser();

		assertNotNull(parser.parseTags(new LinkedList<String>(), false));
		assertEquals(0, parser.parseTags(new LinkedList<String>(), false).size());

		String test = "&& META && newItemOnTop &&DATE &&lala && FORMAT{\"-\"} &&TEXT "
				+ "&&FORMAT { : } && choice:Welches { \" dies, das & solches\", \"was anderes\"} "
				+ "&& CHOICE{1,2,3} && CHOICE : Die Zahlen {a1 und a2,\"3,14\"}";
		List<String> zeilen = new LinkedList<String>();
		zeilen.add(test);
		List<TextFileMetaTag> tags = parser.parseTags(zeilen, false);

		assertNotNull(tags);
		assertEquals(8, tags.size());
		boolean errorFound = false;
		for (String err : parser.getErrors()) {
			if (StringUtils.contains(err, "lala")) {
				errorFound = true;
			}
		}
		assertTrue(errorFound);

		assertEquals(tags.get(0).getArguments().size(), 0);

		assertEquals(TextFileMetaTagName.NEW_ITEM_ON_TOP, tags.get(0).getTextFileMetaTagName());
		assertEquals(tags.get(2).getCaption(), null);
		assertEquals(tags.get(2).getArguments().size(), 1);
		assertEquals(tags.get(2).getArguments().get(0), "-");
		assertEquals(tags.get(4).getArguments().get(0), ":");
		assertEquals(tags.get(5).getCaption(), "Welches");
		assertEquals(tags.get(5).getArguments().get(0), "dies, das & solches");
		assertEquals(tags.get(5).getArguments().get(1), "was anderes");
		assertEquals(tags.get(6).getArguments().get(0), "1");
		assertEquals(tags.get(6).getArguments().get(1), "2");
		assertEquals(tags.get(6).getArguments().get(2), "3");
		assertEquals(tags.get(7).getCaption(), "Die Zahlen");
		assertEquals(tags.get(7).getArguments().get(0), "a1 und a2");
		assertEquals(tags.get(7).getArguments().get(1), "3,14");

	}
}
