# Tantal Clock

The clock for Tantal (`app.tantal.clock`). Our own Compose code over platform
APIs; not a fork of another app.

Done: alarms, world clock, timers and stopwatch. Planned: a home screen
widget and Live Updates for running timers. No network access.

## Building

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Needs JDK 21 and `local.properties` with `sdk.dir` pointing to an Android
SDK that has the `android-37.2` platform.

## Contributing

See the [contribution rules](https://github.com/tantal-os/.github/blob/main/CONTRIBUTING.md)
and the [security policy](https://github.com/tantal-os/.github/blob/main/SECURITY.md).
Report vulnerabilities privately, not in an issue.

## License

Apache-2.0, see `LICENSE`. The bundled Google Sans Flex font is under the
SIL Open Font License, see `app/GoogleSansFlex-OFL.txt`.
