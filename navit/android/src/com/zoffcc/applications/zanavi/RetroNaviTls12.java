package com.zoffcc.applications.zanavi;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Enables TLS 1.2 on Android 4.1-4.4 (API 16-21) using BouncyCastle JSSE provider.
 * The platform OpenSSL on CyanogenMod/old Android can't negotiate TLS 1.2 properly,
 * so we use BouncyCastle's pure-Java TLS implementation (bctls) instead.
 * Also bundles missing root CAs (Sectigo/USERTrust, ISRG/Let's Encrypt) for GitHub.
 */
public class RetroNaviTls12
{
	private static final String TAG = "RetroNaviTls12";

	public static void enable(Context context)
	{
		if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22)
		{
			try
			{
				// Step 1: Build trust managers BEFORE installing BC provider,
				// because BC's TrustManagerFactory can't init from system trust store
				// System trust store (must be created before installing BC provider)
				TrustManagerFactory systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				systemTmf.init((KeyStore) null);
				final X509TrustManager systemTm = getX509TrustManager(systemTmf);

				// Extra CAs from raw resources
				KeyStore extraCaStore = KeyStore.getInstance(KeyStore.getDefaultType());
				extraCaStore.load(null, null);

				CertificateFactory cf = CertificateFactory.getInstance("X.509");

				int[] certResIds = new int[] {
					R.raw.ca_usertrust_ecc,
					R.raw.ca_isrg_root_x1
				};
				String[] certNames = new String[] {
					"usertrust_ecc",
					"isrg_root_x1"
				};

				for (int i = 0; i < certResIds.length; i++)
				{
					InputStream is = context.getResources().openRawResource(certResIds[i]);
					X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
					extraCaStore.setCertificateEntry(certNames[i], cert);
					is.close();
				}

				TrustManagerFactory extraTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				extraTmf.init(extraCaStore);
				final X509TrustManager extraTm = getX509TrustManager(extraTmf);

				X509TrustManager combinedTm = new X509TrustManager()
				{
					public X509Certificate[] getAcceptedIssuers()
					{
						return systemTm.getAcceptedIssuers();
					}

					public void checkClientTrusted(X509Certificate[] chain, String authType)
						throws java.security.cert.CertificateException
					{
						systemTm.checkClientTrusted(chain, authType);
					}

					public void checkServerTrusted(X509Certificate[] chain, String authType)
						throws java.security.cert.CertificateException
					{
						try
						{
							systemTm.checkServerTrusted(chain, authType);
						}
						catch (java.security.cert.CertificateException e)
						{
							extraTm.checkServerTrusted(chain, authType);
						}
					}
				};

				// Step 2: Replace system's stripped BC with full BC (needed for ECDHE/ECDSA)
				Security.removeProvider("BC");
				Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
				Security.addProvider(new org.bouncycastle.jsse.provider.BouncyCastleJsseProvider());

				// Step 3: Create SSLContext using BC's JSSE - real TLS 1.2 in pure Java
				SSLContext sslContext = SSLContext.getInstance("TLSv1.2", "BCJSSE");
				sslContext.init(null, new TrustManager[] { combinedTm }, new java.security.SecureRandom());

				HttpsURLConnection.setDefaultSSLSocketFactory(new Tls12SocketFactory(sslContext.getSocketFactory()));

				RetroNaviLogger.i(TAG, "TLS 1.2 (BouncyCastle) enabled for API " + Build.VERSION.SDK_INT);
			}
			catch (Exception e)
			{
				RetroNaviLogger.i(TAG, "FAILED: " + e.toString());
				Log.e(TAG, "Failed to enable TLS 1.2", e);
			}
		}
	}

	private static X509TrustManager getX509TrustManager(TrustManagerFactory tmf)
	{
		for (TrustManager tm : tmf.getTrustManagers())
		{
			if (tm instanceof X509TrustManager)
			{
				return (X509TrustManager) tm;
			}
		}
		throw new RuntimeException("No X509TrustManager found");
	}

	private static class Tls12SocketFactory extends SSLSocketFactory
	{
		private final SSLSocketFactory delegate;

		Tls12SocketFactory(SSLSocketFactory delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public String[] getDefaultCipherSuites()
		{
			return delegate.getDefaultCipherSuites();
		}

		@Override
		public String[] getSupportedCipherSuites()
		{
			return delegate.getSupportedCipherSuites();
		}

		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException
		{
			return configure(delegate.createSocket(s, host, port, autoClose));
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException
		{
			return configure(delegate.createSocket(host, port));
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException
		{
			return configure(delegate.createSocket(host, port, localHost, localPort));
		}

		@Override
		public Socket createSocket(InetAddress host, int port) throws IOException
		{
			return configure(delegate.createSocket(host, port));
		}

		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException
		{
			return configure(delegate.createSocket(address, port, localAddress, localPort));
		}

		private Socket configure(Socket socket)
		{
			if (socket instanceof SSLSocket)
			{
				try
				{
					socket.setSoTimeout(30000);
				}
				catch (Exception e)
				{
					Log.e(TAG, "setSoTimeout failed", e);
				}
				// Wrap BC's SSLSocket so HttpsURLConnectionImpl's
				// setEnabledProtocols() calls don't crash with unsupported names
				return new BcSslSocketWrapper((SSLSocket) socket);
			}
			return socket;
		}
	}

	/**
	 * Wraps BC's SSLSocket to shield it from Android HttpsURLConnectionImpl
	 * calling setEnabledProtocols with platform-specific protocol names
	 * that BC doesn't understand. All other calls pass through.
	 */
	private static class BcSslSocketWrapper extends SSLSocket
	{
		private final SSLSocket delegate;

		BcSslSocketWrapper(SSLSocket delegate)
		{
			this.delegate = delegate;
		}

		// Protocol-related: absorb bad names, let SSLContext default handle it
		@Override
		public void setEnabledProtocols(String[] protocols)
		{
			// Ignore - SSLContext already set to TLSv1.2
		}

		@Override
		public String[] getEnabledProtocols()
		{
			return delegate.getEnabledProtocols();
		}

		@Override
		public String[] getSupportedProtocols()
		{
			return delegate.getSupportedProtocols();
		}

		@Override
		public void setEnabledCipherSuites(String[] suites)
		{
			delegate.setEnabledCipherSuites(suites);
		}

		@Override
		public String[] getEnabledCipherSuites()
		{
			return delegate.getEnabledCipherSuites();
		}

		@Override
		public String[] getSupportedCipherSuites()
		{
			return delegate.getSupportedCipherSuites();
		}

		@Override
		public javax.net.ssl.SSLSession getSession()
		{
			return delegate.getSession();
		}

		@Override
		public void addHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener)
		{
			delegate.addHandshakeCompletedListener(listener);
		}

		@Override
		public void removeHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener)
		{
			delegate.removeHandshakeCompletedListener(listener);
		}

		@Override
		public void startHandshake() throws IOException
		{
			delegate.startHandshake();
		}

		@Override
		public void setUseClientMode(boolean mode)
		{
			delegate.setUseClientMode(mode);
		}

		@Override
		public boolean getUseClientMode()
		{
			return delegate.getUseClientMode();
		}

		@Override
		public void setNeedClientAuth(boolean need)
		{
			delegate.setNeedClientAuth(need);
		}

		@Override
		public boolean getNeedClientAuth()
		{
			return delegate.getNeedClientAuth();
		}

		@Override
		public void setWantClientAuth(boolean want)
		{
			delegate.setWantClientAuth(want);
		}

		@Override
		public boolean getWantClientAuth()
		{
			return delegate.getWantClientAuth();
		}

		// Socket methods - delegate everything
		@Override
		public java.io.InputStream getInputStream() throws IOException
		{
			return delegate.getInputStream();
		}

		@Override
		public java.io.OutputStream getOutputStream() throws IOException
		{
			return delegate.getOutputStream();
		}

		@Override
		public void close() throws IOException
		{
			delegate.close();
		}

		@Override
		public void connect(java.net.SocketAddress endpoint) throws IOException
		{
			delegate.connect(endpoint);
		}

		@Override
		public void connect(java.net.SocketAddress endpoint, int timeout) throws IOException
		{
			delegate.connect(endpoint, timeout);
		}

		@Override
		public boolean isConnected()
		{
			return delegate.isConnected();
		}

		@Override
		public boolean isClosed()
		{
			return delegate.isClosed();
		}

		@Override
		public InetAddress getInetAddress()
		{
			return delegate.getInetAddress();
		}

		@Override
		public int getPort()
		{
			return delegate.getPort();
		}

		@Override
		public InetAddress getLocalAddress()
		{
			return delegate.getLocalAddress();
		}

		@Override
		public int getLocalPort()
		{
			return delegate.getLocalPort();
		}

		@Override
		public java.net.SocketAddress getRemoteSocketAddress()
		{
			return delegate.getRemoteSocketAddress();
		}

		@Override
		public void setSoTimeout(int timeout) throws java.net.SocketException
		{
			delegate.setSoTimeout(timeout);
		}

		@Override
		public int getSoTimeout() throws java.net.SocketException
		{
			return delegate.getSoTimeout();
		}

		@Override
		public void setEnableSessionCreation(boolean flag)
		{
			delegate.setEnableSessionCreation(flag);
		}

		@Override
		public boolean getEnableSessionCreation()
		{
			return delegate.getEnableSessionCreation();
		}
	}
}
