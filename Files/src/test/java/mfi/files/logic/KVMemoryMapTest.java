package mfi.files.logic;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import junit.framework.TestCase;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;

public class KVMemoryMapTest extends TestCase {

	@Test
	public void testReadWriteReset() {

		KVMemoryMap.getInstance().reset();
		assertEquals(0, KVMemoryMap.getInstance().countEntries());

		KVMemoryMap.getInstance().writeKeyValue("key1", "value1", true);
		assertEquals(1, KVMemoryMap.getInstance().countEntries());

		String val1 = KVMemoryMap.getInstance().readValueFromKey("key1");
		assertEquals("value1", val1);

		KVMemoryMap.getInstance().writeKeyValue("key2", "value2", true);
		assertEquals(2, KVMemoryMap.getInstance().countEntries());

		boolean overwritten = KVMemoryMap.getInstance().writeKeyValue("key2", "value2NEW", false);
		assertEquals(false, overwritten);
		assertEquals(2, KVMemoryMap.getInstance().countEntries());

		String val2 = KVMemoryMap.getInstance().readValueFromKey("key2");
		assertEquals("value2", val2);

		boolean overwritten2 = KVMemoryMap.getInstance().writeKeyValue("key2", "value2NEW", true);
		assertEquals(true, overwritten2);

		String val2NEW = KVMemoryMap.getInstance().readValueFromKey("key2");
		assertEquals("value2NEW", val2NEW);
		assertEquals(2, KVMemoryMap.getInstance().countEntries());

		KVMemoryMap.getInstance().reset();
		assertEquals(0, KVMemoryMap.getInstance().countEntries());
	}

	@Test
	public void testReadValueList() {

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().writeKeyValue("do.not.read.a", "x", false);
		KVMemoryMap.getInstance().writeKeyValue("test.list.key.[0]", "value0", false);
		KVMemoryMap.getInstance().writeKeyValue("do.not.read.b", "x", false);
		KVMemoryMap.getInstance().writeKeyValue("test.list.key.[1]", "value1", false);
		KVMemoryMap.getInstance().writeKeyValue("test.list.key.[2]", "value2", false);
		KVMemoryMap.getInstance().writeKeyValue("do.not.read.c", "x", false);

		List<String> valueList = KVMemoryMap.getInstance().readValueList("test.list.key");

		assertTrue(valueList.size() == 3);
		assertTrue(valueList.get(0).equals("value0"));
		assertTrue(valueList.get(1).equals("value1"));
		assertTrue(valueList.get(2).equals("value2"));
	}

	@Test
	public void testWriteToValueList() {

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue0", false);
		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue1", false);
		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue2", false);

		List<String> valueList = KVMemoryMap.getInstance().readValueList("test.write.list");

		assertTrue(KVMemoryMap.getInstance().countEntries() == 3);
		assertTrue(valueList.size() == 3);
		assertTrue(valueList.contains("writeValue0"));
		assertTrue(valueList.contains("writeValue1"));
		assertTrue(valueList.contains("writeValue2"));

		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue1", false);
		assertTrue(KVMemoryMap.getInstance().countEntries() == 3);

		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue0", true);
		assertTrue(KVMemoryMap.getInstance().countEntries() == 4);

	}

	@Test
	public void testDeleteFromValueList() {

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue0", false);
		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue1", false);
		KVMemoryMap.getInstance().writeToValueList("test.write.list", "writeValue2", false);
		KVMemoryMap.getInstance().writeToValueList("test.write.xx", "yy", false);
		KVMemoryMap.getInstance().writeKeyValue("do.not.read.a", "x", false);

		KVMemoryMap.getInstance().deleteValueFromList("test.write.list", "writeValue1");

		List<String> valueList = KVMemoryMap.getInstance().readValueList("test.write.list");

		assertTrue(KVMemoryMap.getInstance().countEntries() == 4);
		assertTrue(valueList.size() == 2);
		assertTrue(valueList.contains("writeValue0"));
		assertTrue(valueList.contains("writeValue2"));
	}

	@Test
	public void testSaveLoad() throws IOException {

		KVMemoryMap.getInstance().reset();
		FilesFile file1 = FilesFile.createTempFile("kvm11", ".txt");
		file1.deleteOnExit();
		KVMemoryMap.getInstance().load(file1);

		KVMemoryMap.getInstance().writeKeyValue("test.key.a", "valueA", false);
		KVMemoryMap.getInstance().writeKeyValue("test.key.b", "valueB", false);
		KVMemoryMap.getInstance().writeKeyValue("test.key.c", "valueC", false);
		KVMemoryMap.getInstance().writeKeyValue(KVMemoryMap.PREFIX_TEMPORARY + "test.key.d", "valueD", false);

		KVMemoryMap.getInstance().save();

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().load(file1);
		assertTrue(KVMemoryMap.getInstance().countEntries() == 4);
		assertTrue(KVMemoryMap.getInstance().readValueFromKey("test.key.a").equals("valueA"));
		assertTrue(KVMemoryMap.getInstance().readValueFromKey("test.key.b").equals("valueB"));
		assertTrue(KVMemoryMap.getInstance().readValueFromKey("test.key.c").equals("valueC"));
		assertTrue(KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.PREFIX_TEMPORARY + "test.key.d").equals("valueD"));
	}

	@Test
	public void testChangeSinceLastSave() throws IOException {

		KVMemoryMap.getInstance().reset();

		FilesFile file1 = FilesFile.createTempFile("kvm21", ".txt");
		file1.deleteOnExit();
		KVMemoryMap.getInstance().load(file1);

		KVMemoryMap.getInstance().save();

		assertFalse(KVMemoryMap.getInstance().isChangeSinceLastSave());

		KVMemoryMap.getInstance().writeKeyValue("test.key.a", "valueA", false);
		assertTrue(KVMemoryMap.getInstance().isChangeSinceLastSave());

		KVMemoryMap.getInstance().save();
		assertFalse(KVMemoryMap.getInstance().isChangeSinceLastSave());
	}

	@Test
	public void testDeleteKey() {

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().writeKeyValue("test.key.a", "valueA", false);
		KVMemoryMap.getInstance().writeKeyValue("test.key.b", "valueB", false);
		KVMemoryMap.getInstance().writeKeyValue("test.key.c", "valueC", false);
		assertTrue(KVMemoryMap.getInstance().countEntries() == 3);

		KVMemoryMap.getInstance().deleteKey("test.key.c");
		assertTrue(KVMemoryMap.getInstance().countEntries() == 2);

		assertTrue(KVMemoryMap.getInstance().containsKey("test.key.a"));
		assertTrue(KVMemoryMap.getInstance().containsKey("test.key.b"));
		assertFalse(KVMemoryMap.getInstance().containsKey("test.key.c"));
	}

	@Test
	public void testDeleteKeyRangeStartsWith() {

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().writeKeyValue("test.key.a", "valueA", false);
		KVMemoryMap.getInstance().writeKeyValue("anderer.key1", "a1", false);
		KVMemoryMap.getInstance().writeKeyValue("test.key.b", "valueB", false);
		KVMemoryMap.getInstance().writeKeyValue("anderer.key2", "a2", false);
		KVMemoryMap.getInstance().writeKeyValue("test.key.c", "valueC", false);

		KVMemoryMap.getInstance().deleteKeyRangeStartsWith("test.key.");
		assertTrue(KVMemoryMap.getInstance().countEntries() == 2);

		assertTrue(KVMemoryMap.getInstance().containsKey("anderer.key1"));
		assertTrue(KVMemoryMap.getInstance().containsKey("anderer.key2"));
		assertFalse(KVMemoryMap.getInstance().containsKey("test.key.b"));
	}

	@Test
	public void testPartKey() {

		KVMemoryMap.getInstance().reset();

		KVMemoryMap.getInstance().writeKeyValue("generic.key.ABC", "123", true);
		KVMemoryMap.getInstance().writeKeyValue("generic.key.DEF", "456", true);
		KVMemoryMap.getInstance().writeKeyValue("generic.key.GHI", "789", true);

		List<String[]> list = KVMemoryMap.getInstance().readListWithPartKey("generic.key.");

		assertEquals("ABC", list.get(0)[0]);
		assertEquals("123", list.get(0)[1]);

		assertEquals("DEF", list.get(1)[0]);
		assertEquals("456", list.get(1)[1]);

		assertEquals("GHI", list.get(2)[0]);
		assertEquals("789", list.get(2)[1]);
	}

}
