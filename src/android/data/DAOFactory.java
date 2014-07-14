package com.Dolphin.BgLocation.data;

import android.content.Context;

import com.Dolphin.BgLocation.data.sqlite.SQLiteLocationDAO;

public abstract class DAOFactory {
	public static LocationDAO createLocationDAO(Context context) {
		//Very basic for now
		return new SQLiteLocationDAO(context);
	}
}
