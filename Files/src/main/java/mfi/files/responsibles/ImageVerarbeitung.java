package mfi.files.responsibles;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import mfi.files.annotation.Responsible;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.ButtonBar;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.model.Condition;
import mfi.files.model.Model;

@Component
public class ImageVerarbeitung extends AbstractResponsible {

	@Responsible(conditions = { Condition.IMAGE_VIEW_WITH_MENU, Condition.IMAGE_VIEW_FULLSCREEN })
	public void fjImageAnzeigen(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		if (model.lookupConversation().getEditingFile().isDirectory()) {
			model.lookupConversation().setForwardCondition(Condition.FS_FILE_LISTING);
			return;
		}
		if (!model.lookupConversation().getEditingFile().isReadable()) {
			// passwort benoetigt? Dann erstmal zur Passwortmaske weiterleiten
			if (model.lookupConversation().getEditingFile().isClientCrypted()) {
				if (!model.lookupConversation().getEditingFile().isClientKnowsPassword()) {
					model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_CLIENT);
					return;
				}
			} else {
				model.lookupConversation().setForwardCondition(Condition.PASSWORD_ASK_DECRYPT_SERVER);
				return;
			}
		}

		boolean fullscreen = model.lookupConversation().getCondition().equals(Condition.IMAGE_VIEW_FULLSCREEN);

		List<FilesFile> list = model.lookupConversation().getFsListe();
		boolean isNext = (navigateImage(model, +1, list) != -1);
		boolean isPrev = (navigateImage(model, -1, list) != -1);

		if (!fullscreen) {
			ButtonBar buttonBar = new ButtonBar();
			buttonBar.getButtons().add(new Button("Vollbild", Condition.IMAGE_VIEW_FULLSCREEN));
			if (isNext) {
				buttonBar.getButtons().add(new Button("Nächstes", Condition.IMAGE_NEXT));
			}
			if (isPrev) {
				buttonBar.getButtons().add(new Button("Vorheriges", Condition.IMAGE_PREV));
			}
			sb.append(HTMLUtils.buildMenuNar(model, "Image anzeigen", true, buttonBar, true));
			sb.append(HTMLUtils.contentBreakMitTrennlinie());
		}

		sb.append(HTMLUtils.buildImage(model.lookupConversation().getEditingFile(), fullscreen,
				fullscreen ? Condition.IMAGE_VIEW_FULLSCREEN_AFTER_RESET_CLIENT_PW : Condition.IMAGE_VIEW_WITH_MENU_AFTER_RESET_CLIENT_PW,
				model));

		if (fullscreen) {
			sb.append(HTMLUtils.buildImageNavigation(isPrev ? Condition.IMAGE_PREV_FULL : null, Condition.FS_CANCEL_EDITED_FILE,
					isNext ? Condition.IMAGE_NEXT_FULL : null, model));
		}

		return;
	}

	@Responsible(conditions = { Condition.IMAGE_PREV, Condition.IMAGE_NEXT, Condition.IMAGE_PREV_FULL, Condition.IMAGE_NEXT_FULL })
	public void fjImageNavigation(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		int direction = model.lookupConversation().getCondition() == Condition.IMAGE_NEXT
				|| model.lookupConversation().getCondition() == Condition.IMAGE_NEXT_FULL ? +1 : -1;

		List<FilesFile> list = model.lookupConversation().getFsListe();

		int neu = navigateImage(model, direction, list);

		if (neu != -1) {
			if (model.lookupConversation().getEditingFile().isClientCrypted()
					&& model.lookupConversation().getEditingFile().isClientKnowsPassword()) {
				list.get(neu).setClientKnowsPassword(true);
			}
			model.lookupConversation().setNextEditingFile(list.get(neu));
		}
		if (model.lookupConversation().getCondition() == Condition.IMAGE_NEXT_FULL
				|| model.lookupConversation().getCondition() == Condition.IMAGE_PREV_FULL) {
			model.lookupConversation().setForwardCondition(Condition.IMAGE_VIEW_FULLSCREEN);
		} else {
			model.lookupConversation().setForwardCondition(Condition.IMAGE_VIEW_WITH_MENU);
		}
		return;
	}

	private int navigateImage(Model model, int direction, List<FilesFile> list) {

		int neu = -1;
		int aktuell = lookupImagePos(model, list);

		if (aktuell != -1) {
			if (direction == -1) {
				for (int i = aktuell; i > -1; i--) {
					if (!list.get(i).dateiNameUndPfadKlartext()
							.equals(model.lookupConversation().getEditingFile().dateiNameUndPfadKlartext())) {
						if (list.get(i).isViewableImageType()) {
							neu = i;
							break;
						}
					}
				}
			}

			if (direction == +1) {
				for (int i = aktuell; i < list.size(); i++) {
					if (!list.get(i).dateiNameUndPfadKlartext()
							.equals(model.lookupConversation().getEditingFile().dateiNameUndPfadKlartext())) {
						if (list.get(i).isViewableImageType()) {
							neu = i;
							break;
						}
					}
				}
			}
		}
		return neu;
	}

	private int lookupImagePos(Model model, List<FilesFile> list) {

		int aktuell = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).dateiNameUndPfadKlartext().equals(model.lookupConversation().getEditingFile().dateiNameUndPfadKlartext())) {
				aktuell = i;
				break;
			}
		}
		return aktuell;
	}

}
