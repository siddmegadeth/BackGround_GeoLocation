package com.Dolphin.BgLocation.data;

public interface LocationDAO {
	public Location[] getAllLocations();
	public boolean persistLocation(Location l);
	public void deleteLocation(Location l);
}
