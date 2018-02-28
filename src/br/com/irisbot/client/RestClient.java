package br.com.irisbot.client;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;

import com.google.common.io.Files;


/**
 * Created by root on 14/03/17.
 */

public abstract class RestClient{

    private String url;
    private Map<String, String> headers = new HashMap<String, String>();
    
    private static final String crlf = "\r\n";
    private static final String twoHyphens = "--";
    private static final String boundary =  "*****"+RestClient.class.getCanonicalName()+"_"+new Date().getTime()+"*****";

    public RestClient(String url){
        this.url = url;
    }

    public void setBasicAuthentication(String username, String password){
		Authenticator.setDefault(new BasicAuthenticator(username, password));
    }
    
    public void addRequestHeader(String key, String value){
        this.headers.put(key,value);
    }

    public void doPostMultipart(byte[] payload, File attachment, String attachFieldName){
        doConnect("POST", false, true, url, headers, payload, attachment, attachFieldName);
    }
    public void doPost(byte[] payload){
        doConnect("POST", false, true, url, headers, payload, null, null);
    }
    public void doPut(byte[] payload){
        doConnect("PUT", false, true, url, headers, payload, null, null);
    }

    public void doSslPost(byte[] payload){
        doConnect("POST", true, true, url, headers, payload, null, null);
    }
    public void doSslPut(byte[] payload){
        doConnect("PUT", true, true, url, headers, payload, null, null);
    }

    public void doPostMultipart(String payload, File attachment, String attachFieldName){
        doConnect("POST", false, true, url, headers, payload.getBytes(StandardCharsets.UTF_8), attachment, attachFieldName);
    }
    public void doPost(String payload){
        doConnect("POST", false, true, url, headers, payload.getBytes(StandardCharsets.UTF_8), null, null);
    }
    public void doPut(String payload){
        doConnect("PUT", false, true, url, headers, payload.getBytes(StandardCharsets.UTF_8), null, null);
    }
    public void doGet(){
        doConnect("GET", false, false, url, headers, null, null, null);
    }
    public void doDelete(){
        doConnect("DELETE", false, false, url, headers, null, null, null);
    }

    public void doSslPost(String payload){
        doConnect("POST", true, true, url, headers, payload.getBytes(StandardCharsets.UTF_8), null, null);
    }
    public void doSslPut(String payload){
        doConnect("PUT", true, true, url, headers, payload.getBytes(StandardCharsets.UTF_8), null, null);
    }
    public void doSslGet(){
        doConnect("GET", true, false, url, headers, null, null, null);
    }
    public void doSslDelete(){
        doConnect("DELETE", true, false, url, headers, null, null, null);
    }

    private void doConnect(final String method, final boolean ssl, final boolean hasOutput, final String url, final Map<String,String> headers, final byte[] payload, final File attachment, final String attachmentField){

        new Thread( 
        	new Runnable() {
            Response response = new Response();
            @SuppressWarnings({ "resource", "deprecation" })
            public void run() {
                HttpURLConnection conn;
                try {
                    URL _url = new URL(url);
                    if (!ssl) conn = (HttpURLConnection) _url.openConnection();
                    else conn = (HttpsURLConnection) _url.openConnection();
                    for (String key : headers.keySet()) {
                        conn.addRequestProperty(key, headers.get(key));
                    }
                    conn.setRequestMethod(method);
                    conn.setDoOutput(hasOutput);
                    conn.setDoInput(true);
                    conn.setUseCaches(false);
                    conn.setConnectTimeout(10000);
                    String attachField = attachmentField;
                	if(attachment!=null){
                    	if(attachField==null || attachField.isEmpty()) attachField = "file";
                    	conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                    }
                    if (hasOutput || attachment!=null) {
                        OutputStream out = conn.getOutputStream();
                        if(hasOutput) out.write(payload);
                        if(attachment!=null){
                        	DataOutputStream dos = new DataOutputStream(out);
                        	dos.writeBytes(twoHyphens + boundary + crlf);
                        	dos.writeBytes("Content-Disposition: form-data; name=\"" +
                        		    attachField + "\";filename=\"" + 
                        		    attachment.getName() + "\"" + crlf + crlf);
                        	dos.write((Integer) Files.readBytes(new File(attachment.getPath()), null));
                        	dos.writeBytes(crlf + twoHyphens + boundary + twoHyphens + crlf);
                        }
                        out.flush();
                        out.close();
                    }
                    if(payload!=null) response.headers = new String(payload);
                    response.code = conn.getResponseCode();
                    InputStream in;
                    if(response.code>=200 && response.code<400) in = conn.getInputStream();
                    else in = conn.getErrorStream();
                    Scanner s = new Scanner(in).useDelimiter("\\A");
                    response.content = s.hasNext() ? s.next() : "";
                    in.close();
                    s.close();
                } catch (Exception ex) {
                	ex.printStackTrace();
                    response.code = 500;
                    response.content = ex.toString();
                }
                
                whenDone(response);
                
            }
        }).start();

    }

    public abstract void whenDone(Response resp);

    public static class Response{
        private int code = 0;
        private String content = "not connected";
        private String headers = "";
        private Response(){}
        public int getCode(){return code;}
        public String getContent(){return content;}
        public String getHeaders(){return headers;}
    }

    private static class BasicAuthenticator extends Authenticator {
        String baName;
        String baPassword;
        private BasicAuthenticator(String baName1, String baPassword1) {
            baName = baName1;
            baPassword = baPassword1;
        }
        @Override
            public PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(baName, baPassword.toCharArray());
            }
    };
    
    public static byte[] getBinaryBuffer(File file){
    	try{
            FileInputStream fis = new FileInputStream(file);
            byte[] bb = new byte[(int)file.length()];
            fis.read(bb, 0, bb.length);
            fis.close();
            return bb;
        }catch (Exception e){
            return null;
        }
    }
}
