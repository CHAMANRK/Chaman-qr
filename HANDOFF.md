# HANDOFF: Chaman QR

## Kya hai
Android APK (WebView) jo QR generate karta hai + UPI Pay QR card banata hai. Owner: Najeef (Chaman, GitHub CHAMANRK).

## Stack
- WebView app: `app/src/main/assets/index.html` (saara UI + JS), `qr-code-styling.js` (lib)
- `MainActivity.java`: WebView + native bridge (`Android.*`)
- Build: GitHub Actions debug APK (`.github/workflows/build.yml`)

## Layout
- Tab 1 Generator: link/text/wifi/contact/email/phone/sms/upi, styles, logo
- Tab 2 Pay QR: canvas card (1080x1500), amount chips, Payment details (UPI ID, naam, note, footer, logo)
- Bridge: saveImage, shareImage, saveData/loadData (SharedPreferences), backupFile (Documents/ChamanQR), tick (haptic), setBright (full brightness + keep screen on)
- Pay QR: profiles (max 5, `P.profiles` + `P.cur`; amounts/style shared), Show full-screen mode, Payment details collapsed with summary + Edit/Done
- Release: CI har build pe `latest` pre-release banata hai, direct link: https://github.com/CHAMANRK/chaman-qr/releases/download/latest/chaman-qr.apk

- Alerts tab (v0.4): `NotifService.java` (NotificationListenerService) logs money-looking notifications (regex: ₹/rs/inr/credited/deposited/received/paid/debited) to SharedPreferences `notif_log` (last 50, local only); UI shows access status + log. Bridge: notifAccess, openNotifSettings, openAppInfo, openBattery, notifLog, clearNotifLog

## Payment detect plan (concept agreed)
- Primary: Paytm notification (PhonePe notification late aati hai). Paytm UPI ID @ptyes (profile se add hota hai, koi code limit nahi)
- Seen format (Paytm): title "Received ₹<amt> from <name>", text "Deposited in your <bank> - <last4> on <date> at <time>"
- Step 1: notification log (v0.4). Step 2: SMS (READ/RECEIVE_SMS, Ref no.). Step 3: match amount in ~5 min window + green screen + awaaz, dedupe by amount+time/Ref
- Package names log se verify karne hain, guess nahi

## Rules
- Keys/tokens/passwords kabhi chat me paste nahi, kabhi file me hardcode nahi
- Naya step tabhi jab pichla device pe pass ho
- Payment details native SharedPreferences = source of truth, localStorage fallback
- Backup tabhi likhna jab UPI ID bhara ho (khali data se purana backup overwrite na ho)

## Status
- v0.4: (device test pending) Step 1 notification listener + Alerts tab
- v0.3: (device test pending)
  - Pay QR: Show full-screen + brightness, collapsed details + Edit, multiple profiles, haptic + pop animation, Saved tick
  - CI: public GitHub Release `latest` with chaman-qr.apk
- v0.2: (device test pending)
  - Fix: card pe naam/UPI ID/footer ka `fit()` bug (parseInt font weight pakad raha tha)
  - Payment details native storage me save + auto backup file + Restore button
  - CI: optional fixed keystore (secrets: KEYSTORE_B64, KEYSTORE_PASS, KEY_ALIAS) taaki update bina uninstall ho
- v0.1: base app

## Remaining
- Device test v0.2 aur v0.3
- Baaki UI ideas (chips scroll row, sticky preview, extra line on card) agar chahiye
- Keystore secrets set karna (ek baar), uske baad pehla update ek baar uninstall karke install karna padega
