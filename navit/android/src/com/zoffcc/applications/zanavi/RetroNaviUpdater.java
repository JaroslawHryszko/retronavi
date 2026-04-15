package com.zoffcc.applications.zanavi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

/**
 * Entry point for app update. Shows confirmation dialog, then starts
 * RetroNaviAppUpdateService for background download with notification progress.
 * Maps on SD card are NOT affected by the update - only the APK is replaced.
 */
public class RetroNaviUpdater
{
	private static final String APK_URL = "https://github.com/JaroslawHryszko/retronavi/releases/latest/download/retronavi.apk";

	public static void startUpdate(final Context context)
	{
		if (RetroNaviAppUpdateService.isRunning())
		{
			new AlertDialog.Builder(context)
				.setTitle(Navit.get_text("Downloading update"))
				.setMessage(Navit.get_text("Update download is already in progress.\nCheck the notification bar for progress."))
				.setPositiveButton("OK", null)
				.setNegativeButton(Navit.get_text("Cancel") + " download", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						RetroNaviAppUpdateService.requestCancel();
						Toast.makeText(context, Navit.get_text("Download cancelled"), Toast.LENGTH_SHORT).show();
					}
				})
				.show();
			return;
		}

		new AlertDialog.Builder(context)
			.setTitle(Navit.get_text("RetroNavi update"))
			.setMessage(Navit.get_text("Download latest version from GitHub?\n\nMaps will not be removed.") + "\n\n" + Navit.get_text("Download runs in background - you can keep navigating."))
			.setPositiveButton(Navit.get_text("Download"), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					Intent intent = new Intent(context, RetroNaviAppUpdateService.class);
					intent.putExtra("apk_url", APK_URL);
					context.startService(intent);
					Toast.makeText(context, Navit.get_text("Update download started - check notification bar"), Toast.LENGTH_LONG).show();
				}
			})
			.setNegativeButton(Navit.get_text("Cancel"), null)
			.show();
	}
}
