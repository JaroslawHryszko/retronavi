package com.zoffcc.applications.zanavi;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads latest APK from GitHub releases and triggers system installer.
 * Maps on SD card are NOT affected by the update - only the APK is replaced.
 * Called from overflow menu in Navit.java ("Pobierz aktualizacje").
 */
public class RetroNaviUpdater
{
	private static final String TAG = "RetroNaviUpdater";
	private static final String APK_URL = "https://github.com/JaroslawHryszko/retronavi/releases/latest/download/retronavi.apk";
	private static final String APK_FILENAME = "retronavi-update.apk";

	public static void startUpdate(final Context context)
	{
		new AlertDialog.Builder(context)
			.setTitle("Aktualizacja RetroNavi")
			.setMessage("Pobrac najnowsza wersje aplikacji z GitHub?\n\nMapy nie zostana usuniete.")
			.setPositiveButton("Pobierz", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					new DownloadAndInstallTask(context).execute(APK_URL);
				}
			})
			.setNegativeButton("Anuluj", null)
			.show();
	}

	private static class DownloadAndInstallTask extends AsyncTask<String, Integer, File>
	{
		private Context context;
		private ProgressDialog progressDialog;
		private String errorMessage = null;

		DownloadAndInstallTask(Context context)
		{
			this.context = context;
		}

		@Override
		protected void onPreExecute()
		{
			progressDialog = new ProgressDialog(context);
			progressDialog.setTitle("Pobieranie aktualizacji");
			progressDialog.setMessage("Laczenie z GitHub...");
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMax(100);
			progressDialog.setCancelable(false);
			progressDialog.show();
		}

		@Override
		protected File doInBackground(String... urls)
		{
			HttpURLConnection connection = null;
			InputStream input = null;
			FileOutputStream output = null;

			try
			{
				URL url = new URL(urls[0]);
				connection = (HttpURLConnection) url.openConnection();
				connection.setInstanceFollowRedirects(true);
				connection.setConnectTimeout(15000);
				connection.setReadTimeout(30000);
				connection.connect();

				// GitHub redirects, follow manually if needed
				int responseCode = connection.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
					|| responseCode == HttpURLConnection.HTTP_MOVED_PERM
					|| responseCode == 307 || responseCode == 308)
				{
					String redirectUrl = connection.getHeaderField("Location");
					connection.disconnect();
					connection = (HttpURLConnection) new URL(redirectUrl).openConnection();
					connection.setInstanceFollowRedirects(true);
					connection.setConnectTimeout(15000);
					connection.setReadTimeout(30000);
					connection.connect();
					responseCode = connection.getResponseCode();
				}

				if (responseCode != HttpURLConnection.HTTP_OK)
				{
					errorMessage = "Blad serwera: HTTP " + responseCode;
					return null;
				}

				int fileLength = connection.getContentLength();
				File downloadDir = new File(Environment.getExternalStorageDirectory(), "retronavi");
				if (!downloadDir.exists())
				{
					downloadDir.mkdirs();
				}
				File outputFile = new File(downloadDir, APK_FILENAME);

				input = connection.getInputStream();
				output = new FileOutputStream(outputFile);

				byte[] buffer = new byte[8192];
				long totalBytesRead = 0;
				int bytesRead;

				while ((bytesRead = input.read(buffer)) != -1)
				{
					output.write(buffer, 0, bytesRead);
					totalBytesRead += bytesRead;
					if (fileLength > 0)
					{
						publishProgress((int) (totalBytesRead * 100 / fileLength));
					}
				}

				output.flush();
				Log.e(TAG, "APK downloaded: " + outputFile.getAbsolutePath() + " size=" + totalBytesRead);
				return outputFile;
			}
			catch (Exception e)
			{
				Log.e(TAG, "Download failed", e);
				errorMessage = "Blad pobierania: " + e.getMessage();
				return null;
			}
			finally
			{
				try { if (output != null) output.close(); } catch (Exception e) {}
				try { if (input != null) input.close(); } catch (Exception e) {}
				if (connection != null) connection.disconnect();
			}
		}

		@Override
		protected void onProgressUpdate(Integer... progress)
		{
			progressDialog.setProgress(progress[0]);
			progressDialog.setMessage("Pobrano " + progress[0] + "%");
		}

		@Override
		protected void onPostExecute(File apkFile)
		{
			if (progressDialog != null && progressDialog.isShowing())
			{
				progressDialog.dismiss();
			}

			if (apkFile != null && apkFile.exists())
			{
				Toast.makeText(context, "Pobrano. Instalowanie...", Toast.LENGTH_SHORT).show();
				installApk(context, apkFile);
			}
			else
			{
				String msg = errorMessage != null ? errorMessage : "Nie udalo sie pobrac pliku";
				new AlertDialog.Builder(context)
					.setTitle("Blad")
					.setMessage(msg)
					.setPositiveButton("OK", null)
					.show();
			}
		}
	}

	private static void installApk(Context context, File apkFile)
	{
		try
		{
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(intent);
		}
		catch (Exception e)
		{
			Log.e(TAG, "Install failed", e);
			Toast.makeText(context, "Blad instalacji: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}
}
