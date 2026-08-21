/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	XMLHandler.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   XML to Bean 처리 클래스 
 */

package scbk.cert.util;

import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XMLHandler extends DefaultHandler {

	public static HashMap<String, Object> map = null;
	boolean isStart = false;
	private Object object = null;
	private String eleValue = "";
	private String eleName = "";
	private String objName = "";

	@SuppressWarnings("unchecked")
	public XMLHandler() {
		map = new HashMap();
	}

	public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {

		if (name.equals("entity")) {
			isStart = true;
			for (int i = 0; i < attributes.getLength(); i++) {

				if (attributes.getQName(i).equals("class")) {
					try {
						object = Class.forName(attributes.getValue(i)).newInstance();
					} catch (Exception e) {
						Log.write("log.root", CommonUtil.getExceptionMessage(e), this);
					}
				}
				if (attributes.getQName(i).equals("name")) {
					objName = attributes.getValue(i);
				}
			}
		}
		eleName = name;
	}

	public void endElement(String uri, String localName, String name) throws SAXException {

		if (name.equals("entity")) {
			map.put(objName, object);
			isStart = false;
		}
		if (isStart) {
			CommonUtil.setField(object, eleName, eleValue);
		}
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		if (length == 0) {
			eleValue = "";
		} else {
			eleValue = CommonUtil.toString(new String(ch, start, length)).trim();
		}
	}

}
