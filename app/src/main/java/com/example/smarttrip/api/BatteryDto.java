package com.example.smarttrip.api;

import com.google.gson.annotations.SerializedName;

/**
 * Représente l'état batterie dans le payload envoyé à l'API.
 * Correspond au champ "battery" du format JSON attendu.
 */
public class BatteryDto {

    @SerializedName("level")
    private int level;

    @SerializedName("is_charging")
    private boolean isCharging;

    public BatteryDto(int level, boolean isCharging) {
        this.level = level;
        this.isCharging = isCharging;
    }

    public int getLevel() { return level; }
    public boolean isCharging() { return isCharging; }
}
