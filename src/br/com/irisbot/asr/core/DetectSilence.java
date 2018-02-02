package br.com.irisbot.asr.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.google.gson.JsonObject;

/*import br.com.irisbot.asr.azure.speech.AzureSpeechAPI;*/
import br.com.irisbot.asr.google.speech.GoogleCloudAPI;

/**
 * 
 */

/**
 * @author pedro
 *
 */
public class DetectSilence extends Thread {

	private String channel;
	private String callId;

	protected boolean running;
	// private ByteArrayOutputStream out;
	// private AudioInputStream inputStream;
	final static float MAX_8_BITS_SIGNED = Byte.MAX_VALUE;
	final static float MAX_8_BITS_UNSIGNED = 0xff;
	final static float MAX_16_BITS_SIGNED = Short.MAX_VALUE;
	final static float MAX_16_BITS_UNSIGNED = 0xffff;
	public final static AudioFormat audioFormat = new AudioFormat(48000, 16, 2, true, true);

	private AudioFormat format;

	private AudioFormat defaultFormat;

	public DetectSilence(String channel, String callId) {
		this.channel = channel;
		this.callId = callId;
		getFormat();
	}

	private AudioFormat getFormat() {

		if (defaultFormat == null) {
			AudioInputStream stream;
			try {
				File file = new File(DetectSilence.class.getResource("sample/proper.wav").toURI());
				if (!file.exists())
					throw new FileNotFoundException();
				stream = AudioSystem.getAudioInputStream(file);
				format = stream.getFormat();
				defaultFormat = stream.getFormat();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return defaultFormat;
	}

	public void stopAudio() {
		running = false;
	}

	/*
	 * TODO: calibrar vari�veis em fun��o separada
	 */
	public void detectSilenceFromStream(final ServerSocket srv, final InputStream is, final OutputStream os)
			throws IOException, UnsupportedAudioFileException {

		Runnable runner = new Runnable() {
			int bufferSize = (int) format.getSampleRate() * format.getFrameSize();
			byte buffer[] = new byte[bufferSize];
			boolean isSilence = true;

			public void run() {

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				/*
				 * Contador acumulativo para reconstru��o do chat
				 */
				int countAc = 0;
				int count = 0;
				try {
					
					try{

						while ((count = is.read(buffer, 0, buffer.length)) > -1) {
							try {
								countAc += count;
	
								File temp = null;
								String seq = null;
	
								float level = calculateLevel(buffer, 0, 0);
								/*
								 * Iniciou a fala, come�a a grava��o
								 */
								if (isSilence && level >= 0.2) {
									out = new ByteArrayOutputStream();
									isSilence = false;
								}
								
								if (!isSilence) {
									seq = "000000000000" + countAc;
									temp = new File("/tmp/audio/" + seq.substring(seq.length() - 12) + "_" + channel + "_" + callId + ".wav");
									try{temp.getParentFile().mkdirs();} catch (Exception e) {}
								}
	
								/*
								 * Level muito baixo indica que a pessoa parou de
								 * falar Pega o arquivo, para de gravar
								 */
								if (level <= 0.04 && !isSilence && temp != null && seq != null) {
									String resp = getAudioFile(temp, out, seq);
									if(resp!=null){
										os.write((resp+"\n").getBytes());
										os.flush();
									}
									isSilence = true;
								} else {
									// System.out.print(".");
								}
	
								/*
								 * mesmo silencio (ou fone mutado) o count d� maior
								 * que 0 s� cai no else quando encerra a liga��o
								 * mesmo
								 */
								out.write(buffer, 0, count);
	
							} catch (Exception e) {
								e.printStackTrace();
							}
						} // while
					} catch (SocketTimeoutException e) {
						System.out.println("socket-timeout");
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					os.close();
					
					System.out.println("Encerrando " + srv.getLocalPort());
					srv.close();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		Thread captureThread = new Thread(runner);
		captureThread.start();
	}

	public String getAudioFile(File file, ByteArrayOutputStream out, String seq) {
		String res = null;
		// if (file.length() < 32055) return null;
		byte[] audio = out.toByteArray();
		InputStream input = new ByteArrayInputStream(audio);
		try {
			/*
			 * Arquivos pequenos s�o ignorados
			 */
			if (audio.length >= 2048) {
				final AudioFormat format = getFormat();
				final AudioInputStream ais = new AudioInputStream(input, format, audio.length / format.getFrameSize());
				// convertendo para sample rate 16000 Hz pois � o que o asr
				// entende
				AudioFormat newFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000, 16, 1, 2, 8000, false);
				AudioInputStream highResAIS = AudioSystem.getAudioInputStream(newFormat, ais);
				AudioSystem.write(highResAIS, AudioFileFormat.Type.WAVE, file);
				if (file.length() < 32052) { // menos de 32k NORMALMENTE s�o
												// baques surdos
					file.delete();
				} else {
					res = asr(file, seq);
				}
			}
			input.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return res;
	}

	private float calculateLevel(byte[] buffer, int readPoint, int leftOver) {
		float level = 0;

		int max = 0;
		boolean use16Bit = (format.getSampleSizeInBits() == 16);
		boolean signed = (format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED);
		boolean bigEndian = (format.isBigEndian());
		if (use16Bit) {
			for (int i = readPoint; i < buffer.length - leftOver; i += 2) {
				int value = 0;
				// deal with endianness
				int hiByte = (bigEndian ? buffer[i] : buffer[i + 1]);
				int loByte = (bigEndian ? buffer[i + 1] : buffer[i]);
				if (signed) {
					short shortVal = (short) hiByte;
					shortVal = (short) ((shortVal << 8) | (byte) loByte);
					value = shortVal;
				} else {
					value = (hiByte << 8) | loByte;
				}
				max = Math.max(max, value);
			} // for
		} else {
			// 8 bit - no endianness issues, just sign
			for (int i = readPoint; i < buffer.length - leftOver; i++) {
				int value = 0;
				if (signed) {
					value = buffer[i];
				} else {
					short shortVal = 0;
					shortVal = (short) (shortVal | buffer[i]);
					value = shortVal;
				}
				max = Math.max(max, value);
			} // for
		} // 8 bit
			// express max as float of 0.0 to 1.0 of max value
			// of 8 or 16 bits (signed or unsigned)
		if (signed) {
			if (use16Bit) {
				level = (float) max / MAX_16_BITS_SIGNED;
			} else {
				level = (float) max / MAX_8_BITS_SIGNED;
			}
		} else {
			if (use16Bit) {
				level = (float) max / MAX_16_BITS_UNSIGNED;
			} else {
				level = (float) max / MAX_8_BITS_UNSIGNED;
			}
		}

		return level;
	} // calculateLevel

	public boolean getIfRunning() {
		return (running == true);
	}

	private synchronized String asr(File file, String seq) {
		JsonObject json = new JsonObject();
		// transforma file wav em byte array
		try {

			String trans = GoogleCloudAPI.main(file);
			//String trans1 = AzureSpeechAPI.main(file);
			
			// delete file
			file.delete();

			if(trans.isEmpty()) {
				System.out.println(file.getName()+" -> empty transcript");
				return null;
			}
			
			json.addProperty("transcript", trans);
			//json.addProperty("azure_trans", trans1);
			json.addProperty("sequence", seq);
			json.addProperty("call_id", callId);
			json.addProperty("channel", channel);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return json.toString();
	}

}