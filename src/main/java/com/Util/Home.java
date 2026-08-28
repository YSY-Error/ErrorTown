package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.Difficulty;
import org.bukkit.configuration.file.YamlConfiguration;

public class Home {
   public String name;
   List<String> Members;
   List<String> OPs;
   List<String> Denys;
   boolean allowStranger;
   public int level;
   public boolean pvp;
   public boolean pickup;
   public boolean dropitem;
   public String Server;
   public boolean locktime;
   public boolean lockweather;
   public long time;
   public double X;
   public double Y;
   public double Z;
   public int flowers;
   public int Popularity;
   public List<String> Gifts;
   public List<String> Advertisement;
   public List<String> LimitBlock;
   public String icon;
   public String title;
   public List<String> description;

   @Override
   public String toString() {
      return "Home [name=" + this.name + "]";
   }

   public Home(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public String getOwner() {
      return Util.getHomeOwner(this.name);
   }

   public List<String> getGifts() {
      if (Variable.bungee) {
         try {
            return MySQL.getGift(this.name);
         } catch (IOException var3) {
            var3.printStackTrace();
            return null;
         }
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getStringList("gifts");
      }
   }

   public void setGifts(List<String> Gifts) throws IOException {
      this.Gifts = Gifts;
      if (Variable.bungee) {
         MySQL.setGifts(this.name, MySQL.getListStringSpiltByDot(Gifts));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("gifts", Gifts);
         yamlConfiguration.save(f);
      }
   }

   public List<String> getLimitBlock() {
      if (Variable.bungee) {
         return MySQL.getLimitBlock(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getStringList("limitblock");
      }
   }

   public List<String> getAdvertisement() {
      if (Variable.bungee) {
         return MySQL.getAdvertisement(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getStringList("advertisement");
      }
   }

   public void setAdvertisement(List<String> adv) throws IOException {
      this.Advertisement = adv;
      if (Variable.bungee) {
         MySQL.setAdvertisement(this.name, MySQL.getListStringSpiltByDot(this.Advertisement));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("advertisement", this.Advertisement);
         yamlConfiguration.save(f);
      }
   }

   public void setLimitBlock(List<String> adv) throws IOException {
      this.LimitBlock = adv;
      if (Variable.bungee) {
         MySQL.setLimitBlock(this.name, MySQL.getListStringSpiltByDot(this.LimitBlock));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("limitblock", this.LimitBlock);
         yamlConfiguration.save(f);
      }
   }

   public String getIcon() {
      if (Variable.bungee) {
         return MySQL.getIcon(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getString("icon");
      }
   }

   public void setIcon(String str) throws IOException {
      this.icon = str;
      if (Variable.bungee) {
         MySQL.setIcon(this.name, String.valueOf(this.icon));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("icon", this.icon);
         yamlConfiguration.save(f);
      }
   }

   public String getTitle() {
      if (Variable.bungee) {
         return MySQL.getTitle(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getString("title", "");
      }
   }

   public void setTitle(String title) throws IOException {
      this.title = title;
      if (Variable.bungee) {
         MySQL.setTitle(this.name, title);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("title", title);
         yamlConfiguration.save(f);
      }
   }

   public List<String> getDescription() {
      if (Variable.bungee) {
         return MySQL.getDescription(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getStringList("description");
      }
   }

   public void setDescription(List<String> desc) throws IOException {
      this.description = desc;
      if (Variable.bungee) {
         MySQL.setDescription(this.name, String.join("|", desc));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("description", desc);
         yamlConfiguration.save(f);
      }
   }

   public int getFlowers() {
      if (Variable.bungee) {
         return Integer.valueOf(MySQL.getFlowers(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getInt("flowers");
      }
   }

   public void setFlowers(int amount) throws IOException {
      this.flowers = amount;
      if (Variable.bungee) {
         MySQL.setFlowers(this.name, String.valueOf(this.flowers));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("flowers", this.flowers);
         yamlConfiguration.save(f);
      }
   }

   public int getPopularity() {
      if (Variable.bungee) {
         return Integer.valueOf(MySQL.getPopularity(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getInt("popularity");
      }
   }

   public void setPopularity(int amount) throws IOException {
      this.Popularity = amount;
      if (Variable.bungee) {
         MySQL.setPopularity(this.name, String.valueOf(this.Popularity));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("popularity", this.Popularity);
         yamlConfiguration.save(f);
      }
   }

   public List<String> getMembers() {
      if (Variable.bungee) {
         return MySQL.getMembers(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getStringList("Members");
      }
   }

   public void setMembers(List<String> members) throws IOException {
      this.Members = members;
      if (Variable.bungee) {
         MySQL.setMembers(this.name, MySQL.getListStringSpiltByDot(this.Members));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("Members", this.Members);
         yamlConfiguration.save(f);
      }
   }

   public List<String> getOPs() {
      if (Variable.bungee) {
         return MySQL.getOP(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getStringList("OP");
      }
   }

   public void setOPs(List<String> oPs) throws IOException {
      this.OPs = oPs;
      if (Variable.bungee) {
         MySQL.setOP(this.name, MySQL.getListStringSpiltByDot(this.OPs));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("OP", this.OPs);
         yamlConfiguration.save(f);
      }
   }

   public int getExtraMemberSlots() {
      if (Variable.bungee) {
         return 0;
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return Math.max(0, yamlConfiguration.getInt("extraMemberSlots", 0));
      }
   }

   public void setExtraMemberSlots(int amount) throws IOException {
      if (!Variable.bungee) {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("extraMemberSlots", Math.max(0, amount));
         yamlConfiguration.save(f);
      }
   }

   public int getExtraOpSlots() {
      if (Variable.bungee) {
         return 0;
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return Math.max(0, yamlConfiguration.getInt("extraOpSlots", 0));
      }
   }

   public void setExtraOpSlots(int amount) throws IOException {
      if (!Variable.bungee) {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("extraOpSlots", Math.max(0, amount));
         yamlConfiguration.save(f);
      }
   }

   public List<String> getDenys() {
      if (Variable.bungee) {
         return MySQL.getDenys(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getStringList("Denys");
      }
   }

   public void setDenys(List<String> denys) throws IOException {
      this.Denys = denys;
      if (Variable.bungee) {
         MySQL.setDenys(this.name, MySQL.getListStringSpiltByDot(this.Denys));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("Denys", this.Denys);
         yamlConfiguration.save(f);
      }
   }

   public boolean isAllowStranger() {
      if (Variable.bungee) {
         return Boolean.valueOf(MySQL.getPublic(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getBoolean("Public");
      }
   }

   public void setAllowStranger(boolean allowStranger) throws IOException {
      this.allowStranger = allowStranger;
      if (Variable.bungee) {
         MySQL.setPublic(this.name, String.valueOf(this.allowStranger));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("Public", this.allowStranger);
         yamlConfiguration.save(f);
      }
   }

   public int getLevel() {
      if (Variable.bungee) {
         return Integer.valueOf(MySQL.getLevel(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getInt("Level");
      }
   }

   public void setLevel(int level) throws IOException {
      this.level = level;
      if (Variable.bungee) {
         MySQL.setLevel(this.name, String.valueOf(this.level));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("Level", this.level);
         yamlConfiguration.save(f);
      }
   }

   public boolean isPvp() {
      if (Variable.bungee) {
         return Boolean.valueOf(MySQL.getPVP(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getBoolean("PVP");
      }
   }

   public void setPvp(boolean pvp) throws IOException {
      this.pvp = pvp;
      if (Variable.bungee) {
         MySQL.setLevel(this.name, String.valueOf(this.pvp));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("pvp", this.pvp);
         yamlConfiguration.save(f);
      }
   }

   public boolean isPickup() {
      if (Variable.bungee) {
         return Boolean.valueOf(MySQL.getpickup(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getBoolean("pickup");
      }
   }

   public void setPickup(boolean pickup) throws IOException {
      this.pickup = pickup;
      if (Variable.bungee) {
         MySQL.setpickup(this.name, String.valueOf(this.pickup));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("pickup", this.pickup);
         yamlConfiguration.save(f);
      }
   }

   public boolean isDropitem() {
      if (Variable.bungee) {
         return Boolean.valueOf(MySQL.getdropitem(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getBoolean("drop");
      }
   }

   public void setDropitem(boolean dropitem) throws IOException {
      this.dropitem = dropitem;
      if (Variable.bungee) {
         MySQL.setLevel(this.name, String.valueOf(this.dropitem));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("drop", this.dropitem);
         yamlConfiguration.save(f);
      }
   }

   public String getServer() {
      if (Variable.bungee) {
         return MySQL.getServer(this.name);
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getString("Server");
      }
   }

   public boolean isLocktime() {
      if (Variable.bungee) {
         return Boolean.valueOf(MySQL.getlocktime(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getBoolean("locktime");
      }
   }

   public void setLocktime(boolean locktime) throws IOException {
      this.locktime = locktime;
      if (Variable.bungee) {
         MySQL.setlocktime(this.name, String.valueOf(this.locktime));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("locktime", this.locktime);
         yamlConfiguration.save(f);
      }
   }

   public boolean isLockweather() {
      if (Variable.bungee) {
         return Boolean.valueOf(MySQL.getlockweather(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getBoolean("lockweather");
      }
   }

   public void setLockweather(boolean lockweather) throws IOException {
      this.lockweather = lockweather;
      if (Variable.bungee) {
         MySQL.setlockweather(this.name, String.valueOf(this.lockweather));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("lockweather", this.lockweather);
         yamlConfiguration.save(f);
      }
   }

   public long getTime() {
      if (Variable.bungee) {
         return Long.valueOf(MySQL.gettime(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getLong("time");
      }
   }

   public void setTime(long time) throws IOException {
      this.time = time;
      if (Variable.bungee) {
         MySQL.settime(this.name, String.valueOf(this.time));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("time", this.time);
         yamlConfiguration.save(f);
      }
   }

   public double getX() {
      if (Variable.bungee) {
         return Double.valueOf(MySQL.getX(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getDouble("X");
      }
   }

   public void setX(double x) throws IOException {
      this.X = x;
      if (Variable.bungee) {
         MySQL.setX(this.name, String.valueOf(this.X));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("X", this.X);
         yamlConfiguration.save(f);
      }
   }

   public double getY() {
      if (Variable.bungee) {
         return Double.valueOf(MySQL.getY(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getDouble("Y");
      }
   }

   public void setY(double y) throws IOException {
      this.Y = y;
      if (Variable.bungee) {
         MySQL.setY(this.name, String.valueOf(this.Y));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("Y", this.Y);
         yamlConfiguration.save(f);
      }
   }

   public double getZ() {
      if (Variable.bungee) {
         return Double.valueOf(MySQL.getZ(this.name));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         return yamlConfiguration.getDouble("Z");
      }
   }

   public void setZ(double z) throws IOException {
      this.Z = z;
      if (Variable.bungee) {
         MySQL.setZ(this.name, String.valueOf(this.Z));
      } else {
         File f = new File(Variable.Tempf, this.name + ".yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
         yamlConfiguration.set("Z", this.Z);
         yamlConfiguration.save(f);
      }
   }

   public boolean getRuleExplosionProtect() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.explosionProtect", false);
   }

   public void setRuleExplosionProtect(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.explosionProtect", value);
      yml.save(f);
   }

   public boolean getRuleNoFireSpread() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.noFireSpread", false);
   }

   public void setRuleNoFireSpread(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.noFireSpread", value);
      yml.save(f);
   }

   public boolean getRuleNoMobSpawn() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.noMobSpawn", false);
   }

   public void setRuleNoMobSpawn(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.noMobSpawn", value);
      yml.set("rules.allowHostileMobs", !value);
      yml.set("rules.allowPassiveMobs", !value);
      yml.save(f);
   }

   public boolean getRuleMobGriefingEnabled() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.mobGriefing", Main.JavaPlugin.getConfig().getBoolean("mobGriefing"));
   }

   public void setRuleMobGriefingEnabled(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.mobGriefing", value);
      yml.save(f);
   }

   public boolean getRuleAllowHostileMobs() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.allowHostileMobs", !this.getRuleNoMobSpawn());
   }

   public void setRuleAllowHostileMobs(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.allowHostileMobs", value);
      boolean passive = yml.getBoolean("rules.allowPassiveMobs", true);
      yml.set("rules.noMobSpawn", !value && !passive);
      yml.save(f);
   }

   public boolean getRuleAllowPassiveMobs() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.allowPassiveMobs", true);
   }

   public void setRuleAllowPassiveMobs(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.allowPassiveMobs", value);
      boolean hostile = yml.getBoolean("rules.allowHostileMobs", !yml.getBoolean("rules.noMobSpawn", false));
      yml.set("rules.noMobSpawn", !hostile && !value);
      yml.save(f);
   }

   public boolean isNaturalMobSpawningEnabled() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return !yml.contains("rules.allowHostileMobs") && !yml.contains("rules.allowPassiveMobs")
         ? !yml.getBoolean("rules.noMobSpawn", false)
         : yml.getBoolean("rules.allowHostileMobs", !this.getRuleNoMobSpawn()) || yml.getBoolean("rules.allowPassiveMobs", true);
   }

   public Difficulty getRuleDifficulty() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      if (!yml.contains("rules.difficulty")) {
         return null;
      } else {
         String raw = yml.getString("rules.difficulty");
         if (raw == null || raw.trim().isEmpty()) {
            return null;
         } else if (raw.equalsIgnoreCase("peaceful")) {
            return Difficulty.PEACEFUL;
         } else if (raw.equalsIgnoreCase("normal")) {
            return Difficulty.NORMAL;
         } else if (raw.equalsIgnoreCase("hard")) {
            return Difficulty.HARD;
         } else {
            return raw.equalsIgnoreCase("easy") ? Difficulty.EASY : null;
         }
      }
   }

   public void setRuleDifficulty(Difficulty difficulty) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.difficulty", difficulty == null ? "Easy" : difficulty.name());
      yml.save(f);
   }

   public boolean getRuleAllowSpawnerSpawn() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.allowSpawnerSpawn", true);
   }

   public void setRuleAllowSpawnerSpawn(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.allowSpawnerSpawn", value);
      yml.save(f);
   }

   public boolean getRuleAllowAnimalBreed() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.allowAnimalBreed", true);
   }

   public void setRuleAllowAnimalBreed(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.allowAnimalBreed", value);
      yml.save(f);
   }

   public boolean getRuleAllowMobFarm() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getBoolean("rules.allowMobFarm", true);
   }

   public void setRuleAllowMobFarm(boolean value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.allowMobFarm", value);
      yml.save(f);
   }

   public int getRuleMaxMobCount() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getInt("rules.maxMobCount", Main.JavaPlugin.getConfig().getInt("HomeRulesDefaults.MaxMobCount", 48));
   }

   public void setRuleMaxMobCount(int value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("rules.maxMobCount", value);
      yml.save(f);
   }

   public long getLastActive() {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      return yml.getLong("stats.lastActive", 0L);
   }

   public void setLastActive(long value) throws IOException {
      File f = new File(Variable.Tempf, this.name + ".yml");
      YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
      yml.set("stats.lastActive", value);
      yml.save(f);
   }
}
