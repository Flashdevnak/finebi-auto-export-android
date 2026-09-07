# First-run setup

1. Connect Flashlink.
2. Open FineBI inside this app and sign in normally.
3. Press FineBI's normal Excel Export once. The app learns the export request, removes sessionId before persisting the template, and then enables unattended auto-export.
4. Keep Auto Export enabled and set battery usage to Unrestricted.

After setup, the app derives the lightweight `th_update_time` polling request from the captured export widget and forces HUB Select All before every automatic export.
