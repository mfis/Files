package mfi.files.model;

public class CategoryMarker {

	public int categoryLine;
	public int firstEntry;
	public int lastEntry;
	public String categoryName = null;

	public boolean isValidCategory() {
		// -1=noch nicht gefunden,-2=nicht relevant
		return categoryLine != -1 && firstEntry != -1 && lastEntry != -1;
	}
}