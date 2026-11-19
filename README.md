# Instagrrm - Phase 2 (core app + gallery + storage)

Native Android (Kotlin + Jetpack Compose + Media3), for a Samsung S24 (Android 14).

## Install on your phone
1. On the phone: Settings > About phone > Software information > tap "Build number" 7 times. Then Settings > Developer options > turn on USB debugging.
2. Install Android Studio on your computer. Open this folder (Instagrrm) with File > Open. Let it sync (it downloads Gradle and libraries; needs internet).
3. Connect the phone with a USB cable and allow the debugging prompt on the phone.
4. Pick your phone in the device list at the top and press the green Run button. The app installs and opens.
5. When the app asks to show notifications, allow it.

To get a file you can copy to the phone: Build > Build Bundle(s) / APK(s) > Build APK(s), then copy app/build/outputs/apk/debug/app-debug.apk to the phone and open it.

## What is in Phase 1
- Home: title and icon, paste box (stub), Single video, folder list, Add new folder, Storage card (stub), Instagram accounts block (stub).
- Folder rows: tap the name to play, shuffle / scroll mode / sleep rings, remembered between launches. Scroll mode filled = Loop each, empty = Auto-scroll (default).
- Long-press a folder name: edit mode. Drag rows to re-order, drag a row onto the red bar to remove it from the list (files are not deleted), Done to leave.
- Player: swipe up/down, tap to pause/resume, folder name, "1 of 6", progress bar, Scroll ON/OFF (bottom right), back arrow, sleep timer, MP3 cover screen.
- Audio keeps playing with the screen off. Media notification; tapping it opens the playing video.
- Stubs show a short message and arrive in later phases: folder gallery (phase 2), Storage (phase 2), Recent links / downloads (phase 4).

## Known limits in this phase
- Only files directly inside a folder are played (not subfolders).
- The edit-mode list does not scroll; with many folders, remove or reorder a few at a time.
- Not compiled here: if Android Studio reports an error, paste it back and I will fix it.

## Added in Phase 2
- Folder icon on a folder row opens the gallery: 3 columns, video length on each tile, MP3 tiles with length.
- Tap a reel: it loops until you swipe up, then continues through the folder. The folder's sleep ring is respected.
- Long-press to select. "3 selected of 20", Select all, ticked and dimmed tiles. Share, Move and Delete in the bottom bar.
- Move opens the phone's file manager so you can pick any destination folder.
- Delete asks first. Files go to the Android recycle bin when possible; the dialog tells you if any cannot and will be deleted permanently.
- Storage card on Home shows used and free space and opens the Storage screen: folder sizes, five largest videos (Play, Move, Delete), Clear temp files, Free memory.
- Compress buttons are placeholders until Phase 3.
