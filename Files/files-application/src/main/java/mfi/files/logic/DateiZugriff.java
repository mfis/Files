package mfi.files.logic;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import mfi.files.helper.FilesComparator;
import mfi.files.helper.Hilfsklasse;
import mfi.files.io.FilesFile;
import mfi.files.maps.FileMap;
import mfi.files.model.Model;

public class DateiZugriff {

	public static int fileGroesseAlsInt(FilesFile file) {
		long size = file.length();
		return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
	}

	public static String fileGroesseFormatieren(FilesFile file) {

		if (file.isDirectory() || file.isFile()) {
			long groesse = file.isFile() ? file.length() : FileUtils.sizeOfDirectory(file);
			return speicherGroesseFormatieren(groesse);
		} else {
			return "nicht ermittelbar";
		}
	}

	public static String speicherGroesseFormatieren(long groesse) {

		final long KILOBYTE_GRENZE = 1024 * 1024;
		final long MEGABYTE_GRENZE = KILOBYTE_GRENZE * 1024;

		if (groesse < KILOBYTE_GRENZE) {
			long kb = (groesse / 1024);
			return (kb == 0 ? 1 : kb) + " kB";
		} else {
			if (groesse < MEGABYTE_GRENZE) {
				DecimalFormat df = new DecimalFormat("0.0");
				return (df.format((double) groesse / (double) KILOBYTE_GRENZE)) + " MB";
			} else {
				DecimalFormat df = new DecimalFormat("0.00");
				return (df.format((double) groesse / (double) MEGABYTE_GRENZE)) + " GB";
			}
		}
	}

	public static String leseDateiAenderer(FilesFile file, Model model) {
		return FileMap.getInstance().readEditor(file);
	}

	public static String formatLastModifiedDateAsString(FilesFile file, boolean humanReadable) {

		SimpleDateFormat sdf;
		if (humanReadable) {

			Calendar today = Calendar.getInstance();
			today.set(Calendar.MILLISECOND, 0);
			today.set(Calendar.SECOND, 0);
			today.set(Calendar.MINUTE, 0);
			today.set(Calendar.HOUR_OF_DAY, 0);

			Calendar yesterday = Calendar.getInstance();
			yesterday.add(Calendar.DAY_OF_YEAR, -1);

			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(file.lastModified());

			if (today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)) {
				sdf = Hilfsklasse.lookupSimpleDateFormat("HH:mm");
				return "heute, " + sdf.format(cal.getTime());
			} else if (yesterday.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
					&& yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)) {
				sdf = Hilfsklasse.lookupSimpleDateFormat("HH:mm");
				return "gestern, " + sdf.format(cal.getTime());
			} else if ((today.getTimeInMillis() - cal.getTimeInMillis()) < 1000 * 60 * 60 * 24 * 6) {
				sdf = Hilfsklasse.lookupSimpleDateFormat("EEE, HH:mm");
				return sdf.format(cal.getTime());
			} else if (today.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
				sdf = Hilfsklasse.lookupSimpleDateFormat("dd. MMM");
				return sdf.format(cal.getTime());
			} else {
				sdf = Hilfsklasse.lookupSimpleDateFormat("dd. MMM yyyy");
				return sdf.format(cal.getTime());
			}

		} else {
			Date lastMod = new Date(file.lastModified());
			sdf = Hilfsklasse.lookupSimpleDateFormat("dd.MM.yyyy  HH:mm:ss");
			return sdf.format(lastMod);
		}
	}

	public static LinkedList<FilesFile> lesePfadListe(Model model) {

		LinkedList<FilesFile> liste = new LinkedList<FilesFile>();

		boolean uebersichtMoeglich = model.getVerzeichnisBerechtigungen() != null && model.getVerzeichnisBerechtigungen().size() > 1;

		if (uebersichtMoeglich && !Security.isDirectoryAllowedForUser(model, model.lookupConversation().getVerzeichnis())) {
			// Verzeichnis-Uebersicht aufbauen
			model.lookupConversation().setVerzeichnis(null);
			for (String ber : model.getVerzeichnisBerechtigungen()) {
				FilesFile berFile = new FilesFile(ber);
				if (berFile.exists() && berFile.isDirectory()) {
					liste.add(berFile);
				}
			}
			return liste;
		}

		FilesFile[] dirs = null;

		List<FilesFile> parents = new LinkedList<FilesFile>();
		List<FilesFile> folders = new LinkedList<FilesFile>();
		List<FilesFile> files = new LinkedList<FilesFile>();

		if (StringUtils.isNotEmpty(model.lookupConversation().getVerzeichnis())) {
			File file = new FilesFile(model.lookupConversation().getVerzeichnis());
			parents.add((FilesFile) file);
			dirs = ((FilesFile) file).listFiles();
			boolean weiter = true;
			int infiniteLoopPrevention = 0;
			while (weiter) {
				infiniteLoopPrevention++;
				if (infiniteLoopPrevention > 50000) {
					model.lookupConversation().getMeldungen().add("InfiniteLoopPrevention for FileSystem Path: " + file.getAbsolutePath());
					break;
				}
				if (file.exists() && file.isDirectory()) {
					String parent = file.getParent();
					if (parent != null) {
						if (Security.isDirectoryAllowedForUser(model, parent)) {
							parents.add(new FilesFile(parent));
							if (file.getParentFile() != null) {
								file = file.getParentFile();
							} else {
								weiter = false;
							}
						} else {
							weiter = false;
						}
					} else {
						weiter = false;
					}
				} else {
					weiter = false;
				}
			}
			if (uebersichtMoeglich) {
				parents.add(new FilesFile("/")); // Uebersicht Eintrag
			}
		}

		if (dirs != null) {
			for (FilesFile file : dirs) {
				if (file.isDirectory()) {
					folders.add(file);
				} else {
					files.add(file);
				}
			}
		}

		FilesComparator comparator = new FilesComparator();

		Collections.reverse(parents);
		Collections.sort(folders, comparator);
		Collections.sort(files, comparator);

		liste.addAll(parents);
		liste.addAll(folders);
		liste.addAll(files);
		return liste;
	}
}
