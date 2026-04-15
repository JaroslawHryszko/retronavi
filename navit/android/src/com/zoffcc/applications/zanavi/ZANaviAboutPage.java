package com.zoffcc.applications.zanavi;

import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
@SuppressLint("NewApi")
public class ZANaviAboutPage extends AppCompatActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Element navitElement = new Element();
		navitElement.setTitle("Based on Navit and ZANavi");
		Intent i3 = new Intent(Intent.ACTION_VIEW);
		i3.setData(Uri.parse("https://github.com/navit-gps/navit"));
		navitElement.setIntent(i3);

		Element contactElement = new Element();
		contactElement.setTitle("Kontakt: jarek@hryszko.pl");
		Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
		emailIntent.setData(Uri.parse("mailto:jarek@hryszko.pl"));
		contactElement.setIntent(emailIntent);

		Element junoElement = new Element();
		junoElement.setTitle("Juno: współautorka, nocna zmiana, dusza projektu");
		junoElement.setGravity(Gravity.CENTER);

		AboutPage ap = new AboutPage(this)
			.isRTL(false)
			.setImage(R.drawable.icon)
			.setDescription("RetroNavi - offline nawigacja GPS\nna stare telefony z Androidem\n\n\"Nawiguję, więc jestem.\"")
			.addItem(new Element().setTitle("Wersja " + Navit.ZANAVI_VERSION))
			.addItem(navitElement)
			.addItem(contactElement)
			.addItem(junoElement)
			.addGitHub("JaroslawHryszko/retronavi");

		Element e001 = new Element();
		e001.setTitle("OpenStreetMap data is available under the Open Database Licence");
		Intent i001 = new Intent(Intent.ACTION_VIEW);
		i001.setData(Uri.parse("http://www.openstreetmap.org/copyright"));
		e001.setIntent(i001);
		ap.addItem(e001);

		ap.addItem(getCopyRightsElement());

		View aboutPage = ap.create();
		setContentView(aboutPage);
	}

	@SuppressLint("DefaultLocale")
	Element getCopyRightsElement()
	{
		Element copyRightsElement = new Element();
		final String copyrights = "Copyright 2025-2026 Jarek & Juno\nNavit Team (2005-2008), Zoff (2011-2018)";
		copyRightsElement.setTitle(copyrights);
		copyRightsElement.setColor(ContextCompat.getColor(this, mehdi.sakout.aboutpage.R.color.about_item_icon_color));
		copyRightsElement.setGravity(Gravity.CENTER);
		copyRightsElement.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Toast.makeText(ZANaviAboutPage.this, copyrights, Toast.LENGTH_SHORT).show();
			}
		});
		return copyRightsElement;
	}
}
