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
- Bridge: saveImage, shareImage, saveData/loadData (SharedPreferences), backupFile (Documents/ChamanQR)

## Rules
- Keys/tokens/passwords kabhi chat me paste nahi, kabhi file me hardcode nahi
- Naya step tabhi jab pichla device pe pass ho
- Payment details native SharedPreferences = source of truth, localStorage fallback
- Backup tabhi likhna jab UPI ID bhara ho (khali data se purana backup overwrite na ho)

## Status
- v0.2: (device test pending)
  - Fix: card pe naam/UPI ID/footer ka `fit()` bug (parseInt font weight pakad raha tha)
  - Payment details native storage me save + auto backup file + Restore button
  - CI: optional fixed keystore (secrets: KEYSTORE_B64, KEYSTORE_PASS, KEY_ALIAS) taaki update bina uninstall ho
- v0.1: base app

## Remaining
- Device test v0.2
- UI/UX improvements (ideas suggest kiye, Najeef ke confirm ka wait)
- Keystore secrets set karna (ek baar), uske baad pehla update ek baar uninstall karke install karna padega
