package me.eccentric_nz.dynmaptardis;

import java.util.HashMap;
import java.util.Map;
import me.eccentric_nz.TARDIS.TARDIS;
import me.eccentric_nz.TARDIS.api.TardisAPI;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class DynmapTARDIS extends JavaPlugin {

    public String pluginName;
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    TardisAPI tardisapi;
    TARDIS tardis;
    private Layer tardisLayer;
    boolean reload = false;
    boolean stop;

    @Override
    public void onDisable() {
        if (tardisLayer != null) {
            tardisLayer.cleanup();
            tardisLayer = null;
        }
        stop = true;
    }

    private abstract class Layer {

        MarkerSet set;
        MarkerIcon deficon;
        String labelfmt;
        Map<String, Marker> markers = new HashMap<String, Marker>();

        public Layer() {
            set = markerapi.getMarkerSet("tardis");
            if (set == null) {
                set = markerapi.createMarkerSet("tardis", "TARDISes", null, false);
            } else {
                set.setMarkerSetLabel("TARDISes");
            }
            if (set == null) {
                System.err.println("Error creating tardis marker set");
                return;
            }
            set.setLayerPriority(0);
            set.setHideByDefault(false);
            this.deficon = markerapi.getMarkerIcon("tardis");
            labelfmt = "%name% (TARDIS)";
        }

        void cleanup() {
            if (set != null) {
                set.deleteMarkerSet();
                set = null;
            }
            markers.clear();
        }

        void updateMarkerSet() {
            Map<String, Marker> newmap = new HashMap<String, Marker>(); /* Build new map */

            Map<String, Location> marks = getMarkers();
            for (String name : marks.keySet()) {
                Location loc = marks.get(name);
                String wname = loc.getWorld().getName();
                /* Get location */
                String id = wname + "/" + name;
                String label = labelfmt.replace("%name%", name);
                /* See if we already have marker */
                Marker m = markers.remove(id);
                if (m == null) {
                    /* Not found?  Need new one */
                    m = set.createMarker(id, label, wname, loc.getX(), loc.getY(), loc.getZ(), deficon, false);
                } else {
                    /* Else, update position if needed */
                    m.setLocation(wname, loc.getX(), loc.getY(), loc.getZ());
                    m.setLabel(label);
                    m.setMarkerIcon(deficon);
                }
                /* Add to new map */
                newmap.put(id, m);
            }
            /* Now, review old map - anything left is gone */
            for (Marker oldm : markers.values()) {
                oldm.deleteMarker();
            }
            /* And replace with new map */
            markers.clear();
            markers = newmap;
        }
        /* Get current markers, by ID with location */

        public abstract Map<String, Location> getMarkers();
    }

    @Override
    public void onEnable() {
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if (dynmap == null) {
            System.err.println("Cannot find dynmap!");
            return;
        }
        /* Get API */
        api = (DynmapAPI) dynmap;
        /* Get tardis */
        Plugin p = pm.getPlugin("TARDIS");
        if (p == null) {
            System.err.println("Cannot find TARDIS!");
            return;
        }
        tardis = (TARDIS) p;
        getServer().getPluginManager().registerEvents(new OurServerListener(), this);
        /* If both enabled, activate */
        if (dynmap.isEnabled() && tardis.isEnabled()) {
            activate();
        }
    }

    private class OurServerListener implements Listener {

        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if (name.equals("dynmap") || name.equals("TARDIS")) {
                if (dynmap.isEnabled() && tardis.isEnabled()) {
                    activate();
                }
            }
        }
    }

    private void activate() {
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if (markerapi == null) {
            System.err.println("Error loading Dynmap marker API!");
            return;
        }
        /* Now, get tardis API */
        tardisapi = tardis.getTardisAPI();

        /* If not found, signal disabled */
        if (tardisapi == null) {
            System.out.println("TARDIS not found - support disabled");
        }
        /* Load configuration */
        if (reload) {
            if (tardisLayer != null) {
                if (tardisLayer.set != null) {
                    tardisLayer.set.deleteMarkerSet();
                    tardisLayer.set = null;
                }
                tardisLayer = null;
            }
        } else {
            reload = true;
        }
        /* Now, add marker set for TardisAPI */
        if (tardisapi != null) {
            tardisLayer = new TARDISLayer();
        }
        /* Set up update job - based on period */
        stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 5 * 20);
        //System.out.println("version " + this.getDescription().getVersion() + " is activated");
    }

    private class TARDISLayer extends Layer {

        public TARDISLayer() {
            super();
        }
        /* Get current markers, by timelord with location */

        @Override
        public Map<String, Location> getMarkers() {
            HashMap<String, Location> map = new HashMap<String, Location>();
            if (tardisapi != null) {
                HashMap<String, Integer> tl = tardisapi.getTimelordMap();
                for (Map.Entry<String, Integer> lords : tl.entrySet()) {
                    Location loc;
                    try {
                        loc = tardisapi.getTARDISCurrentLocation(lords.getValue());
                        if (loc != null) {
                            map.put(lords.getKey(), loc);
                        }
                    } catch (Exception x) {
                    }
                }
            }
            return map;
        }
    }

    private class MarkerUpdate implements Runnable {

        @Override
        public void run() {
            if (!stop) {
                updateMarkers();
            }
        }
    }

    private void updateMarkers() {
        if (tardisapi != null) {
            tardisLayer.updateMarkerSet();
        }
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 100L);
    }
}
