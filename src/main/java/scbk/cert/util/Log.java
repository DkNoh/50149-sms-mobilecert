/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	Log.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   Log4j 를 이용한 로그 설정 .
 */

package scbk.cert.util;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class Log {

	private static Log log = null;
	private Log() {
	}

	public static void write(String category, String msg, Object ref) {
		if (log == null) {
			log = new Log();
		}
		
		Logger logger = LogManager.getLogger(category);

		if (logger == null) {
			logger = LogManager.getRootLogger();
		}
		/**
		 * if(ref != null) { msg = "[" + ref.getClass().getName()+ "]## "+ msg;
		 * }else{ msg = " "+ msg; }
		 **/
		logger.error(msg, null);
	}
}
