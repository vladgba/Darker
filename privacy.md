# Darker Privacy Policy

**Effective date:** 2026-10-10
**App:** Darker (`x.vladgba.darker`), an open-source screen dimmer for Android
**Developer:** Vladyslav Tishyn (vladgba), vladgba@gmail.com

## Summary

- Darker has **no internet permission**, so the app itself cannot send anything off your phone.
- The developer **receives no data** from the app: no analytics, no crash reports, no ads, no accounts, no third-party libraries.
- Darker **does not read your screen**. It only draws a dark layer over it.
- Everything Darker stores stays **on your phone**, inside the app's private storage.

The sections below explain what is stored locally, what each permission is for, and the few cases where *other* software (Android, Google Play) may handle data under its own policy.

## What the app stores on your phone

The app saves only its own settings:

- whether dimming is on or off;
- the dim level;
- whether it has already asked for notification permission, so it does not ask again.

Darker does not store anything about what is on your screen or how you use your phone.

Uninstalling the app, or clearing its data in Android settings, deletes all of this.

## Permissions

| Permission | Why |
|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Draws the dimming layer on top of other apps. Touches pass straight through it |
| Modify system settings (`WRITE_SETTINGS`) | Lets the brightness slider and widgets change the system screen brightness. Optional: you grant it in Android settings, and you can revoke it at any time |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | Keeps the dimming layer running while it is on, so Android does not remove it |
| Notifications (`POST_NOTIFICATIONS`) | Shows the ongoing "dimming is on" notification with a turn-off button. You can refuse it; dimming still works |

There is no internet, location, contacts, storage, microphone, camera or other permission.

## Accessibility service

Darker includes an **optional** accessibility service. Its only job is to host the dimming layer, because an accessibility overlay can also cover the status bar and notification shade and can go darker (up to 95%).

The service:

- is **off by default**, and runs only if you turn it on yourself in Android's accessibility settings;
- **cannot read window content** (it is declared with `canRetrieveWindowContent="false"`);
- only listens to events from Darker's own package, never from other apps, and ignores even those;
- does not observe, record or act on anything you type, tap or see.

You can turn it off at any time in Android's accessibility settings; Darker then falls back to the regular overlay.

## Data handled by other software

- **Android backup.** If you have turned on backup for your phone (for example, Google One / Google Drive backup), Android may include Darker's settings in that backup. This is controlled by you in Android settings and handled under your backup provider's policy; the developer has no access to it.
- **Google Play.** If you install from Google Play, Google handles installation and its own statistics under [Google's privacy policy](https://policies.google.com/privacy). Darker contains no Google libraries.

## Children

Darker collects no personal data from anyone, including children.

## Changes to this policy

If the app's handling of data changes, this policy will be updated before or together with that version, with a new effective date. The current version is always in the [project repository](https://github.com/vladgba/Darker).

## Contact

Questions or concerns: vladgba@gmail.com
