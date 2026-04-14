package com.zoffcc.applications.zanavi;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Simple file logger for RetroNavi.
 * Writes timestamped log entries to /sdcard/retronavi/retronavi.log
 * Old log is rotated when it exceeds MAX_LOG_SIZE_BYTES.
 */
public class RetroNaviLogger
{
	private static final String TAG = "RetroNaviLogger";
	private static final String LOG_DIR = "retronavi";
	private static final String LOG_FILE = "retronavi.log";
	private static final String LOG_FILE_OLD = "retronavi.log.old";
	private static final long MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

	private static File logFile;
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
	private static boolean initialized = false;

	public static synchronized void init()
	{
		if (initialized) return;

		try
		{
			File dir = new File(Environment.getExternalStorageDirectory(), LOG_DIR);
			if (!dir.exists()) dir.mkdirs();

			logFile = new File(dir, LOG_FILE);
			initialized = true;

			log("I", TAG, "=== RetroNavi Logger started ===");
			log("I", TAG, "Version: " + getVersionInfo());
			log("I", TAG, "Android API: " + android.os.Build.VERSION.SDK_INT);
			log("I", TAG, "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
		}
		catch (Exception e)
		{
			Log.e(TAG, "Failed to init logger", e);
		}
	}

	public static synchronized void log(String level, String tag, String message)
	{
		if (!initialized || logFile == null) return;

		try
		{
			rotateIfNeeded();

			String timestamp = sdf.format(new Date());
			String line = timestamp + " " + level + "/" + tag + ": " + message + "\n";

			PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
			pw.write(line);
			pw.flush();
			pw.close();
		}
		catch (Exception e)
		{
			// silent - don't recurse
		}
	}

	public static void i(String tag, String message)
	{
		Log.i(tag, message);
		log("I", tag, message);
	}

	public static void e(String tag, String message)
	{
		Log.e(tag, message);
		log("E", tag, message);
	}

	public static void e(String tag, String message, Throwable t)
	{
		Log.e(tag, message, t);
		log("E", tag, message + " | " + t.toString());
	}

	public static void w(String tag, String message)
	{
		Log.w(tag, message);
		log("W", tag, message);
	}

	public static void d(String tag, String message)
	{
		Log.d(tag, message);
		log("D", tag, message);
	}

	public static File getLogFile()
	{
		return logFile;
	}

	public static String getLogFilePath()
	{
		if (logFile != null) return logFile.getAbsolutePath();
		return Environment.getExternalStorageDirectory() + "/" + LOG_DIR + "/" + LOG_FILE;
	}

	private static void rotateIfNeeded()
	{
		try
		{
			if (logFile != null && logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES)
			{
				File oldFile = new File(logFile.getParent(), LOG_FILE_OLD);
				if (oldFile.exists()) oldFile.delete();
				logFile.renameTo(oldFile);
				// logFile reference stays the same - new file will be created on next write
			}
		}
		catch (Exception e)
		{
			// silent
		}
	}

	private static String getVersionInfo()
	{
		try
		{
			return "versionCode=" + Navit.VERSION_CODE_FOR_LOG + " versionName=" + Navit.VERSION_NAME_FOR_LOG;
		}
		catch (Exception e)
		{
			return "unknown";
		}
	}
}
