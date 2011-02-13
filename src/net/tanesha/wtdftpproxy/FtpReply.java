package net.tanesha.wtdftpproxy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Common modeling of a Ftp response message.
 * 
 * @author tanesha
 */
public class FtpReply {

	int code;
	public List<String> message = new ArrayList<String>();

	public FtpReply(int code) {
		this.code = code;
	}
	
	public FtpReply add(String msg) {
		this.message.add(msg);
		return this;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (Iterator<String> it = message.iterator(); it.hasNext(); ) {
			String msg = it.next();
			boolean hasMore = it.hasNext();
			b.append(String.format("%3d%s%s", code, hasMore ? "-" : " ", msg)).append("\r\n");
		}
		return b.toString();
	}

}
