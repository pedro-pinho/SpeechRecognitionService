package br.com.irisbot.utils;

import org.apache.commons.text.similarity.LevenshteinDistance;

public class similarityStrings {

	public similarityStrings() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * Calculates the similarity (a number within 0 and 1) between two strings.
	 */
	public static double similarity(String s1, String s2) {
		String longer = s1, shorter = s2;
		if (s1.length() < s2.length()) { // longer should always have greater length
			longer = s2; shorter = s1;
		}
		try {
			int longerLength = longer.length();
			if (longerLength == 0) { return 1.0; /* both strings are zero length */ }
			LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
		    return (longerLength - levenshteinDistance.apply(longer, shorter)) / (double) longerLength;
		}catch(Exception e) {
			e.printStackTrace();
		}
		return new Double(1000);
	}

}
