# Android Rewarded Ads Setup

This app uses AdMob rewarded ads to let Android users earn GenStudio credits.

## Local Defaults

The Android build defaults to Google's rewarded-ad test IDs:

- `ADMOB_APP_ID=ca-app-pub-3940256099942544~3347511713`
- `ADMOB_REWARDED_AD_UNIT_ID=ca-app-pub-3940256099942544/5224354917`
- `ANDROID_STARTER_CREDITS=0`
- `REWARDED_AD_CREDIT_AMOUNT=10`

These defaults are safe for local development and test builds. Replace them before publishing a production APK.

## Android Configuration

Set production values in `LuminaAndroid/.env`:

```properties
ONEIMAGE_API_BASE_URL=https://genstudio.web.app/
ONEIMAGE_WEB_APP_URL=https://genstudio.web.app/
ADMOB_APP_ID=ca-app-pub-3838820812386239~6623010406
ADMOB_REWARDED_AD_UNIT_ID=ca-app-pub-3838820812386239/8866030362
ANDROID_STARTER_CREDITS=0
REWARDED_AD_CREDIT_AMOUNT=10
```

`ANDROID_STARTER_CREDITS` should stay at `0` when new users must buy or earn credits before using server compute.

## Backend Configuration

Set matching backend environment values in `OneImage/.env` or Firebase Functions config/secrets:

```properties
ADMOB_REWARDED_AD_UNIT_ID=ca-app-pub-3838820812386239/8866030362
REWARDED_AD_CREDIT_AMOUNT=10
ANDROID_STARTER_CREDITS=0
ADMOB_REWARDED_SSV_KEYS_URL=https://www.gstatic.com/admob/reward/verifier-keys.json
```

The backend grants credits only from AdMob server-side verification callbacks. The Android app does not write credits directly.

## AdMob Console

In AdMob, configure the rewarded ad unit server-side verification callback URL:

```text
https://genstudio.web.app/api/mobile/admob/rewarded-ssv
```

Current production rewarded ad unit:

- AdMob app ID: `ca-app-pub-3838820812386239~6623010406`
- Ad unit name: `rewards`
- Ad format: `Rewarded`
- Ad unit ID: `ca-app-pub-3838820812386239/8866030362`
- Reward settings: `10 Reward`

The app sets the Firebase UID as the AdMob SSV `user_id`. The backend verifies Google's callback signature, checks the configured ad unit, and grants credits once per AdMob `transaction_id`.

The AdMob console's "Verify URL" tool sends a setup-only callback with placeholder values such as `ad_unit=1234567890` and `transaction_id=123456789`. The backend may return success for that setup check, but it must not grant credits from it. Real credit success still requires a signed reward callback from the app flow with the user's Firebase UID.

## Verification

Run these checks after changing the ad-credit flow:

```powershell
cd C:\Users\denta\source\repos\OneStudio\LuminaAndroid
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest

cd C:\Users\denta\source\repos\OneStudio\OneImage
npm.cmd run lint
npm.cmd test
```

Before production release, deploy the backend first, then install a build that uses the production AdMob app ID and rewarded ad unit ID.
