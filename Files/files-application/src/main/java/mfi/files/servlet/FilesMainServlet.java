package mfi.files.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import mfi.files.helper.CookieHelper;
import mfi.files.helper.ServletHelper;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.logic.Files;
import mfi.files.logic.Security;
import mfi.files.model.Model;
import mfi.files.model.RequestValidationException;

@Controller
public class FilesMainServlet {

	public static final String SESSION_ATTRIBUTE_MODEL = "model";
	public static final long serialVersionUID = 1L;

	private static Log logger = LogFactory.getLog(FilesMainServlet.class);

    @RequestMapping("/")
	public void requestVerarbeiten(HttpServletRequest request, HttpServletResponse response) throws IOException {

		response.setContentType(ServletHelper.CONTENT_TYPE_HTML);
		request.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");

		ThreadLocalHelper.setConversationID(request.getParameter(HTMLUtils.CONVERSATION));

		try {
			StringBuilder sbResponse;
			HttpSession session = request.getSession(false);
			Model model = null;
			Map<String, String> parameters = null;

			try {
				parameters = ServletHelper.parseRequest(request, session, null);
				model = Security.lookupModelFromSession(session, request);

				sbResponse = responseErzeugen(model, parameters);

			} catch (RequestValidationException e) {
				logger.error("Fehler bei Requestparameter-Validation:", e);
				sbResponse = new StringBuilder();
				response.setStatus(ServletHelper.HTTP_STATUS_CODE_NON_AUTHORITATIVE_RESPONSE);
			}

			model = writeCookies(response, model);

			finalizeModel(request, session, model);

			PrintWriter out = response.getWriter();
			out.println(sbResponse.toString());
			out.flush();
			out.close();

		} finally {
			ThreadLocalHelper.unset();
		}
	}

	public void finalizeModel(HttpServletRequest request, HttpSession session, Model model) {

		if (model != null && model.isUserAuthenticated() && !model.isDeleteModelAfterRequest()) {
			session = request.getSession(true);
			model.setSessionID(session.getId());
			session.setAttribute(SESSION_ATTRIBUTE_MODEL, model);
		} else if (model == null || model.isDeleteModelAfterRequest() && session != null) {
			session.removeAttribute(SESSION_ATTRIBUTE_MODEL);
		}
	}

	public Model writeCookies(HttpServletResponse response, Model model) {

		if (model != null) {
			for (Cookie cookieToWrite : model.lookupConversation().getCookiesToWriteToResponse().values()) {
				// write only cookies to delete if model will be deleted
				if (!model.isDeleteModelAfterRequest() || (model.isDeleteModelAfterRequest() && cookieToWrite.getMaxAge() == 0)) {
					response.addCookie(CookieHelper.cloneCookie(cookieToWrite));
				}
			}
			model.lookupConversation().setCookiesReadFromRequest(null);
			model.lookupConversation().setCookiesToWriteToResponse(null);

		}
		return model;
	}

	private StringBuilder responseErzeugen(Model model, Map<String, String> parameters) {

		StringBuilder sbResponse = new StringBuilder(60000);

		try {
			model.lookupConditionForRequest(parameters);
			HTMLUtils.buildHtmlHeader(sbResponse, model, "/", ServletHelper.STRING_ENCODING_UTF8, parameters);

			Security.checkSecurityForRequest(model, parameters);
			ThreadLocalHelper.setModelPassword(Security.generateModelPasswordForSession(model));

			Files.getInstance().files(sbResponse, parameters, model);

		} catch (Exception e) {
			logger.error("Fehler bei Request-Verarbeitung:", e);
			sbResponse = new StringBuilder(2000);
			HTMLUtils.buildHtmlHeader(sbResponse, model, "/", ServletHelper.STRING_ENCODING_UTF8, parameters);
			sbResponse.append(Files.verarbeiteFehler(model));
		}

		HTMLUtils.buildHtmlFooter(sbResponse, model, parameters);

		return sbResponse;
	}

}
