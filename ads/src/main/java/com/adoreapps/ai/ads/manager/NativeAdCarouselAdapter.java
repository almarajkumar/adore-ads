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

    private final List<NativeAd> ads = new ArrayList<>();
    @LayoutRes private final int layoutId;

    public NativeAdCarouselAdapter(@LayoutRes int layoutId) {
        this.layoutId = layoutId;
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
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new CarouselViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
        if (position < 0 || position >= ads.size()) return;
        NativeAd ad = ads.get(position);
        FrameLayout container = (FrameLayout) holder.itemView;
        container.removeAllViews();
        NativeAdView adView = AdsMobileAdsManager.getInstance()
                .inflateAndBindNativeAd(container.getContext(), ad, layoutId);
        container.addView(adView);
    }

    @Override
    public int getItemCount() {
        return ads.size();
    }

    static class CarouselViewHolder extends RecyclerView.ViewHolder {
        CarouselViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
