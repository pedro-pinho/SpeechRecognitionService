package br.com.irisbot.asr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.cloud.speech.v1beta1.RecognitionConfig;
import com.google.cloud.speech.v1beta1.SpeechClient;
import com.google.cloud.speech.v1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;

import br.com.irisbot.asr.MapSegmentation;
import br.com.irisbot.asr.TransObj;
import br.com.irisbot.asr.core.Sync.Chunks;
import br.com.irisbot.asr.speaker.DiarizationActorSupplier;
import br.com.irisbot.utils.similarityStrings;
/*import fr.lium.deprecated.spkDiarization.*;
import fr.lium.spkDiarization.libClusteringData.Segment;
import fr.lium.spkDiarization.programs.Identification;
import fr.lium.spkDiarization.programs.MClust;
import fr.lium.spkDiarization.programs.MSegInit;
import fr.lium.spkDiarization.tools.Wave2FeatureSet;*/

/**
 * 
 */

/**
 * @author pedro@iris-bot.com.br
 *
 */
public class Stream extends Thread {

	/**
	 * This sends requests to Google
	 */
	private ApiStreamObserver<StreamingRecognizeRequest> requestObserver;
	/**
	 * This listens for responses from Google
	 */
	private ResponseApiStreamingObserver<StreamingRecognizeResponse> responseObserver;
	/**
	 * The client managing all connections
	 */
	private SpeechClient speechClient;

	public final static AudioFormat audioFormat = new AudioFormat(16000, 16, 2, true, true);

	private AudioFormat format;
	
	/**
	 * Use this to calculate elapsed time on every google message
	 */
	long start = 0;
	/**
	 * Map what was told, when, in which frame, and if it is final
	 */
	private List<TransObj> listTranscriptions = new ArrayList<TransObj>();
	private static TransObj transcription;
	
    /**
     * This keep track of frames for Segment and Stream sync
     */
    private static int frame = 0;
	/**
	 * This makes program to process only one segment per time
	 */
	private boolean semaphore = true;
	/**
	 * Will use this to keep track of the last thing said, so it doesn't add duplicates
	 */
	private String lastTrans;
	/**
	 * After every segment processed, we erase output 
	 * so it doesnt waste time on already processed segments
	 */
	private boolean needResetOnOutput = false;
	/**
	 * Global configs to reset google stream more easily
	 */
	private RecognitionConfig recConfig;
	private StreamingRecognitionConfig config;

	public Stream() {
		getFormat();
		try {
			
			speechClient = SpeechClient.create();
			// Configure request with raw PCM audio
			recConfig = RecognitionConfig.newBuilder().setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
					.setLanguageCode("pt-BR").setSampleRate(16000).build();
			config = StreamingRecognitionConfig.newBuilder().mergeConfig(recConfig)
					// setInterimResults = partial results come as they are spoken
					.setInterimResults(true)
					.build();
			responseObserver = new ResponseApiStreamingObserver<StreamingRecognizeResponse>();
			transcription = new TransObj();
			resetGoogleStream();
			
			new ResetControl();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Next function and class is for
	 * Workaround: Exceeded maximum allowed stream duration of 65 seconds.
	 * On google speech api
	 */
	private void resetGoogleStream() {
		requestObserver = speechClient.streamingRecognizeCallable().bidiStreamingCall(responseObserver);
		requestObserver.onNext(StreamingRecognizeRequest.newBuilder().setStreamingConfig(config).build());
	}
	class ResetControl {
	    private final ScheduledExecutorService scheduler =
	       Executors.newScheduledThreadPool(1);

	    public void resetForMinutes() {
	        final Runnable beeper = new Runnable() {
                public void run() { 
                	resetGoogleStream();
                }
            };
	        final ScheduledFuture<?> resetHandle =
	            scheduler.scheduleAtFixedRate(beeper, 10, 65, TimeUnit.SECONDS);
	        
	        scheduler.schedule(new Runnable() {
                public void run() { 
                	resetHandle.cancel(true);
                }
	        }, 65*65, TimeUnit.SECONDS);
	    }
	 }
	
	/*public void Reminder(int seconds) {
        timer = new Timer();
        timer.schedule(new CancelTimer(), seconds*1000);
    }
	class CancelTimer extends TimerTask {
        public void run() {
            timer.cancel(); //Terminate the timer thread
        }
    }*/

	private void getFormat() {

		if (format == null) {
			AudioInputStream stream;
			try {
				File file = new File("C://var/www/SpeechRecognitionService/src/br/com/irisbot/asr/core/proper.wav");
				if (!file.exists())
					throw new FileNotFoundException();

				stream = AudioSystem.getAudioInputStream(file);
				format = stream.getFormat();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private byte[] adjustVolume(byte[] audioSamples, float volume) {
		// Necessary in order to convert negative shorts!
		final int USHORT_MASK = (1 << 16) - 1;

		final ByteBuffer buf = ByteBuffer.wrap(audioSamples)
		    .order(ByteOrder.LITTLE_ENDIAN);
		final ByteBuffer newBuf = ByteBuffer.allocate(audioSamples.length)
		    .order(ByteOrder.LITTLE_ENDIAN);

		int sample;

		while (buf.hasRemaining()) {
		    sample = (int) buf.getShort() & USHORT_MASK;
		    sample *= volume;
		    newBuf.putShort((short) (sample & USHORT_MASK));
		}

		return newBuf.array();
		/*byte[] array = new byte[audioSamples.length];
        for (int i = 0; i < array.length; i+=2) {
            // convert byte pair to int
            short buf1 = audioSamples[i+1];
            short buf2 = audioSamples[i];

            buf1 = (short) ((buf1 & 0xff) << 8);
            buf2 = (short) (buf2 & 0xff);

            short res= (short) (buf1 | buf2);
            res = (short) (res * volume);

            // convert back
            array[i] = (byte) res;
            array[i+1] = (byte) (res >> 8);

        }
        return array;*/
	}

	public void speechStreamingGoogle(final ServerSocket srv, final InputStream is, final OutputStream os)
			throws IOException, UnsupportedAudioFileException {

		Runnable runner = new Runnable() {

			int bufferSize                   = (int) format.getSampleRate() * format.getFrameSize();
			ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
			ByteArrayOutputStream out        = new ByteArrayOutputStream();
			byte buffer[]                    = new byte[bufferSize];
			String seq                       = null;
			File temp                        = null;
			int countAc                      = 0;
			int count                        = 0;
			long thisFrameStartsAt           = 0;
			@SuppressWarnings("unused")
			public void run()
			{
				try
				{
					/**
					 * Count usually reads 32k at time
					 * last reading usually goes around 24500
					 * its empty, that's why the threshold is 25k
					 */
					while ((count = is.read(buffer, 0, buffer.length)) > 25000)
					{
						if (start==0)
							start = System.currentTimeMillis();
						//TODO learn best hyperparam
						buffer = adjustVolume(buffer, new Float(1.009)); //increase the volum a bit
						// send to google
						requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
								.setAudioContent(ByteString.copyFrom(buffer)).build());
						Thread.sleep(1000);
						start -= 1000;
						/**
						 * Handle diarization
						 */
						if (needResetOnOutput)
						{
							out = new ByteArrayOutputStream();
							needResetOnOutput = false;
						}
						out.write(buffer, 0, buffer.length);
						countAc += count;
						//deliver transcriptions if they have all fields
						if (mountTranscription())
						{
							os.write((transcription + "\n").getBytes());
							os.flush();
							lastTrans = transcription.getText();
							transcription = new TransObj();
							System.out.println(lastTrans);
						} else {
							/*System.out.println(transcription.getTrans());
							System.out.println(transcription.getTime());
							System.out.println(transcription.getFrame());
							System.out.println(transcription.getIsFinal());*/
						}
						
						// size 1/5 de um audio de 23 segundos
						if (out.size() > (bufferSize*2) && !needResetOnOutput && semaphore)
						{
							frame++;
							thisFrameStartsAt = System.currentTimeMillis() - start;
							semaphore = false;
							seq       = "000000000000" + countAc;
							List<MapSegmentation> ms = new ArrayList<MapSegmentation>();
							//TODO dinamic path
							temp = new File("C://var/www/SpeechRecognitionService/tmp/input/"
										+ seq.substring(seq.length() - 12) + ".wav");
							try {
								temp.getParentFile().mkdirs();
							} catch (Exception e) {}
							
							CompletableFuture<String> text = CompletableFuture
									.supplyAsync(new DiarizationActorSupplier(out, temp, format), cachedThreadPool);

							text.whenComplete((result, exception) -> {
								if (exception != null) {
									text.completeExceptionally(exception);
								}
								try {
									if (text.get().toString() != null)
									{
										String segment = text.get().toString();
										//Maybe this 2 is not proper
										String clusters[] = new String[2];
										Sync sync = null;
										int id = 0;
										
										clusters = segment.split("(;; cluster S\\d \\n)");
										for (String c : clusters)
										{
											if (c.trim()!="")
											{
												//Maybe this 5 is not proper
												//reflect how many segments are possible per cluster
												String lines[] = new String[5];
												long length = 0;
												long start = 0;
												lines = c.split(".\n");
												
												for (String line : lines)
												{
													if (line.trim()!="")
													{
														String regex = "^[\\d]{12}\\ [\\d]{1}\\ (\\d+)\\ (\\d+).+S(\\d)";
													    Pattern p = Pattern.compile(regex);
													    Matcher m = p.matcher(line.trim());
													    while (m.find())
													    {
													    	start 	= Long.parseLong(m.group(1));
													    	length = Long.parseLong(m.group(2));
													    	id 	= Integer.parseInt(m.group(3));
													    	System.out.println("Start: " + m.group(1) + 
													        		", Length: " + m.group(2) + " "+
													        		", personId:  (" + m.group(3) + ") ");
													    }
													    if (start>=0 && length>0 && id>=0) {
													    	ms.add(new MapSegmentation(id, start, length));
													    	sync = new Sync(listTranscriptions);
													    	sync.checkAndAddPossibilities(id);
													    }
													    	
													}
												}
																								
											}
											
										}
										if (!listTranscriptions.isEmpty() && sync!=null) {
											//segment
											System.out.println("going chunks");
											Chunks chnk = sync.new Chunks(frame, thisFrameStartsAt, ms);
											/*os.write((text.get().toString() + "\n").getBytes());
											os.flush();*/
										}
									}
									// reset output to identify next speaker
									needResetOnOutput = true;
									semaphore = true;
								} catch (InterruptedException e) {
									e.printStackTrace();
								} catch (ExecutionException e) {
									e.printStackTrace();
								} catch (ArrayIndexOutOfBoundsException e) {
									e.printStackTrace();
								}
							});

						}

					}
					//Thread.sleep(2000);
					// closing output stream, stop new file diarizations
					//DiarizationActorSupplier.setClosed(true);
					
					
					//out.close();
					//endSession(os);
					
					//os.close();
					System.out.println("Encerrando " + srv.getLocalPort());

					//srv.close();
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					return;
				} catch (IOException e) {
					e.printStackTrace();
					return;
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			} // run
		}; // runnable
		Thread captureThread = new Thread(runner);
		captureThread.start();
	}
	
	public synchronized boolean mountTranscription() {
		String message = responseObserver.catchThis();
		
		if (message==null || message=="") return false;
		
		transcription.setTrans(message);
		transcription.setFrame(frame);
		transcription.setConfidence((message.substring(message.indexOf("stability")+10,message.indexOf(".")+3).trim()));
		//TODO reflect uppon the possibility of giving to cli
		//but with low opacity or something
		//if (transcription.getConfidence()!=null && transcription.getConfidence()<=0.5) return;
		if (message.toString().indexOf("is_final")>-1) {
			transcription.setIsFinal(true);
		} else {
			transcription.setIsFinal(false);
		}
		
		if (lastTrans!=null && similarityStrings.similarity(transcription.getText(), lastTrans)>=0.75) //more than 70% similar to last trans
			return false;
		listTranscriptions.add(transcription);
		return true;
	}
	/**
	 * Close the session and print results to standard output
	 *
	 * @throws IOException
	 *             when failing to close the session
	 */
	void endSession(OutputStream os) throws Exception {
		// Mark transmission as completed after sending the data.
		requestObserver.onCompleted();
		List<StreamingRecognizeResponse> responses = responseObserver.future().get();

		for (StreamingRecognizeResponse response : responses) {
			for (com.google.cloud.speech.v1beta1.StreamingRecognitionResult result : response.getResultsList()) {
				for (com.google.cloud.speech.v1beta1.SpeechRecognitionAlternative alternative : result
						.getAlternativesList()) {
					String resp = alternative.getTranscript();

					if (resp != null) {
						os.write((resp + "\n").getBytes());
						os.flush();
					}
				}
			}
		}
		speechClient.close();
	}

	/**
	 * This class receives the text results once they come in with the #onNext
	 * message
	 */
	class ResponseApiStreamingObserver<T> implements ApiStreamObserver<T> {
		private final SettableFuture<List<T>> future = SettableFuture.create();
		private final List<T> messages = new java.util.ArrayList<T>();
		private String message = "";
		@Override
		public void onNext(T message) {
			if (message.toString().indexOf("endpointer_type")>-1) return;
			System.out.println(message.toString());
			messages.add(message);
			this.message = message.toString();			
		}
		public String catchThis() {
			return message;
		}

		@Override
		public void onError(Throwable t) {
			future.setException(t);
		}

		@Override
		public void onCompleted() {
			future.set(messages);
		}

		// Returns the SettableFuture object to get received messages / exceptions.
		public SettableFuture<List<T>> future() {
			return future;
		}
	}
	
}