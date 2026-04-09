package com.adoreapps.ai.ads.model;

public class AdsResponse<T> {
    private T ads;
    private String unitID;
    private int priority;

    public AdsResponse(T ads, String unitID, int priority) {
        this.ads = ads;
        this.unitID = unitID;
        this.priority = priority;
    }

    public AdsResponse(T ads, String unitID) {
        this.ads = ads;
        this.unitID = unitID;
        this.priority = 100;
    }

    public T getAds() {
        return this.ads;
    }

    public void setAds(T ads) {
        this.ads = ads;
    }

    public String getUnitID() {
        return this.unitID;
    }

    public void setUnitID(String unitID) {
        this.unitID = unitID;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
