package me.eccentric_nz.dynmaptardis;

import me.eccentric_nz.TARDIS.TARDIS;
import me.eccentric_nz.TARDIS.api.TARDISData;
import me.eccentric_nz.TARDIS.api.TardisAPI;
import org.bukkit.Bukkit;
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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class DynmapTARDIS extends JavaPlugin {

    private static final String INFO = "<div class=\"regioninfo\"><div class=\"infowindow\"><span style=\"font-weight:bold;\">Time Lord:</span> %owner%<br/><span style=\"font-weight:bold;\">Console type:</span> %console%<br/><span style=\"font-weight:bold;\">Chameleon circuit:</span> %chameleon%<br/><span style=\"font-weight:bold;\">Location:</span> %location%<br/><span style=\"font-weight:bold;\">Door:</span> %door%<br/><span style=\"font-weight:bold;\">Powered on:</span> %powered%<br/><span style=\"font-weight:bold;\">Siege mode:</span> %siege%<br/><span style=\"font-weight:bold;\">Occupants:</span> %occupants%</div></div>";
    private Plugin dynmap;
    private DynmapAPI api;
    private MarkerAPI markerapi;
    private TardisAPI tardisapi;
    private TARDIS tardis;
    private boolean reload = false;
    private boolean stop;
    private Layer tardisLayer;

    @Override
    public void onDisable() {
        if (tardisLayer != null) {
            tardisLayer.cleanup();
            tardisLayer = null;
        }
        stop = true;
    }

    @Override
    public void onEnable() {
        PluginManager pm = getServer().getPluginManager();
        /*
         * Get dynmap
         */
        dynmap = pm.getPlugin("dynmap");
        if (dynmap == null) {
            Bukkit.getLogger().log(Level.WARNING, "Cannot find dynmap!");
            return;
        }
        /*
         * Get API
         */
        api = (DynmapAPI) dynmap;
        /*
         * Get tardis
         */
        Plugin p = pm.getPlugin("TARDIS");
        if (p == null) {
            Bukkit.getLogger().log(Level.WARNING, "Cannot find TARDIS!");
            return;
        }
        tardis = (TARDIS) p;
        getServer().getPluginManager().registerEvents(new TARDISServerListener(), this);
        /*
         * If both enabled, activate
         */
        if (dynmap.isEnabled() && tardis.isEnabled()) {
            activate();
        }
    }

    private void activate() {
        /*
         * Now, get markers API
         */
        markerapi = api.getMarkerAPI();
        if (markerapi == null) {
            Bukkit.getLogger().log(Level.WARNING, "Error loading Dynmap marker API!");
            return;
        }
        /*
         * Now, get tardis API
         */
        tardisapi = tardis.getTardisAPI();

        /*
         * If not found, signal disabled
         */
        if (tardisapi == null) {
            Bukkit.getLogger().log(Level.WARNING, "TARDIS not found - support disabled");
        }
        /*
         * Load configuration
         */
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
        /*
         * Now, add marker set for TardisAPI
         */
        if (tardisapi != null) {
            tardisLayer = new TARDISLayer();
        }
        /*
         * Set up update job - based on period
         */
        stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 5 * 20);
        //Bukkit.getLogger().log(Level.INFO, "version " + this.getDescription().getVersion() + " is activated");
    }

    private void updateMarkers() {
        if (tardisapi != null) {
            tardisLayer.updateMarkerSet();
        }
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 100L);
    }

    private String formatInfoWindow(String who, TARDISData data, Marker m) {
        String window = INFO;
        window = window.replace("%owner%", who);
        window = window.replace("%console%", data.getConsole());
        window = window.replace("%chameleon%", data.getChameleon());
        String l = "x: " + data.getLocation().getBlockX() + ", y: " + data.getLocation().getBlockY() + ", z: " + data.getLocation().getBlockZ();
        window = window.replace("%location%", l);
        window = window.replace("%powered%", data.getPowered());
        window = window.replace("%door%", data.getDoor());
        window = window.replace("%siege%", data.getSiege());
        String travellers = "";
        if (data.getOccupants().size() > 0) {
            for (String o : data.getOccupants()) {
                travellers += o + "<br />";
            }
        } else {
            travellers = "Empty";
        }
        window = window.replace("%occupants%", travellers);
        return window;
    }

    private abstract class Layer {

        MarkerSet set;
        MarkerIcon deficon;
        String labelfmt;
        Map<String, Marker> markers = new HashMap<>();

        public Layer() {
            set = markerapi.getMarkerSet("tardis");
            if (set == null) {
                set = markerapi.createMarkerSet("tardis", "TARDISes", null, false);
            } else {
                set.setMarkerSetLabel("TARDISes");
            }
            if (set == null) {
                Bukkit.getLogger().log(Level.WARNING, "Error creating tardis marker set");
                return;
            }
            set.setLayerPriority(0);
            set.setHideByDefault(false);
            deficon = markerapi.getMarkerIcon("tardis");
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
            Map<String, Marker> newmap = new HashMap<>();
            /*
             * Build new map
             */
            Map<String, TARDISData> marks = getMarkers();
            marks.keySet().forEach((name) -> {
                Location loc = marks.get(name).getLocation();
                String wname = loc.getWorld().getName();
                /*
                 * Get location
                 */
                String id = wname + "/" + name;
                String label = labelfmt.replace("%name%", name);
                /*
                 * See if we already have marker
                 */
                Marker m = markers.remove(id);
                if (m == null) {
                    /*
                     * Not found? Need new one
                     */
                    m = set.createMarker(id, label, wname, loc.getX(), loc.getY(), loc.getZ(), deficon, false);
                } else {
                    /*
                     * Else, update position if needed
                     */
                    m.setLocation(wname, loc.getX(), loc.getY(), loc.getZ());
                    m.setLabel(label);
                    m.setMarkerIcon(deficon);
                }
                /*
                 * Build popup
                 */
                String desc = formatInfoWindow(name, marks.get(name), m);
                /*
                 * Set popup
                 */
                m.setDescription(desc);
                /*
                 * Add to new map
                 */
                newmap.put(id, m);
            });
            /*
             * Now, review old map - anything left is gone
             */
            markers.values().forEach((oldm) -> {
                oldm.deleteMarker();
            });
            /*
             * And replace with new map
             */
            markers.clear();
            markers = newmap;
        }

        /*
         * Get current markers, by ID with location
         */
        public abstract Map<String, TARDISData> getMarkers();
    }

    private class TARDISServerListener implements Listener {

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

    private class TARDISLayer extends Layer {

        public TARDISLayer() {
            super();
        }

        /*
         * Get current markers, by timelord with location
         */
        @Override
        public Map<String, TARDISData> getMarkers() {
            HashMap<String, TARDISData> map = new HashMap<>();
            if (tardisapi != null) {
                HashMap<String, Integer> tl = tardisapi.getTimelordMap();
                tl.entrySet().forEach((lords) -> {
                    TARDISData data;
                    try {
                        data = tardisapi.getTARDISMapData(lords.getValue());
                        if (data.getLocation() != null) {
                            map.put(lords.getKey(), data);
                        }
                    } catch (Exception x) {
                    }
                });
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
}
