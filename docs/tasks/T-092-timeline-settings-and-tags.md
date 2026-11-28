# T-092 — Settings -> Timeline: hide screenshots/documents & manage Immich tags

**Status:** done (`ad49423`)  
**Component:** Android (`:feature:timeline`, `:app`)  
**Depends on:** T-088, T-090

## Summary

1. Move **"Hide screenshots & documents"** off the Photos timeline header into **Settings -> Timeline** (`TimelineSettingsScreen`), keeping the timeline uncluttered (only showing the `"Clean up (N)"` chip when cleanup candidates exist).
2. Add **Search tags** management on the same `Settings -> Timeline` screen: lists every tag in the user's Immich library (`GET /api/tags`) with a delete action (`DELETE /api/tags/{id}` after confirmation, photos stay) so unwanted imported/auto tags (e.g. `cocktails`) can be removed directly from Picfort.
