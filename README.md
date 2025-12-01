# CalAlarm

An Android application that automatically creates alarms for upcoming calendar events.

## Overview

CalAlarm is works with any calendar provider (Google Calendar, DAVx5, Outlook, etc.) that syncs to the device's local calendar database. It operates fully on-device and does not require Google Play Services.

## Features

- Connects to device's local calendar (works with any calendar app)
- Subscribes to configured calendars' updates for new events
- Automatically schedules alarms at event start time
- Displays scheduled alarms in a list with an option to delete them
- Displays a full-screen alarm with sound and vibration when event starts
- Supports a configurable snooze delay
- Provides an optional persistent "Next alarm" notification showing the upcoming alarm

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
├── data/                 # Data layer
│   ├── local/            # Room database
│   ├── calendar/         # Calendar provider
│   └── repository/       # Repository pattern
├── domain/               # Business logic
├── service/              # Background services
└── ui/                   # UI layer (Compose)
    ├── alarmlist/        # Main screen
    ├── settings/         # Calendar selection
    ├── alarm/            # Full-screen alarm
    ├── permissions/      # Permission handling
    └── theme/            # Theme definitions
```

## Building

```bash
./gradlew build
./gradlew test
```

## License

GPL-3.0-or-later
