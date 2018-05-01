package mfi.files.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import mfi.files.logic.Filter.IgnoreCase;
import mfi.files.logic.Filter.Preset;

public class FilterTest {

	@Test
	public void testStartsWithEmpty() {

		List<String> items = new LinkedList<String>();
		Set<String> in = new HashSet<String>();
		Set<String> ex = new HashSet<String>();

		List<String> list = Filter.filter(items, in, ex, Preset.STARTS_WITH, IgnoreCase.YES);

		assertTrue(list.isEmpty());
	}

	@Test
	public void testStartsWithNull() {

		List<String> items = new LinkedList<String>();
		Set<String> in = null;
		Set<String> ex = new HashSet<String>();

		List<String> list = Filter.filter(items, in, ex, Preset.STARTS_WITH, IgnoreCase.YES);

		assertTrue(list.isEmpty());
	}

	@Test
	public void testStartsWithNoInNoEx() {

		List<String> items = new LinkedList<String>();
		items.add("a");
		items.add("b");
		Set<String> in = new HashSet<String>();
		Set<String> ex = new HashSet<String>();

		List<String> list = Filter.filter(items, in, ex, Preset.STARTS_WITH, IgnoreCase.YES);

		assertEquals(2, list.size());
	}

	@Test
	public void testStartsWithNoIn() {

		List<String> items = new LinkedList<String>();
		items.add("a");
		items.add("b");
		Set<String> in = new HashSet<String>();
		Set<String> ex = new HashSet<String>();
		ex.add("a");

		List<String> list = Filter.filter(items, in, ex, Preset.STARTS_WITH, IgnoreCase.YES);

		assertEquals(1, list.size());
		assertEquals("b", list.get(0));
	}

	@Test
	public void testStartsWithNoEx() {

		List<String> items = new LinkedList<String>();
		items.add("a");
		items.add("b");
		Set<String> in = new HashSet<String>();
		in.add("a");
		Set<String> ex = new HashSet<String>();

		List<String> list = Filter.filter(items, in, ex, Preset.STARTS_WITH, IgnoreCase.YES);

		assertEquals(1, list.size());
		assertEquals("a", list.get(0));
	}

	@Test
	public void testStartsWithCombined1() {

		List<String> items = new LinkedList<String>();
		items.add("abcdefg");
		items.add("abc");
		items.add("xyz");
		Set<String> in = new HashSet<String>();
		in.add("ab");
		Set<String> ex = new HashSet<String>();

		List<String> list = Filter.filter(items, in, ex, Preset.STARTS_WITH, IgnoreCase.YES);

		assertEquals(2, list.size());
		assertEquals("abcdefg", list.get(0));
		assertEquals("abc", list.get(1));
	}

	@Test
	public void testStartsWithCombined2() {

		List<String> items = new LinkedList<String>();
		items.add("abcdefg");
		items.add("abc");
		items.add("xyz");
		Set<String> in = new HashSet<String>();
		in.add("ab");
		Set<String> ex = new HashSet<String>();
		ex.add("abcd");

		List<String> list = Filter.filter(items, in, ex, Preset.STARTS_WITH, IgnoreCase.YES);

		assertEquals(1, list.size());
		assertEquals("abc", list.get(0));
	}

	@Test
	public void testMatchesTrue() {

		Set<String> in = new HashSet<String>();
		in.add("ab");

		boolean b = Filter.matches("abcde", in, null, Preset.STARTS_WITH, IgnoreCase.YES);

		assertEquals(true, b);
	}

	@Test
	public void testMatchesFalse() {

		Set<String> in = new HashSet<String>();
		in.add("abX");

		boolean b = Filter.matches("abcde", in, null, Preset.STARTS_WITH, IgnoreCase.YES);

		assertEquals(false, b);
	}

}
