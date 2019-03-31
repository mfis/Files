package mfi.files.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import mfi.files.helper.ServletHelper;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.logic.AjaxFillIn;
import mfi.files.model.Conversation;
import mfi.files.model.Model;

@Controller
public class FilesAjaxServlet {

	public static final String SERVLETPFAD = "/" + FilesMainServlet.WEBAPP_NAME + "/FilesAjaxServlet";

	private static Logger logger = LoggerFactory.getLogger(FilesMainServlet.class);

	@RequestMapping("/FilesAjaxServlet")
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws FileNotFoundException, IOException {

		HttpSession session = request.getSession(true);
		Model model = null;
		model = (Model) session.getAttribute(FilesMainServlet.SESSION_ATTRIBUTE_MODEL);

		Integer conversationID = null;
		Conversation conversation = null;
		String conv = request.getParameter(HTMLUtils.CONVERSATION);
		if (StringUtils.isNumeric(conv)) {
			conversationID = Integer.parseInt(conv);
			conversation = model.lookupConversation(conversationID);
		}

		response.setContentType("text/event-stream");
		response.setContentType(ServletHelper.CONTENT_TYPE_HTML);
		request.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Cache-Control", "no-cache");

		StringBuilder sb = new StringBuilder();
		boolean isXmlResponse = false;

		if (request.getParameter("type").equals("fillIn")) {

			String id = request.getParameter("id");
			isXmlResponse = true;
			String[] params = StringUtils.split(id, '-');
			String value = null;
			AjaxFillIn fillIn = AjaxFillIn.valueOf(params[0]);
			try {
				value = fillIn.readValue(conversation, params[1]);
			} catch (Exception e) {
				value = fillIn.getFailString();
			}
			sb.append("<item name =\"" + id + "\">" + value + "</item>");

		} else if (request.getParameter("type").equals("setCheckbox")) {

			synchronized (conversation.getEditingFile()) {

				String text = request.getParameter("text");
				String textlength = request.getParameter("textlength");

				if (text.length() == Integer.parseInt(textlength)) {
					if (!conversation.getEditingFile().isClientCrypted()) {
						text = new String(Base64.decodeBase64(text), StandardCharsets.UTF_8);
					}
					conversation.getEditingFile().schreibeFileMitArchivierung(text, model, model.getUser());
					conversation.getEditingFile().refreshHashValue();
				} else {
					response.setStatus(409);
					logger.error("checkbox" + text.length() + " != " + textlength + " / " + text);
				}
			}
		}

		String responseContentString = (isXmlResponse ? "<?xml version=\"1.0\" encoding=\"utf-8\" ?><result>" : "") + sb.toString()
				+ (isXmlResponse ? "</result>" : "");
		response.setContentLength(responseContentString.length());
		PrintWriter out = response.getWriter();
		out.write(responseContentString);
		out.flush();

	}
}
