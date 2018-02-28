package br.com.irisbot.asr.ws;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import br.com.irisbot.asr.core.Stream;

public class SocketListener {
	public static void main(final int port) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					//String callId = orchestrator.getCallId(key>=500?key-500:key);
					
					ServerSocket srv = new ServerSocket(port);
					srv.setSoTimeout(30000);

					System.out.println("aguardando..."+srv.getLocalPort());
					
					Socket cli = srv.accept();
					cli.setSoTimeout(6000);
					System.out.println("aceito.."+srv.getLocalPort());
					
					InputStream is = cli.getInputStream();
					OutputStream os = cli.getOutputStream();
					
					Stream ds = new Stream();
					ds.speechStreamingGoogle(srv,is,os);
					
					//orchestrator.releasePort(key);
					
				}catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
	 /*private static Integer findRandomOpenPortOnAllLocalInterfaces() throws IOException {
	    try (
	        ServerSocket socket = new ServerSocket(0);
	    ) {
	      return socket.getLocalPort();

	    }
	  }*/

}
