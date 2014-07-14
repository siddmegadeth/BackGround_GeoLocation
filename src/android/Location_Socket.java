package com.Dolphin.BgLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;




public class Location_Socket  extends Thread {
	
	private Socket callbackSocket;
	private PrintWriter writer;
	private BufferedReader reader;

	private Boolean mustClose;
	private String host;
	private int port;
	
	public Location_Socket(String host, int port) {
		super();
		setDaemon(true);

		this.mustClose = false;
		this.host = host;
		this.port = port;

	}
	
	public boolean isConnected() {

		boolean result =  (
				this.callbackSocket == null ? false : 
					this.callbackSocket.isConnected() && 
					this.callbackSocket.isBound() && 
					!this.callbackSocket.isClosed() && 
					!this.callbackSocket.isInputShutdown() && 
					!this.callbackSocket.isOutputShutdown());

		// if everything apparently is fine, time to test the streams
		if (result) {
			try {
				this.callbackSocket.getInputStream().available();
			} catch (IOException e) {
				// connection lost
				result = false;
			}
		}

		return result;
	}
	
	public void close() {
		// closing connection
		try {
			//this.writer.close();
			//this.reader.close();
			callbackSocket.shutdownInput();
			callbackSocket.shutdownOutput();
			callbackSocket.close();
			this.mustClose = true;
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	public void write(String data) {
		this.writer.println(data);
	}

	public void run() {
		String chunk = null;

		// creating connection
		try {
			this.callbackSocket = new Socket(this.host, this.port);
			this.writer = new PrintWriter(this.callbackSocket.getOutputStream(), true);
			this.reader = new BufferedReader(new InputStreamReader(callbackSocket.getInputStream()));

			// receiving data chunk
			while(!this.mustClose){

				try {

					if (this.isConnected()) {
						chunk = reader.readLine();

						if (chunk != null) {
							chunk = chunk.replaceAll("\"\"", "null");
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

}
