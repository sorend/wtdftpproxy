package net.tanesha.wtdftpproxy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the server part of the FTP proxy, that is;
 * <ul>
 * 	<li>send 220 greeting to user</li>
 *  <li>handle user FEAT</li>
 *  <li>handle user AUTH</li>
 *  <li>handle user USER</li>
 * </ul>
 * 
 * @author tanesha
 */
public class FtpServer {

	private Logger LOG = Logger.getLogger("server");
	
	private Socket control;
	private Socket sslControl;
	protected BufferedReader reader;
	protected BufferedWriter writer;
	protected InputStream sReader;
	protected OutputStream sWriter;
	
	public FtpServer(Socket socket) {
		this.control = socket;
	}
	
	// negotiate handles features, auth, and gets a user
	public String negociate(Map<String, String> params) throws IOException {
		
		if (params.containsKey("sotimeout")) {
			control.setSoTimeout(Integer.parseInt(params.get("sotimeout")) * 1000);
		}
		else {
			control.setSoTimeout(3600000);
		}
		
		sReader = control.getInputStream();
		sWriter = control.getOutputStream();
		reader = new BufferedReader(new InputStreamReader(sReader, "iso-8859-1"));
		writer = new BufferedWriter(new OutputStreamWriter(sWriter, "iso-8859-1"));
		
		doMessage(new FtpReply(220).add("wtdftpproxy welcome"));

		while (true) {
			
			String command = readCommand();
			
			if (command == null)
				throw new IOException("Got EOF");
			
			if (command.startsWith("USER ")) {
				return command.substring(5);
			}
			else if (command.startsWith("FEAT")) {
				/*
				 * Standard glftpd reply:
				211- Extensions supported:
					AUTH TLS
					AUTH SSL
					PBSZ
					PROT
					CPSV
					SSCN
					MDTM
					SIZE
					REST STREAM
					SYST
				211 END
				*/                  
				doMessage(new FtpReply(211).add("Extensions supported:\r\n AUTH TLS\r\n AUTH SSL\r\n PBSZ\r\n PROT\r\n CPSV\r\n	SSCN\r\n MDTM\r\n SIZE\r\n REST STREAM\r\n SYST\r\n").add("END"));
			}
			else if (command.startsWith("AUTH TLS") || command.startsWith("AUTH SSL")) {

				try {
					sslControl = SSLUtil.instance().toSSL(control, false);
					// sslControl.setSoTimeout(100000);
					
					doMessage(new FtpReply(234).add(command + " OK!"));

					sReader = sslControl.getInputStream();
					sWriter = sslControl.getOutputStream();
					reader = new BufferedReader(new InputStreamReader(sReader, "iso-8859-1"));
					writer = new BufferedWriter(new OutputStreamWriter(sWriter, "iso-8859-1"));
					
					// control.close();
				}
				catch (Exception e) {
					doMessage(new FtpReply(431).add("Auth failed."));
					throw new RuntimeException("Auth failed: " + e.getMessage(), e);
				}
				
			}
			else {
				doMessage(new FtpReply(500).add("Not supported."));
			}
		}
	}
	
	public String readCommand() throws IOException {
		return reader.readLine();
	}
	
	public void doMessage(FtpReply message) throws IOException {
		for (Iterator<String> it = message.message.iterator(); it.hasNext(); ) {
			String msg = it.next();
			boolean hasMore = it.hasNext();
			writer.write(String.format("%3d%s%s\r\n", message.code, hasMore ? "-" : " ", msg));
		}
		writer.flush();
	}

	public void close() {
		try {
			if (reader != null)
				reader.close();
			if (writer != null)
				writer.close();
			if (sReader != null)
				sReader.close();
			if (sWriter != null)
				sWriter.close();
			if (sslControl != null)
				sslControl.close();
			if (control != null)
				control.close();
		}
		catch (IOException e) {
			LOG.log(Level.WARNING, "Error closing client: " + e.getMessage(), e);
			// do nothing
		}
	}

}
