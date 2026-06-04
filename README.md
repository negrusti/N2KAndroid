# N2KAndroid

Android USB-host prototype for enumerating NMEA 2000 bus devices through the Mastervolt 77030100 USB Interface and compatible Mastervolt HID gateways.

The transport is ported from `C:\Gregor\Projects\NMEA2000Analyzer\MastervoltHidCapture.cs`:

- VID `0x1A64`, PID `0x0000`
- 64-byte HID payload reports
- up to 4 packed CAN frames per report
- 14-byte packed frame slots
- 29-bit CAN ID packing/unpacking
- ISO Requests for PGN `60928` Address Claim and PGN `126996` Product Information
- Address-claim NAME decoding
- Product-information fast-packet assembly and basic identity decoding

CI/CD builds the checked-in Gradle wrapper and uploads a debug APK artifact.
