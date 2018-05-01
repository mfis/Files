package mfi.files.responsibles;

import java.util.Collection;
import java.util.Map;

import mfi.files.annotation.Responsible;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.ButtonBar;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.logic.DateiBackup;
import mfi.files.logic.DateiZugriff;
import mfi.files.model.Condition;
import mfi.files.model.Model;

public class Backups extends AbstractResponsible {

	@Responsible(conditions = { Condition.BACKUPS_START, Condition.BACKUP_MAKE_FULLBACKUP })
	public void backup(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		Collection<String> alleBackupDateien = DateiBackup.erstellePfadListeAllerBackupDateien();

		if (model.lookupConversation().getCondition().equals(Condition.BACKUP_MAKE_FULLBACKUP)) {

			sb.append(HTMLUtils.buildMenuNar(model, "Backup gestartet", true, null, false));
			HTMLTable table = new HTMLTable();
			for (String item : alleBackupDateien) {
				FilesFile fileToBackup = new FilesFile(item);
				fileToBackup.backupFile(model);
			}
			sb.append(table.buildTable(model));
			model.lookupConversation().getMeldungen().add("Es wurden " + alleBackupDateien.size() + " Dateien fürs Backup vorgesehen.");

		} else {

			ButtonBar buttonBar = new ButtonBar();
			buttonBar.getButtons().add(new Button("Vollbackup erstellen", Condition.BACKUP_MAKE_FULLBACKUP));
			sb.append(HTMLUtils.buildMenuNar(model, "Backup Liste", true, buttonBar, false));

			long size = 0;
			for (String item : alleBackupDateien) {
				FilesFile fileToBackup = new FilesFile(item);
				size += fileToBackup.length();
			}
			String sizeString = DateiZugriff.speicherGroesseFormatieren(size);

			HTMLTable table = new HTMLTable();
			table.addTD(alleBackupDateien.size() + " Dateien fürs Backup - " + sizeString, HTMLTable.TABLE_HEADER);
			table.addNewRow();
			for (String item : alleBackupDateien) {
				FilesFile fileToBackup = new FilesFile(item);
				table.addTD(fileToBackup.dateiNameUndPfadKlartext(), null);
				table.addNewRow();
			}
			sb.append(table.buildTable(model));
		}

	}

}
