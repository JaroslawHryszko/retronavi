package com.zoffcc.applications.zanavi;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.method.ScrollingMovementMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Simple log viewer - shows contents of /sdcard/retronavi/retronavi.log
 * White background, black monospace text at 8pt, scrolled to bottom.
 * Reads only last 100KB to avoid OOM on low-memory devices.
 * Called from overflow menu in Navit.java ("Logi aplikacji").
 */
public class RetroNaviLogViewerActivity extends Activity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		ScrollView scrollView = new ScrollView(this);
		scrollView.setFillViewport(true);
		scrollView.setBackgroundColor(Color.WHITE);

		TextView textView = new TextView(this);
		textView.setTypeface(Typeface.MONOSPACE);
		textView.setTextSize(8f);
		textView.setTextColor(Color.BLACK);
		textView.setPadding(8, 8, 8, 8);
		textView.setGravity(Gravity.TOP | Gravity.LEFT);

		String logContent = readLogFile();
		if (logContent.length() == 0)
		{
			textView.setText("Brak logow.\n\nPlik: " + RetroNaviLogger.getLogFilePath());
		}
		else
		{
			textView.setText(logContent);
		}

		scrollView.addView(textView);
		setContentView(scrollView);

		setTitle("RetroNavi Logi");

		final ScrollView sv = scrollView;
		scrollView.post(new Runnable()
		{
			public void run()
			{
				sv.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});
	}

	private String readLogFile()
	{
		StringBuilder sb = new StringBuilder();
		try
		{
			File logFile = RetroNaviLogger.getLogFile();
			if (logFile == null || !logFile.exists())
			{
				return "";
			}

			// Read last ~100KB to avoid OOM on old devices
			long skipBytes = 0;
			if (logFile.length() > 100 * 1024)
			{
				skipBytes = logFile.length() - 100 * 1024;
				sb.append("... (pominieto starsze wpisy) ...\n\n");
			}

			BufferedReader reader = new BufferedReader(new FileReader(logFile));
			if (skipBytes > 0)
			{
				reader.skip(skipBytes);
				reader.readLine(); // skip partial line
			}

			String line;
			while ((line = reader.readLine()) != null)
			{
				sb.append(line).append("\n");
			}
			reader.close();
		}
		catch (Exception e)
		{
			sb.append("Blad odczytu: ").append(e.getMessage());
		}
		return sb.toString();
	}
}
