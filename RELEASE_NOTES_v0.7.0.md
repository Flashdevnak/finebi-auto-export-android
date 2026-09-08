# FineBI Auto Export v0.7.0

- Replaces Gmail App Password/SMTP with Google Identity Services authorization and Gmail API send-only access.
- Requests `https://www.googleapis.com/auth/gmail.send`; OAuth access tokens are short-lived and are not persisted by the app.
- Sends XLSX only after FineBI export validation succeeds; keeps duplicate-version protection and retry/backoff behavior.
- Keeps recipient and CC settings locally; legacy SMTP sender/App Password settings are removed during migration.
- Adds Google connect, account change, disconnect, and test-email flow in Auto Email settings.
- Hardens Daily UI routing: both legacy mobile activity entry points redirect to `DailyMainActivity`, preventing old task stacks from restoring the legacy status UI.
- Keeps in-app GitHub Release updater and stable test signing identity for install-over updates.

Google OAuth runtime requires the Android OAuth client for package `com.flashdevnak.finebiautoexport` and the app signing SHA-1 to be registered in the Google Cloud project with Gmail API enabled.
