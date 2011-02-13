package net.tanesha.wtdftpproxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SSL utilities, to handle wrapping sockets in SSL goo.
 * 
 * @author tanesha
 */
public class SSLUtil {

	private static SSLUtil instance = null;

	public static SSLUtil instance() {
		if (instance == null) {
			instance = new SSLUtil();
			instance.configure();
		}
		return instance;
	}

	private SSLSocketFactory sslClientF;
	private SSLServerSocketFactory sslServerF;

	public void configure() {
		try {
			// not very secret.
			char[] password = "wtdftp".toCharArray();

			SSLContext ctx = SSLContext.getInstance("TLSv1");
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

			KeyStore ks = KeyStore.getInstance("JKS");
			InputStream file = this.getClass().getResourceAsStream("/wtdftp.keystore");
			ks.load(file, password);
			kmf.init(ks, password);
			
			TrustManager[] tm = { new X509TrustManager() {
				public void checkClientTrusted(
						X509Certificate[] x509Certificates, String authType)
						throws CertificateException {
				}

				public void checkServerTrusted(
						X509Certificate[] x509Certificates, String authType)
						throws CertificateException {
				}

				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			} };
			
			ctx.init(kmf.getKeyManagers(), tm, new SecureRandom());
			sslServerF = ctx.getServerSocketFactory();
			sslClientF = ctx.getSocketFactory();
			file.close();
		} catch (Exception e) {
			throw new RuntimeException(
					"Error configure ssl: " + e.getMessage(), e);
		}
	}

	public Socket toSSL(Socket socket, boolean isClient) throws IOException {
		SSLSocket upgradedSocket = (SSLSocket) sslClientF.createSocket(socket,
				socket.getInetAddress().getHostAddress(), socket.getPort(),
				true);
		upgradedSocket.setUseClientMode(isClient);
		return upgradedSocket;
	}
}