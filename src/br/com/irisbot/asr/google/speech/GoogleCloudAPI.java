package br.com.irisbot.asr.google.speech;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

//Imports the Google Cloud client library
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;
public class GoogleCloudAPI {
  //public static HashMap<String, String> transcriptions = new HashMap<>();

  public static String main(File file) throws Exception  {
	String trans = "";
	try {
		// Instantiates a client
	    SpeechClient speech = SpeechClient.create();
	
	    byte[] data = Files.readAllBytes(file.toPath());
	    ByteString audioBytes = ByteString.copyFrom(data);
	
	    // Builds the sync recognize request
	    RecognitionConfig config = RecognitionConfig.newBuilder()
	        .setEncoding(AudioEncoding.LINEAR16)
	        .setSampleRateHertz(16000)
	        .setLanguageCode("pt-BR")
	        .build();
	    RecognitionAudio audio = RecognitionAudio.newBuilder()
	        .setContent(audioBytes)
	        .build();
	
	    // Performs speech recognition on the audio file
	    RecognizeResponse response = speech.recognize(config, audio);
	    List<SpeechRecognitionResult> results = response.getResultsList();
	
	    for (SpeechRecognitionResult result: results) {
	      // There can be several alternative transcripts for a given chunk of speech. Just use the
	      // first (most likely) one here.
	      SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
	      trans += alternative.getTranscript() + " ";
	      //transcriptions.put("transcriptions",trans);
	    }
	    speech.close();
	} catch (Exception e) {
		e.printStackTrace();
	}
    return trans.trim();
  }

}