/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	XMLFactory.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   XML 설정 파일을 로딩 .
 */

package scbk.cert.util;

import java.util.HashMap;

public class XMLFactory {

	private static HashMap fMap = null;

	public XMLFactory() {
	}

	public static HashMap getEntity() {
		if (fMap == null) {
			createInstance();
		}
		return fMap;
	}

	public static Object getEntity(String name) {
		if (fMap == null) {
			createInstance();
		}
		return fMap.get(name);
	}

	public static void createInstance() {

		XMLHandler xml = new XMLHandler();
		CreateParser cParser = new CreateParser(xml);
		String serverType = System.getProperty("scbk.cert.server.type", "dev");
		Log.write("log.root", " serverType = " + serverType, null);
		
		StringBuilder sb = new StringBuilder();
		sb.append("../conf/service-");
		sb.append(serverType);
		sb.append(".xml");
		
		try {
			cParser.parse(sb.toString());
		} catch (Exception e) {
			System.out.println(e);
			Log.write("log.root", CommonUtil.getExceptionMessage(e), null);
		}
		fMap = xml.map;
	}
}
