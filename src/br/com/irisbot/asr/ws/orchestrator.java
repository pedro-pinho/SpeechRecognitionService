package br.com.irisbot.asr.ws;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
/**
 * Servlet implementation class orchestrator
 */
@WebServlet("/orchestrator")
public class orchestrator extends HttpServlet {
	private static final long serialVersionUID = 1L;
       

	private static final HashMap<Integer, String> FILA = new HashMap<Integer, String>();
	
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

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException, NoClassDefFoundError {
		String codigo = request.getParameter("cod");
		if(codigo==null) {
			response.getWriter().append("campo COD esta vazio");
			return;
		}
		Integer[] portas = new Integer[4];
		for (int i = 0; i<500; i++) {
			if (FILA.get(i) == null ) {
				FILA.put(i, codigo);
				/*
				 * porta_agente = 9000 + idporta
				 * porta_cliente = 9500 + idporta
				 */
				portas[i] = 9000+i;
				System.out.println(portas[i]);
				SocketListener.main(portas[i]);
				//SocketListener.instance(500+i);
				
				break;
			}
		}
		response.addHeader("Content-type", "application/json");
		response.getWriter().append(portas.toString());
	}
	

}
