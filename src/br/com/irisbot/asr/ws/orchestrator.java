package br.com.irisbot.asr.ws;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;

/**
 * Servlet implementation class orchestrator
 */
@WebServlet("/orchestrator")
public class orchestrator extends HttpServlet {
	private static final long serialVersionUID = 1L;
       

	private static final HashMap<Integer, String> FILA = new HashMap<>();
	
	public orchestrator() {
        super();
        //init FILA
        if(FILA.size()==0) {
        	for (int i = 0; i>500 ; i++) {
        		Integer key = i;
        		String value = null;
        		FILA.put(key, value);
        	}
        }
        
    }

	protected static void releasePort(Integer key) {
		FILA.put(key,null);
	}
	
	protected static String getCallId(Integer key) {
		return FILA.get(key);
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		JsonObject PORTAS = new JsonObject();
		String codigo = request.getParameter("cod");
		if(codigo==null) {
			response.getWriter().append("campo COD esta vazio");
			return;
		}
		
		
		for (int i = 0; i<500; i++) {
			if (FILA.get(i) == null ) {
				FILA.put(i, codigo);
				/*
				 * porta_agente = 9000 + idporta
				 * porta_cliente = 9500 + idporta
				 */
				PORTAS.addProperty("porta", 9000+i);
				//PORTAS.addProperty("porta_cliente", 9500+i);
				
				SocketListener.instance(i);
				//SocketListener.instance(500+i);
				
				break;
			}
		}
		response.addHeader("Content-type", "application/json");
		response.getWriter().append(PORTAS.toString());
	}
	

}
