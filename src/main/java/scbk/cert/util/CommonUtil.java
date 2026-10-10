/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	CommonUtil.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730
 * Comment      :   공통 Utility
 */

package scbk.cert.util;

import java.beans.BeanInfo;
import java.beans.Expression;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

public class CommonUtil {

	public static String zerofill(String str, int size) {
		if (str == null) {
			str = "";
		}
		if (str.length() > size) {
			return str.substring(0, size);
		}
		try {
			NumberFormat nf = NumberFormat.getInstance();
			return zerofill(nf.parse(str), size);
		} catch (Exception e) {
			return zerofill(0, size);
		}

	}

	public static String zerofill(int num, int size) {
		return zerofill(new Integer(num), size);
	}

	public static String zerofill(long num, int size) {
		return zerofill(new Long(num), size);
	}

	public static String zerofill(Number num, int size) {
		String zero = "";
		for (int i = 0; i < size; i++) {
			zero += "0";
		}
		DecimalFormat df = new DecimalFormat(zero);
		return df.format(num);
	}

	public static String zerofillDouble(double d, int size) {
		return zerofillDouble(toString(d), size);
	}

	public static String zerofill(double d, int size) {
		return zerofillDouble(toString(d), size);
	}

	public static String zerofillDouble(String v, int size) {
		int zero = size - v.length();
		if (zero > 0) {
			String prefix = "";
			for (int i = 0; i < zero; i++) {
				prefix += "0";
			}
			return prefix + v;
		} else {
			return v.substring(0, size);
		}
	}

	public static String setFiller(int len) {
		String filler = "";
		for (int i = 0; i < len; i++) {
			filler += " ";
		}
		return filler;
	}

	public static String setFiller(String oldStr, int len) {
		if (oldStr == null) {
			oldStr = "";
		}

		int oldLen = oldStr.length();

		if (oldLen >= len) {
			return oldStr.substring(0, len);
		} else {
			return oldStr + setFiller(len - oldLen);
		}
	}

	public static int length(byte[] b) {
		if (b == null) {
			return 0;
		} else {
			return b.length;
		}
	}

	public static int length(String s) {
		if (s == null) {
			return 0;
		} else {
			return s.length();
		}
	}

	public static String byteFiller(String oldStr, int len) {
		byte[] b = oldStr.getBytes(CertCharset.EUC_KR);
		int oldLen = b.length;

		if (oldLen >= len) {
			return byteTrim(b, len);
		} else {
			byte[] nb = new byte[len];
			int i = 0;
			for (; i < oldLen; i++) {
				nb[i] = b[i];
			}
			for (; i < len; i++) {
				nb[i] = ' ';
			}

			return new String(nb, CertCharset.EUC_KR);
		}
	}

	public static String byteFillerCharSet(String oldStr, int len, String charSet) {
		byte[] b = null;
		try {
			b = oldStr.getBytes(charSet);
		} catch (Exception e) {
			b = oldStr.getBytes(CertCharset.EUC_KR);
		}
		int oldLen = b.length;

		if (oldLen >= len) {
			return byteTrim(b, len);
		} else {
			byte[] nb = new byte[len];
			int i = 0;
			for (; i < oldLen; i++) {
				nb[i] = b[i];
			}
			for (; i < len; i++) {
				nb[i] = ' ';
			}

			return new String(nb, CertCharset.EUC_KR);
		}
	}

	public static String toString(Object object) {

		if (object == null) {
			return "";
		} else {
			if (object instanceof Integer) {
				Integer i = (Integer) object;
				return toString(i.intValue());
			} else if (object instanceof Long) {
				Long l = (Long) object;
				return toString(l.longValue());
			} else if (object instanceof Double) {
				Double d = (Double) object;
				return toString(d.doubleValue());
			} else if (object instanceof byte[]) {
				byte[] b = (byte[]) object;
				return toString(b);
			} else {
				return (String) object;
			}
		}

	}

	public static String toString(int i) {
		return Integer.toString(i);
	}

	public static String toString(long i) {
		return Long.toString(i);
	}

	public static String toString(double i) {
		return Double.toString(i);
	}

	public static long parseLong(double i) {
		Double d = new Double(i);
		return d.longValue();
	}

	public static String toString(byte[] b) {
		return toString(b, 0, length(b));
	}

	public static String toString(byte[] b, String charSet) {
		String s = "";
		try {
			s = new String(b, charSet);
		} catch (Exception e) {
			s = toString(b);
		}
		return s;
	}

	public static String toString(String s, String charSet) {
		try {
			byte[] b = s.getBytes(charSet);
			s = new String(b, charSet);
		} catch (Exception e) {
			return "";
		}
		return s;
	}

	public static String toString(byte[] b, int init) {
		return toString(b, init, b.length - init);
	}

	/**
	 * 바이트 배열을 스트링으로 변환한다.
	 * 
	 * @param b
	 * @param start
	 * @param end
	 * @return
	 */
	public static String toString(byte[] b, int start, int end) {

		if (length(b) < start || length(b) == 0) {
			return "";
		} else if (length(b) < start + end) {
			return new String(b, start, length(b), CertCharset.EUC_KR);
		} else {
			return new String(b, start, end, CertCharset.EUC_KR);
		}
	}

	public static String byteTrim(byte[] b, int len) {
		byte[] nb = null;
		if (b == null || b.length == 0) {
			nb = new byte[len];
			for (int i = 0; i < len; i++) {
				b[i] = ' ';
			}
		} else {
			int oldLen = b.length;
			if (len <= oldLen) {
				nb = new byte[len];
				for (int i = 0; i < len; i++) {
					nb[i] = b[i];
				}
			} else {
				byte[] l = new byte[len - oldLen];
				for (int i = 0; i < (len - oldLen); i++) {
					l[i] = ' ';
				}
				nb = byteAppend(b, l);
			}
		}

		return new String(nb, CertCharset.EUC_KR);
	}

	public static byte[] byteTrim(String str, int len) {
		byte[] b = str.getBytes(CertCharset.EUC_KR);

		byte[] nb = byteTrim(b, len).getBytes(CertCharset.EUC_KR);
		if (nb.length == len) {
			return nb;
		} else {
			byte[] k = new byte[len];
			for (int i = 0; i < len; i++) {
				k[i] = nb[i];
			}
			return k;
		}

	}

	public static byte[] byteAppend(byte[] b, byte[] appendByte) {

		if (b == null && appendByte == null) {
			return new byte[0];
		}

		if (b == null) {
			return appendByte;
		}
		if (appendByte == null) {
			return b;
		}

		int len = b.length + appendByte.length;

		byte[] nb = new byte[len];

		int i = 0;
		for (; i < b.length; i++) {
			nb[i] = b[i];
		}
		for (int j = 0; j < appendByte.length; j++) {
			nb[i] = appendByte[j];
			i++;
		}
		return nb;
	}

	public static String convertBankType(String str) {

		byte[] srcByte = str.getBytes(CertCharset.EUC_KR);
		byte[] destByte = new byte[srcByte.length + 2];
		destByte[0] = 0x0E;
		destByte[srcByte.length + 1] = 0x0F;
		CommonUtil.arrayCopy(destByte, 1, srcByte, srcByte.length);
		return CommonUtil.toString(destByte);
	}

	public static void arrayCopy(Object destBuffer, int destPos, Object srcBuffer, int srcLen) {
		System.arraycopy(srcBuffer, 0, destBuffer, destPos, srcLen);
	}

	public static boolean isNullOrSpace(String str) {
		if (str == null || str.trim().length() == 0) {
			return true;
		} else {
			return false;
		}
	}

	public static long parseLong(String str) {
		long l = 0;
		try {
			l = Long.parseLong(str);
		} catch (Exception e) {
		}
		return l;
	}

	public static int parseInt(String str) {
		int i = 0;
		try {
			i = Integer.parseInt(str);
		} catch (Exception e) {
		}
		return i;
	}

	public static int parseInt(byte[] b) {

		if (b == null) {
			return 0;
		} else {
			return parseInt(new String(b, CertCharset.EUC_KR));
		}
	}

	public static String getExceptionMessage(Exception e) {
		StringBuffer sb = new StringBuffer();
		if (e instanceof SQLException) {
			SQLException sql = (SQLException) e;
			sb.append("SQLState: " + sql.getSQLState());
			sb.append("Message:  " + sql.getMessage());
			sb.append("Vendor:   " + sql.getErrorCode());
			sb.append(sql.getNextException()); // Adds an SQLException object to
												// the end of the chain.
		}
		sb.append("Message =" + e.getMessage() + "\n");
		StackTraceElement[] trace = e.getStackTrace();
		for (int i = 0; i < trace.length; i++) {
			sb.append(trace[i].toString() + "\n");
		}

		return sb.toString();
	}

	public static void setField(Object object, String fieldName,
			String fieldValue) {

		try {
			BeanInfo bi = Introspector.getBeanInfo(object.getClass());
			PropertyDescriptor[] pds = bi.getPropertyDescriptors();

			for (int i = 0; i < pds.length; i++) {

				if (pds[i].getDisplayName().equals(fieldName)) {
					Expression exp = new Expression(object, pds[i].getWriteMethod().getName(), new Object[] { fieldValue });
					exp.execute();
				}
			}
		} catch (Exception e) {
			Log.error("log.root", "event=BEAN_FIELD_ASSIGNMENT_FAILED", e, null);
		}
	}

	public static String getCurrentDate() {
		return getCurrentDate("yyyy-MM-dd HH:mm:ss");
	}

	/**
	 * 인자로 들어오는 형식의 날짜를 반환한다.
	 * 
	 * @return 인자로 들어오는 형식의 날짜
	 */
	public static String getCurrentDate(String s) {
		SimpleDateFormat simpledateformat = new SimpleDateFormat(s);
		return simpledateformat.format(new Date());
	}

	public static String beanToString(Object object) {
		StringBuffer sb = new StringBuffer();
		try {
			BeanInfo bi = Introspector.getBeanInfo(object.getClass());
			sb.append("[CLASSNAME =" + object.getClass().toString() + "]");

			PropertyDescriptor[] pds = bi.getPropertyDescriptors();
			for (int i = 0; i < pds.length; i++) {
				Expression exp = new Expression(object, pds[i].getReadMethod().getName(), new Object[0]);

				if (!pds[i].getReadMethod().getName().equals("getClass")) {

					sb.append("[" + pds[i].getReadMethod().getName() + "=");
					sb.append(CommonUtil.toString(exp.getValue() + "]"));
				}
			}
		} catch (Exception e) {
			Log.error("log.root", "event=BEAN_FORMAT_FAILED", e, null);
		}
		return sb.toString();
	}

	public static String nToB(String str) {
		if (str == null) {
			return "";
		} else {
			return str;
		}
	}

	public static GregorianCalendar getGregorianCalendar(String yyyyMMdd) {
		int yyyy = parseInt(yyyyMMdd.substring(0, 4));
		int mm = parseInt(yyyyMMdd.substring(4, 6));
		int dd = parseInt(yyyyMMdd.substring(6));

		GregorianCalendar calendar = new GregorianCalendar(yyyy, mm - 1, dd, 0, 0, 0);
		return calendar;

	}

	/**
	 * Calendar.YEAR,Calendar.MONTH,Calendar.DATE , Amount , yyyyMMdd
	 * 
	 * @param field
	 * @param amount
	 * @param date
	 * @return
	 */
	public static String getOpDate(int field, int amount, String date) {
		GregorianCalendar calDate = getGregorianCalendar(date);

		if (field == Calendar.YEAR) {
			calDate.add(GregorianCalendar.YEAR, amount);
		} else if (field == Calendar.MONTH) {
			calDate.add(GregorianCalendar.MONTH, amount);
		} else if (field == Calendar.DATE) {
			calDate.add(GregorianCalendar.DATE, amount);
		} else {

		}
		return getYyyyMMdd(calDate);
	}

	public static String getYyyyMMdd(Calendar cal) {
		Locale currentLocal = new Locale("KOREAN", "KOREA");
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", currentLocal);
		return formatter.format(cal.getTime());
	}
	
	public static String setMaskingHost(String data, String inOut) {
		String maskingData = data;
    	try {
    		if("out".equals(inOut)) {
	    		maskingData = CommonUtil.setMasking(maskingData, 386, 390);	// phone
	    		maskingData = CommonUtil.setMasking(maskingData, 396, 404);	// ssn
	    		maskingData = CommonUtil.setMasking(maskingData, 450, 464);	// name
    		} else {
    			maskingData = CommonUtil.setMasking(maskingData, 476, 480);	// phone
				maskingData = CommonUtil.setMasking(maskingData, 486, 498);	// ssn
				maskingData = CommonUtil.setMasking(maskingData, 544, 561);	// name
    		}
    		return maskingData;
		} catch (Exception e) {
			Log.warn("log.day", "event=HOST_MASKING_FAILED", e, null);
			return "";
		} 
	}
	
	public static String setMaskingKcb(String data, String inOut) {
		String maskingData = "";
    	try {
    		if("out".equals(inOut)) {
    			String reqType = data.substring(134, 138);
    			if("BSX".equals(reqType.trim())){
    				maskingData = data;
    				maskingData = setMasking(maskingData, 195, 199); // phone
    				maskingData = setMasking(maskingData, 222, 237); // name
    				maskingData = setMasking(maskingData, 317, 321); // birth
    			} else if ("CNSS".equals(reqType)) {
    				maskingData = data;
    			} else {
    				maskingData = data.substring(0,135);
    			}
    		}
    		if("in".equals(inOut)){
    			String reqType = data.substring(130, 134); // 응답정보부 시작 130
    			if("CNSS".equals(reqType.trim())){
    				maskingData = data;
    				maskingData = setMasking(maskingData, 835, 849); // phone
    				maskingData = setMasking(maskingData, 866, 962); // name
    				maskingData = setMasking(maskingData, 968, 970); // birth
    			} else {
    				maskingData = data;
    			}
    		}
    		return maskingData;
		} catch (Exception e) {
			Log.warn("log.day", "event=KCB_MASKING_FAILED", e, null);
			return "";
		} 
	}
	
	
	public static String setMasking(String str, int startIndex, int endIndex) {
		if(str == null || "".equals(str)) {
			Log.warn("log.day", "event=MASKING_INPUT_EMPTY", null);
			return "";
		};
		
		if(!(str.length() > startIndex && str.length() >= endIndex)) {
			Log.warn("log.day", "event=MASKING_RANGE_INVALID", null);
			return "";
		}
		return replaceChar(str, startIndex, endIndex);
	}
	
    private static String replaceChar(String data, int start, int end) {
    	try {
    		StringBuilder sb = new StringBuilder(data);
            for (int i = start; i < end; i++) {
                sb.setCharAt(i, '*');
            }
            return sb.toString();
		} catch (Exception e) {
			Log.warn("log.day", "event=MASKING_REPLACEMENT_FAILED", e, null);
			return "";
		} 
    }

}
