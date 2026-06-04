# N2KAndroid

Android USB-host prototype for the Mastervolt 77030100 USB Interface and compatible Mastervolt HID gateways.

The transport is ported from `C:\Gregor\Projects\NMEA2000Analyzer\MastervoltHidCapture.cs`:

- VID `0x1A64`, PID `0x0000`
- 64-byte HID payload reports
- up to 4 packed CAN frames per report
- 14-byte packed frame slots
- 29-bit CAN ID packing/unpacking
- ISO Request for PGN `126996` Product Information

This project intentionally has no checked-in Gradle wrapper. CI/CD should provide Gradle or inject the wrapper during its build step.

