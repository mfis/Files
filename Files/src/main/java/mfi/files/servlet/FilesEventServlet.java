package mfi.files.servlet;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import mfi.files.helper.ServletHelper;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Model;

@Controller
public class FilesEventServlet {

	public static final String SERVLETPFAD = "/FilesEventServlet";

	@RequestMapping("/FilesEventServlet")
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

		ThreadLocalHelper.setConversationID(request.getParameter(HTMLUtils.CONVERSATION));
		HttpSession session = request.getSession(false);
		Model model = Security.lookupModelFromSession(session, request);
		Map<String, String> parameters = ServletHelper.parseRequest(request, session, Condition.EVENT);
		Security.checkSecurityForRequest(model, parameters);

		if (model == null || !model.isUserAuthenticated()) {
			throw new IllegalArgumentException("Kein User angemeldet.");
		}

		Integer conversationID = null;
		String conv = request.getParameter(HTMLUtils.CONVERSATION);
		if (StringUtils.isNumeric(conv)) {
			conversationID = Integer.parseInt(conv);
		}

		response.setContentType("text/event-stream");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());

		StringBuilder sb = new StringBuilder();
		Event event = lookupEvent(model, conversationID);
		sb.append("data:" + event.getData() + "\n");
		sb.append("retry:" + event.getRefresh() + "\n");
		sb.append("\n");

		response.setContentLength(sb.length());
		PrintWriter out = response.getWriter();
		out.write(sb.toString());
		out.flush();

	}

	private Event lookupEvent(Model model, Integer conversationID) {

		Event event = new Event();

		FilesFile file = (model != null && model.lookupConversation(conversationID).getEditingFile() != null)
				? model.lookupConversation(conversationID).getEditingFile()
				: null;

		if (file != null) {
			String dbFileName = file.dateiNameUndPfadEscaped();

			if (StringUtils.equals(file.getLastKnownHash(), file.calculateActualHashValue())) {

				String[] lock = file.fileLockVorhandenDirtyRead();
				boolean trenner = false;
				String editorName = null;
				if (lock != null) {
					event.setData("Wird editiert von " + lock[0]);
					editorName = lock[0];
					trenner = true;
				}

				List<String[]> viewer = KVMemoryMap.getInstance().readListWithPartKey("temporary.day.file.event." + dbFileName + ".");
				if (viewer != null && !viewer.isEmpty()) {
					List<String> viewerToPrint = new LinkedList<String>();
					long yet = System.currentTimeMillis();
					for (String[] v : viewer) {
						if (!StringUtils.equals(v[0], model.getUser()) && !StringUtils.equals(v[0], editorName)
								&& (yet - Long.parseLong(v[1]) < (Event.STANDARD_REFRESH * 3))) {
							viewerToPrint.add(v[0]);
						}
					}
					if (!viewerToPrint.isEmpty()) {
						if (trenner) {
							event.setData(event.getData() + " und angezeigt von ");
						} else {
							event.setData(event.getData() + "Wird angezeigt von ");
						}
						for (String name : viewerToPrint) {
							event.setData(event.getData() + name + " ");
						}
						event.setData(event.getData().trim());
					}
				}

			} else {
				event.setData("refresh");
				event.setRefresh(60000);
			}

			KVMemoryMap.getInstance().writeKeyValue("temporary.day.file.event." + dbFileName + "." + model.getUser(),
					Long.toString(System.currentTimeMillis()), true);
		}

		return event;
	}

	private class Event {

		public static final int STANDARD_REFRESH = 2000;

		String data;
		int refresh;

		public Event() {
			setRefresh(STANDARD_REFRESH);
			setData("");
		}

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}

		public int getRefresh() {
			return refresh;
		}

		public void setRefresh(int refresh) {
			this.refresh = refresh;
		}
	}
}
