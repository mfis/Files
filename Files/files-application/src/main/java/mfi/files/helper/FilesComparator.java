package mfi.files.helper;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.io.FilesFile;

public class FilesComparator implements Comparator<FilesFile> {

	private static Logger logger = LoggerFactory.getLogger(FilesComparator.class);

	@Override
	public int compare(FilesFile f1, FilesFile f2) {

		if (f1 == null && f2 == null) {
			return 0;
		}

		if (f1 == null && f2 != null) {
			return 1;
		}

		if (f1 != null && f2 == null) {
			return -1;
		}

		String f1Klartext = "";
		String f2Klartext = "";

		try {
			f1Klartext = f1.dateiNameUndPfadKlartext();
		} catch (Exception e) {
			logger.warn("Klartext zu Datei nicht ermittelbar:" + f1.getAbsolutePath());
		}

		try {
			f2Klartext = f2.dateiNameUndPfadKlartext();
		} catch (Exception e) {
			logger.warn("Klartext zu Datei nicht ermittelbar:" + f2.getAbsolutePath());
		}

		return f1Klartext.compareToIgnoreCase(f2Klartext);
	}

}
