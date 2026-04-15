package com.zoffcc.applications.zanavi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

/**
 * Entry point for map download. Shows confirmation dialog, then starts
 * RetroNaviMapDownloadService for background download with notification progress.
 */
public class RetroNaviMapUpdater
{
	public static final String DEFAULT_MAP_URL = "https://github.com/JaroslawHryszko/retronavi/releases/latest/download/map.bin";

	public static void startUpdate(final Context context)
	{
		if (RetroNaviMapDownloadService.isRunning())
		{
			new AlertDialog.Builder(context)
				.setTitle(Navit.get_text("Downloading map"))
				.setMessage(Navit.get_text("Map download is already in progress.\nCheck the notification bar for progress."))
				.setPositiveButton("OK", null)
				.setNegativeButton(Navit.get_text("Cancel") + " download", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						RetroNaviMapDownloadService.requestCancel();
						Toast.makeText(context, Navit.get_text("Download cancelled"), Toast.LENGTH_SHORT).show();
					}
				})
				.show();
			return;
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final String mapUrl = prefs.getString("map_download_url", DEFAULT_MAP_URL);

		new AlertDialog.Builder(context)
			.setTitle(Navit.get_text("Map update"))
			.setMessage(Navit.get_text("Download map from:") + "\n" + mapUrl + "\n\n" + Navit.get_text("Warning: all existing maps will be removed and replaced with the new one.\nThis may take a while - maps are several hundred MB.") + "\n\n" + Navit.get_text("Download runs in background - you can keep navigating."))
			.setPositiveButton(Navit.get_text("Download"), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					Intent intent = new Intent(context, RetroNaviMapDownloadService.class);
					intent.putExtra("map_url", mapUrl);
					context.startService(intent);
					Toast.makeText(context, Navit.get_text("Map download started - check notification bar"), Toast.LENGTH_LONG).show();
				}
			})
			.setNegativeButton(Navit.get_text("Cancel"), null)
			.show();
	}
}
