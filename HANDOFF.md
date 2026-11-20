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
- Done: notification log (v0.5), parse + Show-mode effect + voice + history (v0.6)
- v0.7: PhonePe notification detect (format unverified)
- Next: SMS backup (Najeef ne abhi mana kiya, baad me) (bank credit SMS ka real format chahiye), PhonePe notification package/format (log se verify), background announce (foreground service)
- Package names log se verify karne hain, guess nahi

## Setup zaruri (Android 15+/ColorOS) - notification text ke liye
- Android 15+ 'sensitive' notification ka text hide karta hai ('Sensitive notification content hidden'). Fix: Wireless debugging se `adb shell cmd appops set --user 0 com.chaman.qr RECEIVE_SENSITIVE_NOTIFICATIONS allow` (uninstall pe dobara karna padta hai, update pe nahi)
- ColorOS me adb grant ke liye Developer options me 'Disable system optimisation' (permission monitoring) ON karna pada; grant ke baad OFF karke bhi text asli aata raha (verified)
- Notification access ON + battery No restriction

## Rules
- Keys/tokens/passwords kabhi chat me paste nahi, kabhi file me hardcode nahi
- Naya step tabhi jab pichla device pe pass ho
- Payment details native SharedPreferences = source of truth, localStorage fallback
- Backup tabhi likhna jab UPI ID bhara ho (khali data se purana backup overwrite na ho)

## Status
- v0.8: (device test pending) PhonePe format mil gaya (screenshot se): grouped notification, title '2 new completed transaction updates', lines '· NAME has sent ₹X to your bank account BANK-1234' (incoming) aur '· ₹X has been sent to <upi>' (outgoing, ignore). Parser: line-by-line, nayi pattern 'NAME has sent ₹X to your/you', ALL CAPS naam -> Title Case. PhonePe notification me lines add hoti rehti hain, isliye per-notification line memory (pay_seen_lines): sirf NAYI lines payment maani jaati hain; listener connect hote hi existing lines absorb (purani payment announce na ho); notification dismiss pe memory clear
- v0.7: (device test chal raha) Alerts UI clean  Alerts UI clean: hint texts hata diye, floating gear icon (top right, sirf Alerts tab) -> bottom sheet settings (Notification access, App info, Battery, Reconnect, Awaaz, Debug log, Test Show effect). Log card title 'Log'. PhonePe detection (package com.phonepe.app) + tolerant parser (received/paid you/sent you; refund/cashback/request skip). PhonePe ka asli notification format abhi verify nahi hua: Debug log ON karke ₹1 mangwa ke log se dekhna
- v0.6: device pe pass. (details: Paytm 'Received ₹X from NAME' parse -> pay_events; Show mode credit effect (card flip -> green 'Received ₹X from NAME', confetti, auto back 12s); voice (TextToSpeech) + haptic; Alerts: Payments received history + today total + voice toggle + 'Test Show effect' button. Amount mismatch vs QR -> amber face)
- v0.5: device pe pass (Step 1 notification listener) Step 1 fix: v0.4 pe access ON tha par Paytm notification log nahi hui. Added diagnostics (service connected, seen count, last pkg), Reconnect/auto rebind on resume, Debug 'log all', wider text extraction
- v0.4: Step 1 notification listener + Alerts tab (device test fail: log empty)
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
