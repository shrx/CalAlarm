# CalAlarm

An Android application that automatically creates alarms for upcoming calendar events.

## Overview

CalAlarm is works with any calendar provider (Google Calendar, DAVx5, Outlook, etc.) that syncs to the device's local calendar database.

## Features

- Connect to device's local calendar (works with any calendar app)
- Periodically check configured calendars for new events
- Automatically schedule alarms at event start time
- Display scheduled alarms in a list with an option to delete them
- Displays a full-screen alarm with sound and vibration when event starts

## Technical Details

- **Min SDK:** 34 (Android 14)
- **Target SDK:** 35 (Android 15)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Database:** Room
- **Background Work:** WorkManager + ContentObserver

## Project Structure

```
app/src/main/java/com/calalarm/
├── data/                     # Data layer
│   ├── local/               # Room database
│   ├── calendar/            # Calendar provider
│   └── repository/          # Repository pattern
├── domain/                  # Business logic
├── service/                 # Background services
└── ui/                      # UI layer (Compose)
    ├── alarmlist/          # Main screen
    ├── settings/           # Calendar selection
    ├── alarm/              # Full-screen alarm
    ├── permissions/        # Permission handling
    └── theme/              # Theme definitions
```

## Building

```bash
./gradlew build
```

## License

TBD
