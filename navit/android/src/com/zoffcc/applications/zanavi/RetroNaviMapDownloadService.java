package com.zoffcc.applications.zanavi;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Background service for downloading map files.
 * Shows progress in the notification bar so the user can continue navigating.
 */
public class RetroNaviMapDownloadService extends IntentService
{
	private static final String TAG = "RetroNaviMapDL";
	private static final int NOTIFICATION_ID = 9001;
	private static final String MAP_FILENAME = "navitmap_001.bin";
	private static final String MAP_TEMP_FILENAME = "navitmap_download.tmp";
	private static final String ACTION_CANCEL = "com.zoffcc.applications.zanavi.CANCEL_MAP_DOWNLOAD";

	private static volatile boolean cancelRequested = false;
	private static volatile boolean running = false;
	private BroadcastReceiver cancelReceiver;

	public RetroNaviMapDownloadService()
	{
		super("RetroNaviMapDownloadService");
	}

	public static boolean isRunning()
	{
		return running;
	}

	public static void requestCancel()
	{
		cancelRequested = true;
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		if (intent == null) return;

		String mapUrl = intent.getStringExtra("map_url");
		if (mapUrl == null) return;

		running = true;
		cancelRequested = false;
		boolean verbose = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("retronavi_verbose_log", false);

		cancelReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				cancelRequested = true;
			}
		};
		registerReceiver(cancelReceiver, new IntentFilter(ACTION_CANCEL));

		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder nb = createNotificationBuilder();

		nb.setContentTitle(Navit.get_text("Downloading map"))
			.setContentText(Navit.get_text("Connecting...") + " " + Navit.get_text("TLS handshake - this may take a moment on older devices"))
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setProgress(100, 0, true)
			.setOngoing(true);
		addCancelAction(nb);
		nm.notify(NOTIFICATION_ID, nb.build());

		HttpURLConnection connection = null;
		InputStream input = null;
		FileOutputStream output = null;

		try
		{
			if (verbose) RetroNaviLogger.i(TAG, "Connecting to: " + mapUrl);

			URL url = new URL(mapUrl);
			connection = (HttpURLConnection) url.openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setConnectTimeout(60000);
			connection.setReadTimeout(60000);

			long t0 = System.currentTimeMillis();
			connection.connect();
			long t1 = System.currentTimeMillis();
			if (verbose) RetroNaviLogger.i(TAG, "connect() in " + (t1 - t0) + " ms");

			if (cancelRequested) { cleanup(nm); return; }

			int responseCode = connection.getResponseCode();
			if (verbose) RetroNaviLogger.i(TAG, "HTTP " + responseCode);

			if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
				|| responseCode == HttpURLConnection.HTTP_MOVED_PERM
				|| responseCode == 307 || responseCode == 308)
			{
				String redirectUrl = connection.getHeaderField("Location");
				if (verbose) RetroNaviLogger.i(TAG, "Redirect: " + redirectUrl);

				nb.setContentText(Navit.get_text("Following redirect..."));
				nm.notify(NOTIFICATION_ID, nb.build());

				connection.disconnect();
				connection = (HttpURLConnection) new URL(redirectUrl).openConnection();
				connection.setInstanceFollowRedirects(true);
				connection.setConnectTimeout(60000);
				connection.setReadTimeout(60000);
				connection.connect();
				responseCode = connection.getResponseCode();
			}

			if (responseCode != HttpURLConnection.HTTP_OK)
			{
				notifyError(nm, nb, Navit.get_text("Server error: HTTP ") + responseCode);
				return;
			}

			long fileLength = connection.getContentLength();
			long fileLengthMB = fileLength > 0 ? fileLength / 1024 / 1024 : 0;

			File mapsDir = new File(Environment.getExternalStorageDirectory(), "retronavi/maps");
			if (!mapsDir.exists()) mapsDir.mkdirs();

			File tempFile = new File(mapsDir, MAP_TEMP_FILENAME);
			File finalFile = new File(mapsDir, MAP_FILENAME);

			input = connection.getInputStream();
			output = new FileOutputStream(tempFile);

			byte[] buffer = new byte[16384];
			long totalBytesRead = 0;
			int bytesRead;
			long lastNotifyUpdate = 0;
			long downloadStartTime = System.currentTimeMillis();

			nb.setProgress(100, 0, false);

			while ((bytesRead = input.read(buffer)) != -1)
			{
				if (cancelRequested)
				{
					output.close();
					output = null;
					tempFile.delete();
					cleanup(nm);
					return;
				}

				output.write(buffer, 0, bytesRead);
				totalBytesRead += bytesRead;

				long now = System.currentTimeMillis();
				if (now - lastNotifyUpdate > 2000)
				{
					lastNotifyUpdate = now;
					long elapsedSec = (now - downloadStartTime) / 1000;
					long speedKBs = elapsedSec > 0 ? (totalBytesRead / 1024 / elapsedSec) : 0;
					int pct = fileLength > 0 ? (int) (totalBytesRead * 100 / fileLength) : 0;
					long dlMB = totalBytesRead / 1024 / 1024;

					String speedStr = speedKBs >= 1024
						? (speedKBs / 1024) + " MB/s"
						: speedKBs + " KB/s";

					nb.setContentTitle(Navit.get_text("Downloading map") + " " + pct + "%")
						.setContentText(dlMB + " / " + fileLengthMB + " MB  (" + speedStr + ")")
						.setProgress(100, pct, false);
					nm.notify(NOTIFICATION_ID, nb.build());
				}
			}

			output.flush();
			output.close();
			output = null;

			Log.i(TAG, "Downloaded: " + totalBytesRead + " bytes");

			nb.setContentTitle(Navit.get_text("Installing map..."))
				.setContentText("")
				.setProgress(100, 100, true);
			nm.notify(NOTIFICATION_ID, nb.build());

			// Remove old maps
			File[] oldMaps = mapsDir.listFiles();
			if (oldMaps != null)
			{
				for (File f : oldMaps)
				{
					if (f.getName().startsWith("navitmap_") && f.getName().endsWith(".bin"))
					{
						f.delete();
					}
				}
			}

			if (!tempFile.renameTo(finalFile))
			{
				notifyError(nm, nb, Navit.get_text("Failed to save map"));
				return;
			}

			// Update maps_cat.txt
			try
			{
				File catFile = new File(mapsDir.getParent(), "maps_cat.txt");
				FileOutputStream catOut = new FileOutputStream(catFile);
				catOut.write("# RetroNavi maps\n".getBytes());
				catOut.write((MAP_FILENAME + ":" + MAP_FILENAME + "\n").getBytes());
				catOut.close();
			}
			catch (Exception ce)
			{
				Log.e(TAG, "maps_cat.txt failed", ce);
			}

			long sizeMB = totalBytesRead / 1024 / 1024;
			nb.setContentTitle(Navit.get_text("Map downloaded"))
				.setContentText(finalFile.getName() + " (" + sizeMB + " MB) - " + Navit.get_text("Restart the app to load the new map."))
				.setSmallIcon(android.R.drawable.stat_sys_download_done)
				.setProgress(0, 0, false)
				.setOngoing(false);
			nm.notify(NOTIFICATION_ID, nb.build());
		}
		catch (Exception e)
		{
			RetroNaviLogger.i(TAG, "Download failed: " + e.toString());
			Log.e(TAG, "Map download failed", e);
			if (!cancelRequested)
			{
				notifyError(nm, nb, Navit.get_text("Download error: ") + e.getMessage());
			}
		}
		finally
		{
			try { if (output != null) output.close(); } catch (Exception e) {}
			try { if (input != null) input.close(); } catch (Exception e) {}
			if (connection != null) connection.disconnect();
			try { unregisterReceiver(cancelReceiver); } catch (Exception e) {}
			running = false;
		}
	}

	private void addCancelAction(Notification.Builder nb)
	{
		Intent cancelIntent = new Intent(ACTION_CANCEL);
		PendingIntent cancelPi = PendingIntent.getBroadcast(this, 0, cancelIntent, 0);
		nb.addAction(android.R.drawable.ic_menu_close_clear_cancel,
			Navit.get_text("Cancel"), cancelPi);
	}

	private Notification.Builder createNotificationBuilder()
	{
		Notification.Builder nb = new Notification.Builder(this);
		Intent intent = new Intent(this, Navit.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
		nb.setContentIntent(pi);
		return nb;
	}

	private void notifyError(NotificationManager nm, Notification.Builder nb, String message)
	{
		nb.setContentTitle(Navit.get_text("Error"))
			.setContentText(message)
			.setSmallIcon(android.R.drawable.stat_notify_error)
			.setProgress(0, 0, false)
			.setOngoing(false);
		nm.notify(NOTIFICATION_ID, nb.build());
		running = false;
	}

	private void cleanup(NotificationManager nm)
	{
		nm.cancel(NOTIFICATION_ID);
		File mapsDir = new File(Environment.getExternalStorageDirectory(), "retronavi/maps");
		File tempFile = new File(mapsDir, MAP_TEMP_FILENAME);
		if (tempFile.exists()) tempFile.delete();
		running = false;
	}
}
