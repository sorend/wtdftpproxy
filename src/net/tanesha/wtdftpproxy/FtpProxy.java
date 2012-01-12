package net.tanesha.wtdftpproxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handle the "proxy" part of the ftp proxy. That means;
 * <ul>
 * 	<li>Get USER via FtpServer module</li>
 * 	<li>Connect to remote via FtpClient module</li>
 *  <li>Handle rest of the message passing between FtpServer and FtpClient</li>
 * </ul>
 * 
 * @author tanesha
 */
public class FtpProxy implements Runnable {

	// pattern to match login information (username@host:port)
	private static Pattern LOGIN = Pattern.compile("^([^@]+)@([^:]+):(\\d+)");

	private Logger LOG = Logger.getLogger("proxy");
	
	private InetAddress bindAddr;
	private Socket serverSock;
	private String secret;
	
	private FtpServer server = null;
	private FtpClient client = null;

	// private boolean running = true;
	
	private Thread CtoS;
	private Thread StoC;
	
	private Map<String, String> params = new HashMap<String, String>();
	
	public FtpProxy(InetAddress bindAddr, Socket serverSock, String secret) {
		this.bindAddr = bindAddr;
		this.serverSock = serverSock;
		this.secret = secret;
	}
	
	private void handleParams(String paramlist) {
		
		String[] plist = paramlist.split(";");

		for (String param : plist) {
			String[] kv = param.split("=");
			if (kv.length < 2)
				params.put(kv[0], "");
			else
				params.put(kv[0], kv[1]);
		}
	}
	
	@Override
	public void run() {

		try {
			
			LOG.info("New connection from " + serverSock.getInetAddress());

			// negotiate with client, and get a USER string.
			server = new FtpServer(serverSock);
			String user = server.negociate(params);
			
			// handle parameters (if any)
			int pidx = user.indexOf(";");
			if (pidx != -1) {
				handleParams(user.substring(pidx + 1));
				user = user.substring(0, pidx);
			}

			String[] logins = user.split(",");

			if (!secret.equals(logins[0])) {
				return;
			}

			client = null;
			FtpReply reply = null;
			
			for (int i = 1; i < logins.length; i++) {
				String login = logins[i];

				Matcher m = LOGIN.matcher(login);
				if (!m.matches())
					continue;
				
				String username = m.group(1);
				String host = m.group(2);
				int port = Integer.parseInt(m.group(3));

				client = new FtpClient(this.bindAddr, host, port, username, params);

				try {
					reply = client.connect();
					// we got a reply, done here.
					break;
				}
				catch (Exception e) {
					client.close();
					LOG.log(Level.INFO, "Login failed " + host + ":" + port + " (" + e.getMessage() + ")", e);
					// do nothing, we try the next
				}
			}

			if (reply == null || client == null) {
				server.doMessage(new FtpReply(421).add("Connection failed to remote(s)"));
				return;
			}
			
			// send the "USER" reply upstream to client
			server.doMessage(reply);
			
			LOG.fine("Setup complete, dumb proxying S<->B<->C mode..");
			
			// start server to client proxying
			StoC = new Thread(new FtpProxyThread(server.sReader, client.sWriter, false));

			// start server to client proxying
			CtoS = new Thread(new FtpProxyThread(client.sReader, server.sWriter, true));
			
			StoC.start(); 
			CtoS.start();
			
			// wait for completion
			StoC.join();
			CtoS.join();

			// LOG.info("All shut down .. ");
		}
		catch (Exception e) {
			try {
				serverSock.getOutputStream().write(("421 Error: " + e.getMessage() + "\r\n").getBytes());
			}
			catch (Exception e2) {
				// do nothing
			}
		}
		finally {
			try {
				serverSock.close();
			}
			catch (IOException e) {
				// do nothing
			}
		}
		
	}
	
	private void shutdown(boolean isServer) {
		// LOG.warning("Shutdown proxy .. " + (isServer ? "from server" : "from client"));
		// running = false;
		client.close();
		server.close();
	}
	
	private class FtpProxyThread implements Runnable {
		private InputStream r;
		private OutputStream w;
		private boolean isServer;
		public FtpProxyThread(InputStream r, OutputStream w, boolean isServer) {
			this.r = r;
			this.w = w;
			this.isServer = isServer;
		}
		@Override
		public void run() {
			byte[] buf = new byte[1024];
			int n = -1;
			try {
				while (-1 != (n = r.read(buf))) {
					w.write(buf, 0, n);
				}
			}
			catch (IOException e) {
				LOG.log(Level.WARNING, "Error on connection: " + e.getMessage(), e);
			}

			shutdown(isServer);
		}
	}
	
}
