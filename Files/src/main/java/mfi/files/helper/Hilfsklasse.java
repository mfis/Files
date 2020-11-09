package mfi.files.helper;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.http.Cookie;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import mfi.files.maps.KVMemoryMap;
import net.pushover.client.MessagePriority;
import net.pushover.client.PushoverException;
import net.pushover.client.PushoverMessage;
import net.pushover.client.PushoverRestClient;
import net.pushover.client.Status;

public class Hilfsklasse {

	public final static String DATE_PATTERN_STD = "EEEE, dd.MM.yyyy";

	public final static String DATE_PATTERN_TS = "dd.MM.yyyy HH:mm:ss";

	public final static String DATE_PATTERN_TS_FILESYSTEM = "yyyy_MM_dd__HH_mm_ss";

	private static DecimalFormat frmt = (DecimalFormat) NumberFormat.getInstance(Locale.GERMAN);

	static {
		frmt.setParseBigDecimal(true);
	}

	public static SimpleDateFormat lookupSimpleDateFormat(String pattern) {
		SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.GERMAN);
		sdf.setTimeZone(TimeZone.getTimeZone(KVMemoryMap.getInstance().readValueFromKey("application.properties.timezone")));
		return sdf;
	}

	public static String tagesDatumAlsString() {

		SimpleDateFormat sdf = lookupSimpleDateFormat(DATE_PATTERN_STD);
		return sdf.format(new Date());
	}

	public static String zeitstempelAlsString() {

		SimpleDateFormat sdf = lookupSimpleDateFormat(DATE_PATTERN_TS);
		return sdf.format(new Date());
	}

	public static String zeitstempelAlsDateisystemObjekt() {

		SimpleDateFormat sdf = lookupSimpleDateFormat(DATE_PATTERN_TS_FILESYSTEM);
		return sdf.format(new Date());
	}

	public static String zeitstempelAlsString(long ts) {

		SimpleDateFormat sdf = lookupSimpleDateFormat(DATE_PATTERN_TS);
		return sdf.format(new Date(ts));
	}

	public static List<String> erstelleDatumsliste(String pattern, int anzahlEintraege, boolean ersterEintragLeer) {

		List<String> list = new LinkedList<String>();
		GregorianCalendar cal = new GregorianCalendar();
		SimpleDateFormat sdf = lookupSimpleDateFormat(pattern);

		if (ersterEintragLeer) {
			list.add("");
		}

		for (int i = 0; i < anzahlEintraege; i++) {
			list.add(sdf.format(cal.getTime()));
			cal.add(Calendar.DATE, -1);
		}

		return list;
	}

	public static String zeilenumbruecheBereinigen(String str) {

		int i = 0;
		while (i != -1) {
			i = StringUtils.indexOf(str, '\n', i);
			if (i != -1 && i > 0 && str.charAt(i - 1) != '\r') {
				String neu = StringUtils.substring(str, 0, i);
				neu = neu + "\r";
				neu = neu + StringUtils.substring(str, i);
				str = neu;
				i++;
			}
			if (i != -1) {
				i++;
			}
		}
		return str;
	}

	public static int countPrintableCharsInSubstring(String source, int startIncl, int stopIncl) {

		if (source == null) {
			return 0;
		}

		if (source.length() <= startIncl) {
			return 0;
		}

		if (source.length() <= stopIncl) {
			stopIncl = source.length() - 1;
		}

		char[] chars = source.toCharArray();

		int anz = 0;
		for (int c = startIncl; c <= stopIncl; c++) {
			int i = chars[c];
			if (i > 31 && i < 127) {
				anz++;
			}
		}

		return anz;
	}

	public static String cookieToString(Cookie cookie) {

		StringBuilder sb = new StringBuilder(100);

		sb.append("Cookie");
		sb.append(" name=" + cookie.getName());
		sb.append(" value=" + cookie.getValue());
		sb.append(" domain=" + cookie.getDomain());
		sb.append(" maxage=" + cookie.getMaxAge());
		sb.append(" path=" + cookie.getPath());
		sb.append(" version=" + cookie.getVersion());
		sb.append(" secure=" + cookie.getSecure());

		return sb.toString();
	}

	public static BigDecimal parseBigDecimal(String s) {
		try {
			return (BigDecimal) frmt.parse(s);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Wert nicht numerisch:" + s);
		}
	}

	public static String printBigDecimal(BigDecimal bd) {
		return frmt.format(bd);
	}

	public static boolean isNumeric(String value) {

		value = StringUtils.remove(value, ',');
		value = StringUtils.remove(value, '.');
		value = StringUtils.remove(value, ' ');
		return StringUtils.isNumeric(value) && StringUtils.isNotBlank(value);
	}

	public static String normalizedString(Object key) {
		String keyNorm;
		if (key == null) {
			keyNorm = null;
		} else if (key instanceof String) {
			if (isNumeric((String) key)) {
				keyNorm = printBigDecimal(parseBigDecimal((String) key));
			} else {
				keyNorm = (String) key;
			}
		} else {
			keyNorm = key.toString();
		}
		return keyNorm;
	}

	public static String hashCodeFromTextFileLine(String line) {

		line = StringUtils.removeStart(line, "?0");
		line = StringUtils.removeStart(line, "?1");
		return line.replaceAll("[^a-zA-Z]+", "");
	}

	public static void sendPushMessage(String text) throws PushoverException {

		String apiToken = KVMemoryMap.getInstance().readValueFromKey("application.pushService.apiToken");
		String userID = KVMemoryMap.getInstance().readValueFromKey("application.pushService.userID");
		String clientName = KVMemoryMap.getInstance().readValueFromKey("application.pushService.clientName");
		String environmentName = KVMemoryMap.getInstance().readValueFromKey("application.environment.name");

		if (StringUtils.isAnyBlank(apiToken, userID, clientName)) {
			LoggerFactory.getLogger(Hilfsklasse.class).warn("sendPushMessage DISABLED: {}", text);
			return;
		}

		PushoverMessage message = PushoverMessage.builderWithApiToken(apiToken) //
				.setUserId(userID) //
				.setDevice(clientName) //
				.setMessage(text) //
				.setPriority(MessagePriority.HIGH) //
				.setTitle(environmentName + " - Files") //
				.build();

		Status status = null;
		status = new PushoverRestClient().pushMessage(message);
		if (status != null && status.getStatus() > 1) {
			throw new IllegalStateException("Pushover client status=" + status);
		}
	}

}
