/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	CreateParser.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730
 * Comment      :   SAX 파싱 처리 
 */

package scbk.cert.util;

import java.io.File;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class CreateParser {

	private DefaultHandler handler;
	private SAXParser saxParser;

	public CreateParser(DefaultHandler handler) {
		this.handler = handler;
		create();
	}

	/**
	 * Create the SAX parser
	 */
	private void create() {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setValidating(true);
			saxParser = factory.newSAXParser();
		} catch (SAXException e) {
			Log.write("log.root", "SAXException =" + e.getMessage(), this);
		} catch (ParserConfigurationException e) {
			Log.write("log.root", "ParserConfigurationException =" + e.getMessage(), this);
		}
	}

	public void parse(File file) throws Exception {
		try {
			saxParser.parse(file, handler);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}

	public void parse(String uri) throws Exception {
		try {
			saxParser.parse(uri, handler);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}

	public void parse(InputStream stream) throws Exception {
		try {
			saxParser.parse(stream, handler);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}

}
