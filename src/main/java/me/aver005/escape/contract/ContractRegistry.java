package me.aver005.escape.contract;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Глобальный реестр контрактов (contracts.yml). */
public class ContractRegistry
{
    private final JavaPlugin plugin;
    private final Map<String, Contract> contracts = new LinkedHashMap<>();

    public ContractRegistry(JavaPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "contracts.yml");}

    public void load()
    {
        contracts.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection root = cfg.getConfigurationSection("contracts");
        if (root == null) {return;}
        for (String id : root.getKeys(false))
        {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {continue;}
            contracts.put(id, Contract.load(id, sec));
        }
    }

    public void save()
    {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Contract contract : contracts.values())
        {
            contract.save(cfg.createSection("contracts." + contract.getId()));
        }
        try {cfg.save(file());}
        catch (IOException e) {plugin.getLogger().severe("Failed to save contracts.yml: " + e.getMessage());}
    }

    public Contract get(String id) {return contracts.get(id);}
    public boolean exists(String id) {return contracts.containsKey(id);}
    public void add(Contract contract) {contracts.put(contract.getId(), contract);}
    public Set<String> ids() {return contracts.keySet();}
}
