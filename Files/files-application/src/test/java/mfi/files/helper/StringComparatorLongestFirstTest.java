package mfi.files.helper;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import junit.framework.TestCase;

public class StringComparatorLongestFirstTest extends TestCase {

	@Test
	public void testSort() {

		List<String> list = new LinkedList<String>();

		list.add("OOO");
		list.add("O");
		list.add(null);
		list.add("OOOO");
		list.add("OO");

		Collections.sort(list, new StringComparatorLongestFirst());

		System.out.println(list.toString());

		assertEquals("OOOO", list.get(0));
		assertEquals("OOO", list.get(1));
		assertEquals("OO", list.get(2));
		assertEquals("O", list.get(3));
		assertEquals(null, list.get(4));

	}

}
