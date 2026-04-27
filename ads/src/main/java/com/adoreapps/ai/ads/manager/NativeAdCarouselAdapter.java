package com.adoreapps.ai.ads.manager;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adoreapps.ai.ads.core.AdsMobileAdsManager;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for a native ad carousel.
 * Each page inflates the configured ad layout and binds a {@link NativeAd} to it.
 */
public class NativeAdCarouselAdapter extends RecyclerView.Adapter<NativeAdCarouselAdapter.CarouselViewHolder> {

    /** Large "infinite" size. Starting position is the middle so the user can swipe either direction. */
    private static final int VIRTUAL_COUNT = 1_000_000;
    private static final int VIRTUAL_START = VIRTUAL_COUNT / 2;

    private final List<NativeAd> ads = new ArrayList<>();
    @LayoutRes private final int layoutId;
    private boolean circular = false;

    public NativeAdCarouselAdapter(@LayoutRes int layoutId) {
        this.layoutId = layoutId;
    }

    public void setCircular(boolean circular) {
        this.circular = circular;
        notifyDataSetChanged();
    }

    public boolean isCircular() {
        return circular;
    }

    /** Starting item position for the ViewPager2. Middle position in circular mode, 0 otherwise. */
    public int getStartPosition() {
        if (!circular || ads.isEmpty()) return 0;
        // Start at a multiple of ads.size() near the middle so setCurrentItem(startPos + 1) works
        return VIRTUAL_START - (VIRTUAL_START % ads.size());
    }

    /**
     * Replace the ads shown by this adapter. Destroys old ads.
     */
    public void setAds(List<NativeAd> newAds) {
        // Destroy old ads (except those being kept)
        for (NativeAd old : ads) {
            if (newAds == null || !newAds.contains(old)) {
                try { old.destroy(); } catch (Exception ignored) {}
            }
        }
        ads.clear();
        if (newAds != null) ads.addAll(newAds);
        notifyDataSetChanged();
    }

    public int getAdsCount() {
        return ads.size();
    }

    public void destroy() {
        for (NativeAd ad : ads) {
            try { ad.destroy(); } catch (Exception ignored) {}
        }
        ads.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        FrameLayout container = new FrameLayout(ctx);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return new CarouselViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
        if (ads.isEmpty()) return;
        int realPosition = circular ? (position % ads.size()) : position;
        if (realPosition < 0 || realPosition >= ads.size()) return;
        NativeAd ad = ads.get(realPosition);
        FrameLayout container = (FrameLayout) holder.itemView;
        container.removeAllViews();
        NativeAdView adView = AdsMobileAdsManager.getInstance()
                .inflateAndBindNativeAd(container.getContext(), ad, layoutId);
        container.addView(adView);
    }

    @Override
    public int getItemCount() {
        if (ads.isEmpty()) return 0;
        return circular ? VIRTUAL_COUNT : ads.size();
    }

    static class CarouselViewHolder extends RecyclerView.ViewHolder {
        CarouselViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
