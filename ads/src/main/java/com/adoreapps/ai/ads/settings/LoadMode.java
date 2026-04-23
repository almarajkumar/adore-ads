package com.adoreapps.ai.ads.settings;

/**
 * Ad loading strategy for a placement.
 *
 * <ul>
 *   <li>{@link #WATERFALL} — iterate through ad unit IDs in order (high floor → low floor → ...).
 *       If one fails, try the next. Default for all placements.</li>
 *   <li>{@link #SINGLE} — try only the first ad unit ID. If it fails, fail immediately (no waterfall).
 *       Use when a placement has only one ad unit or you want to skip waterfall cost.</li>
 * </ul>
 */
public enum LoadMode {
    WATERFALL,
    SINGLE
}
