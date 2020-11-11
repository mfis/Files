package mfi.files.logic;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;

import mfi.files.io.FilesFile;
import mfi.files.logic.Filter.IgnoreCase;
import mfi.files.logic.Filter.Preset;
import mfi.files.maps.KVMemoryMap;

public class DateiBackup {

	public static boolean isInBackupPfad(FilesFile file) {

		Set<String> basisPfade = ermittleBasisPfade();
		Set<String> ausnahmePfade = ermittleAusnahmePfade();
		boolean match = Filter.matches(file.getAbsolutePath(), basisPfade, ausnahmePfade, Preset.STARTS_WITH, IgnoreCase.YES);

		return match;
	}

	public static Collection<String> erstellePfadListeAllerBackupDateien() {

		Set<String> alleDateien = new HashSet<String>();
		Set<String> basisPfade = ermittleBasisPfade();
		Set<String> ausnahmePfade = ermittleAusnahmePfade();

		for (String pfad : basisPfade) {
			FilesFile rootFile = new FilesFile(pfad);
			if (rootFile.exists()) {
				List<File> partList = (List<File>) FileUtils.listFiles(rootFile, FileFilterUtils.trueFileFilter(),
						FileFilterUtils.trueFileFilter());
				for (File file : partList) {
					alleDateien.add(file.getAbsolutePath());
				}
			}
		}
		Collection<String> filtered = Filter.filter(alleDateien, null, ausnahmePfade, Preset.STARTS_WITH, IgnoreCase.YES);
		return filtered;
	}

	public static Collection<FilesFile> erstelleFileListeAllerBackupDateien() {

		Collection<FilesFile> files = new LinkedList<FilesFile>();
		Collection<String> pfade = erstellePfadListeAllerBackupDateien();
		for (String string : pfade) {
			files.add(new FilesFile(string));
		}
		return files;
	}

	private static Set<String> ermittleBasisPfade() {

		Set<String> set = new HashSet<String>();

		List<String[]> backupRoots = KVMemoryMap.getInstance().readListWithPartKey("user", "homeDirectory");
		for (String[] kvEntry : backupRoots) {
			set.add(kvEntry[1]);
		}

		if (StringUtils.isNotBlank(KVMemoryMap.getInstance().readValueFromKey("application.backup.add.dirs"))) {
			StringTokenizer tokenizerFav = new StringTokenizer(KVMemoryMap.getInstance().readValueFromKey("application.backup.add.dirs"),
					",");
			while (tokenizerFav.hasMoreElements()) {
				set.add(((String) tokenizerFav.nextElement()).trim());
			}
		}

		return set;
	}

	private static Set<String> ermittleAusnahmePfade() {

		Set<String> set = new HashSet<String>();

		if (StringUtils.isNotBlank(KVMemoryMap.getInstance().readValueFromKey("application.backup.sub.dirs"))) {

			StringTokenizer tokenizerFav = new StringTokenizer(KVMemoryMap.getInstance().readValueFromKey("application.backup.sub.dirs"),
					",");
			while (tokenizerFav.hasMoreElements()) {
				set.add(((String) tokenizerFav.nextElement()).trim());
			}
		}

		String backupVerzeichnis = StringUtils.trimToNull(KVMemoryMap.getInstance().readValueFromKey("application.properties.backupLocal"));
		set.add(backupVerzeichnis);

		return set;
	}
}
