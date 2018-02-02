package br.com.irisbot.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;

import org.json.JSONObject;
import org.json.JSONTokener;

import br.com.irisbot.asr.TransObj;


public class MySocketCli {
	public static Microphone mic;
	/**
	 * Will use this to catch all transcriptions
	 * Store them with the time they were spoken to join diarization
	 * 
	 * We actually need to store the frame and if its final too
	 * because diarization will have start time 0 for that frame
	 * 
	 * Time, (Transcription, Confidence, Frame, Is_final)
	 */
	//private static TransObj myMap;
	/*private static final Map<String, Entry<String, String, String, Boolean>> myMap = createMap();
    private static Map<String, Entry<String, String, String, Boolean>> createMap()
    {
        Map<String,Entry<String, String>> myMap = new HashMap<String,Entry<String, String>>();
        Map.Entry<String, String> first = newEntry("", "");
        myMap.put("", first);
        return myMap;
    }*/
	
	public static void main(String[] args) {
		try {
			openStream("audios/11973869393 ANA JULIA NÃO FAZ SONDAGEM E DESLIGA NA CARA");
			
		}catch (Throwable e) {
			e.printStackTrace();
		}
		
	}
	

	private static void openStream(final String fileName) throws Exception{
		String[] parts = fileName.split("/");
		//String url = "http://softlayer01.iris-bot.com.br/SpeechRecognitionService/orchestrator?cod="+parts[parts.length-1];
		String url = "http://localhost:8080/SpeechRecognitionService/orchestrator?cod="+URLEncoder.encode(parts[parts.length-1], "UTF-8");
		RestClient cli = new RestClient(url) {
			@Override
			public void whenDone(Response resp) {
				try{
					//System.out.println(resp.getContent());
					
					JSONObject json = new JSONObject(new JSONTokener(resp.getContent()));
					Path file_wav_path = convertMp3toWav(fileName+".mp3");
					connect(file_wav_path, json.getInt("porta"));
					
				}catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		};
		cli.doGet();
	}

	
	private static Path convertMp3toWav(final String fileName) throws Exception{
		 try {
			File file = new File(fileName);
			AudioInputStream in= AudioSystem.getAudioInputStream(file);
			AudioInputStream din = null;
			AudioFormat baseFormat = in.getFormat();
			AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
														16000,
														16,
														1,
														2,
														baseFormat.getSampleRate(),
														false);
			din = AudioSystem.getAudioInputStream(decodedFormat, in);
			//rawplay(decodedFormat, din);
			File file_wav = new File("src/br/com/irisbot/asr/core/proper.wav");
			
			AudioSystem.write(din, Type.WAVE, file_wav);
	        din.close();
			in.close();
			return file_wav.toPath();//.getPath();
		  } catch (Exception e) {
			  e.printStackTrace();
		  }
		return null;
	}

	
	private static void connect(Path path, int port) throws Exception{
		final int BUFFER_SIZE = 1024*1024;
		
		//final Socket cli = new Socket("softlayer01.iris-bot.com.br", port); //
		final Socket cli = new Socket("localhost", port);

		//final byte[] exemplo = Files.readAllBytes(path);

		FileInputStream fis = new FileInputStream(path.toFile());
    	byte[] buffer = new byte[BUFFER_SIZE];

		new Thread(new Runnable() {
			@Override
			public void run() {
				int read = 0;
				try {
					while ( (read = fis.read(buffer)) > 0) {
						cli.getOutputStream().write(buffer);
						//Thread.sleep(300);
					}
					fis.close();
					
				}catch (Exception e) {
					e.printStackTrace();
				}
				
				
			}
		}).start();
    	
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					BufferedReader br   = new BufferedReader(new InputStreamReader(cli.getInputStream(), StandardCharsets.US_ASCII));
					String line         = "";
					String partialTrans = "";
					String partialConf  = "";
					String partialTime  = "";
			    	
			    	while (!br.ready()) Thread.sleep(500);
			    	while((line = br.readLine())!=null && !line.trim().isEmpty()) {
			    		//System.out.println(line);
			    		/**
			    		 * will read line by line
			    		 */
			    		if (line.indexOf("transcript:")>-1) {
			    			partialTrans = line.substring(line.indexOf("transcript:")+12);
			    			//removing unecessary "
			    			partialTrans = partialTrans.replaceAll("\"", "");
			    		} 
			    		if (line.indexOf("stability:")>-1 && partialTrans!="") {
			    			partialConf = line.substring(line.indexOf("stability:")+14);
			    		}
			    		if (line.indexOf("=")>-1 && partialTrans!="" && partialConf!="") {
			    			partialTime = line.substring(line.indexOf("=")+1);
			    			//usually this line is followed by "results {"
			    			partialTime = partialTime.replaceAll("(\\D)", "");
			    		}
			    		/**
			    		 * because of that sequence, after all 5 are filled
			    		 * we can assume they are about the same transcription
			    		 */
			    		if (partialTrans!="" && partialConf!="" && partialTime!="") {
			    			//Only include good reliable transcriptions
			    			if (Integer.parseInt(partialConf)>=0.5) {
			    				/*TransObj entry1 = new TransObj(partialTrans, partialConf, frame, is_final);
				    			myMap.put(partialTime, entry1);*/
			    			}
			    			/**
			    			 * Reset to read new transcription
			    			 */
			    			partialTrans = "";
					    	partialConf = "";
					    	partialTime = "";
			    		}
			    	}
			    	//System.out.println(myMap);
			    	cli.close();
				}catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();

		
	}
}
