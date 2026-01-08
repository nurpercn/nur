package tr.testodasi.heuristic;

import java.util.ArrayList;
import java.util.List;

/**
 * Alternate entry-point: forces a constant 3 samples per project.
 *
 * <p>Behavior:
 * - Forces {@link Data#FIXED_SAMPLES}=3 (dominates both matrix-based and batch/CSV instances)
 * - Disables sample local-search (so samples cannot drift away from 3)
 * - Clamps {@link Data#SAMPLE_MAX} to 3 and prevents room-LS from invoking sample search
 *
 * <p>Then delegates to {@link Main} for the rest of CLI behavior.
 */
public final class MainFixedSamples3 {
  private MainFixedSamples3() {}

  public static void main(String[] args) {
    // Hard lock: 3 samples per project.
    Data.FIXED_SAMPLES = 3;
    Data.INITIAL_SAMPLES = 3;
    Data.SAMPLE_MAX = 3;
    Data.ENABLE_SAMPLE_INCREASE = false;
    Data.ROOM_LS_INCLUDE_SAMPLE_HEURISTIC = false;

    // Delegate to existing CLI, but remove flags that could re-enable sample search / override samples.
    Main.main(filterArgs(args));
  }

  static String[] filterArgs(String[] args) {
    if (args == null || args.length == 0) return new String[0];

    List<String> out = new ArrayList<>(args.length);
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a == null) continue;

      // Drop any "samples" overrides.
      if (startsWithIgnoreCase(a, "--samples=")) continue;
      if ("--samples".equalsIgnoreCase(a)) {
        // also skip the next token if present (value)
        if (i + 1 < args.length) i++;
        continue;
      }

      // Drop any sample-increase toggles.
      if (startsWithIgnoreCase(a, "--sampleIncrease=")) continue;
      if ("--sampleIncrease".equalsIgnoreCase(a)) {
        // Main supports optional next token (bool). Skip it if it's not another flag.
        if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) i++;
        continue;
      }

      // Drop roomLSIncludeSample; in fixed mode it must be false.
      if (startsWithIgnoreCase(a, "--roomLSIncludeSample=")) continue;
      if ("--roomLSIncludeSample".equalsIgnoreCase(a)) {
        if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) i++;
        continue;
      }

      out.add(a);
    }
    return out.toArray(new String[0]);
  }

  private static boolean startsWithIgnoreCase(String s, String prefix) {
    if (s == null || prefix == null) return false;
    if (s.length() < prefix.length()) return false;
    return s.regionMatches(true, 0, prefix, 0, prefix.length());
  }
}

