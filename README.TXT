
wtdftpproxy
-----------

Wtdftpproxy is a simple FTP proxy, which allows "bounce" the control connection
with two "legs" of SSL, between client and server, like shown below;

	ftp-client <--SSL--> wtdftpproxy <--SSL--> ftp-server
	
It is useful when you want to hide your real IP towards the FTP server. With
wtdftpproxy you can run a single proxy instance, to connect to all your servers.

NOTE: You should be aware that you should use "STAT -la" ("Stealth mode" in 
some clients), to have all communication over the bounced control connection. Data
connections will not be "bounced" by this utility. 


Starting wtdftpproxy
--------------------

Use the ant script to build, or use the included wtdftpproxy.jar.
"ant -p" for help. Then run as shown below;

	java -jar wtdftpproxy.jar <[host:]port> <secret>
	
Example (All IPs, port 1234, and "MySecrete" as secret):

	java -jar wtdftpproxy.jar 1234 MySecrete

Example (Specific IP, port 2345, and "YourFeces" as secret):

	java -jar wtdftpproxy.jar 127.0.0.1:2345 YourFeces
	
Logging will be to stdout, but this can be configured by using a standard
java.util.logging configuration.
	

FTP client usage
----------------

In your ftp client, you must set the HOST+PORT to the host and port of the
wtdftpproxy. The magic then works by using a special username;

	secret,username@host:port,username2@host2:port2,username3@host3:port3;param1=pv1;param2;param3
	
The first is the password for the proxy. If you supply wrong secret, the connection
will be dropped by the proxy. The following (comma seperated) are username@host:port
to connect to. Each of them are tried in series, and the first successful is used.

Example (only one bouncer):

	MySecrete,bob@ftp.example.com:21
	
Example (multiple bouncers):

	MySecrete,bob@ftp1.example.com:21,bob@ftp2.example.com:21,bob@ftp3.example.com:21

There are parameters that can be added to the user string aswell. They MUST be last
in the list. They can be the form of param=paramvalue or just param. Each parameter
is seperated by ";". Currently the following parameters are available;

	forcessl	(Drop connection if remote does not support AUTH TLS)
	nossl		(Do not attempt AUTH TLS, even if server supports it)
	timeout=1	(Specify the timeout before trying next user@host:port set)
	sotimeout=600 (Specify the SO-timeout on the sockets)
	
Default is that SSL is tried if available, a SO-timeout of 3600 seconds, and 
a timeout of 10 seconds.


Design
------

A common usage scenario looks like this

Client				Proxy					Server
connect --->		
			  <---	220 Welcome
FEAT --->
AUTH TLS --->
USER ... ---->
				connect --->
								<---	220 Welcome
				FEAT --->
				AUTH TLS --->
				USER ... --->
			<---					<---	310 Password required
PASS ...		--->					--->	22x Now logged in ..


Wolla
-----

Hi to old HAREM ppl for the idea, and safe ftp'ing to all ;-)

/Tanesha
