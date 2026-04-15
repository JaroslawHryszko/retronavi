package com.zoffcc.applications.zanavi;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Background service for downloading APK updates.
 * Shows progress in the notification bar. On completion, launches system installer.
 */
public class RetroNaviAppUpdateService extends IntentService
{
	private static final String TAG = "RetroNaviAppDL";
	private static final int NOTIFICATION_ID = 9002;
	private static final String APK_FILENAME = "retronavi-update.apk";
	private static final String APK_TEMP_FILENAME = "retronavi-update.tmp";
	private static final String ACTION_CANCEL = "com.zoffcc.applications.zanavi.CANCEL_APP_UPDATE";

	private static volatile boolean cancelRequested = false;
	private static volatile boolean running = false;
	private BroadcastReceiver cancelReceiver;

	public RetroNaviAppUpdateService()
	{
		super("RetroNaviAppUpdateService");
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

		String apkUrl = intent.getStringExtra("apk_url");
		if (apkUrl == null) return;

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

		nb.setContentTitle(Navit.get_text("Downloading update"))
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
			if (verbose) RetroNaviLogger.i(TAG, "Connecting to: " + apkUrl);

			URL url = new URL(apkUrl);
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

			File downloadDir = new File(Environment.getExternalStorageDirectory(), "retronavi");
			if (!downloadDir.exists()) downloadDir.mkdirs();

			File tempFile = new File(downloadDir, APK_TEMP_FILENAME);
			File finalFile = new File(downloadDir, APK_FILENAME);

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

					nb.setContentTitle(Navit.get_text("Downloading update") + " " + pct + "%")
						.setContentText(dlMB + " / " + fileLengthMB + " MB  (" + speedStr + ")")
						.setProgress(100, pct, false);
					nm.notify(NOTIFICATION_ID, nb.build());
				}
			}

			output.flush();
			output.close();
			output = null;

			Log.i(TAG, "Downloaded: " + totalBytesRead + " bytes");

			// Rename temp to final
			if (finalFile.exists()) finalFile.delete();
			if (!tempFile.renameTo(finalFile))
			{
				notifyError(nm, nb, Navit.get_text("Failed to save update"));
				return;
			}

			long sizeMB = totalBytesRead / 1024 / 1024;
			nb.setContentTitle(Navit.get_text("Update downloaded"))
				.setContentText(sizeMB + " MB - " + Navit.get_text("Tap to install"))
				.setSmallIcon(android.R.drawable.stat_sys_download_done)
				.setProgress(0, 0, false)
				.setOngoing(false);

			// Tap notification -> install APK
			Intent installIntent = new Intent(Intent.ACTION_VIEW);
			installIntent.setDataAndType(Uri.fromFile(finalFile), "application/vnd.android.package-archive");
			installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent installPi = PendingIntent.getActivity(this, 0, installIntent, 0);
			nb.setContentIntent(installPi);

			nm.notify(NOTIFICATION_ID, nb.build());

			// Also auto-launch installer
			try
			{
				startActivity(installIntent);
			}
			catch (Exception e)
			{
				Log.e(TAG, "Auto-launch installer failed", e);
			}
		}
		catch (Exception e)
		{
			RetroNaviLogger.i(TAG, "Download failed: " + e.toString());
			Log.e(TAG, "APK download failed", e);
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
		File downloadDir = new File(Environment.getExternalStorageDirectory(), "retronavi");
		File tempFile = new File(downloadDir, APK_TEMP_FILENAME);
		if (tempFile.exists()) tempFile.delete();
		running = false;
	}
}
