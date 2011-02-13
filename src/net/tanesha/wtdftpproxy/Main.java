package net.tanesha.wtdftpproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Entrypoint and listener part of the wtdftpproxy.
 * 
 * @author tanesha
 */
public class Main implements Runnable {

	private Logger LOG = Logger.getLogger("main");
	
	private ServerSocket socket;
	private String secret;
	
	private Main(ServerSocket socket, String secret) {
		this.socket = socket;
		this.secret = secret;
	}

	@Override
	public void run() {

		LOG.info("wtdftpproxy started, waiting for connections on " + socket.getInetAddress() + ":" + socket.getLocalPort());
		
		try {
			// forever looping
			while (true) {
				// accept new connections
				Socket sock = socket.accept();
				// for a proxy thread for each connection received.
				new Thread(new FtpProxy(sock, secret)).start();
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Error in socket handling: " + e.getMessage(), e);
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		if (args.length < 2) {
			System.out.println("Syntax " + Main.class.getName() + " <[host:]port> <secret>");
			System.exit(100);
		}
		
		// create and bind server socket
		String[] a = args[0].split(":");
		InetSocketAddress addr;
		if (a.length > 1) {
			addr = new InetSocketAddress(a[0], Integer.parseInt(a[1]));
		}
		else {
			addr = new InetSocketAddress(Integer.parseInt(a[0]));
		}
		ServerSocket ss = new ServerSocket();
		ss.bind(addr);
		
		// create proxy
		Main proxy = new Main(ss, args[1]);
		// run main loop
		proxy.run();
	}
}
