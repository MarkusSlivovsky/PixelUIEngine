package org.mslivo.core.engine.tools.settings;

import org.mslivo.core.engine.tools.Tools;
import org.mslivo.core.engine.tools.particles.particle.ParticleType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Properties;


public class SettingsManager {

    private Properties properties;

    private Properties backUp;

    private Path settingsFile;

    private final HashMap<String, SettingsEntry> entries;

    public SettingsManager(Path settingsFile) throws SettingsException {
        this.entries = new HashMap<>();
        this.properties = new Properties();
        this.init(settingsFile);
    }

    public void init(Path settingsFile) {
        this.settingsFile = settingsFile;
        if (Files.exists(settingsFile) && Files.isRegularFile(settingsFile)) {
            try {
                properties.load(Files.newInputStream(settingsFile));
            } catch (IOException e) {
                throw new SettingsException(e);
            }
        } else {
            if (!Tools.File.makeSureDirectoryExists(settingsFile.getParent())) {
                throw new SettingsException("Can't create directory " + settingsFile.getParent().toString());
            }
        }
        validateAllProperties();
        saveToFile();
    }


    public void restoreBackup() {
        if (isBackupActive()) {
            this.properties = new Properties();
            backUp.forEach((key, value) -> this.properties.setProperty((String) key, (String) value));
            validateAllProperties();
            saveToFile();
            discardBackup();
        }
    }

    public void createBackup() {
        this.backUp = new Properties();
        for (Object propertyO : this.properties.keySet()) {
            String property = (String) propertyO;
            this.backUp.setProperty(property, this.properties.getProperty(property));
        }
    }

    public boolean doesSettingDeviateFromBackup(String name) {
        if (isBackupActive()) {
            if (this.properties.get(name) != null && this.backUp.get(name) != null) {
                return !this.properties.get(name).equals(this.backUp.get(name));
            } else return this.properties.get(name) != null || this.backUp.get(name) != null;
        } else {
            return false;
        }
    }

    public boolean doesAnySettingDeviateFromBackup() {
        if (this.backUp != null) {
            return !this.properties.equals(this.backUp);
        } else {
            return false;
        }
    }

    public boolean isBackupActive() {
        return this.backUp != null;
    }

    public void discardBackup() {
        this.backUp.clear();
        this.backUp = null;
    }

    public void addSetting(String name, String defaultValue, ValidateFunction validateFunction) {
        if (entries.get(name) == null) {
            SettingsEntry settingsEntry = new SettingsEntry(name, defaultValue, validateFunction);
            this.entries.put(settingsEntry.name(), settingsEntry);
            if (this.properties.getProperty(settingsEntry.name()) == null) {
                this.properties.setProperty(settingsEntry.name(), settingsEntry.defaultValue());
            } else {
                // already loaded
                validateProperty(settingsEntry.name());
            }
            saveToFile();
        }
    }

    public void removeSettings(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) {
            this.properties.remove(settingsEntry.name());
            this.entries.remove(settingsEntry.name());
            saveToFile();
        }
    }

    public void setToDefault(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) {
            set(settingsEntry.name(), settingsEntry.defaultValue());
        }
    }

    public void setAllToDefault() {
        for (String setting : entries.keySet()) {
            setToDefault(setting);
        }
    }

    public void setFloat(String name, float intValue) {
        set(name, String.valueOf(intValue));
    }

    public void setInt(String name, int intValue) {
        set(name, String.valueOf(intValue));
    }

    public void setBoolean(String name, boolean boolValue) {
        set(name, boolValue ? "true" : "false");
    }

    public<T extends Enum<T>> void setEnum(String name, Enum<T> enumValue){
        set(name, enumValue.name());
    };


    public boolean getBoolean(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        int value = 0;
        if (settingsEntry != null) {
            return this.properties.getProperty(settingsEntry.name()).equals("true");
        }
        return false;
    }

    public float getFloat(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        float value = 0;
        if (settingsEntry != null) {
            try {
                value = Float.parseFloat(this.properties.getProperty(settingsEntry.name()));
            } catch (Exception e) {
            }
        }
        return value;
    }

    public int getInt(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        int value = 0;
        if (settingsEntry != null) {
            try {
                value = Integer.parseInt(this.properties.getProperty(settingsEntry.name()));
            } catch (Exception e) {
            }
        }
        return value;
    }

    public static boolean isValidString(String value) {
        if(value == null) return false;
        return true;
    }

    public static boolean isValidBoolean(String value) {
        if (value == null) return false;
        try {
            Boolean.parseBoolean(value);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static boolean isValidFloat(String value) {
        if (value == null) return false;
        try {
            Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static <T extends Enum<T>> boolean isValidEnum(String value, Class<T> enumClass){
        return Enum.valueOf(enumClass, value) != null;
    };

    public static boolean isValidInt(String value)  {
        if (value == null) return false;
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public String getString(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) return this.properties.getProperty(settingsEntry.name());
        return null;
    }

    public<T extends Enum<T>> T getEnum(String name, Class<T> enumClass){
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) return Enum.valueOf(enumClass, this.properties.getProperty(settingsEntry.name())) ;
        return null;
    };


    public String[] getStringList(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) return getString(name).split(";");
        return null;
    }

    public void setStringList(String name, String[] values) {
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) set(name, String.join(";", values));

    }

    private void validateAllProperties() {
        for (String name : entries.keySet()) {
            validateProperty(name);
        }
    }

    private void validateProperty(String name) {
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) {
            if (!settingsEntry.validateFunction().isValueValid(properties.getProperty(name))) {
                this.properties.setProperty(name, settingsEntry.defaultValue());
            }
        }
    }

    public void setString(String name, String value) {
        set(name, value);
    }

    private void set(String name, String value) {
        if (value == null) return;
        SettingsEntry settingsEntry = entries.get(name);
        if (settingsEntry != null) {
            String oldValue = this.properties.getProperty(settingsEntry.name());
            this.properties.setProperty(settingsEntry.name(), value);
            validateProperty(settingsEntry.name());
            if (oldValue != null && !oldValue.equals(value)) {
                saveToFile();
            }
        }
    }


    private void saveToFile() {
        try {
            this.properties.store(Files.newOutputStream(settingsFile), null);
        } catch (IOException e) {
            throw new SettingsException(e);
        }

    }

}