package net.tanesha.wtdftpproxy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the client part of the ftp proxy. That is;
 * <ul>
 * 	<li>connect to host:port</li>
 * 	<li>FEAT</li>
 * 	<li>AUTH TLS (if feat contains AUTH)</li>
 * 	<li>USER username</li>
 * </ul>
 * 
 * @author tanesha
 */
public class FtpClient {

	private Logger LOG = Logger.getLogger("client");
	
	private Socket control;
	private Socket sslControl; 
	private InetSocketAddress addr;
	protected BufferedReader reader;
	protected BufferedWriter writer;
	protected InputStream sReader;
	protected OutputStream sWriter;

	private boolean ssl = false;
	private String username;
	private InetAddress bind;
	
	private Map<String, String> params;
	
	public FtpClient(InetAddress bind, String host, int port, String username, Map<String, String> params) {
		this.addr = new InetSocketAddress(host, port);
		this.username = username;
		this.params = params;
		this.bind = bind;
	}
	
	public boolean isSSL() {
		return ssl;
	}
	
	public FtpReply connect() throws IOException {

		int timeout = 10000;
		
		if (params.containsKey("timeout")) {
			timeout = Integer.parseInt(params.get("timeout")) * 1000;
		}
		
		control = new Socket();

		// configure socket, reuse and with timeout
		control.setReuseAddress(true);
		control.setSoTimeout(timeout);
		
		if (bind != null) {
			control.bind(new InetSocketAddress(bind, 0));
		}
		
		// attempt connection
		control.connect(addr, timeout);
		
		// remove timeout, we're connected now.
		if (params.containsKey("sotimeout")) {
			control.setSoTimeout(Integer.parseInt(params.get("sotimeout")) * 1000);
		}
		else {
			control.setSoTimeout(3600000);
		}
		
		// bind reader and writer
		sReader = control.getInputStream();
		sWriter = control.getOutputStream();
		reader = new BufferedReader(new InputStreamReader(sReader, "iso-8859-1"));
		writer = new BufferedWriter(new OutputStreamWriter(sWriter, "iso-8859-1"));
		
		FtpReply welcome = readReply();
		
		if (welcome == null || welcome.code != 220)
			throw new RuntimeException("No welcome message.");

		// attempt SSL unless user don't want it.
		if (!params.containsKey("nossl")) {
			
			// request SSL
			FtpReply auth = doCommand("AUTH TLS");
				
			if (auth.code == 234) {
				try {
					sslControl = SSLUtil.instance().toSSL(control, true);
					// sslControl.setSoTimeout(10000);
					
					// update reader+writer
					sReader = sslControl.getInputStream();
					sWriter = sslControl.getOutputStream();
					reader = new BufferedReader(new InputStreamReader(sReader, "iso-8859-1"));
					writer = new BufferedWriter(new OutputStreamWriter(sWriter, "iso-8859-1"));
					
					// control.close();
					
					ssl = true;
				}
				catch (Exception e) {
					throw new RuntimeException("Error negociating SSL: " + e.getMessage(), e);
				}
			}

			// we want SSL only, but server don't support it.
			if (!ssl && params.containsKey("forcessl")) {
				throw new RuntimeException("Server has no AUTH support, and forcessl specified.");
			}
		
		}
		
		// login the user
		FtpReply userReply = doCommand("USER " + username);
		
		if (userReply.code != 331) {
			throw new RuntimeException("No password asked.");
		}
		else {
			return userReply;
		}
	}

	private FtpReply doCommand(String command) throws IOException {

		LOG.fine(">> " + command);
		writer.write(command + "\r\n");
		writer.flush();
		return readReply();
	}

	private FtpReply readReply() throws IOException {

		FtpReply reply = new FtpReply(-1);

		boolean hasOutput = false;
		boolean complete = false;
		while (!complete) {
			String tmp = reader.readLine();

			System.out.println("<< " + tmp);
			
			if (tmp == null)
				break;

			hasOutput = true;
			
			if (tmp.startsWith(" ")) {
				reply.message.add(tmp.substring(1));
			}
			else {
			
				String code = tmp.substring(0, 3);
				String msg = tmp.substring(3);
	
				reply.code = Integer.parseInt(code);
				
				if (msg.startsWith("-")) {
					reply.message.add(msg.substring(1));
				}
				else {
					reply.message.add(msg.substring(1));
					complete = true;
				}
			}
		}
		
		if (hasOutput)
			return reply;
		else
			return null;
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
