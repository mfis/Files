package mfi.files.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.ServletHelper;

public class DebugServlet extends HttpServlet {

	private static Logger logger = LoggerFactory.getLogger(DebugServlet.class);

	private static final long serialVersionUID = 1L;

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		// doGet(request, response);
		String ajaxUpdateResult = "";

		try {
			List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
			for (FileItem item : items) {
				if (item.isFormField()) {
					ajaxUpdateResult += "Field " + item.getFieldName() + " with value: " + item.getString() + " is successfully read\n\r";
				} else {
					String fileName = item.getName();
					InputStream content = item.getInputStream();
					response.setContentType("text/plain");
					response.setCharacterEncoding("UTF-8");
					// Do whatever with the content InputStream.
					System.out.println(Streams.asString(content));
					ajaxUpdateResult += "File " + fileName + " is successfully uploaded\n\r";
				}

			}

		} catch (FileUploadException e) {
			throw new ServletException("Parsing file upload failed.", e);
		}

		response.getWriter().print(ajaxUpdateResult);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		logger.info("start");

		StringBuilder sb = new StringBuilder();

		response.setContentType("text/html");
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Content-Encoding", "gzip");

		Enumeration<String> h = request.getHeaderNames();
		sb.append("\nHeader:<br><pre>");
		while (h.hasMoreElements()) {
			String a = h.nextElement();
			String val = StringEscapeUtils.escapeHtml4(request.getHeader(a));
			String x;
			if (val == null) {
				x = "null";
			} else {
				x = "" + val.length();
			}
			sb.append("\n(" + x + ") " + StringEscapeUtils.escapeHtml4(a) + " = " + val);

		}
		sb.append("\n</pre>");

		Enumeration<String> e = request.getParameterNames();
		sb.append("\nParameter:<br><pre>");
		while (e.hasMoreElements()) {
			String a = e.nextElement();
			String val = StringEscapeUtils.escapeHtml4(request.getParameter(a));
			String x;
			if (val == null) {
				x = "null";
			} else {
				x = "" + val.length();
			}
			sb.append("\n(" + x + ") " + StringEscapeUtils.escapeHtml4(a) + " = " + val);

		}
		sb.append("\n</pre>");

		e = request.getAttributeNames();
		sb.append("\nAttributes:<br><pre>");
		while (e.hasMoreElements()) {
			String a = e.nextElement();
			Object val = request.getAttribute(a);
			String x;
			if (val == null) {
				x = "null";
			} else {
				x = "" + val.toString();
			}
			sb.append("\n(" + x + ") " + StringEscapeUtils.escapeHtml4(a) + " = " + val);

		}
		sb.append("\n</pre>");

		OutputStream out = new GZIPOutputStream(response.getOutputStream());
		out.write(sb.toString().getBytes(ServletHelper.STRING_ENCODING_UTF8));
		out.close();

		logger.info(sb.toString());
	}
}