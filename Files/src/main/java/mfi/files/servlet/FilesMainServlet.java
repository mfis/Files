package mfi.files.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
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
import mfi.files.helper.StopWatchHelper;
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

	private Model initModel(HttpServletRequest request) throws IOException {

		Model model = new Model();
		model.initializeModelOnFirstRequest(request);
		if (model.isDevelopmentMode()) {
			logger.info("Initializing new Model");
		}

		return model;
	}

	@RequestMapping("/")
	public void requestVerarbeiten(HttpServletRequest request, HttpServletResponse response)
			throws UnsupportedEncodingException, IOException, ServletException {

		StopWatchHelper stopWatch = new StopWatchHelper(this.getClass().getName());
		stopWatch.timePoint(true, "s1");

		response.setContentType(ServletHelper.CONTENT_TYPE_HTML);
		request.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Cache-Control", "no-cache");

		stopWatch.timePoint(true, "s2");

		stopWatch.timePoint(true, "s3");
		ThreadLocalHelper.setConversationID(request.getParameter(HTMLUtils.CONVERSATION));

		Model model;
		HttpSession session = request.getSession(true);
		model = (Model) session.getAttribute(SESSION_ATTRIBUTE_MODEL);
		if (model == null) {
			model = initModel(request);
		}

		StringBuilder sbResponse;
		Map<String, String> parameters = null;

		try {

			parameters = ServletHelper.parseRequest(request, session);
			sbResponse = responseErzeugen(request, stopWatch, model, parameters);

		} catch (RequestValidationException e) {
			logger.error("Fehler bei Requestparameter-Validation:", e);
			sbResponse = new StringBuilder();
			response.setStatus(ServletHelper.HTTP_STATUS_CODE_NON_AUTHORITATIVE_RESPONSE);
		}

		stopWatch.timePoint(model.isDevelopmentMode(), "w1");
		if (model.isDeleteModelAfterRequest()) {
			model = null;
		} else {
			if (model.lookupConversation().getCookiesToWriteToResponse() != null) {
				for (Cookie cookieToWrite : model.lookupConversation().getCookiesToWriteToResponse().values()) {
					response.addCookie(CookieHelper.cloneCookie(cookieToWrite));
				}
			}
			model.setInitialRequest(false);
			model.lookupConversation().setCookiesReadFromRequest(null);
			model.lookupConversation().setCookiesToWriteToResponse(null);
		}

		session.setAttribute(SESSION_ATTRIBUTE_MODEL, model);

		stopWatch.timePoint(model != null && model.isDevelopmentMode(), "w2");
		if (ServletHelper.lookupUseGzip(parameters)) {
			response.setHeader("Content-Encoding", "gzip");
			OutputStream out = new GZIPOutputStream(response.getOutputStream());
			out.write(sbResponse.toString().getBytes(ServletHelper.STRING_ENCODING_UTF8));
			out.flush();
			out.close();
		} else {
			PrintWriter out = response.getWriter();
			out.println(sbResponse.toString());
			out.flush();
			out.close();
		}

		stopWatch.stop(model != null && model.isDevelopmentMode());
		stopWatch.logPoints(model != null && model.isDevelopmentMode());

		ThreadLocalHelper.unset();
	}

	private StringBuilder responseErzeugen(HttpServletRequest request, StopWatchHelper stopWatch, Model model,
			Map<String, String> parameters) {

		StringBuilder sbResponse = new StringBuilder(60000);

		try {
			stopWatch.timePoint(model.isDevelopmentMode(), "header");
			model.lookupConditionForRequest(parameters.get(HTMLUtils.CONDITION));
			HTMLUtils.buildHtmlHeader(sbResponse, model, "/", ServletHelper.STRING_ENCODING_UTF8, parameters);

			stopWatch.timePoint(model.isDevelopmentMode(), "security");
			Security.checkSecurityForRequest(model, request, parameters.get(ServletHelper.SERVLET_SESSION_ID), parameters.isEmpty());
			model.lookupConversation().setCookiesReadFromRequestConvenient(request.getCookies());
			Security.cookieRead(model, parameters);
			ThreadLocalHelper.setModelPassword(Security.generateModelPasswordForSession(model));

			stopWatch.timePoint(model.isDevelopmentMode(), "files");
			Files.getInstance().files(sbResponse, parameters, model);

			stopWatch.timePoint(model.isDevelopmentMode(), "post");

		} catch (Throwable t) {
			logger.error("Fehler bei Request-Verarbeitung:", t);
			sbResponse = new StringBuilder(2000);
			HTMLUtils.buildHtmlHeader(sbResponse, model, "/", ServletHelper.STRING_ENCODING_UTF8, parameters);
			sbResponse.append(Files.verarbeiteFehler(model));
			System.gc();
		}

		stopWatch.timePoint(model.isDevelopmentMode(), "footer");
		HTMLUtils.buildHtmlFooter(sbResponse, model, parameters);

		return sbResponse;
	}

}
