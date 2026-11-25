# Atoll

so I made Atoll cause I wanted my OnePlus 15 to look a bit more like an iPhone. ColorOS already has a pill but it only shows up when I'm recording my screen, running a timer or someone's connected to my hotspot. I just wanted it to stay there even when nothing was going on

you can pick the iPhone size you like, and I didn't want the card to be empty so you can put shortcuts, a note or your phone stats in there :3

## Install

the APK is in [Releases](https://github.com/IDanK0/atoll/releases). Once it's installed, open Atoll and follow the guide. It shows you where to allow notifications and turn on Live Alerts. Then you can turn the pill on.

I tried it on my OnePlus 15 with ColorOS 17. idk if the pill or the card works on HyperOS or OriginOS yet.

## Picking a size

you can match the size in millimeters or make the pill take up the same part of the screen as on the iPhone. You can also just set the width yourself.

on my phone it won't go below 348 px, so the 18 Pro and 18 Pro Max look the same. If the sizes are off on yours, there's a ruler in the app you can line up with the pill on your home screen.

## The card

tap the pill to open it. You can keep up to four shortcuts in there, write a note in Markdown, add a photo or check your phone stats. There's a counter too, and a mode for dice, coins and random numbers. If you'd rather have nothing there, pick Blank.

For the background I kept the presets, but you can pick your own color too, either solid or fading in from black. If you want to put the whole card together yourself, pick Template. You can edit the JSON in the app or import a file.

## How the pill works

Atoll posts a Live Update and ColorOS draws the pill. I put invisible characters in the short text of the notification (`shortCriticalText`) so ColorOS makes it wider.

The ruler is the only thing that needs "Display over other apps". Timer shortcuts go to the Clock app, there's no timer inside Atoll. The app doesn't use the internet, need accessibility access or read your other notifications.

## Build

if you want to build it yourself:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## License

[MIT](LICENSE)
