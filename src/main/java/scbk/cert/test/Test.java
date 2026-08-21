package scbk.cert.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

import scbk.cert.bean.uplus.XPayAuthBean;

public class Test {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		String testRes = "tscbk1              MobileAuthOnlySub_2      N  20210415173258                                                                                                                                                                                                                                    STEP2202104151732534611040108101011111111           1111111588-1599   SCB                 NTEST                                                                                                                                                                                8303091TEST      LGCsg/Nyce11eNuIpztP1J2xzZjrAuvcF7VKPS42uUshKuMRAtGARycacT1DyUJJZ7ndm+vEmTuGXP1k4rBEgA==                                                                        ";
		try {
			new XPayAuthBean(testRes.getBytes(), "STEP2");
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

}