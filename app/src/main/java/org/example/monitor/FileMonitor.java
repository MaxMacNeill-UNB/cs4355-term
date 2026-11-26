package org.example.monitor;

import org.example.config.MonitorTarget;
import org.example.config.PageTarget;
import org.example.plugin.PluginRegistry;
import org.example.plugin.hash.HashPlugin;
import org.example.plugin.source.SourcePlugin;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * checks a target, finds its files, hashes them, compares with the saved hashes,
 * raises alerts for anything new/changed/deleted, then saves the new hashes.
 * TODO: Add method to save new defaults/keep defaults
 */
public class FileMonitor{

    private final PluginRegistry registry;
    private final HashStore store;
    private final AlertLogger alerts;

    public FileMonitor(PluginRegistry registry, HashStore store, AlertLogger alerts){
        this.registry = registry;
        this.store = store;
        this.alerts = alerts;
    }

    /** @return number of alerts raised */
    public int check(MonitorTarget target) throws IOException {
        String id = target.getId() != null ? target.getId() : target.getPath();
        SourcePlugin source = registry.selectSourcePlugin(target);
        HashPlugin hasher = registry.selectHashPlugin(target);

        int depth = 0;
        List<String> domains = List.of();
        if (target instanceof PageTarget page) {
            depth = page.getCrawlSettings().getDepth();
            domains = page.getCrawlSettings().getAllowedDomains();
        }

        //hashes everything
        Map<String, String> now = new TreeMap<>();
        for (String uri : source.discover(target.getPath(), depth, domains, target.getMonitoredExtensions())){
            now.put(uri, hasher.hash(source.fetch(uri)));
        }

        //compare with last saved hashes
        Map<String, String> saved = store.load(id);
        int changes = 0;

        if (saved == null){
            System.out.println("[" + id + "] Baseline saved for " + now.size() + " files.");
        } 
        else{
            for (Map.Entry<String, String> f : now.entrySet()){
                String old = saved.get(f.getKey());
                if (old == null){
                    alerts.alert(id, "NEW:", f.getKey());
                    changes++;
                } 
                else if (!old.equals(f.getValue())){
                    alerts.alert(id, "CHANGED:", f.getKey());
                    changes++;
                }
            }
            for (String uri : saved.keySet()){
                if (!now.containsKey(uri)){
                    alerts.alert(id, "DELETED:", uri);
                    changes++;
                }
            }
            if (changes == 0){
                System.out.println("[" + id + "] No changes.");
            }
        }

        store.save(id, now);
        return changes;
    }
}