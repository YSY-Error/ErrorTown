# World generation warning investigation

## Observed baseline

On Paper `1.21.8-60`, the original `SummerTown-2.1.6.0.jar` logged `No key layers in MapLike[{}]` while creating a type `2` home at `00:14:52`. The same sequence then logged `Preparing start region` and successfully saved `SummerTownWorld/YSYError` during shutdown. This is therefore a warning/error emitted during world metadata or generator decoding, not proof that creation failed.

## Source trace

- `com.ErrorTown.CommandListener` selects the home type and delegates world creation.
- Normal homes use `WorldType.NORMAL`.
- Flat homes use `WorldType.FLAT`.
- Airland homes call `WorldCreator.generator(new CustomChunkGenerator())`.
- `com.Util.CustomChunkGenerator` places the configured sky-island platform; it does not provide a vanilla flat `layers` map.
- Existing-home loading creates a `WorldCreator` from the saved world name and does not rewrite `level.dat`.

## Current conclusion

There is not enough evidence to attribute the warning to an invalid ErrorTown argument. The world completed creation and save, and the baseline log does not show the warning recurring during shutdown or reload. The maintained artifact deliberately leaves world generation unchanged until an isolated type `2` creation test and copied metadata inspection distinguish Paper's legacy generator decoder from plugin input.

## Safe investigation procedure

1. Stop the live server and copy only the server into `ErrorTown/test-server`; never point the test server at the live `plugins/SummerTown`, `SummerTownWorld`, or player data.
2. Install the maintained JAR under the test server's `plugins/SummerTown-2.1.6.0.jar` name and start Paper.
3. With a disposable test player, run one type `2` creation and save the complete `logs/latest.log`.
4. Stop cleanly, then run `scripts/Inspect-ErrorTownWorld.ps1 -WorldPath test-server/SummerTownWorld/<player>`.
5. Compare `level.dat`, `level.dat_old`, `uid.dat`, and `paper-world.yml` hashes and the warning timestamp. Do not edit those files while diagnosing.

Until this test is completed, document the warning as an unresolved Paper/world-format compatibility warning. Do not delete a world, rewrite generator settings, or change `WorldType.FLAT` in production based on this message alone.
